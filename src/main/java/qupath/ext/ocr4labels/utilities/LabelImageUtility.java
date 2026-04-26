package qupath.ext.ocr4labels.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.Collection;

/**
 * Utility class for retrieving and working with label images from whole slide images.
 * Handles different naming conventions used by various slide scanner vendors.
 * Users can customize the keywords to search for in OCR Settings.
 */
public class LabelImageUtility {

    private static final Logger logger = LoggerFactory.getLogger(LabelImageUtility.class);

    /**
     * Gets the current list of keywords to search for in associated image names.
     * These are configurable through OCR Settings.
     */
    private static String[] getLabelKeywords() {
        String keywords = OCRPreferences.getLabelImageKeywords();
        if (keywords == null || keywords.isEmpty()) {
            return new String[]{"label", "barcode"};
        }
        return Arrays.stream(keywords.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private LabelImageUtility() {
        // Utility class - prevent instantiation
    }

    /**
     * Checks if a label image is available for the given ImageData.
     * Does not retrieve the image, only checks for existence.
     *
     * @param imageData The ImageData to check
     * @return true if a label image exists, false otherwise
     */
    public static boolean isLabelImageAvailable(ImageData<?> imageData) {
        if (imageData == null) {
            return false;
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                return false;
            }

            Collection<String> associatedImages = server.getAssociatedImageList();
            if (associatedImages == null || associatedImages.isEmpty()) {
                return false;
            }

            // Check for names containing label keywords (case-insensitive)
            String[] keywords = getLabelKeywords();
            for (String imageName : associatedImages) {
                String lowerName = imageName.toLowerCase();
                for (String keyword : keywords) {
                    if (lowerName.contains(keyword.toLowerCase())) {
                        return true;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            logger.error("Error checking for label image availability", e);
            return false;
        }
    }

    /**
     * Retrieves the label image from the given ImageData.
     * Tries multiple naming conventions and returns the first match found.
     *
     * @param imageData The ImageData to retrieve the label from
     * @return The label image as BufferedImage, or null if not available
     */
    public static BufferedImage retrieveLabelImage(ImageData<?> imageData) {
        if (imageData == null) {
            logger.warn("Cannot retrieve label image from null ImageData");
            return null;
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                logger.warn("ImageServer is null");
                return null;
            }

            Collection<String> associatedImages = server.getAssociatedImageList();
            if (associatedImages == null || associatedImages.isEmpty()) {
                logger.debug("No associated images available for: {}", server.getPath());
                return null;
            }

            logger.debug("Available associated images: {}", associatedImages);

            // Try finding any image containing label keywords (case-insensitive)
            String[] keywords = getLabelKeywords();
            for (String imageName : associatedImages) {
                String lowerName = imageName.toLowerCase();
                for (String keyword : keywords) {
                    if (lowerName.contains(keyword.toLowerCase())) {
                        logger.info("Found label image by keyword '{}': {}", keyword, imageName);
                        BufferedImage img = retrieveImageByName(server, imageName);
                        if (img != null) {
                            return normalizeToEightBit(img);
                        }
                    }
                }
            }

            logger.warn("No label image found for: {}", server.getPath());
            return null;

        } catch (Exception e) {
            logger.error("Error retrieving label image", e);
            return null;
        }
    }

    /**
     * Retrieves a specific associated image by name.
     *
     * @param server    The ImageServer
     * @param imageName The name of the associated image
     * @return The BufferedImage, or null if retrieval fails
     */
    private static BufferedImage retrieveImageByName(ImageServer<?> server, String imageName) {
        try {
            Object image = server.getAssociatedImage(imageName);
            if (image instanceof BufferedImage) {
                BufferedImage labelImage = (BufferedImage) image;
                logger.info("Retrieved label image: {}x{} type={} bits/sample={} bands={}",
                        labelImage.getWidth(), labelImage.getHeight(),
                        labelImage.getType(),
                        labelImage.getSampleModel().getSampleSize(0),
                        labelImage.getRaster().getNumBands());
                return labelImage;
            } else {
                logger.warn("Associated image '{}' is not a BufferedImage: {}",
                        imageName, image != null ? image.getClass() : "null");
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve associated image: {}", imageName, e);
            return null;
        }
    }

    /**
     * Gets the name of the label image if available.
     *
     * @param imageData The ImageData to check
     * @return The label image name, or null if not found
     */
    public static String getLabelImageName(ImageData<?> imageData) {
        if (imageData == null) {
            return null;
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                return null;
            }

            Collection<String> associatedImages = server.getAssociatedImageList();
            if (associatedImages == null) {
                return null;
            }

            // Try finding by keyword
            String[] keywords = getLabelKeywords();
            for (String imageName : associatedImages) {
                String lowerName = imageName.toLowerCase();
                for (String keyword : keywords) {
                    if (lowerName.contains(keyword.toLowerCase())) {
                        return imageName;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            logger.error("Error getting label image name", e);
            return null;
        }
    }

    /**
     * Gets a list of all associated image names for debugging.
     *
     * @param imageData The ImageData to inspect
     * @return Collection of associated image names, or empty collection if none
     */
    public static Collection<String> getAssociatedImageNames(ImageData<?> imageData) {
        if (imageData == null) {
            return java.util.Collections.emptyList();
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) {
                return java.util.Collections.emptyList();
            }

            Collection<String> names = server.getAssociatedImageList();
            return names != null ? names : java.util.Collections.emptyList();

        } catch (Exception e) {
            logger.error("Error getting associated image names", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Normalizes a BufferedImage to 8-bit RGB with full dynamic range.
     * Handles 16-bit images (common in CZI label images) and images with
     * unusual color models where getRGB() loses dynamic range.
     * <p>
     * Uses percentile-based range (0.5th to 99.5th) to avoid outlier pixels
     * compressing the useful dynamic range.
     *
     * @param image The image to normalize
     * @return A normalized 8-bit TYPE_INT_RGB image, or the original if already 8-bit standard
     */
    public static BufferedImage normalizeToEightBit(BufferedImage image) {
        if (image == null) return null;

        int bitsPerSample = image.getSampleModel().getSampleSize(0);
        int numBands = image.getRaster().getNumBands();
        int w = image.getWidth();
        int h = image.getHeight();
        WritableRaster raster = image.getRaster();

        logger.info("normalizeToEightBit: input {}x{} type={} bits={} bands={}",
                w, h, image.getType(), bitsPerSample, numBands);

        // For standard 8-bit images, check if getRGB conversion loses range
        if (bitsPerSample <= 8 && image.getType() != BufferedImage.TYPE_CUSTOM) {
            int sampleMin = 255, sampleMax = 0;
            int step = Math.max(1, (w * h) / 10000);
            for (int i = 0; i < w * h; i += step) {
                int x = i % w;
                int y = i / w;
                int rgb = image.getRGB(x, y);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                sampleMin = Math.min(sampleMin, gray);
                sampleMax = Math.max(sampleMax, gray);
            }
            if (sampleMax - sampleMin >= 50) {
                logger.info("normalizeToEightBit: 8-bit with good range ({}-{}), skipping",
                        sampleMin, sampleMax);
                return image;
            }
            logger.info("normalizeToEightBit: 8-bit with narrow getRGB range ({}-{}), normalizing",
                    sampleMin, sampleMax);
        } else if (bitsPerSample > 8) {
            logger.info("normalizeToEightBit: {}-bit image requires normalization", bitsPerSample);
        } else {
            logger.info("normalizeToEightBit: TYPE_CUSTOM image, normalizing from raster");
        }

        // Build histogram from raster data (band 0) to find percentile range
        int maxPossibleValue = (1 << bitsPerSample) - 1;
        // For very high bit depths, cap histogram size and bin values
        int histSize = Math.min(maxPossibleValue + 1, 65536);
        int[] histogram = new int[histSize];
        double binScale = (histSize < maxPossibleValue + 1)
                ? (double) histSize / (maxPossibleValue + 1) : 1.0;

        int globalMin = Integer.MAX_VALUE;
        int globalMax = Integer.MIN_VALUE;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int val = raster.getSample(x, y, 0);
                if (val < globalMin) globalMin = val;
                if (val > globalMax) globalMax = val;
                int bin = (int) (val * binScale);
                bin = Math.min(bin, histSize - 1);
                histogram[bin]++;
            }
        }

        logger.info("normalizeToEightBit: raster range {}-{}", globalMin, globalMax);

        // Find 0.5th and 99.5th percentile to exclude outliers
        int totalPixels = w * h;
        int lowTarget = (int) (totalPixels * 0.005);
        int highTarget = (int) (totalPixels * 0.995);

        int cumulative = 0;
        int pLow = 0;
        for (int i = 0; i < histSize; i++) {
            cumulative += histogram[i];
            if (cumulative >= lowTarget) {
                pLow = (int) (i / binScale);
                break;
            }
        }

        cumulative = 0;
        int pHigh = maxPossibleValue;
        for (int i = 0; i < histSize; i++) {
            cumulative += histogram[i];
            if (cumulative >= highTarget) {
                pHigh = (int) (i / binScale);
                break;
            }
        }

        // Ensure some minimum range
        if (pHigh - pLow < 10) {
            pLow = globalMin;
            pHigh = globalMax;
        }

        logger.info("normalizeToEightBit: using percentile range {}-{} (abs range {}-{})",
                pLow, pHigh, globalMin, globalMax);

        double scale = (pHigh > pLow) ? 255.0 / (pHigh - pLow) : 1.0;

        BufferedImage normalized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        if (numBands == 1) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int val = (int) ((raster.getSample(x, y, 0) - pLow) * scale);
                    val = Math.max(0, Math.min(255, val));
                    normalized.setRGB(x, y, (0xFF << 24) | (val << 16) | (val << 8) | val);
                }
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = (int) ((raster.getSample(x, y, 0) - pLow) * scale);
                    int g = (int) ((raster.getSample(x, y, Math.min(1, numBands - 1)) - pLow) * scale);
                    int b = (int) ((raster.getSample(x, y, Math.min(2, numBands - 1)) - pLow) * scale);
                    r = Math.max(0, Math.min(255, r));
                    g = Math.max(0, Math.min(255, g));
                    b = Math.max(0, Math.min(255, b));
                    normalized.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }

        // Verify output range
        int outMin = 255, outMax = 0;
        int step = Math.max(1, (w * h) / 5000);
        for (int i = 0; i < w * h; i += step) {
            int x = i % w;
            int y = i / w;
            int rgb = normalized.getRGB(x, y);
            int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
            outMin = Math.min(outMin, gray);
            outMax = Math.max(outMax, gray);
        }
        logger.info("normalizeToEightBit: output range {}-{}", outMin, outMax);

        return normalized;
    }

    /**
     * Retrieves an associated image and applies the given extraction config
     * (crop, rotate, flip) to produce a label image.
     *
     * @param imageData The ImageData containing the associated images
     * @param config    The extraction configuration specifying source, crop, rotation, flip
     * @return The extracted and transformed label image, or null on failure
     */
    public static BufferedImage retrieveImageWithExtraction(
            ImageData<?> imageData,
            qupath.ext.ocr4labels.model.LabelExtractionConfig config) {
        if (imageData == null || config == null || config.getSourceImageName() == null) {
            return null;
        }

        try {
            ImageServer<?> server = imageData.getServer();
            if (server == null) return null;

            // Retrieve the named associated image
            BufferedImage source = retrieveImageByName(server, config.getSourceImageName());
            if (source == null) {
                logger.warn("Associated image '{}' not found", config.getSourceImageName());
                return null;
            }

            source = normalizeToEightBit(source);
            logger.info("Retrieved '{}' for extraction: {}x{}",
                    config.getSourceImageName(), source.getWidth(), source.getHeight());

            // Crop
            if (config.hasCropRegion()) {
                int cx = Math.max(0, config.getCropX());
                int cy = Math.max(0, config.getCropY());
                int cw = Math.min(config.getCropWidth(), source.getWidth() - cx);
                int ch = Math.min(config.getCropHeight(), source.getHeight() - cy);
                if (cw > 0 && ch > 0) {
                    source = source.getSubimage(cx, cy, cw, ch);
                    // getSubimage shares the raster; copy to independent image
                    BufferedImage cropped = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics g = cropped.getGraphics();
                    g.drawImage(source, 0, 0, null);
                    g.dispose();
                    source = cropped;
                    logger.info("Cropped to ({},{} {}x{})", cx, cy, cw, ch);
                } else {
                    logger.warn("Crop region ({},{} {}x{}) outside source image ({}x{}), skipping crop",
                            cx, cy, config.getCropWidth(), config.getCropHeight(),
                            source.getWidth(), source.getHeight());
                }
            }

            // Rotate
            int rot = config.getRotation();
            if (rot == 90 || rot == 180 || rot == 270) {
                source = rotateImage(source, rot);
                logger.info("Rotated {} degrees", rot);
            }

            // Flip
            if (config.isFlipHorizontal() || config.isFlipVertical()) {
                source = flipImage(source, config.isFlipHorizontal(), config.isFlipVertical());
                logger.info("Flipped H={} V={}", config.isFlipHorizontal(), config.isFlipVertical());
            }

            return source;

        } catch (Exception e) {
            logger.error("Failed to extract label from associated image: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Rotates a BufferedImage by 90, 180, or 270 degrees.
     */
    private static BufferedImage rotateImage(BufferedImage img, int degrees) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean swap = (degrees == 90 || degrees == 270);
        int newW = swap ? h : w;
        int newH = swap ? w : h;

        BufferedImage rotated = new BufferedImage(newW, newH, img.getType());
        java.awt.Graphics2D g = rotated.createGraphics();
        java.awt.geom.AffineTransform tx = new java.awt.geom.AffineTransform();

        if (degrees == 90) {
            tx.translate(h, 0);
            tx.rotate(Math.PI / 2);
        } else if (degrees == 180) {
            tx.translate(w, h);
            tx.rotate(Math.PI);
        } else if (degrees == 270) {
            tx.translate(0, w);
            tx.rotate(-Math.PI / 2);
        }

        g.drawImage(img, tx, null);
        g.dispose();
        return rotated;
    }

    /**
     * Flips a BufferedImage horizontally and/or vertically.
     */
    private static BufferedImage flipImage(BufferedImage img, boolean horizontal, boolean vertical) {
        int w = img.getWidth();
        int h = img.getHeight();
        BufferedImage flipped = new BufferedImage(w, h, img.getType());
        java.awt.Graphics2D g = flipped.createGraphics();
        java.awt.geom.AffineTransform tx = new java.awt.geom.AffineTransform();

        if (horizontal && vertical) {
            tx.translate(w, h);
            tx.scale(-1, -1);
        } else if (horizontal) {
            tx.translate(w, 0);
            tx.scale(-1, 1);
        } else if (vertical) {
            tx.translate(0, h);
            tx.scale(1, -1);
        }

        g.drawImage(img, tx, null);
        g.dispose();
        return flipped;
    }
}
