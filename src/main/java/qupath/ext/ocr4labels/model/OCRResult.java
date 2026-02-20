package qupath.ext.ocr4labels.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains the results of OCR processing on a single image.
 * Holds all detected text blocks along with processing metadata.
 */
public class OCRResult {

    private final List<TextBlock> textBlocks;
    private final long processingTimeMs;
    private final LocalDateTime timestamp;
    private final int originalImageWidth;
    private final int originalImageHeight;
    private final int detectedOrientation;

    /**
     * Creates a new OCR result.
     *
     * @param textBlocks         List of detected text blocks
     * @param processingTimeMs   Time taken for OCR processing in milliseconds
     * @param imageWidth         Width of the processed image
     * @param imageHeight        Height of the processed image
     * @param detectedOrientation Detected orientation in degrees (0, 90, 180, 270)
     */
    public OCRResult(List<TextBlock> textBlocks, long processingTimeMs,
                     int imageWidth, int imageHeight, int detectedOrientation) {
        this.textBlocks = textBlocks != null ?
                new ArrayList<>(textBlocks) : new ArrayList<>();
        this.processingTimeMs = processingTimeMs;
        this.timestamp = LocalDateTime.now();
        this.originalImageWidth = imageWidth;
        this.originalImageHeight = imageHeight;
        this.detectedOrientation = detectedOrientation;
    }

    /**
     * Creates a simple OCR result without image dimension tracking.
     */
    public OCRResult(List<TextBlock> textBlocks, long processingTimeMs) {
        this(textBlocks, processingTimeMs, 0, 0, 0);
    }

    /**
     * Creates an empty result (for error cases).
     */
    public static OCRResult empty() {
        return new OCRResult(Collections.emptyList(), 0);
    }

    /**
     * Gets an unmodifiable view of all detected text blocks.
     */
    public List<TextBlock> getTextBlocks() {
        return Collections.unmodifiableList(textBlocks);
    }

    /**
     * Gets text blocks filtered by minimum confidence.
     *
     * @param minConfidence Minimum confidence threshold (0.0 to 1.0)
     * @return List of text blocks meeting the threshold
     */
    public List<TextBlock> getTextBlocksAboveConfidence(double minConfidence) {
        return textBlocks.stream()
                .filter(block -> block.meetsConfidenceThreshold(minConfidence))
                .collect(Collectors.toList());
    }

    /**
     * Gets text blocks of a specific type.
     */
    public List<TextBlock> getTextBlocksByType(TextBlock.BlockType type) {
        return textBlocks.stream()
                .filter(block -> block.getType() == type)
                .collect(Collectors.toList());
    }

    /**
     * Gets the full concatenated text from all blocks.
     *
     * @return All detected text joined with spaces
     */
    public String getFullText() {
        return textBlocks.stream()
                .map(TextBlock::getText)
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" "));
    }

    /**
     * Gets the full text with line breaks between lines/paragraphs.
     */
    public String getFormattedText() {
        StringBuilder sb = new StringBuilder();
        TextBlock.BlockType lastType = null;

        for (TextBlock block : textBlocks) {
            if (lastType != null) {
                if (block.getType() == TextBlock.BlockType.LINE ||
                    block.getType() == TextBlock.BlockType.PARAGRAPH) {
                    sb.append("\n");
                } else {
                    sb.append(" ");
                }
            }
            sb.append(block.getText());
            lastType = block.getType();
        }

        return sb.toString();
    }

    /**
     * Gets the total number of detected text blocks (all types).
     */
    public int getBlockCount() {
        return textBlocks.size();
    }

    /**
     * Gets the count of LINE-type blocks.
     */
    public long getLineCount() {
        return textBlocks.stream()
                .filter(b -> b.getType() == TextBlock.BlockType.LINE && !b.isEmpty())
                .count();
    }

    /**
     * Gets the count of WORD-type blocks.
     */
    public long getWordCount() {
        return textBlocks.stream()
                .filter(b -> b.getType() == TextBlock.BlockType.WORD && !b.isEmpty())
                .count();
    }

    /**
     * Gets the count of blocks that will actually be displayed.
     * The dialog shows LINE blocks if any exist, otherwise WORD blocks.
     */
    public long getDisplayBlockCount() {
        long lines = getLineCount();
        return lines > 0 ? lines : getWordCount();
    }

    /**
     * Checks if any text was detected.
     */
    public boolean hasText() {
        return !textBlocks.isEmpty();
    }

    /**
     * Gets the average confidence across all blocks.
     *
     * @return Average confidence (0.0 to 1.0), or 0.0 if no blocks
     */
    public double getAverageConfidence() {
        if (textBlocks.isEmpty()) {
            return 0.0;
        }
        return textBlocks.stream()
                .mapToDouble(TextBlock::getConfidence)
                .average()
                .orElse(0.0);
    }

    /**
     * Gets the minimum confidence among all blocks.
     */
    public double getMinConfidence() {
        return textBlocks.stream()
                .mapToDouble(TextBlock::getConfidence)
                .min()
                .orElse(0.0);
    }

    /**
     * Gets the maximum confidence among all blocks.
     */
    public double getMaxConfidence() {
        return textBlocks.stream()
                .mapToDouble(TextBlock::getConfidence)
                .max()
                .orElse(0.0);
    }

    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public int getOriginalImageWidth() {
        return originalImageWidth;
    }

    public int getOriginalImageHeight() {
        return originalImageHeight;
    }

    /**
     * Gets the detected orientation in degrees.
     * Values: 0 (upright), 90 (rotated right), 180 (upside down), 270 (rotated left)
     */
    public int getDetectedOrientation() {
        return detectedOrientation;
    }

    @Override
    public String toString() {
        return String.format("OCRResult[blocks=%d, avgConfidence=%.0f%%, time=%dms, orientation=%d]",
                textBlocks.size(),
                getAverageConfidence() * 100,
                processingTimeMs,
                detectedOrientation);
    }
}
