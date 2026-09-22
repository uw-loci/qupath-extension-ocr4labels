package qupath.ext.ocr4labels.ui;

import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.preferences.OCRPreferences;

/**
 * The page segmentation modes offered in the OCR dialogs, with their display names.
 * Both the main OCR dialog and the Settings dialog populate their Mode dropdown
 * from this list so the two never disagree about names, order, or the default.
 */
public enum PSMOption {
    AUTO("Auto (default)", OCRConfiguration.PageSegMode.AUTO),
    AUTO_OSD("Auto + Orientation", OCRConfiguration.PageSegMode.AUTO_OSD),
    SINGLE_BLOCK("Single Block", OCRConfiguration.PageSegMode.SINGLE_BLOCK),
    SINGLE_LINE("Single Line", OCRConfiguration.PageSegMode.SINGLE_LINE),
    SINGLE_WORD("Single Word", OCRConfiguration.PageSegMode.SINGLE_WORD),
    SPARSE_TEXT("Sparse Text", OCRConfiguration.PageSegMode.SPARSE_TEXT),
    SPARSE_TEXT_OSD("Sparse + Orientation", OCRConfiguration.PageSegMode.SPARSE_TEXT_OSD);

    /** Tooltip shared by every Mode dropdown. */
    public static final String TOOLTIP =
            "OCR text detection mode:\n\n" +
            "Auto: Let Tesseract work out the layout (default)\n" +
            "Auto + Orientation: Auto with rotation detection\n" +
            "Single Block: For labels with one paragraph of text\n" +
            "Single Line/Word: For very simple labels\n" +
            "Sparse Text: Finds text scattered across the image\n" +
            "Sparse + Orientation: Sparse Text with rotation detection\n\n" +
            "Only affects text decoding, not barcode scanning.";

    private final String displayName;
    private final OCRConfiguration.PageSegMode mode;

    PSMOption(String displayName, OCRConfiguration.PageSegMode mode) {
        this.displayName = displayName;
        this.mode = mode;
    }

    public OCRConfiguration.PageSegMode getMode() {
        return mode;
    }

    /**
     * @param mode a page segmentation mode
     * @return the dropdown option for {@code mode}, or {@link #AUTO} if the mode is not offered
     */
    public static PSMOption fromMode(OCRConfiguration.PageSegMode mode) {
        for (PSMOption option : values()) {
            if (option.mode == mode) {
                return option;
            }
        }
        return AUTO;
    }

    /** @return the option matching the persisted page segmentation mode preference */
    public static PSMOption fromPreference() {
        return fromMode(OCRPreferences.getPageSegMode());
    }

    @Override
    public String toString() {
        return displayName;
    }
}
