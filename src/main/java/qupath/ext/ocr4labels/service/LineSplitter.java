package qupath.ext.ocr4labels.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import qupath.ext.ocr4labels.model.BoundingBox;
import qupath.ext.ocr4labels.model.TextBlock;

/**
 * Rebuilds Tesseract text lines from their kept words, split wherever the words are
 * separated by a column-sized gap. Tesseract joins everything on one baseline into a line,
 * so a mark at the far edge of a label (a rounded corner read as "ee") became part of the
 * real text on that row, and the line text kept words the confidence filter had dropped.
 */
final class LineSplitter {

    /** A gap wider than this many word heights starts a new line. Word spaces are ~0.3-0.5. */
    static final double GAP_FACTOR = 1.5;

    private LineSplitter() {}

    /**
     * @param lines LINE blocks
     * @param words the WORD blocks that were kept
     * @return one LINE per run of closely spaced kept words; a line with no kept word is dropped
     */
    static List<TextBlock> split(List<TextBlock> lines, List<TextBlock> words) {
        List<TextBlock> result = new ArrayList<>();
        for (TextBlock line : lines) {
            BoundingBox lb = line.getBoundingBox();
            List<TextBlock> inLine = new ArrayList<>();
            if (lb != null) {
                for (TextBlock w : words) {
                    BoundingBox wb = w.getBoundingBox();
                    if (wb != null && lb.contains((int) wb.getCenterX(), (int) wb.getCenterY())) {
                        inLine.add(w);
                    }
                }
            }
            if (inLine.isEmpty()) {
                // Every word on it fell below the confidence threshold
                continue;
            }
            inLine.sort(Comparator.comparingInt(w -> w.getBoundingBox().getX()));
            double maxGap = GAP_FACTOR * medianHeight(inLine);

            List<List<TextBlock>> runs = new ArrayList<>();
            List<TextBlock> run = new ArrayList<>();
            int runMaxX = Integer.MIN_VALUE;
            for (TextBlock w : inLine) {
                BoundingBox wb = w.getBoundingBox();
                if (!run.isEmpty() && wb.getX() - runMaxX > maxGap) {
                    runs.add(run);
                    run = new ArrayList<>();
                }
                run.add(w);
                runMaxX = run.size() == 1 ? wb.getMaxX() : Math.max(runMaxX, wb.getMaxX());
            }
            runs.add(run);

            for (List<TextBlock> r : runs) {
                StringBuilder text = new StringBuilder();
                BoundingBox box = null;
                float confidence = 0;
                for (TextBlock w : r) {
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    text.append(w.getText());
                    box = box == null ? w.getBoundingBox() : box.expandedWith(w.getBoundingBox());
                    confidence += w.getConfidence();
                }
                result.add(TextBlock.line(text.toString(), box, confidence / r.size()));
            }
        }
        return result;
    }

    private static double medianHeight(List<TextBlock> words) {
        int[] heights = words.stream().mapToInt(w -> w.getBoundingBox().getHeight()).sorted().toArray();
        return heights[heights.length / 2];
    }
}
