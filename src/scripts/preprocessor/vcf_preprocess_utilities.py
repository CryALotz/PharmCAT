#! /usr/bin/env python
__author__ = 'BinglanLi'

import allel
import gzip
import os
import re
import shutil
import subprocess
import sys
import tempfile
import traceback
import urllib.parse
import urllib.request

import vcf_preprocess_exceptions as Exceptions


def byte_decoder(a):
    """ Decode byte data into utf-8 """
    return a.decode("utf-8")


def quit_if_exists(path):
    """report an error if the file exists"""
    if os.path.exists(path):
        print('File already exists. Delete if you want to proceed: %s' % (path))
        sys.exit()


def download_from_url(url, download_to_dir, save_to_file=None):
    """download from an url"""

    remote_basename = os.path.basename(urllib.parse.urlparse(url).path)
    if remote_basename:
        # Download to a temp file. If a download succeeds, rename the temp file.
        # If a download fails, the function will throw an exception. The temp file will be removed.

        local_path = os.path.join(download_to_dir, remote_basename) if not save_to_file else save_to_file
        quit_if_exists(local_path)
        with tempfile.TemporaryDirectory(dir=download_to_dir) as temp_dir:
            temp_download_path = os.path.join(temp_dir, 'temp_' + remote_basename)
            with urllib.request.urlopen(url) as response:
                with open(temp_download_path, 'wb') as out_file:
                    print('Downloading from \"%s\"\n\tto \"%s\"' % (url, local_path))
                    shutil.copyfileobj(response, out_file)
            os.rename(temp_download_path, local_path)
        return local_path
    else:
        raise Exceptions.InvalidURL(url)


def decompress_gz_file(path):
    path_to_decompressed = os.path.splitext(path)[0]
    with gzip.open(path, 'rb') as f_in:
        with open(path_to_decompressed, 'wb') as f_out:
            print('Decompressing %s' % (path))
            shutil.copyfileobj(f_in, f_out)
    return path_to_decompressed


_GRCH38_FASTA_FTP_DIR = '/genomes/all/GCA/000/001/405/GCA_000001405.15_GRCh38/seqs_for_alignment_pipelines.ucsc_ids/'
_GRCH38_FASTA_FILE = 'GCA_000001405.15_GRCh38_no_alt_analysis_set.fna'


def download_grch38_ref_fasta_and_index(download_to_dir, save_to_file=None):
    """download the human reference genome sequence GRChh38/hg38 from the NIH FTP site"""

    # download ftp web address (dated Feb 2021)
    url_grch38_fasta = 'ftp://ftp.ncbi.nlm.nih.gov' + _GRCH38_FASTA_FTP_DIR + _GRCH38_FASTA_FILE + '.gz'
    url_grch38_fasta_index = 'ftp://ftp.ncbi.nlm.nih.gov' + _GRCH38_FASTA_FTP_DIR + _GRCH38_FASTA_FILE + '.fai'

    # download and prepare files for vcf normalization using the bcftools
    path_to_ref_seq = download_from_url(url_grch38_fasta, download_to_dir)
    path_to_ref_seq = decompress_gz_file(path_to_ref_seq)
    download_from_url(url_grch38_fasta_index, download_to_dir)

    if save_to_file:
        os.rename(path_to_ref_seq, save_to_file)
        os.rename(path_to_ref_seq + '.fai', save_to_file + '.fai')
        return save_to_file
    return path_to_ref_seq


def tabix_index_vcf(tabix_executable_path, vcf_path):
    """
    index the input vcf using tabix, and the output index file will be written to the working directory

    tabix commands are exclusively "tabix -p vcf <input_vcf>", which generates an index file (.tbi) for an input file (<input_file>) whose file type is specified by "-p vcf".
    .tbi will be output to the current working directory by default.
    """

    try:
        subprocess.run([tabix_executable_path, '-p', 'vcf', vcf_path])
    except Exception as e:
        print('Error: cannot index the file: %s' % vcf_path)
        traceback.print_exception(type(e), e, e.__traceback__)
        sys.exit(1)


def running_bcftools(list_bcftools_command, show_msg=None):
    """
    run the bcftools following the commands stored in the list_bcftools_command

    "bcftools <common_options> <input_vcf>".
    "-Oz" (capitalized letter O) specifies the output type as compressed VCF (z). "-o" writes to a file rather than to
    default standard output.
    "--no-version" will cease appending version and command line information to the output VCF header.
    "-s sample_ID(s)" comma-separated list of samples to include or exclude if prefixed with "^".
    "-r chr|chr:pos|chr:beg-end|chr:beg-[,…]" extracts comma-separated list of regions
    """

    print("%s" % show_msg) if show_msg else print("Running [ %s ]" % (' '.join(list_bcftools_command)))

    p = subprocess.run(list_bcftools_command, stderr=subprocess.PIPE)
    if p.returncode != 0:
        print('Error: ', p.stderr.decode("utf-8"))
        sys.exit(1)


def obtain_vcf_sample_list(bcftools_executable_path, path_to_vcf):
    """
    obtain a list of samples from the input VCF

    "bcftools query <options> <input_vcf>". For bcftools common options, see running_bcftools().
    "-l" list sample names and exit.
    Samples are delimited by '\\\\n' and the last line ends as 'last_sample_ID\\\\n\\\\n'.
    """

    output = subprocess.check_output([bcftools_executable_path, 'query', '-l', path_to_vcf], universal_newlines=True)
    vcf_sample_list = output.split('\n')[:-1]  # remove the black line at the end
    return vcf_sample_list


def remove_vcf_and_index(path_to_vcf):
    """remove the compressed vcf as well as the index file"""

    try:
        os.remove(path_to_vcf)
        os.remove(path_to_vcf + '.tbi')
        print("Removed intermediate files:\n\t%s\n\t%s" % (path_to_vcf, path_to_vcf + '.tbi'))
    except OSError as error_remove_tmp:
        print("Error: %s : %s" % (path_to_vcf, error_remove_tmp.strerror))


def _get_vcf_pos_min_max(positions, flanking_bp=100):
    """ given input positions, return "<min_pos>-<max_pos>"  """
    return '-'.join([str(min(positions) - flanking_bp), str(max(positions) + flanking_bp)])


def _has_chr_string(input_vcf):
    with gzip.open(input_vcf) as f:
        for line in f:
            try:
                line = byte_decoder(line)
            except:
                line = line
            if line[0] != '#':
                fields = line.rstrip().split()
                has_string = 'true' if 'chr' in fields[0] else 'false'
                break

    return has_string

def extract_regions_from_single_file(bcftools_executable_path, tabix_executable_path, input_vcf,
        input_ref_pgx_vcf, output_dir, output_prefix, sample_list):
    """
    Rename chromosomes in input vcf according to a chr-renaming mapping file.
    Extract pgx regions from input_vcf based on the input_ref_pgx_vcf.

    "bcftools annotate <options> <input_vcf>". For bcftools common options, see running_bcftools().
    "--rename-chrs" renames chromosomes according to the map in file_rename_chrs.
    """

    # output path
    path_output = os.path.join(output_dir, 'PharmCAT_preprocess_' + output_prefix + '.pgx_regions.vcf.gz')

    # create index if not already existed
    if not os.path.exists(input_vcf + '.tbi'):
        tabix_index_vcf(tabix_executable_path, input_vcf)

    # obtain PGx regions to be extracted
    input_ref_pgx_pos_pandas = allel.vcf_to_dataframe(input_ref_pgx_vcf)
    input_ref_pgx_pos_pandas['CHROM'] = input_ref_pgx_pos_pandas['CHROM'].replace({'chr': ''}, regex=True).astype(
        str).astype(int)
    ref_pgx_regions = input_ref_pgx_pos_pandas.groupby(['CHROM'])['POS'].agg(_get_vcf_pos_min_max).reset_index()

    # define the regions to be extracted
    if _has_chr_string(input_vcf) == "true":
        # add chromosome name with leading 'chr' to the VCF header
        ref_pgx_regions = ",".join(
            ref_pgx_regions.apply(lambda row: ':'.join(row.values.astype(str)), axis=1).replace({'^': 'chr'},
                                                                                                regex=True))

        # run bcftools
        with tempfile.NamedTemporaryFile(mode="w", dir=output_dir) as file_sample_list:
            for single_sample in sample_list:
                file_sample_list.write("%s\n" % single_sample)
            file_sample_list.seek(0)

            bcftools_command = [bcftools_executable_path, 'view', '-S', file_sample_list.name,
                                                      '-r', ref_pgx_regions, '-Oz', '-o', path_output, input_vcf]
            running_bcftools(bcftools_command,
                             show_msg='Extracting PGx regions for %s.' % input_vcf)
    elif _has_chr_string(input_vcf) == "false":
        # chromosome names
        chr_names_wo_strings = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
                                "17", "18", "19", "20", "21", "22"]
        chr_names_w_strings = ["chr1", "chr2", "chr3", "chr4", "chr5", "chr6", "chr7", "chr8", "chr9", "chr10", "chr11",
                               "chr12", "chr13", "chr14", "chr15", "chr16", "chr17", "chr18", "chr19", "chr20", "chr21",
                               "chr22"]
        # check if two chr arrays are of the same length
        if len(chr_names_wo_strings) != len(chr_names_w_strings):
            print("Error in internal chromosome mapping arrays")
            sys.exit()

        # create a temporary chromosome mapping file
        with tempfile.NamedTemporaryFile(mode="w+", dir=output_dir) as file_rename_chrs:
            for i in range(len(chr_names_wo_strings)):
                file_rename_chrs.write(chr_names_wo_strings[i] + "\t" + chr_names_w_strings[i] + "\n")
            file_rename_chrs.seek(0)

            # extract pgx regions and rename chromosomes
            ref_pgx_regions = ",".join(ref_pgx_regions.apply(lambda row: ':'.join(row.values.astype(str)), axis=1))

            # generate a temporary file of sample names
            with tempfile.NamedTemporaryFile(mode="w", dir=output_dir) as file_sample_list:
                for single_sample in sample_list:
                    file_sample_list.write("%s\n" % single_sample)
                file_sample_list.seek(0)

                # run bcftools
                bcftools_command = [bcftools_executable_path, 'annotate', '-S',
                                                          file_sample_list.name, '--rename-chrs', file_rename_chrs.name,
                                                          '-r', ref_pgx_regions, '-Oz', '-o', path_output, input_vcf]
                running_bcftools(bcftools_command,
                                 show_msg='Extracting PGx regions and modifying chromosome names for %s.' %input_vcf)
    else:
        print("The CHROM column does not comply with either 'chr##' or '##' format.")

    # index the output PGx file
    tabix_index_vcf(tabix_executable_path, path_output)

    return path_output


def extract_regions_from_multiple_files(bcftools_executable_path, tabix_executable_path, input_list,
                                                 path_to_ref_seq, input_ref_pgx_vcf, output_dir, output_prefix, sample_list):
    """
    iterarte through the list of input files
    """

    path_output = os.path.join(output_dir, 'PharmCAT_preprocess_' + output_prefix + '.pgx_regions.vcf.gz')

    with tempfile.TemporaryDirectory(dir=output_dir) as temp_dir:
        preprocessed_file_list = []
        i = 1
        with open(input_list, 'r') as file:
            for line in file:
                line = line.strip()
                if os.path.isfile(line):
                    temp_output_prefix = output_prefix + '_' + str(i)
                    single_file = extract_regions_from_single_file(bcftools_executable_path, tabix_executable_path,
                                                                   line, input_ref_pgx_vcf, temp_dir, temp_output_prefix, sample_list)
                    preprocessed_file_list.append(single_file)
                    i += 1
                else:
                    print("Warning: Skip %s because the file does not exist." % line)
        file.close()

        # concatenate files, requiring input file list
        with tempfile.NamedTemporaryFile(mode = 'w+', dir = output_dir) as temp_concat:
            with tempfile.NamedTemporaryFile(mode = 'w+', dir = output_dir) as temp_file_list:
                for j in range(len(preprocessed_file_list)):
                    temp_file_list.write(preprocessed_file_list[j] + "\n")
                temp_file_list.seek(0)

                # concatenate vcfs
                bcftools_command = [bcftools_executable_path, 'concat', '-a', '-f', temp_file_list.name, '-Oz', '-o', temp_concat.name]
                running_bcftools(bcftools_command,
                                 show_msg='Concatenating chromosome VCFs.')
                # normalize concatenated vcf
                bcftools_command = [bcftools_executable_path, 'norm', '-m+', '-c', 'ws', '-Oz', '-o',
                                    path_output, '-f', path_to_ref_seq, temp_concat.name]
                running_bcftools(bcftools_command,
                                 show_msg='Normalizing chromosome VCFs.')

    # index the concatenated VCF
    tabix_index_vcf(tabix_executable_path, path_output)
    return path_output


def normalize_vcf(bcftools_executable_path, tabix_executable_path, input_vcf, path_to_ref_seq):
    """
    Normalize the input VCF against the human reference genome sequence GRCh38/hg38

    "bcftools norm <options> <input_vcf>". For bcftools common options, see running_bcftools().
    "-m +|-" joins biallelic sites into multiallelic records (+) and convert multiallelic records into uniallelic format (-).
    "-f <ref_seq_fasta>" reference sequence. Supplying this option turns on left-alignment and normalization.
    "-c ws" when incorrect or missing REF allele is encountered, warn (w) and set/fix(s) bad sites.  's' will swap
    alleles and update GT and AC acounts. Importantly, s will NOT fix strand issues in a VCF.
    """

    path_output = os.path.splitext(os.path.splitext(input_vcf)[0])[0] + '.normalized.vcf.gz'

    bcftools_command = [bcftools_executable_path, 'norm', '--no-version', '-m-', '-c', 'ws', '-Oz', '-o',
                        path_output, '-f', path_to_ref_seq, input_vcf]
    running_bcftools(bcftools_command, show_msg='Normalizing VCF')
    tabix_index_vcf(tabix_executable_path, path_output)

    return path_output


def filter_pgx_variants(bcftools_executable_path, tabix_executable_path, input_vcf, path_to_ref_seq, input_ref_pgx_vcf, output_dir, output_prefix):
    """
    Extract specific pgx positions that are present in the reference PGx VCF
    Generate a report of PGx positions that are missing in the input VCF

    "bcftools isec <options> <input_vcf>". For bcftools common options, see running_bcftools().
    "-c none" only records with the same CHR, POS, REF and ALT are considered identical
    "-C" outputs positions present only in the first file but missing in the others.
    "-w" lists input files to output given as 1-based indices. "-w1" extracts and writes records only present in the first file (the reference PGx positions).
    """

    path_output = os.path.splitext(os.path.splitext(input_vcf)[0])[0] + '.multiallelic.vcf.gz'

    with tempfile.TemporaryDirectory(suffix='temp_extract_variants', dir=output_dir) as temp_dir:
        # convert reference PGx variants to the uniallelic format
        ref_pgx_uniallelic = os.path.join(temp_dir, 'temp_reference_pgx_variants_sorted_uniallelic.vcf.gz')
        bcftools_command = [bcftools_executable_path, 'norm', '--no-version', '-m-', '-c', 'ws', '-f', path_to_ref_seq,
                            '-Oz', '-o', ref_pgx_uniallelic, input_ref_pgx_vcf]
        running_bcftools(bcftools_command, show_msg='Preparing the reference PGx VCF')
        tabix_index_vcf(tabix_executable_path, ref_pgx_uniallelic)

        # extract only the required PGx positions
        input_pgx_variants_only = os.path.join(temp_dir, 'temp_input_pgx_variants_only.vcf.gz')
        # output the exact matching variants (both position and alleles) in the second input file
        bcftools_command = [bcftools_executable_path, 'isec', '--no-version', '-c', 'none', '-n=2', '-w2',
                            '-Oz', '-o', input_pgx_variants_only,
                            ref_pgx_uniallelic, input_vcf]
        running_bcftools(bcftools_command, show_msg='Retaining only PGx positions')
        tabix_index_vcf(tabix_executable_path, input_pgx_variants_only)

        # merging the filtered input with the reference PGx positions for the next step
        merge_vcf = os.path.join(temp_dir, 'temp_merged.vcf.gz')
        bcftools_command = [bcftools_executable_path, 'merge', '--no-version', '-m', 'both',
                            '-Oz', '-o', merge_vcf, input_pgx_variants_only, ref_pgx_uniallelic]
        running_bcftools(bcftools_command,
                         show_msg='Trimming file')
        tabix_index_vcf(tabix_executable_path, merge_vcf)

        # enforce the output to comply with PharmCAT format
        multiallelic_vcf = os.path.join(temp_dir, 'temp_multiallelic.vcf.gz')
        bcftools_command = [bcftools_executable_path, 'norm', '--no-version', '-m+', '-N',
                            '-Oz', '-o', multiallelic_vcf, merge_vcf]
        running_bcftools(bcftools_command,
                         show_msg='Enforcing the variant representation per PharmCAT')
        tabix_index_vcf(tabix_executable_path, multiallelic_vcf)

        # remove the artificial PharmCAT sample
        bcftools_command = [bcftools_executable_path, 'view', '-s', '^PharmCAT',
                            '-Oz', '-o', path_output, multiallelic_vcf]
        running_bcftools(bcftools_command, show_msg='Trimming file')
        tabix_index_vcf(tabix_executable_path, path_output)

        # report missing positions in the input VCF
        report_missing_variants = os.path.join(output_dir, output_prefix + '.missing_pgx_var.vcf.gz')
        bcftools_command = [bcftools_executable_path, 'isec', '--no-version', '-c', 'none', '-w1',
                            '-Oz', '-o', report_missing_variants, '-C', ref_pgx_uniallelic, input_vcf]
        running_bcftools(bcftools_command,
                         show_msg='Generating a report of missing PGx allele defining positions')

    return path_output


def output_pharmcat_ready_vcf(bcftools_executable_path, input_vcf, output_dir, output_prefix, sample_list):
    """
    iteratively write to a PharmCAT-ready VCF for each sample

    "bcftools view <options> <input_vcf>". For bcftools common options, see running_bcftools().
    "-U" exclude sites without a called genotype, i.e., GT = './.'
    """

    for single_sample in sample_list:
        output_file_name = os.path.join(output_dir, output_prefix + '.' + single_sample + '.vcf')
        bcftools_command = [bcftools_executable_path, 'view', '--no-version', '-U', '-Ov',
                                                         '-o', output_file_name, '-s', single_sample, input_vcf]
        running_bcftools(bcftools_command,
                         show_msg='Generating a PharmCAT-ready VCF for ' + single_sample)

