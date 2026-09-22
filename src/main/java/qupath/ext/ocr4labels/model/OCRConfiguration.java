package qupath.ext.ocr4labels.model;

import java.awt.Rectangle;
import java.util.Objects;

/**
 * Immutable configuration for OCR processing.
 * Use the {@link Builder} to create instances.
 */
public class OCRConfiguration {

    /**
     * Tesseract Page Segmentation Modes.
     */
    public enum PageSegMode {
        /** Orientation and script detection only */
        OSD_ONLY(0),
        /** Automatic page segmentation with OSD */
        AUTO_OSD(1),
        /** Automatic page segmentation, no OSD */
        AUTO(3),
        /** Assume a single column of text */
        SINGLE_COLUMN(4),
        /** Assume a single uniform block of vertically aligned text */
        SINGLE_BLOCK_VERT(5),
        /** Assume a single uniform block of text */
        SINGLE_BLOCK(6),
        /** Treat the image as a single text line */
        SINGLE_LINE(7),
        /** Treat the image as a single word */
        SINGLE_WORD(8),
        /** Treat the image as a single word in a circle */
        CIRCLE_WORD(9),
        /** Treat the image as a single character */
        SINGLE_CHAR(10),
        /** Find as much text as possible in no particular order */
        SPARSE_TEXT(11),
        /** Sparse text with OSD */
        SPARSE_TEXT_OSD(12),
        /** Raw line - treat the image as a single text line, no hacks */
        RAW_LINE(13);

        private final int value;

        PageSegMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        /**
         * @param value a Tesseract page segmentation mode number
         * @return the matching mode, or {@link #AUTO} if the number is unknown
         */
        public static PageSegMode fromValue(int value) {
            for (PageSegMode mode : values()) {
                if (mode.value == value) {
                    return mode;
                }
            }
            return AUTO;
        }
    }

    /**
     * Tesseract OCR Engine Modes.
     */
    public enum EngineMode {
        /** Legacy Tesseract only */
        LEGACY(0),
        /** Neural net LSTM only */
        LSTM_ONLY(1),
        /** Legacy + LSTM (most accurate but slower) */
        COMBINED(2),
        /** Default based on what's available */
        DEFAULT(3);

        private final int value;

        EngineMode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    private final PageSegMode pageSegMode;
    private final EngineMode engineMode;
    private final String language;
    private final double minConfidence;
    private final boolean enablePreprocessing;
    private final boolean autoRotate;
    private final boolean enhanceContrast;
    private final boolean detectOrientation;
    // Inspired by zindy/qupath-extension-ocr - region-based OCR and character whitelist support
    private final Rectangle cropRegion;
    private final String characterWhitelist;
    private final boolean literalText;

    private OCRConfiguration(Builder builder) {
        this.pageSegMode = builder.pageSegMode;
        this.engineMode = builder.engineMode;
        this.language = builder.language;
        this.minConfidence = builder.minConfidence;
        this.enablePreprocessing = builder.enablePreprocessing;
        this.autoRotate = builder.autoRotate;
        this.enhanceContrast = builder.enhanceContrast;
        this.detectOrientation = builder.detectOrientation;
        this.cropRegion = builder.cropRegion;
        this.characterWhitelist = builder.characterWhitelist;
        this.literalText = builder.literalText;
    }

    /**
     * Creates a default configuration optimized for slide labels.
     */
    public static OCRConfiguration createDefault() {
        return builder().build();
    }

    /**
     * Creates a new builder with default values.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder pre-populated with this configuration's values.
     */
    public Builder toBuilder() {
        return new Builder()
                .pageSegMode(pageSegMode)
                .engineMode(engineMode)
                .language(language)
                .minConfidence(minConfidence)
                .enablePreprocessing(enablePreprocessing)
                .autoRotate(autoRotate)
                .enhanceContrast(enhanceContrast)
                .detectOrientation(detectOrientation)
                .cropRegion(cropRegion)
                .characterWhitelist(characterWhitelist)
                .literalText(literalText);
    }

    public PageSegMode getPageSegMode() {
        return pageSegMode;
    }

    public EngineMode getEngineMode() {
        return engineMode;
    }

    public String getLanguage() {
        return language;
    }

    public double getMinConfidence() {
        return minConfidence;
    }

    public boolean isEnablePreprocessing() {
        return enablePreprocessing;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    public boolean isEnhanceContrast() {
        return enhanceContrast;
    }

    public boolean isDetectOrientation() {
        return detectOrientation;
    }

    /**
     * Gets the region to crop before OCR processing.
     * Inspired by zindy/qupath-extension-ocr region-based OCR support.
     *
     * @return The crop region, or null to process the full image
     */
    public Rectangle getCropRegion() {
        return cropRegion;
    }

    /**
     * Gets the character whitelist for OCR.
     * Inspired by zindy/qupath-extension-ocr character whitelist support.
     *
     * @return The whitelist string (e.g., "0123456789"), or null for no restriction
     */
    public String getCharacterWhitelist() {
        return characterWhitelist;
    }

    /**
     * Checks if a crop region is defined.
     *
     * @return true if OCR should be limited to a specific region
     */
    public boolean hasCropRegion() {
        return cropRegion != null;
    }

    /**
     * Checks if a character whitelist is defined.
     *
     * @return true if OCR should be restricted to specific characters
     */
    public boolean hasCharacterWhitelist() {
        return characterWhitelist != null && !characterWhitelist.isEmpty();
    }

    /**
     * Checks whether literal (dictionary-free) transcription is enabled.
     *
     * <p>Tesseract's LSTM decoder is a beam search that scores candidate character
     * paths against the language's dictionaries, so it prefers readings that look
     * like real words. Slide labels are mostly accession numbers, sample codes and
     * dates, none of which are dictionary words, so dictionary correction has no
     * legitimate role. When this is enabled the word, frequent-word, punctuation,
     * number and bigram dictionaries are all switched off.</p>
     *
     * <p>Treat this as a safeguard rather than an accuracy fix: in controlled tests it
     * made no measurable difference to misread label text. See
     * {@code OCREngine.applyLiteralTextMode} for what did.</p>
     *
     * @return true if the language dictionaries should be disabled
     */
    public boolean isLiteralText() {
        return literalText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OCRConfiguration that = (OCRConfiguration) o;
        return Double.compare(that.minConfidence, minConfidence) == 0 &&
               enablePreprocessing == that.enablePreprocessing &&
               autoRotate == that.autoRotate &&
               enhanceContrast == that.enhanceContrast &&
               detectOrientation == that.detectOrientation &&
               pageSegMode == that.pageSegMode &&
               engineMode == that.engineMode &&
               Objects.equals(language, that.language) &&
               literalText == that.literalText &&
               Objects.equals(cropRegion, that.cropRegion) &&
               Objects.equals(characterWhitelist, that.characterWhitelist);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pageSegMode, engineMode, language, minConfidence,
                enablePreprocessing, autoRotate, enhanceContrast, detectOrientation,
                cropRegion, characterWhitelist, literalText);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("OCRConfiguration[psm=%s, oem=%s, lang=%s, minConf=%.0f%%, " +
                        "preprocess=%b, autoRotate=%b, contrast=%b, detectOrient=%b, literal=%b",
                pageSegMode, engineMode, language, minConfidence * 100,
                enablePreprocessing, autoRotate, enhanceContrast, detectOrientation,
                literalText));
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

    /**
     * Builder for creating {@link OCRConfiguration} instances.
     */
    public static class Builder {
        private PageSegMode pageSegMode = PageSegMode.AUTO;
        private EngineMode engineMode = EngineMode.LSTM_ONLY;
        private String language = "eng";
        private double minConfidence = 0.5;
        private boolean enablePreprocessing = true;
        private boolean autoRotate = true;
        private boolean enhanceContrast = false; // see OCRPreferences.DEFAULT_ENHANCE_CONTRAST
        private boolean detectOrientation = true;
        // Inspired by zindy/qupath-extension-ocr - region-based OCR and character whitelist support
        private Rectangle cropRegion = null;
        private String characterWhitelist = null;
        // Defaults on: slide labels carry codes and identifiers, not prose, so the
        // dictionary bias corrupts more text than it repairs. See isLiteralText().
        private boolean literalText = true;

        public Builder pageSegMode(PageSegMode mode) {
            this.pageSegMode = mode != null ? mode : PageSegMode.AUTO;
            return this;
        }

        public Builder engineMode(EngineMode mode) {
            this.engineMode = mode != null ? mode : EngineMode.LSTM_ONLY;
            return this;
        }

        public Builder language(String language) {
            this.language = language != null && !language.isEmpty() ? language : "eng";
            return this;
        }

        public Builder minConfidence(double confidence) {
            this.minConfidence = Math.max(0.0, Math.min(1.0, confidence));
            return this;
        }

        public Builder enablePreprocessing(boolean enable) {
            this.enablePreprocessing = enable;
            return this;
        }

        public Builder autoRotate(boolean enable) {
            this.autoRotate = enable;
            return this;
        }

        public Builder enhanceContrast(boolean enable) {
            this.enhanceContrast = enable;
            return this;
        }

        public Builder detectOrientation(boolean enable) {
            this.detectOrientation = enable;
            return this;
        }

        /**
         * Sets a region to crop before OCR processing.
         * Inspired by zindy/qupath-extension-ocr region-based OCR support.
         *
         * @param region The region to crop, or null to process the full image
         * @return this builder
         */
        public Builder cropRegion(Rectangle region) {
            this.cropRegion = region;
            return this;
        }

        /**
         * Sets a region to crop before OCR processing.
         * Inspired by zindy/qupath-extension-ocr region-based OCR support.
         *
         * @param x      X coordinate of top-left corner
         * @param y      Y coordinate of top-left corner
         * @param width  Width of the region
         * @param height Height of the region
         * @return this builder
         */
        public Builder cropRegion(int x, int y, int width, int height) {
            this.cropRegion = new Rectangle(x, y, width, height);
            return this;
        }

        /**
         * Sets a character whitelist to restrict OCR output.
         * Inspired by zindy/qupath-extension-ocr character whitelist support.
         *
         * <p>Example: "0123456789" to only recognize digits.</p>
         *
         * @param whitelist The allowed characters, or null for no restriction
         * @return this builder
         */
        public Builder characterWhitelist(String whitelist) {
            this.characterWhitelist = whitelist;
            return this;
        }

        /**
         * Enables or disables literal (dictionary-free) transcription.
         *
         * <p>Defaults to true. Turn it off only when the labels carry ordinary
         * words -- handwritten tissue names or staining notes -- where the
         * language model repairs more than it breaks.</p>
         *
         * @param literal true to disable Tesseract's dictionaries
         * @return this builder
         * @see OCRConfiguration#isLiteralText()
         */
        public Builder literalText(boolean literal) {
            this.literalText = literal;
            return this;
        }

        public OCRConfiguration build() {
            return new OCRConfiguration(this);
        }
    }
}
