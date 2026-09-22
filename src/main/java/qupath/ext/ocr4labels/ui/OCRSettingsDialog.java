package qupath.ext.ocr4labels.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;

import java.io.File;
import java.util.ResourceBundle;

/**
 * Dialog for configuring OCR settings.
 * Designed to be user-friendly for pathologists without technical OCR knowledge.
 */
public class OCRSettingsDialog {

    private static final Logger logger = LoggerFactory.getLogger(OCRSettingsDialog.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.ocr4labels.ui.strings");

    // Download URLs
    private static final String TESSDATA_FAST_URL = "https://github.com/tesseract-ocr/tessdata_fast";
    private static final String TESSDATA_BEST_URL = "https://github.com/tesseract-ocr/tessdata_best";
    private static final String ENG_TRAINEDDATA_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata";
    private static final String OSD_TRAINEDDATA_URL = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/osd.traineddata";

    private OCRSettingsDialog() {
        // Utility class
    }

    /**
     * Shows the OCR settings dialog.
     *
     * @param qupath The QuPath GUI instance
     */
    public static void show(QuPathGUI qupath) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(resources.getString("dialog.settings.title"));
        dialog.initOwner(qupath.getStage());

        // Create content
        ScrollPane scrollPane = new ScrollPane(createContent());
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(550);
        dialog.getDialogPane().setContent(scrollPane);

        // Add buttons
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Show dialog
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                logger.info("OCR settings saved");
            }
        });
    }

    private static VBox createContent() {
        VBox vbox = new VBox(15);
        vbox.setPadding(new Insets(15));
        vbox.setPrefWidth(550);

        // === Downloads Section (NEW) ===
        TitledPane downloadsPane = createDownloadsSection();

        // === Tessdata Path Section ===
        TitledPane tessdataPane = createTessdataSection();

        // === OCR Settings Section ===
        TitledPane ocrPane = createOCRSettingsSection();

        // === Preprocessing Section ===
        TitledPane preprocPane = createPreprocessingSection();

        // === Metadata Section ===
        TitledPane metadataPane = createMetadataSection();

        // === Reset Button ===
        Button resetButton = new Button("Reset to Defaults");
        resetButton.setTooltip(new Tooltip("Restore all settings to their original values"));
        resetButton.setOnAction(e -> {
            OCRPreferences.resetToDefaults();
            Dialogs.showInfoNotification("Settings Reset",
                    "OCR settings have been reset to defaults.\n" +
                            "Please reopen this dialog to see the changes.");
        });

        vbox.getChildren().addAll(
                downloadsPane,
                tessdataPane,
                ocrPane,
                preprocPane,
                metadataPane,
                resetButton
        );

        return vbox;
    }

    /**
     * Creates the Downloads section with links to required files.
     */
    private static TitledPane createDownloadsSection() {
        TitledPane pane = new TitledPane();
        pane.setText("Required Downloads");
        pane.setCollapsible(false);

        VBox content = new VBox(12);
        content.setPadding(new Insets(10));

        // Introduction text
        Label introLabel = new Label(
                "OCR requires data files to recognize text. Download these files and place them " +
                "in a folder on your computer (e.g., C:\\tessdata).");
        introLabel.setWrapText(true);
        introLabel.setStyle("-fx-font-size: 11px;");

        // Required files section
        Label requiredLabel = new Label("Required File:");
        requiredLabel.setStyle("-fx-font-weight: bold;");

        HBox engBox = new HBox(10);
        Hyperlink engLink = new Hyperlink("eng.traineddata (English language - ~4 MB)");
        engLink.setTooltip(new Tooltip("Required for English text recognition. Click to download."));
        engLink.setOnAction(e -> openUrl(ENG_TRAINEDDATA_URL));
        Label engStatus = createStatusLabel("eng.traineddata");
        engBox.getChildren().addAll(engLink, engStatus);

        // Optional files section
        Label optionalLabel = new Label("Optional File (for rotated labels):");
        optionalLabel.setStyle("-fx-font-weight: bold;");

        HBox osdBox = new HBox(10);
        Hyperlink osdLink = new Hyperlink("osd.traineddata (Orientation detection - ~10 MB)");
        osdLink.setTooltip(new Tooltip(
                "Enables automatic detection of rotated text on labels.\n" +
                "Recommended if your labels are sometimes placed sideways."));
        osdLink.setOnAction(e -> openUrl(OSD_TRAINEDDATA_URL));
        Label osdStatus = createStatusLabel("osd.traineddata");
        osdBox.getChildren().addAll(osdLink, osdStatus);

        // Additional languages
        Label additionalLabel = new Label("Additional Languages:");
        additionalLabel.setStyle("-fx-font-weight: bold;");

        Hyperlink additionalLink = new Hyperlink("Browse all available languages");
        additionalLink.setTooltip(new Tooltip(
                "Download additional language files if your labels contain\n" +
                "text in other languages (e.g., German, French, Chinese)."));
        additionalLink.setOnAction(e -> openUrl(TESSDATA_FAST_URL));

        // Tips
        Label tipsLabel = new Label(
                "Tip: After downloading, set the 'Tessdata Path' below to the folder containing these files.");
        tipsLabel.setWrapText(true);
        tipsLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");

        content.getChildren().addAll(
                introLabel,
                new Separator(),
                requiredLabel,
                engBox,
                optionalLabel,
                osdBox,
                new Separator(),
                additionalLabel,
                additionalLink,
                tipsLabel
        );

        pane.setContent(content);
        return pane;
    }

    /**
     * Creates a status label showing if a file exists in the current tessdata path.
     */
    private static Label createStatusLabel(String filename) {
        String tessdataPath = OCRPreferences.getTessdataPath();
        boolean exists = false;

        if (tessdataPath != null && !tessdataPath.isEmpty()) {
            File file = new File(tessdataPath, filename);
            exists = file.exists();
        }

        Label label = new Label(exists ? "[Installed]" : "[Not found]");
        label.setStyle(exists ?
                "-fx-text-fill: green; -fx-font-size: 10px;" :
                "-fx-text-fill: #cc6600; -fx-font-size: 10px;");
        return label;
    }

    /**
     * Creates the Tessdata Path section.
     */
    private static TitledPane createTessdataSection() {
        TitledPane pane = new TitledPane();
        pane.setText("Tessdata Location");
        pane.setCollapsible(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        Label pathLabel = new Label("Tessdata Path:");
        pathLabel.setTooltip(new Tooltip(
                "The folder where you saved the downloaded .traineddata files.\n" +
                "Example: C:\\tessdata or D:\\QuPath\\tessdata"));

        TextField pathField = new TextField(OCRPreferences.getTessdataPath());
        pathField.setPrefWidth(300);
        pathField.setPromptText("Select folder containing .traineddata files");
        pathField.setTooltip(new Tooltip(
                "Click 'Browse' to select the folder, or type the path directly."));
        pathField.textProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setTessdataPath(newVal));

        Button browseButton = new Button("Browse...");
        browseButton.setTooltip(new Tooltip("Open a folder browser to select your tessdata folder"));
        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Tessdata Directory");
            String currentPath = pathField.getText();
            if (currentPath != null && !currentPath.isEmpty()) {
                File current = new File(currentPath);
                if (current.exists()) {
                    chooser.setInitialDirectory(current);
                }
            }
            File selected = chooser.showDialog(browseButton.getScene().getWindow());
            if (selected != null) {
                pathField.setText(selected.getAbsolutePath());
            }
        });

        Label languageLabel = new Label("Language:");
        languageLabel.setTooltip(new Tooltip(
                "The language code for text recognition.\n" +
                "Use 'eng' for English. Other examples:\n" +
                "  deu = German\n" +
                "  fra = French\n" +
                "  chi_sim = Simplified Chinese"));

        TextField languageField = new TextField(OCRPreferences.getLanguage());
        languageField.setPrefWidth(100);
        languageField.setPromptText("eng");
        languageField.setTooltip(new Tooltip(
                "Enter the language code matching your .traineddata file.\n" +
                "For eng.traineddata, enter 'eng'."));
        languageField.textProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setLanguage(newVal));

        // Label image keywords
        Label keywordsLabel = new Label("Find Labels:");
        keywordsLabel.setTooltip(new Tooltip(
                "Keywords to search for when finding label images.\n" +
                "Different slide scanners use different names for the label image."));

        TextField keywordsField = new TextField(OCRPreferences.getLabelImageKeywords());
        keywordsField.setPrefWidth(200);
        keywordsField.setPromptText("label,barcode");
        keywordsField.setTooltip(new Tooltip(
                "Comma-separated keywords to search for in associated image names.\n\n" +
                "To see your slide's associated images in QuPath:\n" +
                "  Open an image, then look at the Image tab (bottom left).\n" +
                "  Scroll down to 'Associated images' to see available sub-images.\n\n" +
                "Examples:\n" +
                "  'label' matches 'label', 'Label', 'slide_label'\n" +
                "  'barcode' matches 'barcode_image'\n\n" +
                "Add your scanner's label image name if it differs from the defaults."));
        keywordsField.textProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setLabelImageKeywords(newVal));

        Label keywordsHint = new Label("Comma-separated list (e.g., label,barcode,label_image)");
        keywordsHint.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");

        grid.add(pathLabel, 0, 0);
        grid.add(pathField, 1, 0);
        grid.add(browseButton, 2, 0);
        grid.add(languageLabel, 0, 1);
        grid.add(languageField, 1, 1);
        grid.add(keywordsLabel, 0, 2);
        grid.add(keywordsField, 1, 2);
        grid.add(keywordsHint, 1, 3);

        GridPane.setHgrow(pathField, Priority.ALWAYS);
        pane.setContent(grid);
        return pane;
    }

    /**
     * Creates the OCR Settings section.
     */
    private static TitledPane createOCRSettingsSection() {
        TitledPane pane = new TitledPane();
        pane.setText("Text Detection Settings");
        pane.setCollapsible(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        // Page Segmentation Mode
        Label psmLabel = new Label("Detection Mode:");
        psmLabel.setTooltip(new Tooltip(
                "How the OCR engine looks for text on your label.\n" +
                "Different modes work better for different label layouts."));

        ComboBox<PSMOption> psmCombo = new ComboBox<>();
        psmCombo.getItems().addAll(PSMOption.values());
        psmCombo.setTooltip(new Tooltip(PSMOption.TOOLTIP));
        psmCombo.setValue(PSMOption.fromPreference());
        psmCombo.valueProperty().addListener((obs, oldMode, newMode) -> {
            if (newMode != null) {
                OCRPreferences.setPageSegMode(newMode.getMode());
            }
        });

        // Minimum Confidence
        Label confLabel = new Label("Confidence Threshold:");
        confLabel.setTooltip(new Tooltip(
                "How confident the OCR must be before showing detected text.\n" +
                "Lower values show more text but may include errors.\n" +
                "Higher values show only confident detections."));

        Slider confSlider = new Slider(0, 100, OCRPreferences.getMinConfidence() * 100);
        confSlider.setShowTickLabels(true);
        confSlider.setShowTickMarks(true);
        confSlider.setMajorTickUnit(25);
        confSlider.setPrefWidth(200);
        confSlider.setTooltip(new Tooltip(
                "Slide left to see more detected text (may include mistakes)\n" +
                "Slide right to see only high-confidence text"));

        Label confValue = new Label(String.format("%.0f%%", confSlider.getValue()));
        confSlider.valueProperty().addListener((obs, old, newVal) -> {
            confValue.setText(String.format("%.0f%%", newVal.doubleValue()));
            OCRPreferences.setMinConfidence(newVal.doubleValue() / 100.0);
        });

        HBox confBox = new HBox(10, confSlider, confValue);

        // Literal transcription - disables Tesseract's language dictionaries
        Label literalLabel = new Label("Text Style:");
        literalLabel.setTooltip(new Tooltip(
                "Whether to let the English dictionary correct what OCR reads."));

        CheckBox literalCb = new CheckBox("Literal text (no dictionary)");
        literalCb.setSelected(OCRPreferences.isLiteralText());
        literalCb.setTooltip(new Tooltip(
                "Report the characters OCR actually saw, without correcting them\n" +
                "toward English words. Recommended, and on by default.\n\n" +
                "Slide labels are mostly sample codes, accession numbers, dates and\n" +
                "e-mail addresses. None of those are dictionary words, so the language\n" +
                "model rewrites correct readings into wrong ones - it is what turns\n" +
                "'histology@lji.org' into 'histoloawalli.org'.\n\n" +
                "Turn this OFF only if your labels carry ordinary words, such as\n" +
                "handwritten tissue names or staining notes, where the dictionary\n" +
                "repairs more than it breaks."));
        literalCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setLiteralText(newVal));

        grid.add(psmLabel, 0, 0);
        grid.add(psmCombo, 1, 0);
        grid.add(confLabel, 0, 1);
        grid.add(confBox, 1, 1);
        grid.add(literalLabel, 0, 2);
        grid.add(literalCb, 1, 2);

        pane.setContent(grid);
        return pane;
    }

    /**
     * Creates the Preprocessing section.
     */
    private static TitledPane createPreprocessingSection() {
        TitledPane pane = new TitledPane();
        pane.setText("Image Enhancement");
        pane.setCollapsible(false);

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));

        CheckBox detectOrientationCb = new CheckBox("Detect text orientation");
        detectOrientationCb.setSelected(OCRPreferences.isDetectOrientation());
        detectOrientationCb.setTooltip(new Tooltip(
                "Automatically detect if the label text is rotated or upside down.\n\n" +
                "Note: Requires osd.traineddata file to be installed.\n" +
                "Enable this if labels are sometimes placed sideways on slides."));
        detectOrientationCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setDetectOrientation(newVal));

        CheckBox autoRotateCb = new CheckBox("Auto-rotate to correct orientation");
        autoRotateCb.setSelected(OCRPreferences.isAutoRotate());
        autoRotateCb.setTooltip(new Tooltip(
                "Automatically rotate the image to make text horizontal before reading.\n\n" +
                "This improves accuracy when labels are placed at an angle.\n" +
                "Only works if 'Detect text orientation' is enabled."));
        autoRotateCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setAutoRotate(newVal));
        autoRotateCb.disableProperty().bind(detectOrientationCb.selectedProperty().not());

        CheckBox enhanceContrastCb = new CheckBox("Enhance image contrast");
        enhanceContrastCb.setSelected(OCRPreferences.isEnhanceContrast());
        enhanceContrastCb.setTooltip(new Tooltip(
                "Hard-threshold the image to pure black and white before OCR.\n\n" +
                "Off by default. In testing it made text worse at every blur level and\n" +
                "never beat the untouched image, including on the faded labels it was\n" +
                "meant to help. Tesseract already thresholds internally.\n\n" +
                "Leave off unless a specific label reads better with it."));
        enhanceContrastCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setEnhanceContrast(newVal));

        // Separator before workflow settings
        Separator sep = new Separator();

        CheckBox autoRunCb = new CheckBox("Auto-run OCR when switching images");
        autoRunCb.setSelected(OCRPreferences.isAutoRunOnEntrySwitch());
        autoRunCb.setTooltip(new Tooltip(
                "Automatically run OCR when you select a different image in the project list.\n\n" +
                "When disabled, you must click the Scan button after selecting each image.\n" +
                "Disable this for faster navigation through many images."));
        autoRunCb.selectedProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setAutoRunOnEntrySwitch(newVal));

        content.getChildren().addAll(
                detectOrientationCb,
                autoRotateCb,
                enhanceContrastCb,
                sep,
                autoRunCb
        );
        pane.setContent(content);
        return pane;
    }

    /**
     * Creates the Metadata section.
     */
    private static TitledPane createMetadataSection() {
        TitledPane pane = new TitledPane();
        pane.setText("QuPath Metadata");
        pane.setCollapsible(false);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        Label prefixLabel = new Label("Key Prefix:");
        prefixLabel.setTooltip(new Tooltip(
                "Text added to the beginning of all metadata field names.\n" +
                "Helps identify which metadata came from OCR."));

        TextField prefixField = new TextField(OCRPreferences.getMetadataPrefix());
        prefixField.setPrefWidth(150);
        prefixField.setPromptText("OCR_");
        prefixField.setTooltip(new Tooltip(
                "Example: With prefix 'OCR_', a field named 'CaseID' becomes 'OCR_CaseID'\n\n" +
                "This helps you identify which metadata fields were created by OCR\n" +
                "versus other sources in your QuPath project."));
        prefixField.textProperty().addListener((obs, old, newVal) ->
                OCRPreferences.setMetadataPrefix(newVal));

        Label prefixHint = new Label("Prefix added to all OCR metadata keys in your QuPath project");
        prefixHint.setStyle("-fx-text-fill: gray; -fx-font-size: 10px;");
        prefixHint.setWrapText(true);

        grid.add(prefixLabel, 0, 0);
        grid.add(prefixField, 1, 0);
        grid.add(prefixHint, 1, 1);

        pane.setContent(grid);
        return pane;
    }

    /**
     * Opens a URL in the default browser.
     */
    private static void openUrl(String url) {
        try {
            java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
        } catch (Exception ex) {
            logger.error("Failed to open browser for URL: {}", url, ex);
            Dialogs.showErrorMessage("Browser Error",
                    "Could not open the download link.\n\n" +
                    "Please manually visit:\n" + url);
        }
    }
}
