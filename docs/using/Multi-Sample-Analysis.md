---
title: Multi-Sample Analysis
parent: Using PharmCAT
nav_order: 5
---

# Multi-Sample Analysis
{: .no_toc }

As of March 2022, PharmCAT only takes a single-sample VCF. If a multi-sample VCF is provided, only the first sample will be annotated. However, PharmCAT can generate a PGx report in a matter of seconds for a preprocessed VCF. The fast runtime of PharmCAT allows you to batch-annotate VCFs, with a little help from a bit of scripting, to scale from a few dozen samples to a biobank-scale cohorts in an efficient manner.

This documentation shares concrete examples of how to use PharmCAT to batch process multiple samples in a High-Performance Computing (HPC) environment.

We expect readers to be familiar with using both PharmCAT's [VCF preprocessor](/using/VCF-Preprocessor) and the core [PharmCAT tool](/using/Running-PharmCAT).  In addition to the documentation, we also have a [tutorial](https://github.com/PharmGKB/PharmCAT-tutorial) available that walks you through working with real genetic data sets. 

---

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Example using the Stanford Sherlock HPC

This part uses the VCFs on multiple GeT-RM samples[1](#reference) generated from the 30x high-coverage WGS sequencing performed by the New York Genome Center (NYGC)[2](#reference). The job was run on the Stanford Sherlock cluster, a High-Performance Computing (HPC) cluster which uses Slurm as an open-source resource manager and job scheduler. The scripts to run PharmCAT on the Stanford Sherlock cluster is written in shell programming language and can be easily adapted to another HPC environment supported by a different job scheduler.

### Preprocessing the data

This section provides examples for working with different types of input VCFs.  For the full script, see  [PharmCAT-tutorial/src/02_VCF_preprocessing.sh](https://github.com/PharmGKB/PharmCAT-tutorial/blob/main/src/).

#### Case 1 - single-sample VCFs

If your genetic data is already stored in single-sample VCFs, you are one step closer to running the PharmCAT. We recommend the users to still run the PharmCAT VCF preprocessor to ensure the appropriate variant representation format, which can be achieved by the following command.

On the command line:
```console
$ python3 PharmCAT_VCF_Preprocess.py --input_vcf <single_sample_vcf>
```

For example:
```console
$ cd PharmCAT-tutorial/
$ cat data/single_sample_vcf_list.txt
data/PharmCAT_tutorial_get-rm_wgs_30x_grch38.NA18526.vcf.gz
data/PharmCAT_tutorial_get-rm_wgs_30x_grch38.NA18565.vcf.gz
data/PharmCAT_tutorial_get-rm_wgs_30x_grch38.NA18861.vcf.gz
```

Sample script:
```shell
# run the preprocessor iteratively across single-sample VCFs
for SINGLE_VCF in $(cat data/single_sample_vcf_list.txt)
do
  # run the PharmCAT VCF preprocessor for a single-sample VCF
  python3 PharmCAT_VCF_Preprocess.py --input_vcf "$SINGLE_VCF"
done
```

#### Case 2 - multi-sample VCF

Population- or biobank-scale VCFs most likely come in multi-sample format. The PharmCAT VCF preprocessor is designed to help the users with this case and produce multiple single-sample VCFs that PharmCAT requires. The simplest command to preprocess a multi-sample VCF is as follows.

On the command line:
```console
$ python3 PharmCAT_VCF_Preprocess.py --input_vcf <multi_sample_vcf>
```

Sample script:
```shell
cd PharmCAT-tutorial/
python3 PharmCAT_VCF_Preprocess.py --input_vcf data/PharmCAT_tutorial_get-rm_wgs_30x_grch38.vcf.gz
```

#### Case 3 - multi-sample VCF divided by chromosome or into consecutive genetic blocks

As sometimes seen with large-scale genetic studies, the genetic data may be divided into multiple by-chromosome VCFs or VCFs with consecutive genetic blocks. The PharmCAT VCF preprocessor can manage this type of genetic data sets by taking a list of VCFs as the input.

On the command line:
```console
$ python3 PharmCAT_VCF_Preprocess.py --input_list <list_of_input_vcf>
```

Sample script:
```shell
cd PharmCAT-tutorial/
# run the PharmCAT VCF preprocessor for multiple VCFs with non-overlapping genetic regions of the same cohort
python3 PharmCAT_VCF_Preprocess.py --input_list data/input_vcf_list.txt
```

###  Running PharmCAT

Assuming the users have run the PharmCAT VCF preprocessor to generate multiple single-sample VCFs which is named such as `pharmcat_ready_vcf.<sample_id>.vcf`. Use the following command to batch annotate multiple VCFs using PharmCAT. A full example can be found at [PharmCAT-tutorial/src/03_PharmCAT.sh](https://github.com/PharmGKB/PharmCAT-tutorial/blob/main/src/03_PharmCAT.sh).

On the command line:
```console
$ java -jar <path_to_the_latest_pharmcat_jar> -vcf <single_sample_vcf>
```

Sample script:
```shell
cd PharmCAT-tutorial/
# running multiple samples
for SINGLE_SAMPLE in $(cat data/test_get-rm_samples.txt)
do
  # always use the latest PharmCAT
  java -jar pharmcat.jar -vcf results/pharmcat_ready/pharmcat_ready."$SINGLE_SAMPLE".vcf
done
```

The output is a set of PGx reports in HTML format named as `pharmcat.<sample_id>.report.html`.

#### Batch outside calls

To incorporate outside PGx calls with PharmCAT for multiple samples, the users have to prepare the outside PGx calls of each sample in separate files and supply the individual file as outside calls to the PharmCAT.

### Running individual components

You can run the individual modules of PharmCAT on multiple samples in a similar manner to how you run the whole PharmCAT pipeline. This is useful if you are interested in understanding population PGx and specifically obtaining PGx frequencies (named alleles, diplotypes, or metabolizer phenotypes) in your cohort. To do so, you can run the following commands against your data. More details and a tutorial can be found at [PharmCAT-tutorial/src/03_PharmCAT.sh](https://github.com/PharmGKB/PharmCAT-tutorial/blob/main/src/03_PharmCAT.sh).

#### Named Allele Matcher

On the command line:
```console
$ java -jar <path_to_the_latest_pharmcat_jar> -matcher -vcf <sample.vcf>
```

Sample script:
```shell
cd PharmCAT-tutorial/
for SINGLE_SAMPLE in "$(cat data/test_get-rm_samples.txt)"
do
  java -jar pharmcat.jar -matcher -vcf results/pharmcat_ready/pharmcat_ready_vcf."$SINGLE_SAMPLE".vcf
done
```

#### Phenotyper - use the Named Allele Matcher JSON data

On the command line:
```console
$ java -jar <path_to_the_latest_pharmcat_jar> -phenotyper -pi <sample.match.json>
```

Sample script:
```shell
cd PharmCAT-tutorial/
for SINGLE_SAMPLE in "$(cat data/test_get-rm_samples.txt)"
do
  java -jar pharmcat.jar -phenotyper -pi results/pharmcat_ready/"$SINGLE_SAMPLE".match.json
done
```

#### Reporter

On the command line:
```console
$ java -jar <path_to_the_latest_pharmcat_jar> -reporter -ri <sample.phenotype.json> -reporterJson
```

Sample script:
```shell
cd PharmCAT-tutorial/
for SINGLE_SAMPLE in "$(cat <sample_list.txt>)"
do
  java -jar pharmcat.jar -reporter -ri "$SINGLE_SAMPLE".phenotype.json -reporterJson \
    -t 'Report for '"$SINGLE_SAMPLE"
done
```

These commands yield `Named Allele Matcher`, `Phenotyper`, and `Reporter` results for each individual separately in the format of JSON files. We encourage the users to explore and perform data analysis using the rich content in these JSON files, which can be easily achieved using auxiliary JSON libraries and data analysis packages in R or python.

### Extracting PharmCAT JSON content into TSV

We also provide accessory R scripts that organize and extract the content from the [Named Allele Matcher](https://github.com/PharmGKB/PharmCAT-tutorial/blob/main/src/organize_pharmcat_named_allele_matcher_results.R) or [Phenotyper](https://github.com/PharmGKB/PharmCAT-tutorial/blob/main/src/organize_pharmcat_phenotyper_results.R) JSON outputs into tab-separated values (TSV) files. Here is an example TSV that the users will obtain from the provided R scripts.

|samples|gene |diplotype                                      |haplotype_1            |haplotype_2            |haplotype_1_variants|haplotype_2_variants|missing_positions|
|-------|-----|-----------------------------------------------|-----------------------|-----------------------|--------------------|--------------------|-----------------|
|NA18526|ABCG2|rs2231142 reference (G)/rs2231142 reference (G)|rs2231142 reference (G)|rs2231142 reference (G)|88131171_G          |88131171_G          |NULL             |
|NA18526|CYP3A5|\*1/\*1                                          |\*1                     |\*1                     |99652770_T;99665212_C;99672916_T|99652770_T;99665212_C;99672916_T|99660516;99676198|
|NA18526|SLCO1B1|\*1/\*15                                         |\*1                     |\*15                    |21176804_A;21176868_A;21176879_C;21176898_G;<...truncated for visual clarity...>|21176804_G;21176868_A;21176879_C;<...truncated for visual clarity...>|21172734;<...truncated for visual clarity...>|
|NA18565|ABCG2|rs2231142 reference (G)/rs2231142 reference (G)|rs2231142 reference (G)|rs2231142 reference (G)|88131171_G          |88131171_G          |NULL             |
|NA18565|CYP3A5|\*1/\*3                                          |\*1                     |\*3                     |99652770_T;99665212_C;99672916_T|99652770_T;99665212_C;99672916_C|99660516;99676198|
|NA18565|SLCO1B1|\*1/\*1                                          |\*1                     |\*1                     |21176804_A;21176868_A;21176879_C;<...truncated for visual clarity...>|21176804_A;21176868_A;21176879_C;<...truncated for visual clarity...>|21172734;;<...truncated for visual clarity...>|
|NA18861|CYP3A5|\*1/\*1                                          |\*1                     |\*1                     |99652770_T;99665212_C;99672916_T|99652770_T;99665212_C;99672916_T|99660516;99676198|
|NA18861|SLCO1B1|\*1/\*43                                         |\*1                     |\*43                    |21176804_A;21176868_A;21176879_C;<...truncated for visual clarity...>|21176804_G;21176868_A;21176879_A;<...truncated for visual clarity...>|21172734;;<...truncated for visual clarity...>|


#### Extracting the PharmCAT Named Allele Matcher JSON data into a TSV file
```shell
cd PharmCAT-tutorial/
SCRIPT_PATH=src/organize_pharmcat_named_allele_matcher_results.R
MATCHER_DIR=results/pharmcat_named_allele_matcher/
MATCHER_PATTERN=pharmcat_named_allele_matcher*json
PROJECT_DIR="$PWD"
Rscript  “$SCRIPT_PATH” \
--input-dir "$MATCHER_DIR" \
--input-file-pattern "$MATCHER_PATTERN" \
--output-dir "$PROJECT_DIR"
```

#### Extracting the PharmCAT Phenotyper JSON data into a TSV file

```shell
cd PharmCAT-tutorial/
SCRIPT_PATH=src/organize_pharmcat_phenotyper_results.R
PHENOTYPER_DIR=results/pharmcat_phenotyper/
PHENOTYPER_PATTERN=pharmcat_phenotyper*json
PROJECT_DIR="$PWD"
Rscript  "$SCRIPT_PATH" \
--input-dir "$PHENOTYPER_DIR" \
--input-file-pattern "$PHENOTYPER_PATTERN" \
--output-dir "$PROJECT_DIR"
```

---

## Example with the Penn Medicine Biobank

This section provides an alternative example which is carried out on the Penn Medicine Biobank data. This example provides scripts that convert PharmCAT JSON files into a Python DataFrame and CSV/TSV

The Stanford and UPenn PharmCAT teams independently developed methods for post-processing multiple samples from PharmCAT. This method, developed by UPenn, takes the output JSON files from multiple PharmCAT runs and converts them to a DataFrame object for use in Python or export to CSV/TSV.

### Preprocessing the data

Before proceeding, you should preprocess your multi-sample VCF or single sample VCFs using [PharmCAT's VCF preprocessor](/using/VCF-Preprocessor).

### Running PharmCAT

Running PharmCAT on the single-sample VCFs is pretty straightforward. First you need to create a text file with all of all the output file names from the preprocessor. This can easily be generated with the command:

```console
$ ls <preprocess_out_dir> | grep ".vcf$" > pharmcat_inputs.txt`
```

Verify that this contains all the samples by taking the line count (`wc -l pharmcat_inputs.txt`) and ensuring it equals the number of samples.

Then you need to use a for-loop to run PharmCAT repeatedly on each input file. The `-reporterJson` flag ensures that PharmCAT outputs JSON files for each sample containing the calls.

```shell
BASE_DIR="~/project/preprocess_out"  # the output directory from the preprocessor
OUT_DIR="pharmcat_out/"  # where to put the PharmCAT output
for i in $(cat pharmcat_inputs.txt) # a file with the filenames of your preprocessed VCFs
do
    SAMPLE=`echo  $i | cut -d "." -f 2` # the sample name is in the preprocessor output filename
    # always use the latest version of PharmCAT
    java -jar pharmcat.jar -vcf ${BASE_DIR}/${i} -bf ${SAMPLE} -reporterJson -o ${OUT_DIR}
done
```

### Reading the PharmCAT calls into Python

Here is a exemplary python output for the PharmCAT Named Allele Matcher:

|SAMPLE_ID|CACNA1S            |CFTR                  |CYP2B6|CYP2C19|rs12777823|CYP2C9|<...truncated for visual clarity...>|
|---------|-------------------|----------------------|------|-------|----------|------|------------------------------------|
|SAMPLE_1 |Reference/Reference|No CPIC variants found|\*1/\*1 |\*1/\*1  |G/G       |\*1/\*1 |<...truncated for visual clarity...>|
|SAMPLE_2 |Reference/Reference|No CPIC variants found|\*1/\*1 |\*1/\*1  |G/G       |\*1/\*1 |<...truncated for visual clarity...>|
|SAMPLE_3 |Reference/Reference|No CPIC variants found|\*1/\*1 |\*1/\*1  |G/G       |\*1/\*1 |<...truncated for visual clarity...>|


Below is Python3 code for converting the JSON files from PharmCAT Named Allele Matcher and Phenotyper into Pandas DataFrames, which can be exported to CSV or processed further in python. Because of the long runtime of this process, we recommend saving the calls to CSV and loading the data from CSV when needed, instead of regenerating the DataFrames many times.

```python
# Need to give a list of sample IDs
sample_list = [x.strip() for x in open("sample_ids.txt", "r").readlines()]
# PharmCAT output directory
base_dir = "~/project/pharmcat_out/"

# The following list specifies which genes should be pulled from the PharmCAT output. Be sure to include any new genes that get added to PharmCAT if they are relevant to you.
genelist = ["CACNA1S","CFTR","CYP2B6","CYP2C19","rs12777823","CYP2C9","CYP3A5","CYP4F2","DPYD","IFNL3/4","NUDT15","RYR1","TPMT","UGT1A1","VKORC1","SLCO1B1"]
columns = ["PID"] + genelist


for pid in sample_list:
    pid = str(pid)
    # Load in the JSON
    sample_file = "{0}/{1}.report.json".format(base_dir, pid.strip())
    sample_json = json.load(open(sample_file, "r"))

    # Build a dictionary that stores the genotype and phenotype for each gene from the JSON
    gene2genotype = dict([(key, "NA") for key in genelist])
    gene2phenotype = dict([(key, "NA") for key in genelist])
    for genotype in sample_json["genotypes"]:
        gene = genotype["gene"]
        # since there can be multiple genotypes called due to ambiguity, join them with ';'
        gene2genotype[gene] = ";".join(set(genotype["calls"]))
        gene2phenotype[gene] = ";".join(set(genotype["phenotype"]))

    # Optional section: code to extract rs12777823 for Warfarin (remove rs12777823 from genelist if deleting this section)
    for call in sample_json["geneCalls"]:
        if call["gene"] == "CYP2C9":
            for variant in call["variantsOfInterest"]:
                if variant["dbSnpId"] == "rs12777823":
                    gene2genotype["rs12777823"] = variant['call']
                    if gene2genotype["rs12777823"] is None:
                        gene2genotype["rs12777823"] = "NA"
                    gene2genotype["rs12777823"] = gene2genotype["rs12777823"].replace("|", "/")

    # Convert the dictionaries into DataFrames
	genotypes = pd.DataFrame(columns=columns)
	phenotypes = pd.DataFrame(columns=columns)
    ## Convert the dictionary into lists corresponding to DataFrame rows (one per sample)
    geno_row = [pid.strip()] + [gene2genotype[gene] for gene in gene2genotype]
    pheno_row = [pid.strip()] + [gene2phenotype[gene] for gene in gene2phenotype]

    ## Add each row to the DataFrames
    genotypes = genotypes.append(pd.Series(geno_row, index=genotypes.columns), ignore_index=True)
    phenotypes = phenotypes.append(pd.Series(pheno_row, index=phenotypes.columns), ignore_index=True)

    # Optional section: re-encode phenotypes from Warfarin-related genes
    phenotypes["CYP4F2"] = genotypes["CYP4F2"].apply(lambda x: "*3" in x).replace({False:"not actionable", True:"actionable"})
    phenotypes["VKORC1"] = genotypes["VKORC1"].apply(lambda x: "variant" in x).replace({False:"not actionable", True:"actionable"})
    phenotypes["IFNL3/4"] = genotypes["IFNL3/4"].apply(lambda x: "variant" in x).replace({False:"not actionable", True:"actionable"})
    phenotypes["rs12777823"] = genotypes["rs12777823"].apply(lambda x: "A" in x).replace({False:"not actionable", True:"actionable"})

genotypes = genotypes.set_index("PID")
phenotypes = phenotypes.set_index("PID")

# Output the DataFrames as CSV
genotypes.to_csv("genotypes.csv")
phenotypes.to_csv("phenotypes.csv")
```

The `genotypes` and `phenotypes` DataFrames should have the sample ID as the index, with one sample per row and one gene per column.

---

## Reference
1. Pratt, V. M. et al. Characterization of 137 Genomic DNA Reference Materials for 28 Pharmacogenetic Genes: A GeT-RM Collaborative Project. J Mol Diagn 18, 109–123 (2016).
2. Byrska-Bishop, M. et al. High coverage whole genome sequencing of the expanded 1000 Genomes Project cohort including 602 trios. 2021.02.06.430068 (2021) doi:10.1101/2021.02.06.430068.