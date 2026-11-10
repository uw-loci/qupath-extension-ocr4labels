package qupath.ext.ocr4labels.utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility class for matching OCR-detected text against a known vocabulary.
 * Uses fuzzy matching (Levenshtein distance) to correct OCR errors by finding
 * the closest match in a user-provided list of valid values.
 *
 * <p>This is useful when the expected values are known ahead of time (e.g., a list
 * of sample IDs, patient codes, or specimen names). OCR mistakes like "0" vs "O",
 * "1" vs "l", or "rn" vs "m" can be automatically corrected.
 *
 * <p>Two matching modes are available:
 * <ul>
 *   <li><b>Standard mode:</b> All character substitutions have equal cost (1.0).
 *       Best for scientific sample names with intentional letter/number mixtures.</li>
 *   <li><b>OCR-weighted mode:</b> Common OCR confusions have reduced cost (0.3-0.5).
 *       Best for natural text where 0/O and 1/l/I confusions are likely errors.</li>
 * </ul>
 *
 * @see <a href="https://github.com/wolfgarbe/SymSpell">SymSpell algorithm</a>
 * @see <a href="https://commons.apache.org/proper/commons-text/apidocs/org/apache/commons/text/similarity/LevenshteinDistance.html">Apache Commons Text LevenshteinDistance</a>
 */
public class TextMatcher {

    private static final Logger logger = LoggerFactory.getLogger(TextMatcher.class);

    /**
     * The list of valid/known values to match against.
     */
    private final List<String> vocabulary;

    /**
     * Maximum edit distance to consider a match valid.
     * Default is 2 (allows up to 2 character changes).
     */
    private int maxEditDistance = 2;

    /**
     * Minimum similarity ratio (0.0 to 1.0) required for a match.
     * Calculated as: 1 - (editDistance / maxLength).
     * Default is 0.6 (60% similar).
     */
    private double minSimilarity = 0.6;

    /**
     * Whether to use case-insensitive matching.
     */
    private boolean caseInsensitive = true;

    /**
     * Whether to apply OCR-specific weighted edit distance.
     * When enabled, common OCR confusions (0/O, 1/l/I, rn/m) have lower cost.
     * When disabled, all substitutions have equal cost - better for scientific
     * sample names where letter/number mixtures are intentional.
     */
    private boolean useOCRWeights = false;

    /**
     * OCR confusion weight matrix.
     * Maps character pairs to their substitution cost (0.0 to 1.0).
     * Lower values mean the characters are commonly confused by OCR.
     */
    private static final Map<CharPair, Double> OCR_CONFUSION_WEIGHTS = new HashMap<>();

    static {
        // Initialize OCR confusion weights
        // Cost of 0.3 = very commonly confused (treated as almost the same)
        // Cost of 0.5 = commonly confused
        // Cost of 0.7 = occasionally confused
        // Cost of 1.0 = normal substitution (default)

        // Zero and letter O - extremely common OCR confusion
        addConfusion('0', 'O', 0.3);
        addConfusion('0', 'o', 0.3);

        // One, lowercase L, uppercase I - very common
        addConfusion('1', 'l', 0.3);
        addConfusion('1', 'I', 0.3);
        addConfusion('l', 'I', 0.3);
        addConfusion('1', '|', 0.3);
        addConfusion('l', '|', 0.3);
        addConfusion('I', '|', 0.3);

        // Five and S
        addConfusion('5', 'S', 0.5);
        addConfusion('5', 's', 0.5);

        // Eight and B
        addConfusion('8', 'B', 0.5);

        // Six and G (in some fonts)
        addConfusion('6', 'G', 0.7);

        // Two and Z
        addConfusion('2', 'Z', 0.5);
        addConfusion('2', 'z', 0.5);

        // Nine and g or q
        addConfusion('9', 'g', 0.7);
        addConfusion('9', 'q', 0.7);

        // C and G (curved letters)
        addConfusion('C', 'G', 0.7);
        addConfusion('c', 'G', 0.7);
        addConfusion('C', 'c', 0.5);

        // E and F (missing horizontal bar)
        addConfusion('E', 'F', 0.7);
        addConfusion('e', 'c', 0.7);

        // H and N (similar structure)
        addConfusion('H', 'N', 0.7);

        // M and N
        addConfusion('M', 'N', 0.7);

        // U and V
        addConfusion('U', 'V', 0.7);
        addConfusion('u', 'v', 0.7);

        // W and VV (double V)
        addConfusion('W', 'w', 0.5);

        // D and O (rounded)
        addConfusion('D', 'O', 0.7);
        addConfusion('D', '0', 0.7);

        // Period and comma
        addConfusion('.', ',', 0.5);

        // Hyphen, underscore, and dash variants
        addConfusion('-', '_', 0.5);
        addConfusion('-', '\u2013', 0.3); // en-dash
        addConfusion('-', '\u2014', 0.3); // em-dash

        // Space handling - sometimes spaces are missed or added
        addConfusion(' ', '_', 0.7);

        // Common lowercase/uppercase confusions beyond case
        addConfusion('c', 'C', 0.5);
        addConfusion('k', 'K', 0.5);
        addConfusion('o', 'O', 0.5);
        addConfusion('p', 'P', 0.5);
        addConfusion('s', 'S', 0.5);
        addConfusion('u', 'U', 0.5);
        addConfusion('v', 'V', 0.5);
        addConfusion('w', 'W', 0.5);
        addConfusion('x', 'X', 0.5);
        addConfusion('z', 'Z', 0.5);
    }

    /**
     * Helper to add bidirectional confusion weights.
     */
    private static void addConfusion(char c1, char c2, double weight) {
        OCR_CONFUSION_WEIGHTS.put(new CharPair(c1, c2), weight);
        OCR_CONFUSION_WEIGHTS.put(new CharPair(c2, c1), weight);
    }

    /**
     * Creates an empty TextMatcher. Load vocabulary before use.
     */
    public TextMatcher() {
        this.vocabulary = new ArrayList<>();
    }

    /**
     * Creates a TextMatcher with the given vocabulary.
     *
     * @param vocabulary List of valid values to match against
     */
    public TextMatcher(List<String> vocabulary) {
        this.vocabulary = new ArrayList<>(vocabulary);
    }

    /**
     * Loads vocabulary from a text file.
     * Supports CSV (uses first column), TSV, or plain text (one value per line).
     *
     * @param file The file to load
     * @throws IOException if the file cannot be read
     */
    public void loadVocabularyFromFile(File file) throws IOException {
        vocabulary.clear();

        String filename = file.getName().toLowerCase(Locale.ROOT);
        boolean isCSV = filename.endsWith(".csv");
        boolean isTSV = filename.endsWith(".tsv") || filename.endsWith(".txt");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // Skip header row if it looks like a header
                if (firstLine && looksLikeHeader(line)) {
                    firstLine = false;
                    logger.debug("Skipping header row: {}", line);
                    continue;
                }
                firstLine = false;

                String value;
                if (isCSV) {
                    // Parse CSV - use first column
                    value = parseCSVFirstColumn(line);
                } else if (isTSV) {
                    // Parse TSV - use first column
                    int tabIndex = line.indexOf('\t');
                    value = tabIndex > 0 ? line.substring(0, tabIndex).trim() : line;
                } else {
                    // Plain text - whole line is the value
                    value = line;
                }

                if (!value.isEmpty()) {
                    vocabulary.add(value);
                }
            }
        }

        logger.info("Loaded {} vocabulary entries from: {}", vocabulary.size(), file.getName());
    }

    /**
     * Checks if a line looks like a CSV/TSV header.
     */
    private boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("sample") || lower.contains("name") ||
               lower.contains("id") || lower.contains("code") ||
               lower.contains("label") || lower.contains("value") ||
               lower.contains("specimen") || lower.contains("patient") ||
               lower.contains("slide") || lower.contains("case") ||
               lower.contains("date");
    }

    /**
     * Parses the first column from a CSV line, handling quoted values.
     */
    private String parseCSVFirstColumn(String line) {
        if (line.startsWith("\"")) {
            // Quoted value - find closing quote
            int endQuote = line.indexOf("\"", 1);
            if (endQuote > 1) {
                return line.substring(1, endQuote);
            }
        }
        // Unquoted - find comma
        int comma = line.indexOf(',');
        return comma > 0 ? line.substring(0, comma).trim() : line;
    }

    /**
     * Finds the best matching value from the vocabulary for the given OCR text.
     *
     * @param ocrText The OCR-detected text to match
     * @return MatchResult containing the best match and confidence, or null if no match found
     */
    public MatchResult findBestMatch(String ocrText) {
        if (ocrText == null || ocrText.isEmpty() || vocabulary.isEmpty()) {
            return null;
        }

        String normalizedInput = caseInsensitive ? ocrText.toLowerCase(Locale.ROOT) : ocrText;

        MatchResult bestMatch = null;
        double bestScore = Double.MAX_VALUE;

        for (String candidate : vocabulary) {
            String normalizedCandidate = caseInsensitive ? candidate.toLowerCase(Locale.ROOT) : candidate;

            // Exact match - return immediately with perfect score
            if (normalizedInput.equals(normalizedCandidate)) {
                return new MatchResult(candidate, ocrText, 0.0, 1.0);
            }

            // Quick length check - if lengths differ too much, skip
            int lenDiff = Math.abs(normalizedInput.length() - normalizedCandidate.length());
            if (lenDiff > maxEditDistance) {
                continue;
            }

            // Calculate edit distance (weighted or standard)
            double distance = calculateEditDistance(normalizedInput, normalizedCandidate);

            // Check if this is a valid match within threshold
            if (distance <= maxEditDistance && distance < bestScore) {
                double similarity = calculateSimilarity(normalizedInput, normalizedCandidate, distance);
                if (similarity >= minSimilarity) {
                    bestScore = distance;
                    bestMatch = new MatchResult(candidate, ocrText, distance, similarity);
                }
            }
        }

        return bestMatch;
    }

    /**
     * Finds all matches within the threshold, sorted by score.
     *
     * @param ocrText The OCR-detected text to match
     * @param maxResults Maximum number of results to return
     * @return List of matches sorted by distance (best first)
     */
    public List<MatchResult> findAllMatches(String ocrText, int maxResults) {
        if (ocrText == null || ocrText.isEmpty() || vocabulary.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedInput = caseInsensitive ? ocrText.toLowerCase(Locale.ROOT) : ocrText;
        List<MatchResult> matches = new ArrayList<>();

        for (String candidate : vocabulary) {
            String normalizedCandidate = caseInsensitive ? candidate.toLowerCase(Locale.ROOT) : candidate;

            // Quick length check
            int lenDiff = Math.abs(normalizedInput.length() - normalizedCandidate.length());
            if (lenDiff > maxEditDistance) {
                continue;
            }

            double distance = calculateEditDistance(normalizedInput, normalizedCandidate);

            if (distance <= maxEditDistance) {
                double similarity = calculateSimilarity(normalizedInput, normalizedCandidate, distance);
                if (similarity >= minSimilarity) {
                    matches.add(new MatchResult(candidate, ocrText, distance, similarity));
                }
            }
        }

        // Sort by distance (best matches first)
        matches.sort(Comparator.comparingDouble(MatchResult::getEditDistance));

        // Limit results
        if (matches.size() > maxResults) {
            return matches.subList(0, maxResults);
        }

        return matches;
    }

    /**
     * Calculates the edit distance between two strings.
     * Uses weighted Levenshtein if OCR weights are enabled, standard otherwise.
     *
     * @param s1 First string (should be pre-normalized if case-insensitive)
     * @param s2 Second string (should be pre-normalized if case-insensitive)
     * @return The edit distance (can be fractional if using OCR weights)
     */
    private double calculateEditDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();

        // Use double for weighted distances
        double[][] dp = new double[len1 + 1][len2 + 1];

        // Initialize first row and column
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }

        // Fill in the rest of the matrix
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                char c1 = s1.charAt(i - 1);
                char c2 = s2.charAt(j - 1);

                if (c1 == c2) {
                    // Characters match - no cost
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    // Get substitution cost (weighted or standard)
                    double subCost = getSubstitutionCost(c1, c2);

                    // Standard edit operations
                    double delete = dp[i - 1][j] + 1.0;      // Delete from s1
                    double insert = dp[i][j - 1] + 1.0;      // Insert into s1
                    double substitute = dp[i - 1][j - 1] + subCost;

                    dp[i][j] = Math.min(Math.min(delete, insert), substitute);
                }
            }
        }

        return dp[len1][len2];
    }

    /**
     * Gets the substitution cost for two characters.
     * When OCR weights are enabled, common OCR confusions have lower cost.
     *
     * @param c1 First character
     * @param c2 Second character
     * @return The substitution cost (1.0 for standard, lower for OCR confusions)
     */
    private double getSubstitutionCost(char c1, char c2) {
        if (c1 == c2) return 0.0;

        if (!useOCRWeights) {
            // Standard mode - all substitutions cost 1.0
            return 1.0;
        }

        // OCR-weighted mode - check confusion matrix
        CharPair pair = new CharPair(c1, c2);
        Double weight = OCR_CONFUSION_WEIGHTS.get(pair);

        if (weight != null) {
            return weight;
        }

        // Check case-insensitive version if not found
        if (Character.isLetter(c1) && Character.isLetter(c2)) {
            char lc1 = Character.toLowerCase(c1);
            char lc2 = Character.toLowerCase(c2);
            if (lc1 == lc2) {
                // Same letter, different case - low cost
                return 0.3;
            }
            // Check lowercase version in matrix
            pair = new CharPair(lc1, lc2);
            weight = OCR_CONFUSION_WEIGHTS.get(pair);
            if (weight != null) {
                return weight;
            }
        }

        // Default substitution cost
        return 1.0;
    }

    /**
     * Calculates a similarity score between 0.0 and 1.0.
     *
     * @param s1 First string
     * @param s2 Second string
     * @param editDistance Pre-calculated edit distance
     * @return Similarity ratio
     */
    private double calculateSimilarity(String s1, String s2, double editDistance) {
        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) return 1.0;
        return 1.0 - (editDistance / maxLength);
    }

    /**
     * Applies vocabulary matching to correct all values in the given map.
     *
     * @param fieldValues Map of field names to OCR-detected values
     * @return Map of field names to corrected values (unchanged if no match found)
     */
    public Map<String, String> correctAll(Map<String, String> fieldValues) {
        Map<String, String> corrected = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            MatchResult match = findBestMatch(value);
            if (match != null && match.getEditDistance() > 0) {
                corrected.put(key, match.getMatchedValue());
                logger.debug("Corrected '{}' -> '{}' (distance={}, similarity={:.2f})",
                        value, match.getMatchedValue(),
                        match.getEditDistance(), match.getSimilarity());
            } else {
                corrected.put(key, value);
            }
        }

        return corrected;
    }

    // Getters and setters

    public List<String> getVocabulary() {
        return Collections.unmodifiableList(vocabulary);
    }

    public int getVocabularySize() {
        return vocabulary.size();
    }

    public boolean hasVocabulary() {
        return !vocabulary.isEmpty();
    }

    public int getMaxEditDistance() {
        return maxEditDistance;
    }

    public void setMaxEditDistance(int maxEditDistance) {
        this.maxEditDistance = maxEditDistance;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    public void setMinSimilarity(double minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }

    public void setCaseInsensitive(boolean caseInsensitive) {
        this.caseInsensitive = caseInsensitive;
    }

    public boolean isUseOCRWeights() {
        return useOCRWeights;
    }

    public void setUseOCRWeights(boolean useOCRWeights) {
        this.useOCRWeights = useOCRWeights;
    }

    public void clearVocabulary() {
        vocabulary.clear();
    }

    /**
     * Adds a custom confusion weight for two characters.
     * This allows domain-specific confusion patterns to be added.
     *
     * @param c1 First character
     * @param c2 Second character
     * @param weight Cost between 0.0 (same) and 1.0 (unrelated)
     */
    public static void addCustomConfusion(char c1, char c2, double weight) {
        addConfusion(c1, c2, weight);
    }

    /**
     * Result of a vocabulary match operation.
     */
    public static class MatchResult {
        private final String matchedValue;
        private final String originalValue;
        private final double editDistance;
        private final double similarity;

        public MatchResult(String matchedValue, String originalValue,
                          double editDistance, double similarity) {
            this.matchedValue = matchedValue;
            this.originalValue = originalValue;
            this.editDistance = editDistance;
            this.similarity = similarity;
        }

        public String getMatchedValue() {
            return matchedValue;
        }

        public String getOriginalValue() {
            return originalValue;
        }

        public double getEditDistance() {
            return editDistance;
        }

        public double getSimilarity() {
            return similarity;
        }

        public boolean isExactMatch() {
            return editDistance == 0;
        }

        /**
         * Returns true if this was a correction (not exact match).
         */
        public boolean wasCorrected() {
            return editDistance > 0;
        }

        @Override
        public String toString() {
            if (isExactMatch()) {
                return String.format("'%s' (exact match)", matchedValue);
            }
            return String.format("'%s' -> '%s' (distance=%.2f, %.0f%% similar)",
                    originalValue, matchedValue, editDistance, similarity * 100);
        }
    }

    /**
     * Simple pair of characters for use as map key.
     */
    private static class CharPair {
        private final char c1;
        private final char c2;

        CharPair(char c1, char c2) {
            this.c1 = c1;
            this.c2 = c2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CharPair charPair = (CharPair) o;
            return c1 == charPair.c1 && c2 == charPair.c2;
        }

        @Override
        public int hashCode() {
            return 31 * c1 + c2;
        }
    }
}
