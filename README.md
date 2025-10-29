# PyramidizeQuPathCLI
QuPath TIFF Merger Pipeline
A high-performance pipeline for merging field-of-view TIFF images into pyramidal OME-TIFF format using QuPath's command-line interface. Designed for HPC environments with support for SLURM job scheduling and parallel processing.
Overview
This pipeline converts multiple TIFF field-of-view images into single pyramidal OME-TIFF files by parsing spatial metadata from baseline TIFF tags. It features a three-tier architecture for scalable batch processing:

Core Script (merge_tiffs.groovy) - QuPath Groovy script for TIFF merging
Local Controller (Bash) - Processes individual folders
Master Controller (Bash) - Orchestrates parallel processing across multiple folders

Features

✅ Pyramidal OME-TIFF output for efficient viewing
✅ Automatic spatial positioning from TIFF metadata
✅ Parallel processing support (multi-folder and multi-tile)
✅ SLURM job scheduler integration
✅ Configurable via text-based config files
✅ Lossless compression
✅ Compatible with QuPath 0.4.3+

Requirements
Software

QuPath 0.4.3 or later
Bash 4.0+
SLURM (optional, for HPC cluster scheduling)

System

Sufficient RAM for image processing (depends on image size)
Disk space for output OME-TIFF files

Input Requirements

TIFF images must contain baseline TIFF tags:

X_RESOLUTION and Y_RESOLUTION
X_POSITION and Y_POSITION
IMAGE_WIDTH and IMAGE_LENGTH

Usage:
sbatch run-pyramidize_and_stitch.s qupath_unmix_tiles-config.txt

Example config in repo
