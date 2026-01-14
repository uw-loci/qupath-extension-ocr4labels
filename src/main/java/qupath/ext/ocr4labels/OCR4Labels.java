package qupath.ext.ocr4labels;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.model.TextBlock;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.ext.ocr4labels.service.OCREngine;
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.lib.images.ImageData;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.scripting.QP;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main scripting API for OCR for Labels extension.
 * All public static methods are callable from Groovy scripts.
 *
 * <p>Example usage in QuPath Script Editor:</p>
 * <pre>
 * import qupath.ext.ocr4labels.OCR4Labels
 *
 * // Simple OCR with defaults
 * def results = OCR4Labels.runOCR()
 * println "Found: " + results
 *
 * // With builder for custom configuration
 * def results = OCR4Labels.builder()
 *     .sparseText()
 *     .enhance()
 *     .minConfidence(0.5)
 *     .run()
 *
 * // Region-based OCR (inspired by zindy/qupath-extension-ocr)
 * def results = OCR4Labels.builder()
 *     .region(0, 70, 400, 90)  // x, y, width, height
 *     .run()
 *
 * // Restricted character set (inspired by zindy/qupath-extension-ocr)
 * def results = OCR4Labels.builder()
 *     .allowedChars("0123456789")  // digits only
 *     .run()
 *
 * // Set metadata from results
 * if (results.size() > 0) {
 *     OCR4Labels.setMetadataValue("OCR_field_0", results[0])
 * }
 * </pre>
 *
 * @author Michael Nelson
 */
public class OCR4Labels {

    private static final Logger logger = LoggerFactory.getLogger(OCR4Labels.class);

    // Thread-local OCR engine for thread safety in batch processing
    private static final ThreadLocal<OCREngine> engineThreadLocal = new ThreadLocal<>();

    // Store the last OCR result for metadata operations
    private static final ThreadLocal<List<String>> lastResultsThreadLocal = new ThreadLocal<>();

    private OCR4Labels() {
        // Static utility class - no instantiation
    }

    // ========== Core OCR Methods ==========

    /**
     * Run OCR on the label image of the current image with default settings.
     * Uses Sparse Text mode, enhanced contrast, and 50% minimum confidence.
     *
     * @return List of detected text strings, or empty list if no text found
     */
    public static List<String> runOCR() {
        return builder().run();
    }

    /**
     * Run OCR with a specific configuration.
     *
     * @param config The OCR configuration to use
     * @return List of detected text strings
     */
    public static List<String> runOCR(OCRConfiguration config) {
        try {
            OCRResult result = runOCRDetailed(config);
            List<String> texts = extractTextsFromResult(result);
            lastResultsThreadLocal.set(texts);
            return texts;
        } catch (Exception e) {
            logger.error("OCR failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Run OCR and return full result with bounding boxes and metadata.
     *
     * @return Detailed OCR result, or empty result if processing fails
     */
    public static OCRResult runOCRDetailed() {
        return runOCRDetailed(getDefaultConfiguration());
    }

    /**
     * Run OCR with configuration and return full detailed result.
     *
     * @param config The OCR configuration to use
     * @return Detailed OCR result
     */
    public static OCRResult runOCRDetailed(OCRConfiguration config) {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null) {
            logger.warn("No image data available");
            return OCRResult.empty();
        }

        if (!hasLabelImage()) {
            logger.warn("No label image available for current image");
            return OCRResult.empty();
        }

        BufferedImage labelImage = getLabelImage();
        if (labelImage == null) {
            logger.warn("Failed to retrieve label image");
            return OCRResult.empty();
        }

        try {
            OCREngine engine = getOrCreateEngine();
            return engine.processImage(labelImage, config);
        } catch (OCREngine.OCRException e) {
            logger.error("OCR processing failed: {}", e.getMessage());
            return OCRResult.empty();
        }
    }

    // ========== Configuration Builder ==========

    /**
     * Create an OCR configuration builder for fluent API.
     *
     * <p>Example:</p>
     * <pre>
     * def results = OCR4Labels.builder()
     *     .sparseText()
     *     .enhance()
     *     .invert()
     *     .minConfidence(0.3)
     *     .run()
     * </pre>
     *
     * @return A new builder instance
     */
    public static OCRBuilder builder() {
        return new OCRBuilder();
    }

    // ========== Metadata Methods ==========

    /**
     * Set a metadata value on the current image's project entry.
     *
     * @param key The metadata key
     * @param value The metadata value
     */
    public static void setMetadataValue(String key, String value) {
        if (key == null || key.isEmpty()) {
            logger.warn("Cannot set metadata with empty key");
            return;
        }

        var project = QP.getProject();
        ImageData<?> imageData = QP.getCurrentImageData();

        if (project == null || imageData == null) {
            logger.warn("Cannot set metadata - no project or image data");
            return;
        }

        // Find the project entry for the current image
        ProjectImageEntry<?> entry = findCurrentEntry(project, imageData);
        if (entry != null) {
            entry.getMetadata().put(key, value != null ? value : "");
            logger.info("Set metadata: {} = {}", key, value);
        } else {
            logger.warn("Could not find project entry for current image");
        }
    }

    /**
     * Set OCR result as metadata on current image.
     * Uses the last OCR results from runOCR() or builder().run().
     *
     * @param fieldIndex The index of the detected field (0-based)
     * @param metadataKey The metadata key to use
     */
    public static void setMetadata(int fieldIndex, String metadataKey) {
        List<String> lastResults = lastResultsThreadLocal.get();
        if (lastResults == null || lastResults.isEmpty()) {
            logger.warn("No OCR results available. Run OCR first.");
            return;
        }

        if (fieldIndex < 0 || fieldIndex >= lastResults.size()) {
            logger.warn("Field index {} out of range (0-{})", fieldIndex, lastResults.size() - 1);
            return;
        }

        setMetadataValue(metadataKey, lastResults.get(fieldIndex));
    }

    /**
     * Set all OCR results as metadata using a map of field index to metadata key.
     *
     * @param fieldMappings Map of field index (0-based) to metadata key
     */
    public static void setMetadataFromMap(Map<Integer, String> fieldMappings) {
        if (fieldMappings == null || fieldMappings.isEmpty()) {
            return;
        }

        List<String> lastResults = lastResultsThreadLocal.get();
        if (lastResults == null || lastResults.isEmpty()) {
            logger.warn("No OCR results available. Run OCR first.");
            return;
        }

        for (Map.Entry<Integer, String> entry : fieldMappings.entrySet()) {
            int fieldIndex = entry.getKey();
            String metadataKey = entry.getValue();

            if (fieldIndex >= 0 && fieldIndex < lastResults.size()) {
                setMetadataValue(metadataKey, lastResults.get(fieldIndex));
            }
        }
    }

    // ========== Utility Methods ==========

    /**
     * Check if the current image has a label image.
     *
     * @return true if a label image is available
     */
    public static boolean hasLabelImage() {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null) {
            return false;
        }
        return LabelImageUtility.isLabelImageAvailable(imageData);
    }

    /**
     * Get the label image as a BufferedImage.
     *
     * @return The label image, or null if not available
     */
    public static BufferedImage getLabelImage() {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null) {
            return null;
        }
        return LabelImageUtility.retrieveLabelImage(imageData);
    }

    /**
     * Invert an image (for light text on dark background).
     *
     * @param image The image to invert
     * @return The inverted image
     */
    public static BufferedImage invertImage(BufferedImage image) {
        if (image == null) {
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage inverted = new BufferedImage(width, height, image.getType());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = 255 - ((rgb >> 16) & 0xFF);
                int g = 255 - ((rgb >> 8) & 0xFF);
                int b = 255 - (rgb & 0xFF);
                int a = (rgb >> 24) & 0xFF;
                inverted.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return inverted;
    }

    /**
     * Get the names of available associated images for the current image.
     *
     * @return List of associated image names (e.g., "label", "macro", "thumbnail")
     */
    public static List<String> getAssociatedImageNames() {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(LabelImageUtility.getAssociatedImageNames(imageData));
    }

    /**
     * Get the current image name.
     *
     * @return The image name, or empty string if not available
     */
    public static String getCurrentImageName() {
        ImageData<?> imageData = QP.getCurrentImageData();
        if (imageData == null) {
            return "";
        }
        var server = imageData.getServer();
        if (server == null) {
            return "";
        }
        var metadata = server.getMetadata();
        return metadata.getName() != null ? metadata.getName() : "";
    }

    // ========== Internal Methods ==========

    private static OCRConfiguration getDefaultConfiguration() {
        return OCRConfiguration.builder()
                .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
                .enhanceContrast(true)
                .minConfidence(0.5)
                .language(OCRPreferences.getLanguage())
                .detectOrientation(OCRPreferences.isDetectOrientation())
                .autoRotate(OCRPreferences.isAutoRotate())
                .build();
    }

    private static OCREngine getOrCreateEngine() throws OCREngine.OCRException {
        OCREngine engine = engineThreadLocal.get();
        if (engine == null || !engine.isInitialized()) {
            engine = new OCREngine();

            String tessdataPath = OCRPreferences.getTessdataPath();
            if (tessdataPath == null || tessdataPath.isEmpty()) {
                throw new OCREngine.OCRException(
                        "Tessdata path not configured. Please configure in Extensions > OCR for Labels > OCR Settings");
            }

            File tessdataDir = new File(tessdataPath);
            if (!tessdataDir.exists()) {
                throw new OCREngine.OCRException("Tessdata directory not found: " + tessdataPath);
            }

            engine.initialize(tessdataPath, OCRPreferences.getLanguage());
            engineThreadLocal.set(engine);
        }
        return engine;
    }

    private static List<String> extractTextsFromResult(OCRResult result) {
        if (result == null || !result.hasText()) {
            return Collections.emptyList();
        }

        // Get line-level text blocks for meaningful groupings
        List<TextBlock> lines = result.getTextBlocksByType(TextBlock.BlockType.LINE);
        if (!lines.isEmpty()) {
            return lines.stream()
                    .map(TextBlock::getText)
                    .filter(text -> text != null && !text.isEmpty())
                    .collect(Collectors.toList());
        }

        // Fall back to all text blocks
        return result.getTextBlocks().stream()
                .map(TextBlock::getText)
                .filter(text -> text != null && !text.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static ProjectImageEntry<?> findCurrentEntry(
            qupath.lib.projects.Project<?> project, ImageData<?> imageData) {
        if (project == null || imageData == null) {
            return null;
        }

        var server = imageData.getServer();
        if (server == null) {
            return null;
        }

        var serverUris = server.getURIs();
        String currentUri = serverUris.isEmpty() ? null :
                serverUris.iterator().next().toString();

        if (currentUri == null) {
            return null;
        }

        for (var entry : project.getImageList()) {
            try {
                var entryUris = entry.getURIs();
                String entryUri = entryUris.isEmpty() ? null :
                        entryUris.iterator().next().toString();
                if (currentUri.equals(entryUri)) {
                    return entry;
                }
            } catch (Exception e) {
                // Continue searching
            }
        }

        return null;
    }

    /**
     * Cleans up thread-local resources.
     * Should be called at end of batch processing if needed.
     */
    public static void cleanup() {
        OCREngine engine = engineThreadLocal.get();
        if (engine != null) {
            engine.dispose();
            engineThreadLocal.remove();
        }
        lastResultsThreadLocal.remove();
    }
}
