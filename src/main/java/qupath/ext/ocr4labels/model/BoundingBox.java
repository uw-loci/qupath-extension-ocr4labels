package qupath.ext.ocr4labels.model;

import java.util.Objects;

/**
 * Represents a rectangular bounding box for detected text regions.
 * Uses image coordinates with origin at top-left.
 */
public class BoundingBox {

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    /**
     * Creates a new bounding box.
     *
     * @param x      X coordinate of top-left corner
     * @param y      Y coordinate of top-left corner
     * @param width  Width of the box
     * @param height Height of the box
     * @throws IllegalArgumentException if width or height is negative
     */
    public BoundingBox(int x, int y, int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Width and height must be non-negative");
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * Gets the X coordinate of the right edge.
     */
    public int getMaxX() {
        return x + width;
    }

    /**
     * Gets the Y coordinate of the bottom edge.
     */
    public int getMaxY() {
        return y + height;
    }

    /**
     * Gets the center X coordinate.
     */
    public double getCenterX() {
        return x + width / 2.0;
    }

    /**
     * Gets the center Y coordinate.
     */
    public double getCenterY() {
        return y + height / 2.0;
    }

    /**
     * Calculates the area of this bounding box.
     */
    public int getArea() {
        return width * height;
    }

    /**
     * Checks if this bounding box contains the specified point.
     */
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /**
     * Checks if this bounding box intersects with another.
     */
    public boolean intersects(BoundingBox other) {
        return x < other.getMaxX() && getMaxX() > other.x &&
               y < other.getMaxY() && getMaxY() > other.y;
    }

    /**
     * Creates a new bounding box expanded by the specified margin on all sides.
     */
    public BoundingBox expand(int margin) {
        return new BoundingBox(
                x - margin,
                y - margin,
                width + 2 * margin,
                height + 2 * margin
        );
    }

    /**
     * Creates a new bounding box that is the union of this box and another.
     * The result is the smallest box that contains both boxes.
     *
     * @param other The other bounding box
     * @return A new bounding box containing both
     */
    public BoundingBox expandedWith(BoundingBox other) {
        if (other == null) {
            return this;
        }
        int minX = Math.min(this.x, other.x);
        int minY = Math.min(this.y, other.y);
        int maxX = Math.max(this.getMaxX(), other.getMaxX());
        int maxY = Math.max(this.getMaxY(), other.getMaxY());
        return new BoundingBox(minX, minY, maxX - minX, maxY - minY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoundingBox that = (BoundingBox) o;
        return x == that.x && y == that.y && width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return String.format("BoundingBox[x=%d, y=%d, w=%d, h=%d]", x, y, width, height);
    }
}
