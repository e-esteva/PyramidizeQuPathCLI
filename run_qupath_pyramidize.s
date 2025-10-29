#!/bin/bash
#SBATCH --mem=300GB
#SBATCH --time=0-12
#SBATCH --out=pyramidize_%j.out
#SBATCH --error=pyramidize_%j.err
#SBATCH --partition=a100_short

module load qupath/0.4.3

source qupath_unmix_tiles-config.txt

QuPath script -a ${input_folder} -a ${output_name} qupath_merge_unmixed_files_to_pyramidv0.4.3.groovy
