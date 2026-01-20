package qupath.ext.ocr4labels.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.BarcodeResult;
import qupath.ext.ocr4labels.model.BoundingBox;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.model.RegionType;
import qupath.ext.ocr4labels.model.TextBlock;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.CompletableFuture;

/**
 * Unified decoder service that dispatches to either OCR (Tesseract) or barcode (ZXing)
 * engines based on the region type.
 * <p>
 * This service provides a single interface for decoding any type of content from
 * image regions, supporting TEXT, BARCODE, and AUTO detection modes.
 */
public class UnifiedDecoderService {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedDecoderService.class);

    private final OCREngine ocrEngine;
    private final BarcodeEngine barcodeEngine;

    /**
     * Result of unified decoding, containing either OCR or barcode results.
     */
    public static class DecodedResult {
        private final String text;
        private final float confidence;
        private final BoundingBox boundingBox;
        private final RegionType sourceType;  // What type was actually used
        private final String format;          // For barcodes: the barcode format
        private final long processingTimeMs;
        private final boolean success;
        private final String errorMessage;

        private DecodedResult(String text, float confidence, BoundingBox boundingBox,
                              RegionType sourceType, String format, long processingTimeMs,
                              boolean success, String errorMessage) {
            this.text = text;
            this.confidence = confidence;
            this.boundingBox = boundingBox;
            this.sourceType = sourceType;
            this.format = format;
            this.processingTimeMs = processingTimeMs;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        /**
         * Creates a successful result from OCR.
         */
        public static DecodedResult fromOCR(String text, float confidence, BoundingBox boundingBox,
                                             long processingTimeMs) {
            return new DecodedResult(text, confidence, boundingBox, RegionType.TEXT,
                    null, processingTimeMs, true, null);
        }

        /**
         * Creates a successful result from barcode decoding.
         */
        public static DecodedResult fromBarcode(String text, String format, BoundingBox boundingBox,
                                                 long processingTimeMs) {
            return new DecodedResult(text, 1.0f, boundingBox, RegionType.BARCODE,
                    format, processingTimeMs, true, null);
        }

        /**
         * Creates an empty result (nothing found).
         */
        public static DecodedResult empty(RegionType attemptedType, long processingTimeMs) {
            return new DecodedResult("", 0f, null, attemptedType,
                    null, processingTimeMs, false, null);
        }

        /**
         * Creates an error result.
         */
        public static DecodedResult error(String errorMessage, long processingTimeMs) {
            return new DecodedResult("", 0f, null, null,
                    null, processingTimeMs, false, errorMessage);
        }

        public String getText() { return text; }
        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }
        public RegionType getSourceType() { return sourceType; }
        public String getFormat() { return format; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public boolean isSuccess() { return success; }
        public boolean hasText() { return text != null && !text.isEmpty(); }
        public String getErrorMessage() { return errorMessage; }

        /**
         * Gets a display string showing the format (for barcodes) or source type.
         */
        public String getTypeDisplay() {
            if (sourceType == RegionType.BARCODE && format != null) {
                return "[" + format + "]";
            }
            return sourceType != null ? sourceType.getDisplayName() : "";
        }

        @Override
        public String toString() {
            if (!success) {
                return String.format("DecodedResult[failed: %s]",
                        errorMessage != null ? errorMessage : "no content found");
            }
            return String.format("DecodedResult[%s: '%s', conf=%.0f%%, time=%dms]",
                    getTypeDisplay(), text, confidence * 100, processingTimeMs);
        }
    }

    /**
     * Creates a new unified decoder service.
     *
     * @param ocrEngine The OCR engine to use for text decoding
     */
    public UnifiedDecoderService(OCREngine ocrEngine) {
        this.ocrEngine = ocrEngine;
        this.barcodeEngine = new BarcodeEngine();
    }

    /**
     * Creates a new unified decoder service with a custom barcode engine.
     *
     * @param ocrEngine The OCR engine to use for text decoding
     * @param barcodeEngine The barcode engine to use for barcode decoding
     */
    public UnifiedDecoderService(OCREngine ocrEngine, BarcodeEngine barcodeEngine) {
        this.ocrEngine = ocrEngine;
        this.barcodeEngine = barcodeEngine;
    }

    /**
     * Decodes content from an image region based on the specified type.
     *
     * @param image The full image
     * @param region The region to decode (may be null to use full image)
     * @param regionType The type of content to decode
     * @param config OCR configuration (used when type is TEXT or AUTO fallback)
     * @return The decoded result
     */
    public DecodedResult decodeRegion(BufferedImage image, Rectangle region,
                                       RegionType regionType, OCRConfiguration config) {
        if (image == null) {
            return DecodedResult.error("Image cannot be null", 0);
        }

        if (regionType == null) {
            regionType = RegionType.TEXT;
        }

        long startTime = System.currentTimeMillis();

        try {
            // Extract region if specified
            BufferedImage targetImage = extractRegion(image, region);

            switch (regionType) {
                case TEXT:
                    return decodeAsText(targetImage, region, config, startTime);

                case BARCODE:
                    return decodeAsBarcode(targetImage, region, startTime);

                case AUTO:
                    return decodeAuto(targetImage, region, config, startTime);

                default:
                    return DecodedResult.error("Unknown region type: " + regionType, 0);
            }

        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("Decoding failed: {}", e.getMessage(), e);
            return DecodedResult.error(e.getMessage(), processingTime);
        }
    }

    /**
     * Decodes content asynchronously.
     *
     * @param image The full image
     * @param region The region to decode
     * @param regionType The type of content to decode
     * @param config OCR configuration
     * @return CompletableFuture containing the decoded result
     */
    public CompletableFuture<DecodedResult> decodeRegionAsync(BufferedImage image, Rectangle region,
                                                               RegionType regionType, OCRConfiguration config) {
        return CompletableFuture.supplyAsync(() -> decodeRegion(image, region, regionType, config));
    }

    /**
     * Extracts a region from the image, clamped to image bounds.
     */
    private BufferedImage extractRegion(BufferedImage image, Rectangle region) {
        if (region == null) {
            return image;
        }

        int x = Math.max(0, region.x);
        int y = Math.max(0, region.y);
        int width = Math.min(region.width, image.getWidth() - x);
        int height = Math.min(region.height, image.getHeight() - y);

        if (width <= 0 || height <= 0) {
            logger.warn("Invalid region dimensions, using full image");
            return image;
        }

        return image.getSubimage(x, y, width, height);
    }

    /**
     * Decodes the region as text using OCR.
     */
    private DecodedResult decodeAsText(BufferedImage image, Rectangle region,
                                        OCRConfiguration config, long startTime) {
        try {
            if (ocrEngine == null || !ocrEngine.isInitialized()) {
                return DecodedResult.error("OCR engine not initialized",
                        System.currentTimeMillis() - startTime);
            }

            OCRResult result = ocrEngine.processImage(image, config);
            long processingTime = System.currentTimeMillis() - startTime;

            if (result == null || !result.hasText()) {
                return DecodedResult.empty(RegionType.TEXT, processingTime);
            }

            // Combine all text blocks into a single result
            StringBuilder combined = new StringBuilder();
            float avgConfidence = 0;
            BoundingBox combinedBbox = null;

            for (TextBlock block : result.getTextBlocks()) {
                if (block.getText() != null && !block.getText().isEmpty()) {
                    if (combined.length() > 0) {
                        combined.append(" ");
                    }
                    combined.append(block.getText());
                    avgConfidence += block.getConfidence();

                    // Expand combined bounding box
                    if (block.getBoundingBox() != null) {
                        combinedBbox = combinedBbox != null
                                ? combinedBbox.expandedWith(block.getBoundingBox())
                                : block.getBoundingBox();
                    }
                }
            }

            if (combined.length() == 0) {
                return DecodedResult.empty(RegionType.TEXT, processingTime);
            }

            avgConfidence /= result.getTextBlocks().size();

            // Adjust bounding box for region offset
            if (combinedBbox != null && region != null) {
                combinedBbox = new BoundingBox(
                        combinedBbox.getX() + region.x,
                        combinedBbox.getY() + region.y,
                        combinedBbox.getWidth(),
                        combinedBbox.getHeight()
                );
            }

            return DecodedResult.fromOCR(combined.toString().trim(), avgConfidence,
                    combinedBbox, processingTime);

        } catch (OCREngine.OCRException e) {
            long processingTime = System.currentTimeMillis() - startTime;
            logger.error("OCR failed: {}", e.getMessage());
            return DecodedResult.error(e.getMessage(), processingTime);
        }
    }

    /**
     * Decodes the region as a barcode.
     */
    private DecodedResult decodeAsBarcode(BufferedImage image, Rectangle region, long startTime) {
        BarcodeResult result = barcodeEngine.decodeWithRetry(image);
        long processingTime = System.currentTimeMillis() - startTime;

        if (!result.hasBarcode()) {
            return DecodedResult.empty(RegionType.BARCODE, processingTime);
        }

        // Get combined text for multiple barcodes
        String text = result.getCombinedText();
        String format = result.getBarcodeCount() > 1
                ? "MULTIPLE"
                : result.getFormat();

        // Get bounding box from first barcode, adjusted for region offset
        BoundingBox bbox = result.getFirstBarcode().getBoundingBox();
        if (bbox != null && region != null) {
            bbox = new BoundingBox(
                    bbox.getX() + region.x,
                    bbox.getY() + region.y,
                    bbox.getWidth(),
                    bbox.getHeight()
            );
        }

        return DecodedResult.fromBarcode(text, format, bbox, processingTime);
    }

    /**
     * Auto-detection: tries barcode first (fast), falls back to OCR if no barcode found.
     */
    private DecodedResult decodeAuto(BufferedImage image, Rectangle region,
                                      OCRConfiguration config, long startTime) {
        // Try barcode first - it's fast (~10-50ms typically)
        BarcodeResult barcodeResult = barcodeEngine.decodeAll(image);

        if (barcodeResult.hasBarcode()) {
            long processingTime = System.currentTimeMillis() - startTime;
            String text = barcodeResult.getCombinedText();
            String format = barcodeResult.getBarcodeCount() > 1
                    ? "MULTIPLE"
                    : barcodeResult.getFormat();

            BoundingBox bbox = barcodeResult.getFirstBarcode().getBoundingBox();
            if (bbox != null && region != null) {
                bbox = new BoundingBox(
                        bbox.getX() + region.x,
                        bbox.getY() + region.y,
                        bbox.getWidth(),
                        bbox.getHeight()
                );
            }

            logger.debug("AUTO mode: found barcode(s) in {}ms", processingTime);
            return DecodedResult.fromBarcode(text, format, bbox, processingTime);
        }

        // No barcode found - fall back to OCR
        logger.debug("AUTO mode: no barcode found, falling back to OCR");
        return decodeAsText(image, region, config, startTime);
    }

    /**
     * Gets the barcode engine for direct access if needed.
     */
    public BarcodeEngine getBarcodeEngine() {
        return barcodeEngine;
    }
}
