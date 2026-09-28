package qupath.ext.ocr4labels.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OCRResultTest {

    private static final BoundingBox BOX = new BoundingBox(0, 0, 10, 10);

    @Test
    void wordsAndTheirLineReadOnce() {
        // The engine returns each word AND the line holding them; the field read
        // "610 TOMO 610 TOMO" when both levels were joined.
        OCRResult result = new OCRResult(List.of(
                TextBlock.word("610", BOX, 0.9f),
                TextBlock.word("TOMO", BOX, 0.9f),
                TextBlock.line("610 TOMO", BOX, 0.9f)), 0);

        assertThat(result.getFullText()).isEqualTo("610 TOMO");
        assertThat(result.getReadingBlocks()).extracting(TextBlock::getText).containsExactly("610 TOMO");
    }

    @Test
    void wordsAreReadWhenNoLineWasFound() {
        OCRResult result = new OCRResult(List.of(
                TextBlock.word("610", BOX, 0.9f),
                TextBlock.word("TOMO", BOX, 0.9f)), 0);

        assertThat(result.getFullText()).isEqualTo("610 TOMO");
    }
}
