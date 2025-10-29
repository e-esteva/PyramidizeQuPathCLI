/**
 * Convert TIFF fields of view to a pyramidal OME-TIFF.
 * Modified to accept folder path as command-line argument.
 * Compatible with QuPath v0.4.3
 *
 * Usage:
 *   QuPath script -a "path/to/folder" script.groovy
 *   QuPath script -a "path/to/folder" -a "output.ome.tif" script.groovy
 *
 * Locations are parsed from the baseline TIFF tags, therefore these need to be set.
 *
 * @author Pete Bankhead (modified for command-line use)
 */

import qupath.lib.common.GeneralTools
import qupath.lib.images.servers.ImageServerProvider
import qupath.lib.images.servers.ImageServers
import qupath.lib.images.servers.SparseImageServer
import qupath.lib.images.writers.ome.OMEPyramidWriter
import qupath.lib.regions.ImageRegion

import javax.imageio.ImageIO
import javax.imageio.plugins.tiff.BaselineTIFFTagSet
import javax.imageio.plugins.tiff.TIFFDirectory
import java.awt.image.BufferedImage

// Parse command-line arguments for QuPath 0.4.3
def args = []
try {
    // QuPath 0.4.3 uses 'args' variable directly in script context
    if (binding.hasVariable('args')) {
        args = binding.getVariable('args')
    }
} catch (Exception e) {
    print "Could not access args: ${e.message}"
}

if (!args || args.size() < 1) {
    print 'ERROR: No folder path provided!'
    print 'Usage: QuPath script -a "path/to/folder" script.groovy'
    print 'Or with output file: QuPath script -a "path/to/folder" -a "output.ome.tif" script.groovy'
    return
}

String folderPath = args[0].toString()
File dir = new File(folderPath)

// Validate directory
if (!dir.exists()) {
    print "ERROR: Directory does not exist: ${folderPath}"
    return
}
if (!dir.isDirectory()) {
    print "ERROR: Path is not a directory: ${folderPath}"
    return
}

print "Scanning directory: ${dir.getAbsolutePath()}"

// Get all TIFF files in the directory (excluding OME-TIFF)
List<File> files = dir.listFiles().findAll {
    return it.isFile() && 
           (it.getName().toLowerCase().endsWith('.tif') || it.getName().toLowerCase().endsWith('.tiff')) &&
           !it.getName().toLowerCase().endsWith('.ome.tif')
}

if (!files || files.isEmpty()) {
    print "ERROR: No TIFF files found in directory: ${folderPath}"
    return
}

print "Found ${files.size()} TIFF files"

// Determine output file
File fileOutput
if (args.size() >= 2) {
    // Output file specified as second argument
    String outputPath = args[1].toString()
    fileOutput = new File(outputPath)
    if (!fileOutput.isAbsolute()) {
        // Make it relative to the input directory
        fileOutput = new File(dir, outputPath)
    }
} else {
    // Auto-generate output name based on directory name
    String baseName = dir.getName()
    fileOutput = new File(dir, baseName + '.ome.tif')
    int count = 1
    while (fileOutput.exists()) {
        fileOutput = new File(dir, baseName + '-' + count + '.ome.tif')
        count++
    }
}

print "Output file will be: ${fileOutput.getAbsolutePath()}"

// Parse image regions & create a sparse server
print 'Parsing regions from ' + files.size() + ' files...'
def builder = new SparseImageServer.Builder()
files.parallelStream().forEach { f ->
    def region = parseRegion(f)
    if (region == null) {
        print 'WARN: Could not parse region for ' + f
        return
    }
    def serverBuilder = ImageServerProvider.getPreferredUriImageSupport(BufferedImage.class, f.toURI().toString()).getBuilders().get(0)
    builder.jsonRegion(region, 1.0, serverBuilder)
}

print 'Building server...'
def server = builder.build()
server = ImageServers.pyramidalize(server)

// Write the pyramid
long startTime = System.currentTimeMillis()
String pathOutput = fileOutput.getAbsolutePath()
new OMEPyramidWriter.Builder(server)
        .downsamples(server.getPreferredDownsamples())
        .tileSize(512)
        .parallelize()
        .losslessCompression()
        .build()
        .writePyramid(pathOutput)
        
long endTime = System.currentTimeMillis()
print('Image written to ' + pathOutput + ' in ' + GeneralTools.formatNumber((endTime - startTime)/1000.0, 1) + ' s')
server.close()

print 'Done!'


// Helper functions
static ImageRegion parseRegion(File file, int z = 0, int t = 0) {
    if (checkTIFF(file)) {
        try {
            return parseRegionFromTIFF(file, z, t)
        } catch (Exception e) {
            print e.getLocalizedMessage()
        }
    }
}

/**
 * Check for TIFF 'magic number'.
 */
static boolean checkTIFF(File file) {
    file.withInputStream {
        def bytes = it.readNBytes(4)
        short byteOrder = toShort(bytes[0], bytes[1])
        int val
        if (byteOrder == 0x4949) {
            // Little-endian
            val = toShort(bytes[3], bytes[2])
        } else if (byteOrder == 0x4d4d) {
            val = toShort(bytes[2], bytes[3])
        } else
            return false
        return val == 42 || val == 43
    }
}

/**
 * Combine two bytes to create a short, in the given order
 */
static short toShort(byte b1, byte b2) {
    return (b1 << 8) + (b2 << 0)
}

/**
 * Parse an ImageRegion from a TIFF image, using the metadata.
 */
static ImageRegion parseRegionFromTIFF(File file, int z = 0, int t = 0) {
    int x, y, width, height
    file.withInputStream {
        def reader = ImageIO.getImageReadersByFormatName("TIFF").next()
        reader.setInput(ImageIO.createImageInputStream(it))
        def metadata = reader.getImageMetadata(0)
        def tiffDir = TIFFDirectory.createFromMetadata(metadata)

        double xRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_RESOLUTION)
        double yRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_RESOLUTION)

        double xPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_POSITION)
        double yPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_POSITION)

        width = tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_WIDTH).getAsLong(0) as int
        height = tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_LENGTH).getAsLong(0) as int

        x = Math.round(xRes * xPos) as int
        y = Math.round(yRes * yPos) as int
    }
    return ImageRegion.createInstance(x, y, width, height, z, t)
}

/**
 * Helper for parsing rational from TIFF metadata.
 */
static double getRational(TIFFDirectory tiffDir, int tag) {
    long[] rational = tiffDir.getTIFFField(tag).getAsRational(0);
    return rational[0] / (double)rational[1];
}
