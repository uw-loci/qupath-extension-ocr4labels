package qupath.ext.ocr4labels.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Template for batch OCR processing.
 * Stores the mapping between detected field positions and metadata keys.
 * Can be saved/loaded to allow reuse across sessions.
 */
public class OCRTemplate {

    private static final Logger logger = LoggerFactory.getLogger(OCRTemplate.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String name;
    private String description;
    private List<FieldMapping> fieldMappings;
    private OCRConfiguration configuration;
    private long createdTimestamp;
    private boolean useFixedPositions;
    private double dilationFactor = 1.2; // Default 20% dilation

    /**
     * Creates a new empty template.
     */
    public OCRTemplate() {
        this.fieldMappings = new ArrayList<>();
        this.createdTimestamp = System.currentTimeMillis();
    }

    /**
     * Creates a template with the given name.
     */
    public OCRTemplate(String name) {
        this();
        this.name = name;
    }

    /**
     * Represents a mapping from a detected field to a metadata key.
     * Optionally stores the bounding box for fixed-position OCR mode.
     * Supports different region types (TEXT, BARCODE, AUTO) for hybrid decoding.
     */
    public static class FieldMapping {
        private int fieldIndex;
        private String metadataKey;
        private String exampleText;
        private boolean enabled;
        // Bounding box for fixed-position mode (normalized 0-1 coordinates)
        private double normalizedX;
        private double normalizedY;
        private double normalizedWidth;
        private double normalizedHeight;
        private boolean hasBoundingBox;
        // Region type for hybrid decoding (TEXT, BARCODE, or AUTO)
        private String regionType; // Stored as string for Gson compatibility

        public FieldMapping() {
            this.enabled = true;
            this.hasBoundingBox = false;
            this.regionType = RegionType.TEXT.name(); // Default for backward compatibility
        }

        public FieldMapping(int fieldIndex, String metadataKey, String exampleText) {
            this.fieldIndex = fieldIndex;
            this.metadataKey = metadataKey;
            this.exampleText = exampleText;
            this.enabled = true;
            this.hasBoundingBox = false;
            this.regionType = RegionType.TEXT.name();
        }

        public FieldMapping(int fieldIndex, String metadataKey, String exampleText,
                           double normalizedX, double normalizedY,
                           double normalizedWidth, double normalizedHeight) {
            this.fieldIndex = fieldIndex;
            this.metadataKey = metadataKey;
            this.exampleText = exampleText;
            this.enabled = true;
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.normalizedWidth = normalizedWidth;
            this.normalizedHeight = normalizedHeight;
            this.hasBoundingBox = true;
            this.regionType = RegionType.TEXT.name();
        }

        /**
         * Full constructor with region type support.
         */
        public FieldMapping(int fieldIndex, String metadataKey, String exampleText,
                           double normalizedX, double normalizedY,
                           double normalizedWidth, double normalizedHeight,
                           RegionType regionType) {
            this.fieldIndex = fieldIndex;
            this.metadataKey = metadataKey;
            this.exampleText = exampleText;
            this.enabled = true;
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.normalizedWidth = normalizedWidth;
            this.normalizedHeight = normalizedHeight;
            this.hasBoundingBox = true;
            this.regionType = regionType != null ? regionType.name() : RegionType.TEXT.name();
        }

        public int getFieldIndex() {
            return fieldIndex;
        }

        public void setFieldIndex(int fieldIndex) {
            this.fieldIndex = fieldIndex;
        }

        public String getMetadataKey() {
            return metadataKey;
        }

        public void setMetadataKey(String metadataKey) {
            this.metadataKey = metadataKey;
        }

        public String getExampleText() {
            return exampleText;
        }

        public void setExampleText(String exampleText) {
            this.exampleText = exampleText;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean hasBoundingBox() {
            return hasBoundingBox;
        }

        public double getNormalizedX() {
            return normalizedX;
        }

        public double getNormalizedY() {
            return normalizedY;
        }

        public double getNormalizedWidth() {
            return normalizedWidth;
        }

        public double getNormalizedHeight() {
            return normalizedHeight;
        }

        public void setBoundingBox(double normalizedX, double normalizedY,
                                   double normalizedWidth, double normalizedHeight) {
            this.normalizedX = normalizedX;
            this.normalizedY = normalizedY;
            this.normalizedWidth = normalizedWidth;
            this.normalizedHeight = normalizedHeight;
            this.hasBoundingBox = true;
        }

        /**
         * Gets the region type for this field.
         * Defaults to TEXT for backward compatibility with old templates.
         *
         * @return The region type
         */
        public RegionType getRegionType() {
            return RegionType.fromString(regionType);
        }

        /**
         * Sets the region type for this field.
         *
         * @param type The region type
         */
        public void setRegionType(RegionType type) {
            this.regionType = type != null ? type.name() : RegionType.TEXT.name();
        }

        /**
         * Gets the bounding box coordinates scaled to an image of the given dimensions,
         * optionally dilated by the specified factor.
         *
         * @param imageWidth The image width in pixels
         * @param imageHeight The image height in pixels
         * @param dilationFactor Dilation factor (e.g., 1.2 for 20% larger)
         * @return int array [x, y, width, height] in pixel coordinates
         */
        public int[] getScaledBoundingBox(int imageWidth, int imageHeight, double dilationFactor) {
            if (!hasBoundingBox) return null;

            double centerX = normalizedX + normalizedWidth / 2;
            double centerY = normalizedY + normalizedHeight / 2;
            double dilatedWidth = normalizedWidth * dilationFactor;
            double dilatedHeight = normalizedHeight * dilationFactor;

            double x = (centerX - dilatedWidth / 2) * imageWidth;
            double y = (centerY - dilatedHeight / 2) * imageHeight;
            double w = dilatedWidth * imageWidth;
            double h = dilatedHeight * imageHeight;

            // Clamp to image bounds
            x = Math.max(0, x);
            y = Math.max(0, y);
            w = Math.min(w, imageWidth - x);
            h = Math.min(h, imageHeight - y);

            return new int[]{(int) x, (int) y, (int) w, (int) h};
        }

        @Override
        public String toString() {
            return String.format("Field %d -> %s [%s] (example: '%s')",
                    fieldIndex, metadataKey, getRegionType().getDisplayName(), exampleText);
        }
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<FieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    public void setFieldMappings(List<FieldMapping> fieldMappings) {
        this.fieldMappings = fieldMappings != null ? fieldMappings : new ArrayList<>();
    }

    public void addFieldMapping(FieldMapping mapping) {
        if (fieldMappings == null) {
            fieldMappings = new ArrayList<>();
        }
        fieldMappings.add(mapping);
    }

    public OCRConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OCRConfiguration configuration) {
        this.configuration = configuration;
    }

    public long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public boolean isUseFixedPositions() {
        return useFixedPositions;
    }

    public void setUseFixedPositions(boolean useFixedPositions) {
        this.useFixedPositions = useFixedPositions;
    }

    public double getDilationFactor() {
        return dilationFactor;
    }

    public void setDilationFactor(double dilationFactor) {
        this.dilationFactor = dilationFactor;
    }

    /**
     * Checks if this template has bounding box data for fixed-position mode.
     */
    public boolean hasBoundingBoxData() {
        if (fieldMappings == null) return false;
        return fieldMappings.stream().anyMatch(FieldMapping::hasBoundingBox);
    }

    /**
     * Gets the number of enabled field mappings.
     */
    public int getEnabledMappingCount() {
        if (fieldMappings == null) return 0;
        return (int) fieldMappings.stream().filter(FieldMapping::isEnabled).count();
    }

    /**
     * Saves this template to a JSON file.
     *
     * @param file The file to save to
     * @throws IOException if saving fails
     */
    public void saveToFile(File file) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(this, writer);
            logger.info("Saved OCR template to: {}", file.getAbsolutePath());
        }
    }

    /**
     * Loads a template from a JSON file.
     *
     * @param file The file to load from
     * @return The loaded template
     * @throws IOException if loading fails
     */
    public static OCRTemplate loadFromFile(File file) throws IOException {
        try (Reader reader = new FileReader(file)) {
            OCRTemplate template = GSON.fromJson(reader, OCRTemplate.class);
            logger.info("Loaded OCR template from: {}", file.getAbsolutePath());
            return template;
        }
    }

    /**
     * Creates a template from the current OCR dialog field entries.
     *
     * @param name The template name
     * @param entries The field entries from the OCR dialog
     * @param config The OCR configuration used
     * @return A new template
     */
    public static OCRTemplate fromFieldEntries(String name,
            List<? extends FieldEntryProvider> entries,
            OCRConfiguration config) {
        OCRTemplate template = new OCRTemplate(name);
        template.setConfiguration(config);

        for (int i = 0; i < entries.size(); i++) {
            FieldEntryProvider entry = entries.get(i);
            String key = entry.getMetadataKey();
            String text = entry.getText();

            // Only include entries with valid keys
            if (key != null && !key.isEmpty()) {
                FieldMapping mapping = new FieldMapping(i, key, text);
                mapping.setRegionType(entry.getRegionType());
                template.addFieldMapping(mapping);
            }
        }

        return template;
    }

    /**
     * Interface for field entry providers (allows different implementations).
     */
    public interface FieldEntryProvider {
        String getText();
        String getMetadataKey();

        /**
         * Gets the region type for this field entry.
         * Default implementation returns TEXT for backward compatibility.
         *
         * @return The region type
         */
        default RegionType getRegionType() {
            return RegionType.TEXT;
        }
    }

    @Override
    public String toString() {
        return String.format("OCRTemplate[name='%s', mappings=%d]",
                name, fieldMappings != null ? fieldMappings.size() : 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OCRTemplate that = (OCRTemplate) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
