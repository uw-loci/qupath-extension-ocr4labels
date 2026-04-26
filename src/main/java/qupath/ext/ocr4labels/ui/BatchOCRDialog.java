package qupath.ext.ocr4labels.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.controller.OCRController;
import qupath.ext.ocr4labels.model.BoundingBox;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.model.OCRTemplate;
import qupath.ext.ocr4labels.model.RegionType;
import qupath.ext.ocr4labels.model.TextBlock;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.ext.ocr4labels.service.OCREngine;
import qupath.ext.ocr4labels.service.UnifiedDecoderService;
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.ext.ocr4labels.utilities.OCRMetadataManager;
import qupath.ext.ocr4labels.utilities.TextFilters;
import qupath.ext.ocr4labels.utilities.TextMatcher;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dialog for batch OCR processing across all images in a project.
 * Allows users to create or load a template, preview results, and apply to all images.
 */
public class BatchOCRDialog {

    private static final Logger logger = LoggerFactory.getLogger(BatchOCRDialog.class);

    private final QuPathGUI qupath;
    private final Project<?> project;
    private final OCREngine ocrEngine;
    private final List<ProjectImageEntry<?>> imagesWithLabels;

    private Stage stage;
    private OCRTemplate currentTemplate;
    private ObservableList<ImageProcessingEntry> imageEntries;

    // UI components
    private TableView<OCRTemplate.FieldMapping> templateTable;
    private TableView<ImageProcessingEntry> resultsTable;
    private ProgressBar progressBar;
    private Label statusLabel;
    private Button processButton;
    private Button applyButton;
    private AtomicBoolean processingCancelled;

    // Vocabulary matching for OCR correction
    private TextMatcher textMatcher;
    private Label vocabularyStatusLabel;
    private CheckBox ocrWeightsCheckBox;

    /**
     * Shows the batch OCR dialog.
     */
    public static void show(QuPathGUI qupath, OCREngine ocrEngine) {
        new BatchOCRDialog(qupath, ocrEngine).showDialog();
    }

    private BatchOCRDialog(QuPathGUI qupath, OCREngine ocrEngine) {
        this.qupath = qupath;
        this.project = qupath.getProject();
        this.ocrEngine = ocrEngine;
        this.imagesWithLabels = findImagesWithLabels();
        this.imageEntries = FXCollections.observableArrayList();
        this.processingCancelled = new AtomicBoolean(false);
    }

    private List<ProjectImageEntry<?>> findImagesWithLabels() {
        List<ProjectImageEntry<?>> result = new ArrayList<>();
        if (project == null) return result;

        for (ProjectImageEntry<?> entry : project.getImageList()) {
            try {
                ImageData<?> imageData = entry.readImageData();
                if (imageData != null && LabelImageUtility.isLabelImageAvailable(imageData)) {
                    result.add(entry);
                }
            } catch (Exception e) {
                logger.debug("Could not check image for label: {}", entry.getImageName());
            }
        }
        return result;
    }

    private void showDialog() {
        if (imagesWithLabels.isEmpty()) {
            Dialogs.showWarningNotification("No Labels Found",
                    "No images in the project have label images available.");
            return;
        }

        stage = new Stage();
        stage.setTitle("Batch OCR Processing");
        stage.initOwner(qupath.getStage());
        stage.initModality(Modality.WINDOW_MODAL);

        BorderPane root = new BorderPane();
        root.setTop(createHeaderPane());
        root.setCenter(createMainContent());
        root.setBottom(createButtonBar());

        Scene scene = new Scene(root, 900, 650);
        stage.setScene(scene);
        stage.show();

        // Initialize image entries
        for (ProjectImageEntry<?> entry : imagesWithLabels) {
            imageEntries.add(new ImageProcessingEntry(entry));
        }
    }

    private VBox createHeaderPane() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(15));
        header.setStyle("-fx-background-color: #f0f0f0;");

        Label titleLabel = new Label("Batch OCR Processing");
        titleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label infoLabel = new Label(String.format(
                "Found %d images with labels out of %d total images in project.",
                imagesWithLabels.size(), project.getImageList().size()));

        Label instructionLabel = new Label(
                "1. Create a template by running OCR on a sample image, or load a saved template.\n" +
                "2. Review the field mappings below.\n" +
                "3. Click 'Process All' to run OCR on all images.\n" +
                "4. Review results and click 'Apply Metadata' to save.");
        instructionLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        header.getChildren().addAll(titleLabel, infoLabel, instructionLabel);
        return header;
    }

    private SplitPane createMainContent() {
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);

        // Top: Template section
        VBox templateSection = createTemplateSection();

        // Bottom: Results section
        VBox resultsSection = createResultsSection();

        splitPane.getItems().addAll(templateSection, resultsSection);
        splitPane.setDividerPositions(0.4);

        return splitPane;
    }

    private VBox createTemplateSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));

        // Template toolbar
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label templateLabel = new Label("Field Mappings:");
        templateLabel.setStyle("-fx-font-weight: bold;");
        templateLabel.setTooltip(new Tooltip("Define which detected text fields map to metadata keys"));

        Button createFromCurrentBtn = new Button("Create from Current Image");
        createFromCurrentBtn.setTooltip(new Tooltip(
                "Run OCR on the currently open image and use its fields as a template"));
        createFromCurrentBtn.setOnAction(e -> createTemplateFromCurrentImage());

        Button loadTemplateBtn = new Button("Load Template...");
        loadTemplateBtn.setTooltip(new Tooltip("Load a previously saved template file"));
        loadTemplateBtn.setOnAction(e -> loadTemplate());

        Button saveTemplateBtn = new Button("Save Template...");
        saveTemplateBtn.setTooltip(new Tooltip("Save the current field mappings to a file"));
        saveTemplateBtn.setOnAction(e -> saveTemplate());
        saveTemplateBtn.disableProperty().bind(
                javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> currentTemplate == null || currentTemplate.getFieldMappings().isEmpty(),
                        templateTable != null ? templateTable.getItems() : FXCollections.observableArrayList()
                )
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(templateLabel, createFromCurrentBtn, loadTemplateBtn, saveTemplateBtn);

        // Template table
        templateTable = new TableView<>();
        templateTable.setPlaceholder(new Label("No template loaded. Create or load a template to begin."));
        templateTable.setEditable(true);

        TableColumn<OCRTemplate.FieldMapping, Boolean> enabledCol = new TableColumn<>("Use");
        enabledCol.setCellValueFactory(data -> {
            SimpleBooleanProperty prop = new SimpleBooleanProperty(data.getValue().isEnabled());
            prop.addListener((obs, old, newVal) -> data.getValue().setEnabled(newVal));
            return prop;
        });
        enabledCol.setCellFactory(CheckBoxTableCell.forTableColumn(enabledCol));
        enabledCol.setPrefWidth(50);

        TableColumn<OCRTemplate.FieldMapping, String> indexCol = new TableColumn<>("Field #");
        indexCol.setCellValueFactory(data ->
                new SimpleStringProperty(String.valueOf(data.getValue().getFieldIndex() + 1)));
        indexCol.setPrefWidth(60);

        TableColumn<OCRTemplate.FieldMapping, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getRegionType().getDisplayName()));
        typeCol.setPrefWidth(70);

        TableColumn<OCRTemplate.FieldMapping, String> keyCol = new TableColumn<>("Metadata Key");
        keyCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getMetadataKey()));
        keyCol.setPrefWidth(150);

        TableColumn<OCRTemplate.FieldMapping, String> exampleCol = new TableColumn<>("Example Text");
        exampleCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getExampleText()));
        exampleCol.setPrefWidth(250);

        templateTable.getColumns().addAll(enabledCol, indexCol, typeCol, keyCol, exampleCol);
        VBox.setVgrow(templateTable, Priority.ALWAYS);

        section.getChildren().addAll(toolbar, templateTable);
        return section;
    }

    private VBox createResultsSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(10));

        Label resultsLabel = new Label("Processing Results:");
        resultsLabel.setStyle("-fx-font-weight: bold;");

        // Results table with editable cells
        resultsTable = new TableView<>(imageEntries);
        resultsTable.setPlaceholder(new Label("Click 'Process All' to run OCR on all images."));
        resultsTable.setEditable(true);

        // Fixed columns (non-editable)
        TableColumn<ImageProcessingEntry, String> nameCol = new TableColumn<>("Image Name");
        nameCol.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getImageName()));
        nameCol.setPrefWidth(200);
        nameCol.setMinWidth(150);

        TableColumn<ImageProcessingEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        statusCol.setPrefWidth(80);
        statusCol.setMinWidth(60);

        resultsTable.getColumns().addAll(nameCol, statusCol);

        // Wrap in ScrollPane for horizontal scrolling
        ScrollPane scrollPane = new ScrollPane(resultsTable);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Filter bar for text manipulation
        HBox filterBar = createFilterBar();

        // Progress section
        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER_LEFT);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);

        statusLabel = new Label("");

        progressBox.getChildren().addAll(progressBar, statusLabel);

        section.getChildren().addAll(resultsLabel, scrollPane, filterBar, progressBox);
        return section;
    }

    /**
     * Updates the results table columns based on the current template's field mappings.
     * Called after loading a template or after processing to add dynamic field columns.
     */
    private void updateResultsTableColumns() {
        if (currentTemplate == null) return;

        // Remove all columns except the first two (Image Name, Status)
        while (resultsTable.getColumns().size() > 2) {
            resultsTable.getColumns().remove(resultsTable.getColumns().size() - 1);
        }

        // Add a column for each enabled field mapping
        for (OCRTemplate.FieldMapping mapping : currentTemplate.getFieldMappings()) {
            if (!mapping.isEnabled()) continue;

            String key = mapping.getMetadataKey();
            TableColumn<ImageProcessingEntry, String> fieldCol = new TableColumn<>(key);
            fieldCol.setCellValueFactory(data -> data.getValue().getFieldProperty(key));
            fieldCol.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn());
            fieldCol.setOnEditCommit(e -> {
                e.getRowValue().setFieldValue(key, e.getNewValue());
            });
            fieldCol.setPrefWidth(120);
            fieldCol.setMinWidth(80);
            fieldCol.setEditable(true);

            resultsTable.getColumns().add(fieldCol);
        }

        // Adjust table width to fit all columns
        double totalWidth = resultsTable.getColumns().stream()
                .mapToDouble(TableColumn::getPrefWidth)
                .sum();
        resultsTable.setPrefWidth(Math.max(totalWidth + 20, 600));
    }

    /**
     * Creates the filter bar with buttons for text filtering operations.
     */
    private HBox createFilterBar() {
        HBox filterBar = new HBox(5);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(5, 0, 5, 0));

        Label filterLabel = new Label("Filter all fields:");
        filterLabel.setStyle("-fx-font-size: 11px;");
        filterLabel.setTooltip(new Tooltip("Apply text post-processing filters to all processed fields"));

        // Create filter buttons
        for (TextFilters.TextFilter filter : TextFilters.ALL_FILTERS) {
            Button btn = new Button(filter.getButtonLabel());
            btn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
            btn.setTooltip(new Tooltip(filter.getTooltip()));
            btn.setOnAction(e -> applyTextFilter(filter));
            filterBar.getChildren().add(btn);
        }

        // Add the label at the beginning
        filterBar.getChildren().add(0, filterLabel);

        // Add separator and vocabulary matching section
        filterBar.getChildren().add(new Separator(javafx.geometry.Orientation.VERTICAL));

        // Vocabulary matching controls
        Button loadVocabBtn = new Button("Load List...");
        loadVocabBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
        loadVocabBtn.setOnAction(e -> loadVocabularyFile());

        // Help button with tooltip explaining vocabulary matching
        Label helpLabel = new Label("?");
        helpLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #0066cc; " +
                "-fx-cursor: hand; -fx-padding: 0 3 0 3;");
        helpLabel.setTooltip(new Tooltip(
                "Vocabulary Matching - Correct OCR errors automatically\n\n" +
                "Load a text file (CSV, TSV, or plain text) containing known valid values.\n" +
                "After filtering, click 'Match All' to correct OCR mistakes by finding the\n" +
                "closest match from your list across ALL processed images.\n\n" +
                "Example: If your list contains 'Sample_001' and OCR detected 'Samp1e_0O1',\n" +
                "the matcher will correct it to 'Sample_001'.\n\n" +
                "File format:\n" +
                "  - CSV: Uses first column (header row auto-skipped)\n" +
                "  - TSV/TXT: Uses first column or whole line\n" +
                "  - One valid value per line\n\n" +
                "Uses Levenshtein distance with OCR-aware weighting\n" +
                "(0/O, 1/l/I confusions have lower penalty)."));

        Button matchBtn = new Button("Match All");
        matchBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
        matchBtn.setDisable(true); // Enabled when vocabulary is loaded
        matchBtn.setTooltip(new Tooltip("Match all detected text against loaded vocabulary to correct OCR errors"));
        matchBtn.setOnAction(e -> applyVocabularyMatching());

        // OCR weights toggle
        ocrWeightsCheckBox = new CheckBox("OCR weights");
        ocrWeightsCheckBox.setStyle("-fx-font-size: 10px;");
        ocrWeightsCheckBox.setSelected(false); // Default off for scientific names
        ocrWeightsCheckBox.setTooltip(new Tooltip(
                "OCR-Weighted Matching\n\n" +
                "When ENABLED: Common OCR confusions have lower penalty:\n" +
                "  - 0/O, 1/l/I, 5/S, 8/B are treated as similar\n" +
                "  - Better for natural text with accidental letter/number swaps\n\n" +
                "When DISABLED (default): All character changes have equal cost\n" +
                "  - Better for scientific sample names like 'PBS_O1' vs 'PBS_01'\n" +
                "  - Treats intentional letter/number choices as significant"));

        // Status label showing loaded vocabulary info
        vocabularyStatusLabel = new Label("");
        vocabularyStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666;");

        // Store match button reference for enabling/disabling
        loadVocabBtn.setUserData(matchBtn);

        filterBar.getChildren().addAll(loadVocabBtn, helpLabel, ocrWeightsCheckBox, matchBtn, vocabularyStatusLabel);

        return filterBar;
    }

    /**
     * Applies a text filter to all field values across all processed entries.
     */
    private void applyTextFilter(TextFilters.TextFilter filter) {
        // Count entries that have been processed
        long processedCount = imageEntries.stream()
                .filter(e -> "Done".equals(e.getStatus()) || "Applied".equals(e.getStatus()))
                .count();

        if (processedCount == 0) {
            Dialogs.showWarningNotification("No Data", "Process images first to filter their field values.");
            return;
        }

        int modifiedEntries = 0;
        int modifiedFields = 0;

        for (ImageProcessingEntry entry : imageEntries) {
            if (!"Done".equals(entry.getStatus()) && !"Applied".equals(entry.getStatus())) {
                continue;
            }

            boolean entryModified = false;
            Map<String, String> metadata = entry.getMetadata();
            if (metadata == null) continue;

            for (Map.Entry<String, String> field : metadata.entrySet()) {
                String original = field.getValue();
                String filtered = filter.apply(original);
                if (original != null && !original.equals(filtered)) {
                    entry.setFieldValue(field.getKey(), filtered);
                    modifiedFields++;
                    entryModified = true;
                }
            }

            if (entryModified) {
                modifiedEntries++;
            }
        }

        if (modifiedFields > 0) {
            resultsTable.refresh();
            logger.info("Applied filter '{}' to {} fields across {} entries",
                    filter.getName(), modifiedFields, modifiedEntries);
            Dialogs.showInfoNotification("Filter Applied",
                    String.format("Modified %d fields across %d images.", modifiedFields, modifiedEntries));
        } else {
            Dialogs.showInfoNotification("No Changes",
                    "Filter '" + filter.getName() + "' did not modify any text.");
        }
    }

    /**
     * Opens a file chooser to load a vocabulary file for OCR correction matching.
     */
    private void loadVocabularyFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Vocabulary File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported", "*.csv", "*.tsv", "*.txt"),
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"),
                new FileChooser.ExtensionFilter("TSV Files", "*.tsv"),
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        // Set initial directory to project folder if available
        if (project != null) {
            try {
                File projectDir = project.getPath().toFile().getParentFile();
                if (projectDir != null && projectDir.isDirectory()) {
                    chooser.setInitialDirectory(projectDir);
                }
            } catch (Exception e) {
                logger.debug("Could not get project directory: {}", e.getMessage());
            }
        }

        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        try {
            if (textMatcher == null) {
                textMatcher = new TextMatcher();
            }
            textMatcher.loadVocabularyFromFile(file);

            // Update UI to show vocabulary is loaded
            int count = textMatcher.getVocabularySize();
            vocabularyStatusLabel.setText(String.format("(%d entries)", count));

            // Enable the Match All button - find it in the filter bar
            for (javafx.scene.Node node : ((HBox) vocabularyStatusLabel.getParent()).getChildren()) {
                if (node instanceof Button && "Match All".equals(((Button) node).getText())) {
                    node.setDisable(false);
                    break;
                }
            }

            Dialogs.showInfoNotification("Vocabulary Loaded",
                    String.format("Loaded %d entries from %s", count, file.getName()));

        } catch (IOException e) {
            logger.error("Failed to load vocabulary file", e);
            Dialogs.showErrorMessage("Load Error",
                    "Failed to load vocabulary file:\n" + e.getMessage());
        }
    }

    /**
     * Applies vocabulary matching to correct OCR errors across all processed images.
     * Uses fuzzy matching (Levenshtein distance) to find the closest match
     * from the loaded vocabulary for each detected text value.
     */
    private void applyVocabularyMatching() {
        if (textMatcher == null || !textMatcher.hasVocabulary()) {
            Dialogs.showWarningNotification("No Vocabulary",
                    "Please load a vocabulary file first using 'Load List...'");
            return;
        }

        // Count entries that have been processed
        long processedCount = imageEntries.stream()
                .filter(e -> "Done".equals(e.getStatus()) || "Applied".equals(e.getStatus()))
                .count();

        if (processedCount == 0) {
            Dialogs.showWarningNotification("No Data",
                    "Process images first before applying vocabulary matching.");
            return;
        }

        // Apply OCR weights setting from checkbox
        textMatcher.setUseOCRWeights(ocrWeightsCheckBox.isSelected());

        int totalCorrected = 0;
        int totalExact = 0;
        int totalNoMatch = 0;
        int entriesModified = 0;

        for (ImageProcessingEntry entry : imageEntries) {
            if (!"Done".equals(entry.getStatus()) && !"Applied".equals(entry.getStatus())) {
                continue;
            }

            Map<String, String> metadata = entry.getMetadata();
            if (metadata == null) continue;

            boolean entryModified = false;

            for (Map.Entry<String, String> field : metadata.entrySet()) {
                String original = field.getValue();
                if (original == null || original.isEmpty()) {
                    totalNoMatch++;
                    continue;
                }

                TextMatcher.MatchResult match = textMatcher.findBestMatch(original);

                if (match == null) {
                    totalNoMatch++;
                } else if (match.isExactMatch()) {
                    totalExact++;
                } else {
                    // Found a fuzzy match - apply correction
                    entry.setFieldValue(field.getKey(), match.getMatchedValue());
                    totalCorrected++;
                    entryModified = true;
                    logger.debug("Corrected '{}' -> '{}' in {}",
                            original, match.getMatchedValue(), entry.getImageName());
                }
            }

            if (entryModified) {
                entriesModified++;
            }
        }

        if (totalCorrected > 0) {
            resultsTable.refresh();
        }

        // Log what mode was used
        String modeInfo = ocrWeightsCheckBox.isSelected() ? "OCR-weighted" : "standard";
        logger.info("Batch vocabulary matching ({}): corrected={}, exact={}, noMatch={}, entriesModified={}",
                modeInfo, totalCorrected, totalExact, totalNoMatch, entriesModified);

        // Show summary
        if (totalCorrected > 0) {
            Dialogs.showInfoNotification("Matching Complete",
                    String.format("Corrected %d field(s) across %d image(s) using %s matching.",
                            totalCorrected, entriesModified, modeInfo));
        } else if (totalExact > 0) {
            Dialogs.showInfoNotification("No Corrections Needed",
                    "All detected values already match the vocabulary.");
        } else {
            Dialogs.showWarningNotification("No Matches",
                    "None of the detected values matched the vocabulary.\n\n" +
                    "Try:\n" +
                    "  - Toggling 'OCR weights' checkbox\n" +
                    "  - Checking your vocabulary file contents\n" +
                    "  - Applying text filters first");
        }
    }

    private HBox createButtonBar() {
        HBox buttonBar = new HBox(15);
        buttonBar.setPadding(new Insets(15));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        processButton = new Button("Process All");
        processButton.setTooltip(new Tooltip("Run OCR on all images using the current template"));
        processButton.setOnAction(e -> processAllImages());
        processButton.setDisable(true); // Enabled when template is loaded

        applyButton = new Button("Apply Metadata");
        applyButton.setTooltip(new Tooltip("Save the detected metadata to all processed images"));
        applyButton.setOnAction(e -> applyAllMetadata());
        applyButton.setDisable(true); // Enabled after processing

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> {
            processingCancelled.set(true);
            stage.close();
        });

        buttonBar.getChildren().addAll(spacer, processButton, applyButton, cancelButton);
        return buttonBar;
    }

    private void createTemplateFromCurrentImage() {
        // Open the single image OCR dialog for creating templates
        // This allows users to edit metadata keys and save templates with proper positions
        Dialogs.showInfoNotification("Create Template",
                "The OCR dialog will open. Use it to:\n\n" +
                "1. Run OCR and adjust detected fields\n" +
                "2. Edit metadata key names as needed\n" +
                "3. Click 'Save Template...' to save\n\n" +
                "Then load the template here for batch processing.");

        OCRDialog.show(qupath, ocrEngine);
        return;

        // Legacy code below - kept for reference but no longer executed
        /*
        ImageData<?> imageData = qupath.getImageData();
        if (imageData == null) {
            Dialogs.showWarningNotification("No Image Open",
                    "Please open an image first to create a template from it.");
            return;
        }

        if (!LabelImageUtility.isLabelImageAvailable(imageData)) {
            Dialogs.showWarningNotification("No Label Image",
                    "The current image does not have a label image.");
            return;
        }

        // Get the label image
        BufferedImage labelImage = LabelImageUtility.retrieveLabelImage(imageData);
        if (labelImage == null) {
            Dialogs.showErrorMessage("Error", "Could not retrieve label image.");
            return;
        }

        // Run OCR
        statusLabel.setText("Running OCR on current image...");
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // Indeterminate

        new Thread(() -> {
            try {
                OCRConfiguration config = OCRConfiguration.builder()
                        .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
                        .language(OCRPreferences.getLanguage())
                        .minConfidence(OCRPreferences.getMinConfidence())
                        .enhanceContrast(OCRPreferences.isEnhanceContrast())
                        .build();

                OCRResult result = ocrEngine.processImage(labelImage, config);

                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("");

                    if (result.getBlockCount() == 0) {
                        Dialogs.showWarningNotification("No Text Found",
                                "OCR did not detect any text on the label.");
                        return;
                    }

                    // Create template from results
                    currentTemplate = new OCRTemplate("Template from " + imageData.getServer().getMetadata().getName());
                    currentTemplate.setConfiguration(config);

                    String prefix = OCRPreferences.getMetadataPrefix();
                    int fieldIndex = 0;

                    // Get lines first, then words if no lines
                    List<TextBlock> blocks = new ArrayList<>();
                    for (TextBlock block : result.getTextBlocks()) {
                        if (block.getType() == TextBlock.BlockType.LINE && !block.isEmpty()) {
                            blocks.add(block);
                        }
                    }
                    if (blocks.isEmpty()) {
                        for (TextBlock block : result.getTextBlocks()) {
                            if (block.getType() == TextBlock.BlockType.WORD && !block.isEmpty()) {
                                blocks.add(block);
                            }
                        }
                    }

                    for (TextBlock block : blocks) {
                        String key = prefix + "field_" + fieldIndex;
                        OCRTemplate.FieldMapping mapping = new OCRTemplate.FieldMapping(
                                fieldIndex, key, block.getText());
                        currentTemplate.addFieldMapping(mapping);
                        fieldIndex++;
                    }

                    // Update table
                    templateTable.setItems(FXCollections.observableArrayList(
                            currentTemplate.getFieldMappings()));
                    processButton.setDisable(false);

                    Dialogs.showInfoNotification("Template Created",
                            String.format("Created template with %d field mappings.\n" +
                                    "Edit the metadata keys as needed, then click 'Process All'.",
                                    currentTemplate.getFieldMappings().size()));
                });

            } catch (Exception e) {
                logger.error("Error creating template", e);
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("");
                    Dialogs.showErrorMessage("OCR Error", "Failed to run OCR: " + e.getMessage());
                });
            }
        }).start();
        */
    }

    private void loadTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load OCR Template");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OCR Template (*.json)", "*.json"));

        // Set initial directory to project folder if available
        if (project != null) {
            try {
                File projectDir = project.getPath().toFile().getParentFile();
                if (projectDir != null && projectDir.isDirectory()) {
                    chooser.setInitialDirectory(projectDir);
                }
            } catch (Exception e) {
                logger.debug("Could not get project directory: {}", e.getMessage());
            }
        }

        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        try {
            currentTemplate = OCRTemplate.loadFromFile(file);
            templateTable.setItems(FXCollections.observableArrayList(
                    currentTemplate.getFieldMappings()));
            processButton.setDisable(false);

            // Update results table columns to match template fields
            updateResultsTableColumns();

            Dialogs.showInfoNotification("Template Loaded",
                    String.format("Loaded template '%s' with %d field mappings.",
                            currentTemplate.getName(), currentTemplate.getFieldMappings().size()));

        } catch (IOException e) {
            logger.error("Error loading template", e);
            Dialogs.showErrorMessage("Load Error", "Failed to load template: " + e.getMessage());
        }
    }

    private void saveTemplate() {
        if (currentTemplate == null || currentTemplate.getFieldMappings().isEmpty()) {
            Dialogs.showWarningNotification("No Template",
                    "Create a template first before saving.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save OCR Template");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("OCR Template (*.json)", "*.json"));
        chooser.setInitialFileName("ocr_template.json");

        // Set initial directory to project folder if available
        if (project != null) {
            try {
                File projectDir = project.getPath().toFile().getParentFile();
                if (projectDir != null && projectDir.isDirectory()) {
                    chooser.setInitialDirectory(projectDir);
                }
            } catch (Exception e) {
                logger.debug("Could not get project directory: {}", e.getMessage());
            }
        }

        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        try {
            // Update template with current table values
            currentTemplate.setFieldMappings(new ArrayList<>(templateTable.getItems()));
            currentTemplate.saveToFile(file);

            Dialogs.showInfoNotification("Template Saved",
                    "Template saved to: " + file.getName());

        } catch (IOException e) {
            logger.error("Error saving template", e);
            Dialogs.showErrorMessage("Save Error", "Failed to save template: " + e.getMessage());
        }
    }

    private void processAllImages() {
        if (currentTemplate == null || currentTemplate.getFieldMappings().isEmpty()) {
            Dialogs.showWarningNotification("No Template",
                    "Please create or load a template first.");
            return;
        }

        // Update template with any edits
        currentTemplate.setFieldMappings(new ArrayList<>(templateTable.getItems()));

        int enabledCount = currentTemplate.getEnabledMappingCount();
        if (enabledCount == 0) {
            Dialogs.showWarningNotification("No Fields Enabled",
                    "Please enable at least one field mapping.");
            return;
        }

        processingCancelled.set(false);
        processButton.setDisable(true);
        applyButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(0);

        // Reset all entries
        for (ImageProcessingEntry entry : imageEntries) {
            entry.reset();
        }

        int totalImages = imageEntries.size();
        AtomicInteger processedCount = new AtomicInteger(0);

        new Thread(() -> {
            OCRConfiguration config = currentTemplate.getConfiguration();
            if (config == null) {
                config = OCRConfiguration.builder()
                        .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
                        .language(OCRPreferences.getLanguage())
                        .minConfidence(OCRPreferences.getMinConfidence())
                        .enhanceContrast(OCRPreferences.isEnhanceContrast())
                        .build();
            }

            for (ImageProcessingEntry entry : imageEntries) {
                if (processingCancelled.get()) {
                    break;
                }

                int current = processedCount.incrementAndGet();
                Platform.runLater(() -> {
                    progressBar.setProgress((double) current / totalImages);
                    statusLabel.setText(String.format("Processing %d of %d: %s",
                            current, totalImages, entry.getImageName()));
                });

                processImage(entry, config);
            }

            Platform.runLater(() -> {
                progressBar.setVisible(false);
                processButton.setDisable(false);

                if (processingCancelled.get()) {
                    statusLabel.setText("Processing cancelled.");
                } else {
                    // Count successful
                    long successful = imageEntries.stream()
                            .filter(e -> "Done".equals(e.getStatus()))
                            .count();
                    statusLabel.setText(String.format("Processed %d images. %d successful.",
                            totalImages, successful));
                    applyButton.setDisable(successful == 0);
                }
            });

        }).start();
    }

    private void processImage(ImageProcessingEntry entry, OCRConfiguration config) {
        try {
            entry.setStatus("Processing...");

            ImageData<?> imageData = entry.getProjectEntry().readImageData();
            if (imageData == null) {
                entry.setStatus("Error");
                entry.setFieldsFound("N/A");
                return;
            }

            BufferedImage labelImage = LabelImageUtility.retrieveLabelImage(imageData);
            if (labelImage == null) {
                entry.setStatus("No label");
                entry.setFieldsFound("N/A");
                return;
            }

            // Check if template has bounding box data (fixed-position mode)
            if (currentTemplate.hasBoundingBoxData()) {
                // Use position-based extraction with unified decoder
                processImageWithPositions(entry, labelImage, config);
            } else {
                // Use general OCR (original behavior)
                processImageWithGenericOCR(entry, labelImage, config);
            }

        } catch (Exception e) {
            logger.error("Error processing image: {}", entry.getImageName(), e);
            entry.setStatus("Error");
            entry.setFieldsFound("N/A");
            entry.setMetadataPreview(e.getMessage());
        }
    }

    /**
     * Process image using fixed-position extraction with unified decoder.
     * Supports mixed TEXT and BARCODE regions from template.
     */
    private void processImageWithPositions(ImageProcessingEntry entry, BufferedImage labelImage,
                                            OCRConfiguration config) {
        int imgWidth = labelImage.getWidth();
        int imgHeight = labelImage.getHeight();
        double dilation = currentTemplate.getDilationFactor();

        int fieldsPopulated = 0;
        int textFields = 0;
        int barcodeFields = 0;

        for (OCRTemplate.FieldMapping mapping : currentTemplate.getFieldMappings()) {
            if (!mapping.isEnabled() || !mapping.hasBoundingBox()) continue;

            String key = mapping.getMetadataKey();
            RegionType regionType = mapping.getRegionType();

            // Barcode regions need more context than text regions. ZXing bounding
            // boxes are very tight around the barcode modules - the decoder needs
            // quiet zones and surrounding whitespace to reliably decode.
            double effectiveDilation = (regionType == RegionType.BARCODE || regionType == RegionType.AUTO)
                    ? Math.max(dilation, 2.5)
                    : dilation;

            int[] box = mapping.getScaledBoundingBox(imgWidth, imgHeight, effectiveDilation);
            if (box == null || box[2] < 5 || box[3] < 5) continue;

            try {
                // Extract region
                BufferedImage regionImage = labelImage.getSubimage(box[0], box[1], box[2], box[3]);

                // Use unified decoder based on region type
                java.awt.Rectangle region = new java.awt.Rectangle(0, 0, box[2], box[3]);
                UnifiedDecoderService.DecodedResult result =
                        OCRController.getInstance().decodeRegion(regionImage, region, regionType, config);

                if (result.hasText()) {
                    entry.setFieldValue(key, result.getText());
                    fieldsPopulated++;

                    if (result.getSourceType() == RegionType.BARCODE) {
                        barcodeFields++;
                    } else {
                        textFields++;
                    }
                } else {
                    entry.setFieldValue(key, "");
                }

            } catch (Exception e) {
                logger.warn("Failed to extract region for field {}: {}", key, e.getMessage());
                entry.setFieldValue(key, "");
            }
        }

        entry.setFieldsFound(String.valueOf(fieldsPopulated));

        // Update preview with type breakdown
        if (fieldsPopulated == 0) {
            entry.setMetadataPreview("(no matching fields)");
        } else {
            String preview = fieldsPopulated + " fields";
            if (textFields > 0 && barcodeFields > 0) {
                preview += String.format(" (%d text, %d barcode)", textFields, barcodeFields);
            }
            entry.setMetadataPreview(preview);
        }

        entry.setStatus("Done");
    }

    /**
     * Process image using general OCR without position data.
     * Original behavior for templates without bounding boxes.
     */
    private void processImageWithGenericOCR(ImageProcessingEntry entry, BufferedImage labelImage,
                                             OCRConfiguration config) throws OCREngine.OCRException {
        OCRResult result = ocrEngine.processImage(labelImage, config);

        // Extract text blocks
        List<String> detectedTexts = new ArrayList<>();
        for (TextBlock block : result.getTextBlocks()) {
            if (block.getType() == TextBlock.BlockType.LINE && !block.isEmpty()) {
                detectedTexts.add(block.getText());
            }
        }
        if (detectedTexts.isEmpty()) {
            for (TextBlock block : result.getTextBlocks()) {
                if (block.getType() == TextBlock.BlockType.WORD && !block.isEmpty()) {
                    detectedTexts.add(block.getText());
                }
            }
        }

        entry.setFieldsFound(String.valueOf(detectedTexts.size()));
        entry.setDetectedTexts(detectedTexts);

        // Build metadata and populate editable field properties
        int fieldsPopulated = 0;
        for (OCRTemplate.FieldMapping mapping : currentTemplate.getFieldMappings()) {
            if (!mapping.isEnabled()) continue;

            int idx = mapping.getFieldIndex();
            String key = mapping.getMetadataKey();
            if (idx < detectedTexts.size()) {
                String value = detectedTexts.get(idx);
                entry.setFieldValue(key, value);
                fieldsPopulated++;
            } else {
                // Field not found - set empty
                entry.setFieldValue(key, "");
            }
        }

        // Update preview (for backward compatibility)
        if (fieldsPopulated == 0) {
            entry.setMetadataPreview("(no matching fields)");
        } else {
            entry.setMetadataPreview(fieldsPopulated + " fields");
        }

        entry.setStatus("Done");
    }

    private void applyAllMetadata() {
        // Count entries that have been processed successfully
        long successCount = imageEntries.stream()
                .filter(e -> "Done".equals(e.getStatus()))
                .count();

        if (successCount == 0) {
            Dialogs.showWarningNotification("No Metadata",
                    "No images have been processed successfully.");
            return;
        }

        boolean confirm = Dialogs.showConfirmDialog("Apply Metadata",
                String.format("This will apply OCR metadata to %d images.\n\n" +
                        "Any edits you made to the field values will be saved.\n\n" +
                        "Do you want to continue?", successCount));

        if (!confirm) return;

        int applied = 0;
        int failed = 0;

        for (ImageProcessingEntry entry : imageEntries) {
            if (!"Done".equals(entry.getStatus())) continue;

            // Get metadata from the entry (includes any user edits)
            Map<String, String> metadata = entry.getMetadata();
            if (metadata == null || metadata.isEmpty()) continue;

            try {
                int count = OCRMetadataManager.setMetadataBatch(
                        entry.getProjectEntry(), metadata, project);
                if (count > 0) {
                    applied++;
                    entry.setStatus("Applied");
                } else {
                    failed++;
                    entry.setStatus("Failed");
                }
            } catch (Exception e) {
                logger.error("Error applying metadata to: {}", entry.getImageName(), e);
                failed++;
                entry.setStatus("Failed");
            }
        }

        resultsTable.refresh();

        if (failed == 0) {
            Dialogs.showInfoNotification("Metadata Applied",
                    String.format("Successfully applied metadata to %d images.", applied));
        } else {
            Dialogs.showWarningNotification("Partial Success",
                    String.format("Applied metadata to %d images. %d failed.", applied, failed));
        }

        applyButton.setDisable(true);
    }

    /**
     * Entry representing an image being processed.
     */
    public static class ImageProcessingEntry {
        private final ProjectImageEntry<?> projectEntry;
        private final SimpleStringProperty status;
        private final SimpleStringProperty fieldsFound;
        private final SimpleStringProperty metadataPreview;
        private List<String> detectedTexts;
        private Map<String, String> metadata;
        // Observable properties for each field - keys are metadata keys, values are editable
        private final Map<String, SimpleStringProperty> fieldProperties = new LinkedHashMap<>();

        public ImageProcessingEntry(ProjectImageEntry<?> projectEntry) {
            this.projectEntry = projectEntry;
            this.status = new SimpleStringProperty("Pending");
            this.fieldsFound = new SimpleStringProperty("-");
            this.metadataPreview = new SimpleStringProperty("-");
        }

        public void reset() {
            status.set("Pending");
            fieldsFound.set("-");
            metadataPreview.set("-");
            detectedTexts = null;
            metadata = null;
            fieldProperties.clear();
        }

        /**
         * Gets or creates an observable property for a metadata field.
         */
        public SimpleStringProperty getFieldProperty(String key) {
            return fieldProperties.computeIfAbsent(key, k -> new SimpleStringProperty(""));
        }

        /**
         * Sets the value for a metadata field (creates property if needed).
         */
        public void setFieldValue(String key, String value) {
            getFieldProperty(key).set(value != null ? value : "");
            // Also update the metadata map
            if (metadata == null) {
                metadata = new LinkedHashMap<>();
            }
            metadata.put(key, value);
        }

        /**
         * Gets the current value for a metadata field.
         */
        public String getFieldValue(String key) {
            SimpleStringProperty prop = fieldProperties.get(key);
            return prop != null ? prop.get() : "";
        }

        public ProjectImageEntry<?> getProjectEntry() {
            return projectEntry;
        }

        public String getImageName() {
            return projectEntry.getImageName();
        }

        public String getStatus() {
            return status.get();
        }

        public void setStatus(String value) {
            Platform.runLater(() -> status.set(value));
        }

        public SimpleStringProperty statusProperty() {
            return status;
        }

        public void setFieldsFound(String value) {
            Platform.runLater(() -> fieldsFound.set(value));
        }

        public SimpleStringProperty fieldsFoundProperty() {
            return fieldsFound;
        }

        public void setMetadataPreview(String value) {
            Platform.runLater(() -> metadataPreview.set(value));
        }

        public SimpleStringProperty metadataPreviewProperty() {
            return metadataPreview;
        }

        public List<String> getDetectedTexts() {
            return detectedTexts;
        }

        public void setDetectedTexts(List<String> detectedTexts) {
            this.detectedTexts = detectedTexts;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata;
        }
    }
}
