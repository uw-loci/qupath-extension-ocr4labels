package qupath.ext.ocr4labels.utilities;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import qupath.ext.ocr4labels.model.RegionType;

/**
 * Crop geometry for decoding one region of a label image: snapping a hand-drawn
 * rectangle to the ink inside it, and the margins added around a region before decoding.
 */
public final class RegionCrop {

    /** Grey-level spread below which a region is treated as blank and left unsnapped. */
    static final int MIN_CONTRAST = 40;
    /** Ink pixels a row or column needs to count, so an isolated speck does not. */
    static final int MIN_INK_PIXELS = 2;
    /** Pixels kept around the snapped ink for its anti-aliased edge. */
    static final int SNAP_MARGIN = 2;

    private RegionCrop() {}

    /**
     * Shrinks a drawn rectangle to the ink inside it, plus {@link #SNAP_MARGIN}.
     * The result never extends past the drawn rectangle.
     *
     * @param image    the label image
     * @param drawn    the drawn rectangle, in image pixels
     * @param lightInk true if the text is lighter than its background
     * @return the snapped rectangle, or {@code drawn} if no ink stands out
     */
    public static Rectangle snapToInk(BufferedImage image, Rectangle drawn, boolean lightInk) {
        Rectangle area = drawn.intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        if (area.isEmpty()) {
            return drawn;
        }
        int w = area.width;
        int h = area.height;
        int[] gray = new int[w * h];
        int min = 255;
        int max = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(area.x + x, area.y + y);
                int g = (((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114) / 1000;
                gray[y * w + x] = g;
                min = Math.min(min, g);
                max = Math.max(max, g);
            }
        }
        if (max - min < MIN_CONTRAST) {
            return drawn;
        }

        int threshold = otsuThreshold(gray);
        int[] rowInk = new int[h];
        int[] colInk = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int g = gray[y * w + x];
                if (lightInk ? g > threshold : g <= threshold) {
                    rowInk[y]++;
                    colInk[x]++;
                }
            }
        }

        int top = firstAtLeast(rowInk, MIN_INK_PIXELS, false);
        int bottom = firstAtLeast(rowInk, MIN_INK_PIXELS, true);
        int left = firstAtLeast(colInk, MIN_INK_PIXELS, false);
        int right = firstAtLeast(colInk, MIN_INK_PIXELS, true);
        if (top < 0 || left < 0) {
            return drawn;
        }

        int x0 = Math.max(0, left - SNAP_MARGIN);
        int y0 = Math.max(0, top - SNAP_MARGIN);
        int x1 = Math.min(w - 1, right + SNAP_MARGIN);
        int y1 = Math.min(h - 1, bottom + SNAP_MARGIN);
        if (x1 - x0 + 1 < 5 || y1 - y0 + 1 < 5) {
            return drawn;
        }
        return new Rectangle(area.x + x0, area.y + y0, x1 - x0 + 1, y1 - y0 + 1);
    }

    /**
     * Text margins tried around a region, as fractions of its height; the most confident
     * read wins. On a real label no single margin read every field: 0.10 read "8443",
     * "02_IF" and "SM1-2" but not the date, 0.25 only the date. The best of the three
     * read all of them.
     */
    static final float[] TEXT_MARGINS = {0.10f, 0.25f, 0.40f};

    /**
     * Real image pixels to add around a stored region before cropping it, one entry per
     * crop to try. Text margins scale with the text HEIGHT, so a wide box cannot reach the
     * next word; barcode margins scale with the box, as ZXing's boxes can be partial.
     *
     * @return {padX, padY} on each side, per candidate crop
     */
    public static java.util.List<int[]> margins(int width, int height, RegionType type) {
        if (type == RegionType.BARCODE) {
            return java.util.List.of(new int[] {Math.round(width * 0.125f), Math.round(height * 0.125f)});
        }
        java.util.List<int[]> result = new java.util.ArrayList<>();
        for (float f : TEXT_MARGINS) {
            int pad = Math.round(height * f);
            result.add(new int[] {pad, pad});
        }
        return result;
    }

    /**
     * Plain border drawn around a crop, which gives the decoder margin without pulling
     * in neighbouring ink. Wider for anything that may be a barcode: ZXing needs a quiet zone.
     *
     * @return {padX, padY} on each side
     */
    public static int[] quietBorder(int width, int height, RegionType type) {
        int padY = Math.max(8, Math.round(height * 0.2f));
        int padX = type == RegionType.TEXT ? padY : Math.max(padY, Math.round(width * 0.1f));
        return new int[] {padX, padY};
    }

    /**
     * The colour to fill a quiet border with: the median of the region's outermost pixels,
     * i.e. its background. A white border around a grey label adds a hard edge, and on a
     * background darker than about 170 Tesseract then read NOTHING where the same crop
     * with a background-coloured border read correctly.
     *
     * @param region the cropped region
     * @return packed RGB of the background
     */
    public static int backgroundRgb(BufferedImage region) {
        int w = region.getWidth();
        int h = region.getHeight();
        int n = h > 1 ? 2 * w + 2 * (h - 2) : w;
        int[] r = new int[n];
        int[] g = new int[n];
        int[] b = new int[n];
        int k = 0;
        for (int y = 0; y < h; y++) {
            boolean edgeRow = y == 0 || y == h - 1;
            for (int x = 0; x < w; x++) {
                if (!edgeRow && x != 0 && x != w - 1) {
                    continue;
                }
                if (k == n) {
                    break;
                }
                int rgb = region.getRGB(x, y);
                r[k] = (rgb >> 16) & 0xff;
                g[k] = (rgb >> 8) & 0xff;
                b[k] = rgb & 0xff;
                k++;
            }
        }
        return (median(r, k) << 16) | (median(g, k) << 8) | median(b, k);
    }

    private static int median(int[] values, int count) {
        int[] sorted = java.util.Arrays.copyOf(values, count);
        java.util.Arrays.sort(sorted);
        return sorted[count / 2];
    }

    private static int firstAtLeast(int[] counts, int min, boolean fromEnd) {
        for (int i = 0; i < counts.length; i++) {
            int k = fromEnd ? counts.length - 1 - i : i;
            if (counts[k] >= min) {
                return k;
            }
        }
        return -1;
    }

    private static int otsuThreshold(int[] gray) {
        int[] hist = new int[256];
        for (int g : gray) {
            hist[g]++;
        }
        long total = gray.length;
        long sumAll = 0;
        for (int i = 0; i < 256; i++) {
            sumAll += (long) i * hist[i];
        }
        long sumBack = 0;
        long weightBack = 0;
        double best = -1;
        int threshold = 127;
        for (int t = 0; t < 256; t++) {
            weightBack += hist[t];
            if (weightBack == 0) {
                continue;
            }
            long weightFore = total - weightBack;
            if (weightFore == 0) {
                break;
            }
            sumBack += (long) t * hist[t];
            double meanBack = (double) sumBack / weightBack;
            double meanFore = (double) (sumAll - sumBack) / weightFore;
            double between = (double) weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore);
            if (between > best) {
                best = between;
                threshold = t;
            }
        }
        return threshold;
    }
}
