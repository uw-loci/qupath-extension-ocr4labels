package qupath.ext.ocr4labels.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Result of barcode decoding from an image region.
 * <p>
 * Can contain zero, one, or multiple decoded barcodes from a single region.
 * When multiple barcodes are found, they are joined with semicolons for
 * the combined text representation.
 */
public class BarcodeResult {

    private final List<DecodedBarcode> barcodes;
    private final boolean success;
    private final String errorMessage;
    private final long processingTimeMs;

    /**
     * Represents a single decoded barcode.
     */
    public static class DecodedBarcode {
        private final String text;
        private final String format;
        private final BoundingBox boundingBox;

        public DecodedBarcode(String text, String format, BoundingBox boundingBox) {
            this.text = text;
            this.format = format;
            this.boundingBox = boundingBox;
        }

        /**
         * Gets the decoded text content of the barcode.
         *
         * @return The decoded text
         */
        public String getText() {
            return text;
        }

        /**
         * Gets the barcode format (e.g., "QR_CODE", "CODE_128", "DATA_MATRIX").
         *
         * @return The format name
         */
        public String getFormat() {
            return format;
        }

        /**
         * Gets the bounding box of the detected barcode within the image.
         * May be null if position information is not available.
         *
         * @return The bounding box, or null
         */
        public BoundingBox getBoundingBox() {
            return boundingBox;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s", format, text);
        }
    }

    /**
     * Creates a successful result with the given barcodes.
     *
     * @param barcodes The decoded barcodes
     * @param processingTimeMs The processing time in milliseconds
     */
    public BarcodeResult(List<DecodedBarcode> barcodes, long processingTimeMs) {
        this.barcodes = barcodes != null ? new ArrayList<>(barcodes) : new ArrayList<>();
        this.success = !this.barcodes.isEmpty();
        this.errorMessage = null;
        this.processingTimeMs = processingTimeMs;
    }

    /**
     * Creates a failed result with an error message.
     *
     * @param errorMessage The error message
     * @param processingTimeMs The processing time in milliseconds
     */
    public BarcodeResult(String errorMessage, long processingTimeMs) {
        this.barcodes = new ArrayList<>();
        this.success = false;
        this.errorMessage = errorMessage;
        this.processingTimeMs = processingTimeMs;
    }

    /**
     * Creates an empty result (no barcode found, but no error).
     *
     * @param processingTimeMs The processing time in milliseconds
     * @return An empty successful result
     */
    public static BarcodeResult empty(long processingTimeMs) {
        return new BarcodeResult(Collections.emptyList(), processingTimeMs);
    }

    /**
     * Creates a result with a single barcode.
     *
     * @param text The decoded text
     * @param format The barcode format
     * @param boundingBox The bounding box (may be null)
     * @param processingTimeMs The processing time in milliseconds
     * @return A result containing one barcode
     */
    public static BarcodeResult single(String text, String format, BoundingBox boundingBox,
                                        long processingTimeMs) {
        List<DecodedBarcode> list = new ArrayList<>();
        list.add(new DecodedBarcode(text, format, boundingBox));
        return new BarcodeResult(list, processingTimeMs);
    }

    /**
     * Creates a failed result.
     *
     * @param errorMessage The error message
     * @param processingTimeMs The processing time in milliseconds
     * @return A failed result
     */
    public static BarcodeResult error(String errorMessage, long processingTimeMs) {
        return new BarcodeResult(errorMessage, processingTimeMs);
    }

    /**
     * Checks if decoding was successful (at least one barcode found).
     *
     * @return true if successful
     */
    public boolean isSuccess() {
        return success;
    }

    /**
     * Checks if any barcodes were found.
     *
     * @return true if at least one barcode was decoded
     */
    public boolean hasBarcode() {
        return !barcodes.isEmpty();
    }

    /**
     * Gets the number of barcodes found.
     *
     * @return The barcode count
     */
    public int getBarcodeCount() {
        return barcodes.size();
    }

    /**
     * Gets the list of decoded barcodes.
     *
     * @return Unmodifiable list of barcodes
     */
    public List<DecodedBarcode> getBarcodes() {
        return Collections.unmodifiableList(barcodes);
    }

    /**
     * Gets the first decoded barcode, if any.
     *
     * @return The first barcode, or null if none found
     */
    public DecodedBarcode getFirstBarcode() {
        return barcodes.isEmpty() ? null : barcodes.get(0);
    }

    /**
     * Gets the combined text of all decoded barcodes, separated by semicolons.
     * This is the primary value used for metadata storage.
     *
     * @return Combined text, or empty string if no barcodes
     */
    public String getCombinedText() {
        return barcodes.stream()
                .map(DecodedBarcode::getText)
                .collect(Collectors.joining(";"));
    }

    /**
     * Gets the text of the first barcode, or empty string if none.
     *
     * @return First barcode text, or empty string
     */
    public String getText() {
        return barcodes.isEmpty() ? "" : barcodes.get(0).getText();
    }

    /**
     * Gets the format of the first barcode, or null if none.
     *
     * @return First barcode format, or null
     */
    public String getFormat() {
        return barcodes.isEmpty() ? null : barcodes.get(0).getFormat();
    }

    /**
     * Gets the error message if decoding failed.
     *
     * @return The error message, or null if successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Gets the processing time in milliseconds.
     *
     * @return Processing time
     */
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    @Override
    public String toString() {
        if (!success && errorMessage != null) {
            return String.format("BarcodeResult[error=%s, time=%dms]", errorMessage, processingTimeMs);
        }
        if (barcodes.isEmpty()) {
            return String.format("BarcodeResult[no barcode found, time=%dms]", processingTimeMs);
        }
        return String.format("BarcodeResult[%d barcode(s): %s, time=%dms]",
                barcodes.size(), getCombinedText(), processingTimeMs);
    }
}
