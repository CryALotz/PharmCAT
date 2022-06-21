---
parent: Using PharmCAT
title: VCF Preprocessor
permalink: using/VCF-Preprocessor/
nav_order: 3
render_with_liquid: false
---
# PharmCAT VCF Preprocessor

The PharmCAT VCF preprocessor is a script that can preprocess VCF files for PharmCAT.

This tool will:

1. Strip out PGx positions that PharmCAT does not care about.
2. Break down a multi-sample VCF to multiple single-sample VCF files.
3. Automatically download the necessary Human Reference Genome Sequence FASTA and index files from the NIH FTP site if they are not provided.
4. __Perform VCF normalization__ - a standardization process that turns VCF into a parsimonious, left-aligned variant representation format (as discussed in [Unified Representation of Genetic Variants](https://doi.org/10.1093/bioinformatics/btv112) by Tan, Abecasis, and Kang).
5. Normalize the multiallelic variant representation to PharmCAT's expectation.
6. Process a subset of samples if a sample file is provided.

The PharmCAT VCF preprocessing produces two types of **output**: 

1. One or more PharmCAT-ready, single-sample VCF file(s)
2. A report of missing pharmacogenomics core allele defining positions in user's input


## How to run the PharmCAT VCF preprocessing tool

### Prerequisite

We assume that the input VCF files are prepared following the [Variant Call Format (VCF) Version >= 4.1](https://samtools.github.io/hts-specs/VCFv4.2.pdf).

To run the tool, you need to download the following bioinformatic tools:
* [bcftools >= v1.15](http://www.htslib.org/download/)
* [htslib >= v1.15](http://www.htslib.org/download/)

We assume a working python3 installation with necessary dependencies:
* python >= 3.9.4
* pandas >= 1.4.2
* scikit-allel >= 1.3.5

To install necessary python packages, run the following code
```console
$ pip3 install -r PharmCAT_VCF_Preprocess_py3_requirements.txt
```

### Command line

To normalize and prepare a VCF file (single or multiple samples) for PharmCAT, run the following code substituted with proper arguments/inputs:

```console
$ python3 PharmCAT_VCF_Preprocess.py --input_vcf path/to/compressed_vcf.vcf.gz
```

**Mandatory** argument: _either_ `--input_vcf` _or_ `--input_list`.

--input_vcf
: Path to a single VCF file

--input_list
: Path to file containing list of paths to VCF files (one per line), sorted by chromosome position. All VCF files must have the same set of samples.  Use this when data for a sample has been split among multiple files (e.g. VCF files from large cohorts, such as UK Biobank).

  Example valid `input_list` file:
  ```
  chr1_set1.vcf
  chr1_set2.vcf
  chr2_set1.vcf
  chr2_set2.vcf
  ...
  ```
  Example invalid `input_list` file:
  ```
  chr3_set2.vcf
  chr2_set2.vcf
  chr1_set1.vcf
  chr1_set2.vcf
  ...
  ```

VCF files can have more than 1 sample and should be bgzip compressed.  If not bgzip compressed, they will be automatically bgzipped.


**Optional** arguments:

--ref_pgx_vcf
: A sorted, compressed VCF of PGx core allele defining positions used by PharmCAT, by default, `pharmcat_positions.vcf.bgz` under the current working directory. You can find this VCF in the `pharmcat_preprocessor-<release_version>.tar.gz` available from the PharmCAT GitHub releases page.
 
--ref_seq
: The [GRCh38.p14](https://www.ncbi.nlm.nih.gov/assembly/GCF_000001405.40/) FASTA file. The FASTA file has to be decompressed and indexed (.fai). These mandatory files will be automatically downloaded (~1GB) to the current working directory if not provided by user (see [Notes](#notes) for details).

--sample_file
: The list of samples to be processed and prepared for PharmCAT. The file should contain one sample per line.

--path_to_bcftools
: Bcftools must be installed. This argument is optional if users can run bcftools directly using the command line `bcftools <commands>`. Alternatively, users can download and compile [bcftools](http://www.htslib.org/download/) and provide the path to the executable bcftools program as `/path/to/executable/bcftools`.

--path_to_tabix
: Similar to bcftools, tabix must be installed. Tabix is a part of the [htslib](http://www.htslib.org/download/). If users cannot directly run tabix using the command line `tabix <commands>`, the alternative is to download and compile [htslib](http://www.htslib.org/download/) and provide the path to the executable tabix program as `/path/to/executable/tabix` which should be under the htslib program folder.

--path_to_bgzip
: Similar to tabix, bgzip must be installed. Bgzip is a part of the [htslib](http://www.htslib.org/download/). If users cannot directly run bgzip using the command line `bgzip <commands>`, the alternative is to download and compile [htslib](http://www.htslib.org/download/) and provide the path to the executable bgzip program as `/path/to/executable/bgzip` which should be under the htslib program folder.

--output_folder
: Output a compressed PharmCAT VCF file to `/path/to/output/pharmcat/vcf`. The default is the parent directory of the input.

--output_prefix
: Prefix of the output VCF files. Default is `pharmcat_ready_vcf`.

--keep_intermediate_files
: This option will help you save useful intermediate files, for example, a normalized, multiallelic VCF named `input_prefix.pgx_regions.normalized.multiallelic.vcf.gz`, which will include all PGx regions from the first position to the last one in each chromosome as listed in the reference PGx VCF.

-0 <span class="altArg"><br />or --missing_to_ref</span>
: This option will add missing PGx positions to the output. Missing PGx positions are those whose genotypes are all missing "./." in every single sample.
  * This option will not convert "./." to "0/0" if any other sample has non-missing genotype at this position as these missing calls are likely missing for good reasons.
  * This **SHOULD ONLY BE USED** if you are sure your data is reference at the missing positions
    instead of unreadable/uncallable at those positions. Running PharmCAT with positions as missing vs reference can lead to different results.


**Output**
1. 1 or more PharmCAT-ready VCF file(s), which will be named as `output_prefix>_<sample_ID>.vcf`, for example, `pharmcat_ready_vcf.sample_1.vcf`, `pharmcat_ready_vcf.sample_2.vcf`, etc.
2. The report of missing PGx positions, which will be named as `<output_prefix>.missing_pgx_var.vcf.gz`, for example `pharmcat_ready_vcf.missing_pgx_var.vcf.gz`. This file only reports positions that are missing in all samples.
  
  If `--missing_to_ref` is turned on, you can use this report to trace positions whose genotypes are missing in all samples (`./.`) in the original input but have now been added into the output VCF(s) as reference (`0/0`).


## Tutorial

### Case 1 - single-sample VCF
Imagine we have a VCF named *"test_1.vcf.gz"* to be used in PharmCAT.
```console
$ gunzip -c test_1.vcf.gz
$ cat test_1.vcf
<...header truncated...>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	FORMAT	Sample_1
2	233760233	rs3064744	C	CAT	.	PASS	.	GT	1/0
2	233760233	rs3064744	CAT	C	.	PASS	.	GT	0/0
2	233760233	rs3064744	C	CATAT	.	PASS	.	GT	0/1
7	117548628	.	GTTTTTTTA	GTTTTTA	.	PASS	.	GT	0/1
```

Command to run the PharmCAT VCF preprocessor:
```console
$ python3 PharmCAT_VCF_Preprocess.py --input_vcf test_1.vcf.gz
```

VCF preprocessor will return two files in this test case.
1. one named *"pharmcat_ready_vcf.Sample_1.vcf"*, which is a PharmCAT-ready VCF
2. the other named *"pharmcat_ready_vcf.missing_pgx_var.vcf.gz"* as a report of missing PGx positions.

To be noted, the chr7 variant is not used in PharmCAT and as such, was accordingly removed by the PharmCAT VCF preprocessor.

```console
$ cat reference.Sample_1.vcf
<...header truncated...>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	FORMAT	Sample_1
chr2	233760233	rs3064744	CAT	C,CATATAT,CATAT	.	PASS	PX=UGT1A1	3/2

$ gunzip -c reference.missing_pgx_var.vcf.gz
$ cat reference.missing_pgx_var.vcf
<...header truncated...>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	FORMAT	PharmCAT
chr1	97078987	rs114096998	G	T	.	PASS	PX=DPYD	GT	0/0
chr1	97078993	rs148799944	C	G	.	PASS	PX=DPYD	GT	0/0
chr1	97079005	rs140114515	C	T	.	PASS	PX=DPYD	GT	0/0
<...truncated...>
```

### Case 2 - multi-sample VCF
Imagine we have a VCF named *"test_2.vcf.gz"* that has two samples with different sample names from the case 1.
```console
$ gunzip -c example.vcf.gz
<...header truncated...>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	FORMAT	Sample_1	Sample_2
1	97740414	rs72549309	AATGA	A	.	PASS	.	GT	1/0	0/1
2	233760233	rs3064744	C	CAT	.	PASS	.	GT	1/0	0/0
2	233760233	rs3064744	CAT	C	.	PASS	.	GT	0/0	0/1
2	233760233	rs3064744	C	CATAT	.	PASS	.	GT	0/1	1/0
7	117548628	.	GTTTTTTTA	GTTTTTA	.	PASS	.	GT	0/1	1/0
10	94942212	rs1304490498	AAGAAATGGAA	A	.	PASS	.	GT	1/0	0/1
13	48037826	rs777311140	G	GCGGG	.	PASS	.	GT	1/0	0/1
19	38499645	rs121918596	GGAG	G	.	PASS	.	GT	1/0	0/1
22	42130727	.	AG	A	.	PASS	.	GT	1/0	0/1
M	1555	.	G	A	PASS	.	GT	1/0	0/1
```

Command to run the PharmCAT VCF preprocessor:
```console
$ python3 PharmCAT_VCF_Preprocess.py --input_vcf test_2.vcf.gz
```

VCF preprocessor will return three (3) files in this test case.
1. one named *"pharmcat_ready_vcf.s1.vcf"*. Note that the output PharmCAT-ready VCFs will use the exact sample names from the input VCF.
2. one named *"pharmcat_ready_vcf.s2.vcf"*
3. the third named *"pharmcat_ready_vcf.missing_pgx_var.vcf.gz"* as a report of missing PGx positions.

```console
$ cat reference.Sample_1.vcf
<...header truncated...>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	FORMAT	Sample_1
chr1    97740410        rs72549309      GATGA   G       .       PASS    PX=DPYD       GT      1/0
chr2    233760233       rs3064744       CAT     C,CATATAT,CATAT .       PASS    PX=UGT1A1 GT      3/2
chr10   94942205        rs1304490498    CAATGGAAAGA     C       .       PASS    PX=CYP2C9     GT      1/0
chr13   48037825        rs777311140     C       CGCGG   .       PASS    PX=NUDT15     GT      1/0
chr19   38499644        rs121918596     TGGA    T       .       PASS    PX=RYR1       GT      1/0

$ cat reference.Sample_2.vcf
<...header truncated...>
#CHROM  POS     ID      REF     ALT     QUAL    FILTER  INFO    FORMAT  Sample_2
chr1    97740410        rs72549309      GATGA   G       .       PASS    PX=DPYD       GT      0/1
chr2    233760233       rs3064744       CAT     C,CATATAT,CATAT .       PASS    PX=UGT1A1 GT      2/1
chr10   94942205        rs1304490498    CAATGGAAAGA     C       .       PASS    PX=CYP2C9     GT      0/1
chr13   48037825        rs777311140     C       CGCGG   .       PASS    PX=NUDT15     GT      0/1
chr19   38499644        rs121918596     TGGA    T       .       PASS    PX=RYR1       GT      0/1


$ gunzip -c reference.missing_pgx_var.vcf.gz
<...header truncated...>
#CHROM	POS	ID	REF	ALT	QUAL	FILTER	INFO	FORMAT	PharmCAT
chr1	97078987	rs114096998	G	T	.	PASS	PX=DPYD	GT	0/0
chr1	97078993	rs148799944	C	G	.	PASS	PX=DPYD	GT	0/0
chr1	97079005	rs140114515	C	T	.	PASS	PX=DPYD	GT	0/0
<...truncated...>
```

### Notes

As of June 2022, the latest Human Reference Genome assembly is [**GRCh38.p14**](https://www.ncbi.nlm.nih.gov/assembly/GCF_000001405.40).  It is available through the [NCBI RefSeq FTP site](https://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/001/405/GCF_000001405.40_GRCh38.p14/GCF_000001405.40_GRCh38.p14_genomic.fna.gz).

PharmCAT takes this file and prepares it for use with the following commands:

```console
# curl -#fSL https://ftp.ncbi.nlm.nih.gov/genomes/all/GCF/000/001/405/GCF_000001405.40_GRCh38.p14/GCF_000001405.40_GRCh38.p14_genomic.fna.gz -o genomic.fna.gz
# gunzip genomic.fna.gz
# cat genomic.fna |  sed -r 's/^>(NC_0+([0-9]+)\.*)/>chr\2 \1/g' > chrfix.fna
# bgzip -c chrfix.fna > reference.fna.bgz
# samtools faidx reference.fna.bgz
# tar -czvf GRCh38_reference_fasta.tar reference.fna.bgz reference.fna.bgz.fai reference.fna.bgz.gzi
```

PharmCAT makes this indexed FASTA files available on [Zenodo](https://zenodo.org/record/6640691). 
