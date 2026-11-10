package qupath.ext.ocr4labels.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Numbers in the generated workflow script are Groovy literals in every default locale. */
class ScriptNumberTest {

    private static String in(Locale locale, double value) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            return OCRDialog.scriptNumber(value);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void minConfidenceKeepsADecimalPointInGerman() {
        assertEquals("0.50", in(Locale.GERMANY, 0.5));
    }

    @Test
    void minConfidenceKeepsADecimalPointInTurkish() {
        assertEquals("0.75", in(Locale.forLanguageTag("tr-TR"), 0.75));
    }
}
