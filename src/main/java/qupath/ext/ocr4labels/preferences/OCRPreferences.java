package qupath.ext.ocr4labels.preferences;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Manages persistent preferences for the OCR for Labels extension.
 * Uses QuPath's PathPrefs system for cross-session persistence.
 */
public class OCRPreferences {

    private static final Logger logger = LoggerFactory.getLogger(OCRPreferences.class);

    // Preference key prefix
    private static final String PREFIX = "ocr4labels.";

    // Default values
    private static final String DEFAULT_LANGUAGE = "eng";
    private static final String DEFAULT_TESSDATA_PATH = "";
    private static final double DEFAULT_MIN_CONFIDENCE = 0.5;
    private static final boolean DEFAULT_AUTO_ROTATE = true;
    private static final boolean DEFAULT_ENHANCE_CONTRAST = true;
    private static final boolean DEFAULT_DETECT_ORIENTATION = true;
    private static final int DEFAULT_PAGE_SEG_MODE = 11; // PSM_SPARSE_TEXT (best for labels)
    private static final String DEFAULT_METADATA_PREFIX = "OCR_";
    private static final String DEFAULT_LABEL_IMAGE_KEYWORDS = "label,barcode";
    private static final boolean DEFAULT_AUTO_RUN_ON_ENTRY_SWITCH = true;

    // Properties
    private static StringProperty languageProperty;
    private static StringProperty tessdataPathProperty;
    private static DoubleProperty minConfidenceProperty;
    private static BooleanProperty autoRotateProperty;
    private static BooleanProperty enhanceContrastProperty;
    private static BooleanProperty detectOrientationProperty;
    private static IntegerProperty pageSegModeProperty;
    private static StringProperty metadataPrefixProperty;
    private static StringProperty labelImageKeywordsProperty;
    private static BooleanProperty autoRunOnEntrySwitchProperty;

    // Window size properties for remembering dialog sizes
    private static DoubleProperty dialogWidthProperty;
    private static DoubleProperty dialogHeightProperty;

    private OCRPreferences() {
        // Utility class - prevent instantiation
    }

    /**
     * Installs preferences and creates the persistent properties.
     * Should be called once during extension initialization.
     */
    public static void installPreferences() {
        logger.info("Installing OCR for Labels preferences");

        // OCR settings
        languageProperty = PathPrefs.createPersistentPreference(
                PREFIX + "language", DEFAULT_LANGUAGE);

        tessdataPathProperty = PathPrefs.createPersistentPreference(
                PREFIX + "tessdataPath", DEFAULT_TESSDATA_PATH);

        minConfidenceProperty = PathPrefs.createPersistentPreference(
                PREFIX + "minConfidence", DEFAULT_MIN_CONFIDENCE);

        autoRotateProperty = PathPrefs.createPersistentPreference(
                PREFIX + "autoRotate", DEFAULT_AUTO_ROTATE);

        enhanceContrastProperty = PathPrefs.createPersistentPreference(
                PREFIX + "enhanceContrast", DEFAULT_ENHANCE_CONTRAST);

        detectOrientationProperty = PathPrefs.createPersistentPreference(
                PREFIX + "detectOrientation", DEFAULT_DETECT_ORIENTATION);

        pageSegModeProperty = PathPrefs.createPersistentPreference(
                PREFIX + "pageSegMode", DEFAULT_PAGE_SEG_MODE);

        metadataPrefixProperty = PathPrefs.createPersistentPreference(
                PREFIX + "metadataPrefix", DEFAULT_METADATA_PREFIX);

        labelImageKeywordsProperty = PathPrefs.createPersistentPreference(
                PREFIX + "labelImageKeywords", DEFAULT_LABEL_IMAGE_KEYWORDS);

        autoRunOnEntrySwitchProperty = PathPrefs.createPersistentPreference(
                PREFIX + "autoRunOnEntrySwitch", DEFAULT_AUTO_RUN_ON_ENTRY_SWITCH);

        // Dialog size properties
        dialogWidthProperty = PathPrefs.createPersistentPreference(
                PREFIX + "dialogWidth", 1000.0);

        dialogHeightProperty = PathPrefs.createPersistentPreference(
                PREFIX + "dialogHeight", 700.0);

        logger.info("OCR for Labels preferences installed");
    }

    // === Property accessors ===

    public static StringProperty languageProperty() {
        return languageProperty;
    }

    public static StringProperty tessdataPathProperty() {
        return tessdataPathProperty;
    }

    public static DoubleProperty minConfidenceProperty() {
        return minConfidenceProperty;
    }

    public static BooleanProperty autoRotateProperty() {
        return autoRotateProperty;
    }

    public static BooleanProperty enhanceContrastProperty() {
        return enhanceContrastProperty;
    }

    public static BooleanProperty detectOrientationProperty() {
        return detectOrientationProperty;
    }

    public static IntegerProperty pageSegModeProperty() {
        return pageSegModeProperty;
    }

    public static StringProperty metadataPrefixProperty() {
        return metadataPrefixProperty;
    }

    public static DoubleProperty dialogWidthProperty() {
        return dialogWidthProperty;
    }

    public static DoubleProperty dialogHeightProperty() {
        return dialogHeightProperty;
    }

    // === Convenience value getters ===

    public static String getLanguage() {
        return languageProperty != null ? languageProperty.get() : DEFAULT_LANGUAGE;
    }

    public static void setLanguage(String language) {
        if (languageProperty != null) {
            languageProperty.set(language);
        }
    }

    public static String getTessdataPath() {
        return tessdataPathProperty != null ? tessdataPathProperty.get() : DEFAULT_TESSDATA_PATH;
    }

    public static void setTessdataPath(String path) {
        if (tessdataPathProperty != null) {
            tessdataPathProperty.set(path);
        }
    }

    public static double getMinConfidence() {
        return minConfidenceProperty != null ? minConfidenceProperty.get() : DEFAULT_MIN_CONFIDENCE;
    }

    public static void setMinConfidence(double confidence) {
        if (minConfidenceProperty != null) {
            minConfidenceProperty.set(confidence);
        }
    }

    public static boolean isAutoRotate() {
        return autoRotateProperty != null ? autoRotateProperty.get() : DEFAULT_AUTO_ROTATE;
    }

    public static void setAutoRotate(boolean autoRotate) {
        if (autoRotateProperty != null) {
            autoRotateProperty.set(autoRotate);
        }
    }

    public static boolean isEnhanceContrast() {
        return enhanceContrastProperty != null ? enhanceContrastProperty.get() : DEFAULT_ENHANCE_CONTRAST;
    }

    public static void setEnhanceContrast(boolean enhance) {
        if (enhanceContrastProperty != null) {
            enhanceContrastProperty.set(enhance);
        }
    }

    public static boolean isDetectOrientation() {
        return detectOrientationProperty != null ? detectOrientationProperty.get() : DEFAULT_DETECT_ORIENTATION;
    }

    public static void setDetectOrientation(boolean detect) {
        if (detectOrientationProperty != null) {
            detectOrientationProperty.set(detect);
        }
    }

    public static int getPageSegMode() {
        return pageSegModeProperty != null ? pageSegModeProperty.get() : DEFAULT_PAGE_SEG_MODE;
    }

    public static void setPageSegMode(int mode) {
        if (pageSegModeProperty != null) {
            pageSegModeProperty.set(mode);
        }
    }

    public static String getMetadataPrefix() {
        return metadataPrefixProperty != null ? metadataPrefixProperty.get() : DEFAULT_METADATA_PREFIX;
    }

    public static void setMetadataPrefix(String prefix) {
        if (metadataPrefixProperty != null) {
            metadataPrefixProperty.set(prefix);
        }
    }

    public static String getLabelImageKeywords() {
        return labelImageKeywordsProperty != null ? labelImageKeywordsProperty.get() : DEFAULT_LABEL_IMAGE_KEYWORDS;
    }

    public static void setLabelImageKeywords(String keywords) {
        if (labelImageKeywordsProperty != null) {
            labelImageKeywordsProperty.set(keywords);
        }
    }

    public static StringProperty labelImageKeywordsProperty() {
        return labelImageKeywordsProperty;
    }

    public static BooleanProperty autoRunOnEntrySwitchProperty() {
        return autoRunOnEntrySwitchProperty;
    }

    public static boolean isAutoRunOnEntrySwitch() {
        return autoRunOnEntrySwitchProperty != null ? autoRunOnEntrySwitchProperty.get() : DEFAULT_AUTO_RUN_ON_ENTRY_SWITCH;
    }

    public static void setAutoRunOnEntrySwitch(boolean autoRun) {
        if (autoRunOnEntrySwitchProperty != null) {
            autoRunOnEntrySwitchProperty.set(autoRun);
        }
    }

    public static double getDialogWidth() {
        return dialogWidthProperty != null ? dialogWidthProperty.get() : 1000.0;
    }

    public static void setDialogWidth(double width) {
        if (dialogWidthProperty != null) {
            dialogWidthProperty.set(width);
        }
    }

    public static double getDialogHeight() {
        return dialogHeightProperty != null ? dialogHeightProperty.get() : 700.0;
    }

    public static void setDialogHeight(double height) {
        if (dialogHeightProperty != null) {
            dialogHeightProperty.set(height);
        }
    }

    /**
     * Resets all preferences to their default values.
     */
    public static void resetToDefaults() {
        setLanguage(DEFAULT_LANGUAGE);
        setTessdataPath(DEFAULT_TESSDATA_PATH);
        setMinConfidence(DEFAULT_MIN_CONFIDENCE);
        setAutoRotate(DEFAULT_AUTO_ROTATE);
        setEnhanceContrast(DEFAULT_ENHANCE_CONTRAST);
        setDetectOrientation(DEFAULT_DETECT_ORIENTATION);
        setPageSegMode(DEFAULT_PAGE_SEG_MODE);
        setMetadataPrefix(DEFAULT_METADATA_PREFIX);
        setLabelImageKeywords(DEFAULT_LABEL_IMAGE_KEYWORDS);
        logger.info("OCR preferences reset to defaults");
    }
}
