package qupath.ext.ocr4labels.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Case-insensitive matching treats an upper-case I the same in every default locale. */
class TextMatcherLocaleTest {

    private static <T> T inTurkish(Supplier<T> body) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            return body.get();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void upperCaseOcrTextIsAnExactMatchInTurkish() {
        TextMatcher matcher = new TextMatcher(List.of("biopsy", "resection"));
        TextMatcher.MatchResult result = inTurkish(() -> matcher.findBestMatch("BIOPSY"));
        assertNotNull(result);
        assertEquals("biopsy", result.getMatchedValue());
        assertTrue(result.isExactMatch());
    }

    @Test
    void upperCaseVocabularyIsAnExactMatchInTurkish() {
        TextMatcher matcher = new TextMatcher(List.of("PATIENT ID"));
        TextMatcher.MatchResult result = inTurkish(() -> matcher.findBestMatch("patient id"));
        assertNotNull(result);
        assertEquals(0.0, result.getEditDistance());
    }
}
