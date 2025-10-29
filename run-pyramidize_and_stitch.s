#!/bin/bash
#SBATCH --mem=50GB
#SBATCH --time=0-8
#SBATCH --error=p_s_%j.err
#SBATCH --out=p_s_%j.out

p_s_configFile=$1

echo ${p_s_configFile}

source ${p_s_configFile}
echo ${workDir}


# run p_s module
function p_s_module {
    local job_id=($(sbatch --export=configfile=$configFile --mail-user=${user} --array=1-${sample_count} --partition=a100_short ${p_s_module_Path}))
    echo ${job_id[3]}
}



### MODULE 1:
echo p_s at `date`
mod1_job=$(p_s_module)
echo Finished performing QC on fastqs at `date`

