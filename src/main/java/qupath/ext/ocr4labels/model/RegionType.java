package qupath.ext.ocr4labels.model;

/**
 * Defines the type of content expected in a region for decoding.
 * <p>
 * Used to determine whether to use OCR (Tesseract) or barcode scanning (ZXing)
 * when extracting content from a defined region.
 */
public enum RegionType {

    /**
     * Text content - use OCR (Tesseract) for extraction.
     */
    TEXT("Text", "Use OCR to extract text", "TXT"),

    /**
     * Barcode content - use barcode scanner (ZXing) for decoding.
     * Supports 1D barcodes (Code128, Code39, EAN, UPC, etc.) and
     * 2D codes (QR Code, DataMatrix, PDF417, Aztec).
     */
    BARCODE("Barcode", "Use barcode scanner to decode 1D/2D codes", "BAR"),

    /**
     * Auto-detect - try barcode scanning first, fall back to OCR if no barcode found.
     * This is useful when the region content type is unknown or may vary.
     */
    AUTO("Try Both", "Try barcode first, fall back to OCR if not found", "?");

    private final String displayName;
    private final String description;
    private final String shortLabel;

    RegionType(String displayName, String description, String shortLabel) {
        this.displayName = displayName;
        this.description = description;
        this.shortLabel = shortLabel;
    }

    /**
     * Gets the user-friendly display name for this region type.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the description explaining what this region type does.
     *
     * @return The description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the short label for overlay display (e.g., "TXT", "BAR", "?").
     *
     * @return The short label
     */
    public String getShortLabel() {
        return shortLabel;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Parses a RegionType from a string value.
     * Defaults to TEXT if the value is null or unrecognized (for backward compatibility).
     *
     * @param value The string value to parse
     * @return The corresponding RegionType, or TEXT if not recognized
     */
    public static RegionType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return TEXT;
        }

        for (RegionType type : values()) {
            if (type.name().equalsIgnoreCase(value) ||
                type.displayName.equalsIgnoreCase(value)) {
                return type;
            }
        }

        return TEXT; // Default for backward compatibility with old templates
    }
}
