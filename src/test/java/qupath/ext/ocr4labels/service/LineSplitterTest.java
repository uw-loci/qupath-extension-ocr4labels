package qupath.ext.ocr4labels.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.ocr4labels.model.BoundingBox;
import qupath.ext.ocr4labels.model.TextBlock;

class LineSplitterTest {

    // Boxes as Tesseract returned them for the 02_IF label: the rounded corner read as
    // "ee" sat on the date's baseline, 250px to its left.
    private static final TextBlock EE = TextBlock.word("ee", new BoundingBox(5, 506, 146, 80), 0.67f);
    private static final TextBlock D1 = TextBlock.word("11/1", new BoundingBox(402, 498, 100, 44), 0.96f);
    private static final TextBlock D2 = TextBlock.word("4/202)", new BoundingBox(515, 495, 163, 91), 0.93f);
    private static final TextBlock LINE =
            TextBlock.line("ee a. : 11/1 4/202)", new BoundingBox(5, 495, 673, 91), 0.54f);

    @Test
    void wordsAcrossAColumnGapBecomeSeparateLines() {
        List<TextBlock> lines = LineSplitter.split(List.of(LINE), List.of(EE, D1, D2));
        assertThat(lines).extracting(TextBlock::getText).containsExactly("ee", "11/1 4/202)");
    }

    @Test
    void lineTextDropsWordsTheConfidenceFilterRemoved() {
        // "a." and ":" were below the threshold, so they are not in the word list
        List<TextBlock> lines = LineSplitter.split(List.of(LINE), List.of(D1, D2));
        assertThat(lines).extracting(TextBlock::getText).containsExactly("11/1 4/202)");
    }

    @Test
    void ordinaryWordSpacingKeepsOneLine() {
        TextBlock a = TextBlock.word("610", new BoundingBox(10, 10, 40, 20), 0.9f);
        TextBlock b = TextBlock.word("TOMO", new BoundingBox(58, 10, 60, 20), 0.9f);
        TextBlock line = TextBlock.line("610 TOMO", new BoundingBox(10, 10, 108, 20), 0.9f);
        assertThat(LineSplitter.split(List.of(line), List.of(a, b)))
                .extracting(TextBlock::getText).containsExactly("610 TOMO");
    }

    @Test
    void lineWithNoKeptWordIsDropped() {
        assertThat(LineSplitter.split(List.of(LINE), List.of())).isEmpty();
    }
}
