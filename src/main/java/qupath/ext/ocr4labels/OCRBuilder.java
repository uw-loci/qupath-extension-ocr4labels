package qupath.ext.ocr4labels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.ext.ocr4labels.service.OCREngine;
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.lib.images.ImageData;
import qupath.lib.scripting.QP;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;

/**
 * Fluent builder for OCR configuration and execution.
 * Provides a convenient API for Groovy scripting.
 *
 * <p>Example usage:</p>
 * <pre>
 * // Basic usage
 * def results = OCR4Labels.builder()
 *     .sparseText()
 *     .enhance()
 *     .run()
 *
 * // Full configuration
 * def results = OCR4Labels.builder()
 *     .sparseText()           // Use sparse text mode (best for labels)
 *     .enhance()              // Apply adaptive thresholding
 *     .invert()               // Invert colors for light text on dark
 *     .minConfidence(0.3)     // Lower threshold for difficult labels
 *     .detectOrientation()    // Enable orientation detection
 *     .autoRotate()           // Auto-rotate if sideways
 *     .run()
 *
 * // Region-based OCR (inspired by zindy/qupath-extension-ocr)
 * def results = OCR4Labels.builder()
 *     .sparseText()
 *     .region(0, 70, 400, 90) // Only process specific area (x, y, width, height)
 *     .run()
 *
 * // Restricted character set (inspired by zindy/qupath-extension-ocr)
 * def results = OCR4Labels.builder()
 *     .singleLine()
 *     .allowedChars("0123456789")  // Only recognize digits
 *     .run()
 * </pre>
 *
 * @author Michael Nelson
 */
public class OCRBuilder {

    private static final Logger logger = LoggerFactory.getLogger(OCRBuilder.class);

    private OCRConfiguration.PageSegMode pageSegMode = OCRConfiguration.PageSegMode.SPARSE_TEXT;
    private String language;
    private double minConfidence = 0.5;
    private boolean enhanceContrast = true;
    private boolean invertImage = false;
    private boolean detectOrientation = false;
    private boolean autoRotate = false;
    // Inspired by zindy/qupath-extension-ocr - region-based OCR and character whitelist support
    // See: https://github.com/zindy/qupath-extension-ocr
    private Rectangle cropRegion = null;
    private String characterWhitelist = null;

    /**
     * Creates a new builder with default settings.
     * Default: sparse text mode, enhanced contrast, 50% min confidence.
     */
    public OCRBuilder() {
        // Load defaults from preferences
        this.language = OCRPreferences.getLanguage();
        this.detectOrientation = OCRPreferences.isDetectOrientation();
        this.autoRotate = OCRPreferences.isAutoRotate();
    }

    // ========== Page Segmentation Mode Methods ==========

    /**
     * Use sparse text mode - finds text scattered across the image.
     * This is the best mode for label images (default).
     *
     * @return this builder
     */
    public OCRBuilder sparseText() {
        this.pageSegMode = OCRConfiguration.PageSegMode.SPARSE_TEXT;
        return this;
    }

    /**
     * Use automatic page segmentation mode.
     * Tesseract automatically determines text layout.
     *
     * @return this builder
     */
    public OCRBuilder autoDetect() {
        this.pageSegMode = OCRConfiguration.PageSegMode.AUTO;
        return this;
    }

    /**
     * Treat image as a single uniform block of text.
     *
     * @return this builder
     */
    public OCRBuilder singleBlock() {
        this.pageSegMode = OCRConfiguration.PageSegMode.SINGLE_BLOCK;
        return this;
    }

    /**
     * Treat image as a single text line.
     *
     * @return this builder
     */
    public OCRBuilder singleLine() {
        this.pageSegMode = OCRConfiguration.PageSegMode.SINGLE_LINE;
        return this;
    }

    /**
     * Treat image as a single word.
     *
     * @return this builder
     */
    public OCRBuilder singleWord() {
        this.pageSegMode = OCRConfiguration.PageSegMode.SINGLE_WORD;
        return this;
    }

    /**
     * Use single column mode for columnar text.
     *
     * @return this builder
     */
    public OCRBuilder singleColumn() {
        this.pageSegMode = OCRConfiguration.PageSegMode.SINGLE_COLUMN;
        return this;
    }

    /**
     * Set the page segmentation mode directly.
     *
     * @param mode The PSM mode to use
     * @return this builder
     */
    public OCRBuilder pageSegMode(OCRConfiguration.PageSegMode mode) {
        this.pageSegMode = mode != null ? mode : OCRConfiguration.PageSegMode.SPARSE_TEXT;
        return this;
    }

    // ========== Preprocessing Methods ==========

    /**
     * Enable image enhancement (adaptive thresholding).
     * This improves contrast for better OCR accuracy (default: enabled).
     *
     * @return this builder
     */
    public OCRBuilder enhance() {
        this.enhanceContrast = true;
        return this;
    }

    /**
     * Disable image enhancement.
     *
     * @return this builder
     */
    public OCRBuilder noEnhance() {
        this.enhanceContrast = false;
        return this;
    }

    /**
     * Invert the image colors.
     * Use this for light text on dark backgrounds.
     *
     * @return this builder
     */
    public OCRBuilder invert() {
        this.invertImage = true;
        return this;
    }

    /**
     * Do not invert the image colors (default).
     *
     * @return this builder
     */
    public OCRBuilder noInvert() {
        this.invertImage = false;
        return this;
    }

    // ========== Detection Settings ==========

    /**
     * Set the minimum confidence threshold for text detection.
     *
     * @param confidence Confidence threshold 0.0-1.0 (default: 0.5)
     * @return this builder
     */
    public OCRBuilder minConfidence(double confidence) {
        this.minConfidence = Math.max(0.0, Math.min(1.0, confidence));
        return this;
    }

    /**
     * Set the OCR language.
     *
     * @param lang Language code (e.g., "eng", "deu", "fra")
     * @return this builder
     */
    public OCRBuilder language(String lang) {
        this.language = lang != null && !lang.isEmpty() ? lang : "eng";
        return this;
    }

    // ========== Orientation Methods ==========

    /**
     * Enable orientation detection.
     * Requires osd.traineddata in tessdata directory.
     *
     * @return this builder
     */
    public OCRBuilder detectOrientation() {
        this.detectOrientation = true;
        return this;
    }

    /**
     * Disable orientation detection (default unless enabled in preferences).
     *
     * @return this builder
     */
    public OCRBuilder noDetectOrientation() {
        this.detectOrientation = false;
        return this;
    }

    /**
     * Enable automatic rotation based on detected orientation.
     * Only effective if orientation detection is also enabled.
     *
     * @return this builder
     */
    public OCRBuilder autoRotate() {
        this.autoRotate = true;
        return this;
    }

    /**
     * Disable automatic rotation.
     *
     * @return this builder
     */
    public OCRBuilder noAutoRotate() {
        this.autoRotate = false;
        return this;
    }

    // ========== Region and Whitelist Methods ==========
    // Inspired by zindy/qupath-extension-ocr
    // See: https://github.com/zindy/qupath-extension-ocr

    /**
     * Set a region to crop before OCR processing.
     * Only the specified region will be processed, which can improve
     * accuracy and performance for labels with known text positions.
     *
     * <p>Inspired by zindy/qupath-extension-ocr region-based OCR support.</p>
     *
     * @param x      X coordinate of top-left corner
     * @param y      Y coordinate of top-left corner
     * @param width  Width of the region
     * @param height Height of the region
     * @return this builder
     */
    public OCRBuilder region(int x, int y, int width, int height) {
        this.cropRegion = new Rectangle(x, y, width, height);
        return this;
    }

    /**
     * Clear any previously set region, processing the full image.
     *
     * @return this builder
     */
    public OCRBuilder fullImage() {
        this.cropRegion = null;
        return this;
    }

    /**
     * Set a character whitelist to restrict OCR output to specific characters.
     * This can significantly improve accuracy when you know the expected character set.
     *
     * <p>Inspired by zindy/qupath-extension-ocr character whitelist support.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code .allowedChars("0123456789")} - digits only</li>
     *   <li>{@code .allowedChars("ABCDEFGHIJKLMNOPQRSTUVWXYZ")} - uppercase letters only</li>
     *   <li>{@code .allowedChars("0123456789ABCDEF-")} - hex digits and hyphens</li>
     * </ul>
     *
     * @param whitelist The allowed characters (e.g., "0123456789" for digits only)
     * @return this builder
     */
    public OCRBuilder allowedChars(String whitelist) {
        this.characterWhitelist = whitelist;
        return this;
    }

    /**
     * Clear any previously set character whitelist, allowing all characters.
     *
     * @return this builder
     */
    public OCRBuilder allChars() {
        this.characterWhitelist = null;
        return this;
    }

    // ========== Build and Run Methods ==========

    /**
     * Build the OCR configuration.
     *
     * @return The built configuration
     */
    public OCRConfiguration build() {
        return OCRConfiguration.builder()
                .pageSegMode(pageSegMode)
                .language(language)
                .minConfidence(minConfidence)
                .enhanceContrast(enhanceContrast)
                .detectOrientation(detectOrientation)
                .autoRotate(autoRotate)
                .enablePreprocessing(true)
                .cropRegion(cropRegion)
                .characterWhitelist(characterWhitelist)
                .build();
    }

    /**
     * Run OCR with the configured settings and return text results.
     *
     * @return List of detected text strings
     */
    public List<String> run() {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null) {
            logger.warn("No image data available");
            return Collections.emptyList();
        }

        if (!LabelImageUtility.isLabelImageAvailable(imageData)) {
            logger.warn("No label image available for current image");
            return Collections.emptyList();
        }

        BufferedImage labelImage = LabelImageUtility.retrieveLabelImage(imageData);
        if (labelImage == null) {
            logger.warn("Failed to retrieve label image");
            return Collections.emptyList();
        }

        // Apply inversion if requested
        if (invertImage) {
            labelImage = OCR4Labels.invertImage(labelImage);
        }

        OCRConfiguration config = build();
        return OCR4Labels.runOCR(config);
    }

    /**
     * Run OCR with the configured settings and return detailed result.
     *
     * @return Detailed OCR result with bounding boxes
     */
    public OCRResult runDetailed() {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null) {
            logger.warn("No image data available");
            return OCRResult.empty();
        }

        if (!LabelImageUtility.isLabelImageAvailable(imageData)) {
            logger.warn("No label image available for current image");
            return OCRResult.empty();
        }

        BufferedImage labelImage = LabelImageUtility.retrieveLabelImage(imageData);
        if (labelImage == null) {
            logger.warn("Failed to retrieve label image");
            return OCRResult.empty();
        }

        // Apply inversion if requested
        if (invertImage) {
            labelImage = OCR4Labels.invertImage(labelImage);
        }

        OCRConfiguration config = build();
        return OCR4Labels.runOCRDetailed(config);
    }

    /**
     * Run OCR on a specific image (not the label).
     *
     * @param image The image to process
     * @return List of detected text strings
     */
    public List<String> runOn(BufferedImage image) {
        if (image == null) {
            logger.warn("Image cannot be null");
            return Collections.emptyList();
        }

        // Apply inversion if requested
        BufferedImage processedImage = invertImage ? OCR4Labels.invertImage(image) : image;

        OCRConfiguration config = build();
        try {
            OCREngine engine = new OCREngine();
            String tessdataPath = OCRPreferences.getTessdataPath();
            if (tessdataPath == null || tessdataPath.isEmpty()) {
                logger.error("Tessdata path not configured");
                return Collections.emptyList();
            }
            engine.initialize(tessdataPath, language);
            OCRResult result = engine.processImage(processedImage, config);

            return result.getTextBlocks().stream()
                    .map(block -> block.getText())
                    .filter(text -> text != null && !text.isEmpty())
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());

        } catch (OCREngine.OCRException e) {
            logger.error("OCR failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Generate a script representation of this builder's configuration.
     * Useful for workflow recording.
     *
     * @return Groovy script string
     */
    public String toScript() {
        StringBuilder script = new StringBuilder();
        script.append("OCR4Labels.builder()");

        // Page segmentation mode
        switch (pageSegMode) {
            case SPARSE_TEXT:
                script.append("\n    .sparseText()");
                break;
            case AUTO:
                script.append("\n    .autoDetect()");
                break;
            case SINGLE_BLOCK:
                script.append("\n    .singleBlock()");
                break;
            case SINGLE_LINE:
                script.append("\n    .singleLine()");
                break;
            case SINGLE_WORD:
                script.append("\n    .singleWord()");
                break;
            case SINGLE_COLUMN:
                script.append("\n    .singleColumn()");
                break;
            default:
                // Use pageSegMode() for other modes
                script.append("\n    .pageSegMode(OCRConfiguration.PageSegMode.")
                        .append(pageSegMode.name()).append(")");
        }

        // Preprocessing
        if (enhanceContrast) {
            script.append("\n    .enhance()");
        } else {
            script.append("\n    .noEnhance()");
        }

        if (invertImage) {
            script.append("\n    .invert()");
        }

        // Confidence
        if (minConfidence != 0.5) {
            script.append("\n    .minConfidence(").append(minConfidence).append(")");
        }

        // Language (only if not default)
        if (language != null && !language.equals("eng")) {
            script.append("\n    .language(\"").append(language).append("\")");
        }

        // Orientation
        if (detectOrientation) {
            script.append("\n    .detectOrientation()");
            if (autoRotate) {
                script.append("\n    .autoRotate()");
            }
        }

        // Region (inspired by zindy/qupath-extension-ocr)
        if (cropRegion != null) {
            script.append("\n    .region(")
                    .append(cropRegion.x).append(", ")
                    .append(cropRegion.y).append(", ")
                    .append(cropRegion.width).append(", ")
                    .append(cropRegion.height).append(")");
        }

        // Character whitelist (inspired by zindy/qupath-extension-ocr)
        if (characterWhitelist != null && !characterWhitelist.isEmpty()) {
            script.append("\n    .allowedChars(\"").append(characterWhitelist).append("\")");
        }

        script.append("\n    .run()");
        return script.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("OCRBuilder[psm=%s, enhance=%b, invert=%b, conf=%.0f%%, orient=%b",
                pageSegMode, enhanceContrast, invertImage, minConfidence * 100, detectOrientation));
        if (cropRegion != null) {
            sb.append(String.format(", region=(%d,%d,%d,%d)",
                    cropRegion.x, cropRegion.y, cropRegion.width, cropRegion.height));
        }
        if (characterWhitelist != null && !characterWhitelist.isEmpty()) {
            sb.append(", whitelist='").append(characterWhitelist).append("'");
        }
        sb.append("]");
        return sb.toString();
    }
}
