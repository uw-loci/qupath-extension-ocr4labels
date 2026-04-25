package qupath.ext.ocr4labels.utilities;

import java.util.regex.Pattern;

/**
 * Utility class for filtering OCR text output.
 * Provides predefined filters for common use cases like removing non-alphanumeric characters.
 */
public class TextFilters {

    /**
     * Filter that keeps only letters (a-z, A-Z).
     * Regex: [^a-zA-Z]
     */
    public static final TextFilter LETTERS_ONLY = new TextFilter(
            "Letters Only",
            "abcABC",
            "Keep only letters (a-z, A-Z). Removes numbers, symbols, and whitespace.",
            "[^a-zA-Z]",
            ""
    );

    /**
     * Filter that keeps only numbers (0-9).
     * Regex: [^0-9]
     */
    public static final TextFilter NUMBERS_ONLY = new TextFilter(
            "Numbers Only",
            "123",
            "Keep only numbers (0-9). Removes letters, symbols, and whitespace.",
            "[^0-9]",
            ""
    );

    /**
     * Filter that keeps alphanumeric characters only.
     * Regex: [^a-zA-Z0-9]
     */
    public static final TextFilter ALPHANUMERIC = new TextFilter(
            "Alphanumeric",
            "aA1",
            "Keep letters and numbers only. Removes symbols and whitespace.",
            "[^a-zA-Z0-9]",
            ""
    );

    /**
     * Filter that keeps characters safe for filenames.
     * Allows: letters, numbers, dash, underscore, period
     * Regex: [^a-zA-Z0-9._-]
     */
    public static final TextFilter FILENAME_SAFE = new TextFilter(
            "Filename Safe",
            "-_.",
            "Keep characters safe for filenames: letters, numbers, dash, underscore, period.",
            "[^a-zA-Z0-9._-]",
            ""
    );

    /**
     * Filter that removes special/unusual characters but keeps most readable ones.
     * Removes: control characters, unusual symbols
     * Keeps: letters, numbers, common punctuation, spaces
     */
    public static final TextFilter STANDARD_CHARS = new TextFilter(
            "Standard Chars",
            "&*!",
            "Remove unusual characters. Keep letters, numbers, common punctuation, and spaces.",
            "[^a-zA-Z0-9\\s.,;:!?'\"()-]",
            ""
    );

    /**
     * Filter that replaces whitespace with underscores and collapses multiple underscores.
     * Regex: \\s+ -> _
     */
    public static final TextFilter NO_WHITESPACE = new TextFilter(
            "No Whitespace",
            "A B->A_B",
            "Replace all whitespace (spaces, tabs, newlines) with underscores.",
            "\\s+",
            "_"
    );

    /**
     * All available predefined filters. Returned as an unmodifiable list so
     * callers cannot mutate the shared filter set.
     */
    public static final java.util.List<TextFilter> ALL_FILTERS = java.util.List.of(
            LETTERS_ONLY,
            NUMBERS_ONLY,
            ALPHANUMERIC,
            FILENAME_SAFE,
            STANDARD_CHARS,
            NO_WHITESPACE
    );

    /**
     * Applies a filter to the given text.
     *
     * @param text The text to filter
     * @param filter The filter to apply
     * @return The filtered text
     */
    public static String apply(String text, TextFilter filter) {
        if (text == null || text.isEmpty() || filter == null) {
            return text;
        }
        return text.replaceAll(filter.getRegex(), filter.getReplacement());
    }

    /**
     * Applies multiple filters in sequence.
     *
     * @param text The text to filter
     * @param filters The filters to apply in order
     * @return The filtered text
     */
    public static String applyAll(String text, TextFilter... filters) {
        if (text == null || text.isEmpty() || filters == null) {
            return text;
        }
        String result = text;
        for (TextFilter filter : filters) {
            result = apply(result, filter);
        }
        return result;
    }

    /**
     * Represents a text filter with its display name, button label, tooltip, and regex.
     */
    public static class TextFilter {
        private final String name;
        private final String buttonLabel;
        private final String tooltip;
        private final String regex;
        private final String replacement;
        private final Pattern pattern;

        public TextFilter(String name, String buttonLabel, String tooltip, String regex, String replacement) {
            this.name = name;
            this.buttonLabel = buttonLabel;
            this.tooltip = tooltip;
            this.regex = regex;
            this.replacement = replacement;
            this.pattern = Pattern.compile(regex);
        }

        public String getName() {
            return name;
        }

        public String getButtonLabel() {
            return buttonLabel;
        }

        public String getTooltip() {
            return tooltip + "\n\nRegex: " + regex + (replacement.isEmpty() ? " (remove)" : " -> " + replacement);
        }

        public String getRegex() {
            return regex;
        }

        public String getReplacement() {
            return replacement;
        }

        public Pattern getPattern() {
            return pattern;
        }

        /**
         * Applies this filter to the given text.
         */
        public String apply(String text) {
            if (text == null || text.isEmpty()) {
                return text;
            }
            return pattern.matcher(text).replaceAll(replacement);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private TextFilters() {
        // Utility class - prevent instantiation
    }
}
