package qupath.ext.ocr4labels.model;

/**
 * Configuration for extracting a label region from an associated image
 * (e.g., cropping the label portion out of a "macro" image).
 *
 * <p>Stored as part of an {@link OCRTemplate} and applied before OCR
 * processing. This allows batch processing of slides that lack a
 * dedicated "label" associated image.
 */
public class LabelExtractionConfig {

    private String sourceImageName;
    private int cropX;
    private int cropY;
    private int cropWidth;
    private int cropHeight;
    private int rotation; // 0, 90, 180, 270
    private boolean flipHorizontal;
    private boolean flipVertical;

    /** No-arg constructor for Gson deserialization. */
    public LabelExtractionConfig() {}

    public LabelExtractionConfig(String sourceImageName,
            int cropX, int cropY, int cropWidth, int cropHeight,
            int rotation, boolean flipHorizontal, boolean flipVertical) {
        this.sourceImageName = sourceImageName;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropWidth = cropWidth;
        this.cropHeight = cropHeight;
        this.rotation = rotation;
        this.flipHorizontal = flipHorizontal;
        this.flipVertical = flipVertical;
    }

    public String getSourceImageName() { return sourceImageName; }
    public void setSourceImageName(String sourceImageName) { this.sourceImageName = sourceImageName; }

    public int getCropX() { return cropX; }
    public void setCropX(int cropX) { this.cropX = cropX; }

    public int getCropY() { return cropY; }
    public void setCropY(int cropY) { this.cropY = cropY; }

    public int getCropWidth() { return cropWidth; }
    public void setCropWidth(int cropWidth) { this.cropWidth = cropWidth; }

    public int getCropHeight() { return cropHeight; }
    public void setCropHeight(int cropHeight) { this.cropHeight = cropHeight; }

    public int getRotation() { return rotation; }
    public void setRotation(int rotation) { this.rotation = rotation; }

    public boolean isFlipHorizontal() { return flipHorizontal; }
    public void setFlipHorizontal(boolean flipHorizontal) { this.flipHorizontal = flipHorizontal; }

    public boolean isFlipVertical() { return flipVertical; }
    public void setFlipVertical(boolean flipVertical) { this.flipVertical = flipVertical; }

    /** Returns true if a crop region has been defined (non-zero dimensions). */
    public boolean hasCropRegion() {
        return cropWidth > 0 && cropHeight > 0;
    }

    @Override
    public String toString() {
        return String.format("LabelExtractionConfig[source=%s, crop=(%d,%d,%d,%d), rot=%d, flipH=%b, flipV=%b]",
                sourceImageName, cropX, cropY, cropWidth, cropHeight, rotation, flipHorizontal, flipVertical);
    }
}
