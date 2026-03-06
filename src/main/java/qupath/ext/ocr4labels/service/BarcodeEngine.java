package qupath.ext.ocr4labels.service;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.BarcodeResult;
import qupath.ext.ocr4labels.model.BoundingBox;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Barcode decoding engine using ZXing library.
 * <p>
 * Supports decoding of 1D barcodes (Code128, Code39, EAN, UPC, etc.) and
 * 2D codes (QR Code, DataMatrix, PDF417, Aztec) from image regions.
 * <p>
 * Features:
 * <ul>
 *   <li>Auto-detection of all supported barcode formats</li>
 *   <li>Multiple barcode detection in a single region</li>
 *   <li>Retry logic with image preprocessing (inversion, contrast enhancement)</li>
 *   <li>Bounding box extraction for detected barcodes</li>
 * </ul>
 */
public class BarcodeEngine {

    private static final Logger logger = LoggerFactory.getLogger(BarcodeEngine.class);

    private final MultiFormatReader reader;
    private final MultipleBarcodeReader multiReader;
    private final Map<DecodeHintType, Object> hints;

    /**
     * Creates a new barcode engine configured to detect all barcode formats.
     */
    public BarcodeEngine() {
        this.reader = new MultiFormatReader();
        this.multiReader = new GenericMultipleBarcodeReader(reader);
        this.hints = createDefaultHints();
    }

    /**
     * Creates default decoding hints for optimal barcode detection.
     */
    private Map<DecodeHintType, Object> createDefaultHints() {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);

        // Enable all barcode formats
        hints.put(DecodeHintType.POSSIBLE_FORMATS, EnumSet.allOf(BarcodeFormat.class));

        // Try harder to find barcodes (slower but more accurate)
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

        // Enable pure barcode mode (assumes barcode fills most of the image)
        hints.put(DecodeHintType.PURE_BARCODE, Boolean.FALSE);

        return hints;
    }

    /**
     * Decodes the first barcode found in the given image.
     *
     * @param image The image to scan
     * @return BarcodeResult containing the decoded barcode, or empty if none found
     */
    public BarcodeResult decode(BufferedImage image) {
        if (image == null) {
            return BarcodeResult.error("Image cannot be null", 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Result result = reader.decode(bitmap, hints);

            long processingTime = System.currentTimeMillis() - startTime;

            BoundingBox bbox = extractBoundingBox(result);
            return BarcodeResult.single(
                    result.getText(),
                    result.getBarcodeFormat().name(),
                    bbox,
                    processingTime
            );

        } catch (NotFoundException e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.debug("No barcode found in image ({}ms)", processingTime);
            return BarcodeResult.empty(processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.warn("Barcode decoding error: {}", e.getMessage());
            return BarcodeResult.error(e.getMessage(), processingTime);
        }
    }

    /**
     * Decodes all barcodes found in the given image.
     * Returns multiple barcodes if found, joined by semicolons in the combined text.
     *
     * @param image The image to scan
     * @return BarcodeResult containing all decoded barcodes
     */
    public BarcodeResult decodeAll(BufferedImage image) {
        if (image == null) {
            return BarcodeResult.error("Image cannot be null", 0);
        }

        long startTime = System.currentTimeMillis();

        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Result[] results = multiReader.decodeMultiple(bitmap, hints);

            long processingTime = System.currentTimeMillis() - startTime;

            if (results == null || results.length == 0) {
                return BarcodeResult.empty(processingTime);
            }

            List<BarcodeResult.DecodedBarcode> barcodes = new ArrayList<>();
            for (Result result : results) {
                BoundingBox bbox = extractBoundingBox(result);
                barcodes.add(new BarcodeResult.DecodedBarcode(
                        result.getText(),
                        result.getBarcodeFormat().name(),
                        bbox
                ));
            }

            logger.debug("Found {} barcode(s) in image ({}ms)", barcodes.size(), processingTime);
            return new BarcodeResult(barcodes, processingTime);

        } catch (NotFoundException e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.debug("No barcodes found in image ({}ms)", processingTime);
            return BarcodeResult.empty(processingTime);

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.warn("Barcode decoding error: {}", e.getMessage());
            return BarcodeResult.error(e.getMessage(), processingTime);
        }
    }

    /**
     * Attempts to decode barcodes with retry logic.
     * Tries multiple image preprocessing strategies:
     * 1. Original image
     * 2. Inverted image (for light barcodes on dark background)
     * 3. Enhanced contrast image
     * 4. Enhanced + inverted
     * 5. Upscaled 2x (for small regions where barcode modules are too few pixels)
     * 6. Upscaled 3x
     *
     * @param image The image to scan
     * @return BarcodeResult containing decoded barcodes
     */
    public BarcodeResult decodeWithRetry(BufferedImage image) {
        if (image == null) {
            return BarcodeResult.error("Image cannot be null", 0);
        }

        logger.info("decodeWithRetry: image {}x{} type={} bits={}",
                image.getWidth(), image.getHeight(), image.getType(),
                image.getSampleModel().getSampleSize(0));

        // Diagnostic: save input image for debugging barcode failures
        saveDiagnosticImage(image, "barcode_input");

        long startTime = System.currentTimeMillis();

        // Try 1: Original image
        BarcodeResult result = decodeAll(image);
        if (result.hasBarcode()) {
            logger.info("Barcode found in original image: {} barcodes", result.getBarcodeCount());
            return result;
        }

        // Try 2: Inverted image
        BufferedImage inverted = invertImage(image);
        result = decodeAll(inverted);
        if (result.hasBarcode()) {
            logger.info("Barcode found in inverted image");
            return new BarcodeResult(result.getBarcodes(),
                    System.currentTimeMillis() - startTime);
        }

        // Try 3: Enhanced contrast
        BufferedImage enhanced = enhanceContrast(image);
        result = decodeAll(enhanced);
        if (result.hasBarcode()) {
            logger.info("Barcode found in contrast-enhanced image");
            return new BarcodeResult(result.getBarcodes(),
                    System.currentTimeMillis() - startTime);
        }

        // Try 4: Enhanced + inverted
        BufferedImage enhancedInverted = invertImage(enhanced);
        result = decodeAll(enhancedInverted);
        if (result.hasBarcode()) {
            logger.info("Barcode found in enhanced+inverted image");
            return new BarcodeResult(result.getBarcodes(),
                    System.currentTimeMillis() - startTime);
        }

        // Try 5-6: Upscale for small images where barcode modules may be too few pixels
        // ZXing needs sufficient pixel resolution per barcode module to decode
        int minDim = Math.min(image.getWidth(), image.getHeight());
        if (minDim < 500) {
            for (int scaleFactor = 2; scaleFactor <= 3; scaleFactor++) {
                BufferedImage scaled = scaleImage(image, scaleFactor);
                logger.info("Retrying barcode at {}x scale ({}x{})",
                        scaleFactor, scaled.getWidth(), scaled.getHeight());
                result = decodeAll(scaled);
                if (result.hasBarcode()) {
                    logger.info("Barcode found at {}x scale", scaleFactor);
                    // Scale bounding boxes back to original coordinates
                    List<BarcodeResult.DecodedBarcode> adjusted = new ArrayList<>();
                    for (BarcodeResult.DecodedBarcode bc : result.getBarcodes()) {
                        BoundingBox bbox = bc.getBoundingBox();
                        BoundingBox adjBbox = bbox != null
                                ? new BoundingBox(bbox.getX() / scaleFactor, bbox.getY() / scaleFactor,
                                                  bbox.getWidth() / scaleFactor, bbox.getHeight() / scaleFactor)
                                : null;
                        adjusted.add(new BarcodeResult.DecodedBarcode(bc.getText(), bc.getFormat(), adjBbox));
                    }
                    return new BarcodeResult(adjusted, System.currentTimeMillis() - startTime);
                }
            }
        }

        long processingTime = System.currentTimeMillis() - startTime;
        logger.info("No barcode found after all retry attempts ({}ms)", processingTime);
        return BarcodeResult.empty(processingTime);
    }

    /**
     * Scales an image by an integer factor using bicubic interpolation.
     */
    private BufferedImage scaleImage(BufferedImage source, int factor) {
        int newW = source.getWidth() * factor;
        int newH = source.getHeight() * factor;
        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, newW, newH, null);
        g.dispose();
        return scaled;
    }

    /**
     * Extracts bounding box from ZXing result points.
     */
    private BoundingBox extractBoundingBox(Result result) {
        ResultPoint[] points = result.getResultPoints();
        if (points == null || points.length == 0) {
            return null;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float maxY = Float.MIN_VALUE;

        for (ResultPoint point : points) {
            if (point != null) {
                minX = Math.min(minX, point.getX());
                minY = Math.min(minY, point.getY());
                maxX = Math.max(maxX, point.getX());
                maxY = Math.max(maxY, point.getY());
            }
        }

        if (minX == Float.MAX_VALUE) {
            return null;
        }

        return new BoundingBox(
                (int) minX,
                (int) minY,
                (int) (maxX - minX),
                (int) (maxY - minY)
        );
    }

    /**
     * Inverts the colors of an image (for detecting light barcodes on dark backgrounds).
     */
    private BufferedImage invertImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage inverted = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int r = 255 - ((rgb >> 16) & 0xFF);
                int g = 255 - ((rgb >> 8) & 0xFF);
                int b = 255 - (rgb & 0xFF);
                inverted.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return inverted;
    }

    /**
     * Enhances image contrast using simple thresholding.
     */
    private BufferedImage enhanceContrast(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage enhanced = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        // Calculate histogram to find optimal threshold
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int gray = (int) (0.299 * ((rgb >> 16) & 0xFF) +
                                  0.587 * ((rgb >> 8) & 0xFF) +
                                  0.114 * (rgb & 0xFF));
                histogram[gray]++;
            }
        }

        // Find Otsu's threshold
        int total = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        float sumB = 0;
        int wB = 0;
        float maxVariance = 0;
        int threshold = 128;

        for (int i = 0; i < 256; i++) {
            wB += histogram[i];
            if (wB == 0) continue;

            int wF = total - wB;
            if (wF == 0) break;

            sumB += i * histogram[i];
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;

            float variance = (float) wB * wF * (mB - mF) * (mB - mF);
            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = i;
            }
        }

        // Apply threshold
        Graphics2D g2d = enhanced.createGraphics();
        g2d.drawImage(source, 0, 0, null);
        g2d.dispose();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = enhanced.getRGB(x, y) & 0xFF;
                int value = gray < threshold ? 0 : 255;
                enhanced.setRGB(x, y, (value << 16) | (value << 8) | value);
            }
        }

        return enhanced;
    }

    /**
     * Decodes a barcode from a specific region of an image.
     *
     * @param image The full image
     * @param region The region to scan (x, y, width, height)
     * @return BarcodeResult containing decoded barcodes
     */
    public BarcodeResult decodeRegion(BufferedImage image, Rectangle region) {
        if (image == null) {
            return BarcodeResult.error("Image cannot be null", 0);
        }
        if (region == null) {
            return decodeWithRetry(image);
        }

        // Clamp region to image bounds
        int x = Math.max(0, region.x);
        int y = Math.max(0, region.y);
        int width = Math.min(region.width, image.getWidth() - x);
        int height = Math.min(region.height, image.getHeight() - y);

        if (width <= 0 || height <= 0) {
            return BarcodeResult.error("Invalid region dimensions", 0);
        }

        BufferedImage regionImage = image.getSubimage(x, y, width, height);
        BarcodeResult result = decodeWithRetry(regionImage);

        // Adjust bounding boxes to full image coordinates
        if (result.hasBarcode()) {
            List<BarcodeResult.DecodedBarcode> adjusted = new ArrayList<>();
            for (BarcodeResult.DecodedBarcode barcode : result.getBarcodes()) {
                BoundingBox bbox = barcode.getBoundingBox();
                BoundingBox adjustedBbox = bbox != null
                        ? new BoundingBox(bbox.getX() + x, bbox.getY() + y,
                                          bbox.getWidth(), bbox.getHeight())
                        : null;
                adjusted.add(new BarcodeResult.DecodedBarcode(
                        barcode.getText(), barcode.getFormat(), adjustedBbox));
            }
            return new BarcodeResult(adjusted, result.getProcessingTimeMs());
        }

        return result;
    }

    /**
     * Saves a diagnostic copy of the image for debugging barcode detection failures.
     * Writes to the user's home directory as barcode_debug_[name].png.
     * This is temporary diagnostic code - remove once barcode detection is stable.
     */
    private void saveDiagnosticImage(BufferedImage image, String name) {
        try {
            // Create a deep copy (standalone image, not a subimage view)
            BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();

            File outFile = new File(System.getProperty("user.home"),
                    "barcode_debug_" + name + ".png");
            javax.imageio.ImageIO.write(copy, "png", outFile);
            logger.info("Saved diagnostic image: {} ({}x{})", outFile.getAbsolutePath(),
                    copy.getWidth(), copy.getHeight());

            // Log pixel value statistics
            int min = 255, max = 0;
            long sum = 0;
            int w = copy.getWidth(), h = copy.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int rgb = copy.getRGB(x, y);
                    int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                    min = Math.min(min, gray);
                    max = Math.max(max, gray);
                    sum += gray;
                }
            }
            long avg = sum / (w * h);
            logger.info("Diagnostic image stats: min={} max={} avg={}", min, max, avg);
        } catch (Exception e) {
            logger.debug("Failed to save diagnostic image: {}", e.getMessage());
        }
    }
}
