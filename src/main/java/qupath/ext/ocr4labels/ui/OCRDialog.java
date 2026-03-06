package qupath.ext.ocr4labels.ui;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.controller.OCRController;
import qupath.ext.ocr4labels.model.BarcodeResult;
import qupath.ext.ocr4labels.model.BoundingBox;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.model.RegionType;
import qupath.ext.ocr4labels.model.TextBlock;
import qupath.ext.ocr4labels.service.UnifiedDecoderService;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.ext.ocr4labels.service.OCREngine;
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.ext.ocr4labels.utilities.MetadataKeyValidator;
import qupath.ext.ocr4labels.utilities.OCRMetadataManager;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

import javafx.stage.FileChooser;
import qupath.ext.ocr4labels.model.OCRTemplate;

import qupath.ext.ocr4labels.utilities.TextFilters;
import qupath.ext.ocr4labels.utilities.TextMatcher;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Main dialog for OCR processing and field labeling.
 * Supports navigating through all project entries and applying OCR results as metadata.
 */
public class OCRDialog {

    private static final Logger logger = LoggerFactory.getLogger(OCRDialog.class);

    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.ocr4labels.ui.strings");

    private final QuPathGUI qupath;
    private final OCREngine ocrEngine;
    private final Project<?> project;

    // Current state
    private ProjectImageEntry<?> selectedEntry;
    private BufferedImage labelImage;

    private Stage stage;
    private ImageView imageView;
    private Canvas overlayCanvas;
    private StackPane imageStack;
    private ScrollPane imageScrollPane;
    private Label noLabelLabel;
    private TableView<OCRFieldEntry> fieldsTable;
    private ObservableList<OCRFieldEntry> fieldEntries;
    private TextArea metadataPreview;
    private ProgressIndicator progressIndicator;
    private ListView<ProjectImageEntry<?>> entryListView;
    private Button runOCRButton;

    private OCRResult currentResult;
    private int selectedIndex = -1;

    // Toolbar controls for OCR settings
    private ComboBox<PSMOption> psmCombo;
    private CheckBox invertCheckBox;
    private CheckBox thresholdCheckBox;
    private Slider confSlider;

    // Region selection state
    private boolean regionSelectionMode = false;
    private double selectionStartX, selectionStartY;
    private double selectionEndX, selectionEndY;
    private boolean hasSelection = false;
    private ToggleButton selectRegionButton;
    private ComboBox<RegionType> regionTypeCombo;
    private ComboBox<String> scopeCombo;
    private Button scanButton;

    // Template state
    private OCRTemplate currentTemplate;
    private CheckBox useFixedPositionsCheckBox;

    // Previous field entries for metadata key preservation across slides
    private List<OCRFieldEntry> previousFieldEntries = new ArrayList<>();
    private int previousImageWidth = 0;
    private int previousImageHeight = 0;

    // Vocabulary matching for OCR correction
    private TextMatcher textMatcher;
    private Label vocabularyStatusLabel;
    private CheckBox ocrWeightsCheckBox;

    // Text filter checkboxes - applied before OCR results are displayed
    private final Map<TextFilters.TextFilter, CheckBox> filterCheckBoxes = new LinkedHashMap<>();

    // Dirty state tracking - prompt to save template before closing
    private boolean isDirty = false;
    private String lastSavedState = "";

    // Display range adjustment for label preview (does not affect OCR)
    private Slider displayMinSlider;
    private Slider displayMaxSlider;

    // Undo/Redo support
    private static final int MAX_UNDO_LEVELS = 50;
    private final java.util.Deque<List<FieldSnapshot>> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<List<FieldSnapshot>> redoStack = new java.util.ArrayDeque<>();
    private boolean isUndoRedoOperation = false;

    /**
     * Shows the OCR dialog for processing project entries.
     *
     * @param qupath The QuPath GUI instance
     * @param ocrEngine The OCR engine to use
     */
    public static void show(QuPathGUI qupath, OCREngine ocrEngine) {
        Project<?> project = qupath.getProject();
        if (project == null) {
            Dialogs.showWarningNotification("No Project",
                    "Please open a project first to use the OCR dialog.");
            return;
        }

        if (project.getImageList().isEmpty()) {
            Dialogs.showWarningNotification("Empty Project",
                    "The project has no images. Add images to the project first.");
            return;
        }

        OCRDialog dialog = new OCRDialog(qupath, project, ocrEngine);
        dialog.showDialog();
    }

    private OCRDialog(QuPathGUI qupath, Project<?> project, OCREngine ocrEngine) {
        this.qupath = qupath;
        this.project = project;
        this.ocrEngine = ocrEngine;
        this.fieldEntries = FXCollections.observableArrayList();
    }

    private void showDialog() {
        stage = new Stage();
        stage.setTitle(resources.getString("dialog.title"));
        stage.initOwner(qupath.getStage());
        stage.initModality(Modality.WINDOW_MODAL);

        BorderPane root = new BorderPane();
        root.setTop(createToolbar());
        root.setCenter(createMainContent());
        root.setBottom(createButtonBar());

        Scene scene = new Scene(root, OCRPreferences.getDialogWidth(), OCRPreferences.getDialogHeight());
        stage.setScene(scene);

        // Handle close request - check for unsaved changes
        stage.setOnCloseRequest(e -> {
            OCRPreferences.setDialogWidth(stage.getWidth());
            OCRPreferences.setDialogHeight(stage.getHeight());

            if (isDirty && !fieldEntries.isEmpty()) {
                // Prompt user about unsaved changes
                ButtonType saveBtn = new ButtonType("Save Template...", ButtonBar.ButtonData.YES);
                ButtonType discardBtn = new ButtonType("Discard", ButtonBar.ButtonData.NO);
                ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                var result = Dialogs.builder()
                        .title("Unsaved Changes")
                        .contentText("You have unsaved field mappings.\n\nWould you like to save them as a template before closing?")
                        .buttons(saveBtn, discardBtn, cancelBtn)
                        .showAndWait()
                        .orElse(cancelBtn);

                if (result == saveBtn) {
                    saveTemplate();
                    // If still dirty after save attempt (user cancelled save dialog), cancel close
                    if (isDirty) {
                        e.consume();
                    }
                } else if (result == cancelBtn) {
                    e.consume(); // Cancel the close
                }
                // Discard: let it close
            }
        });

        // Add keyboard shortcuts for undo/redo
        scene.setOnKeyPressed(e -> {
            if (e.isControlDown() || e.isMetaDown()) {
                switch (e.getCode()) {
                    case Z:
                        if (e.isShiftDown()) {
                            redo(); // Ctrl+Shift+Z = redo (alternative)
                        } else {
                            undo(); // Ctrl+Z = undo
                        }
                        e.consume();
                        break;
                    case Y:
                        redo(); // Ctrl+Y = redo
                        e.consume();
                        break;
                    default:
                        break;
                }
            }
        });

        stage.show();

        // Select initial entry (current image if available, otherwise none)
        selectInitialEntry();
    }

    /**
     * Selects the initial entry based on the currently open image in QuPath.
     */
    private void selectInitialEntry() {
        ImageData<?> currentImageData = qupath.getImageData();
        if (currentImageData == null) {
            // No image open - show empty state
            return;
        }

        // Find the project entry matching the current image
        var currentServer = currentImageData.getServer();
        if (currentServer == null) return;

        var currentUris = currentServer.getURIs();
        String currentUri = currentUris.isEmpty() ? null : currentUris.iterator().next().toString();
        if (currentUri == null) return;

        for (ProjectImageEntry<?> entry : project.getImageList()) {
            try {
                var entryUris = entry.getURIs();
                String entryUri = entryUris.isEmpty() ? null : entryUris.iterator().next().toString();
                if (currentUri.equals(entryUri)) {
                    entryListView.getSelectionModel().select(entry);
                    return;
                }
            } catch (Exception e) {
                // Continue searching
            }
        }
    }

    private ToolBar createToolbar() {
        // === SCAN SECTION: Unified scanning controls ===

        // Unified Scan button - primary action
        scanButton = new Button("Scan");
        scanButton.setOnAction(e -> performUnifiedScan());
        scanButton.setStyle("-fx-font-weight: bold; -fx-background-color: #4a90d9; -fx-text-fill: white; " +
                "-fx-border-color: #2d5a87; -fx-border-width: 2px; -fx-border-radius: 3px; -fx-background-radius: 3px;");
        scanButton.setTooltip(new Tooltip(
                "Scan the label image to extract content.\n\n" +
                "What gets scanned depends on Scope:\n" +
                "  Full Image: Scans the entire label\n" +
                "  Selection: Scans only the drawn rectangle\n\n" +
                "How content is decoded depends on Decode As:\n" +
                "  Try Both: Looks for barcodes first, then OCR\n" +
                "  Text: Uses OCR only\n" +
                "  Barcode: Looks for barcodes only"));

        progressIndicator = new ProgressIndicator();
        progressIndicator.setMaxSize(24, 24);
        progressIndicator.setVisible(false);

        // Scope dropdown: Full Image vs Selected Region
        Label scopeLabel = new Label("Scope:");
        scopeCombo = new ComboBox<>();
        scopeCombo.getItems().addAll("Full Image", "Selection");
        scopeCombo.setValue("Full Image");
        scopeCombo.setTooltip(new Tooltip(
                "What area to scan:\n\n" +
                "Full Image: Process the entire label\n" +
                "Selection: Process only the selected region\n" +
                "           (draw a rectangle first)"));
        scopeCombo.valueProperty().addListener((obs, old, newVal) -> updateScanButtonState());

        // Type/Decode dropdown
        Label decodeLabel = new Label("Decode As:");
        regionTypeCombo = new ComboBox<>();
        regionTypeCombo.getItems().addAll(RegionType.values());
        regionTypeCombo.setValue(RegionType.AUTO);
        regionTypeCombo.setTooltip(new Tooltip(
                "How to decode the content:\n\n" +
                "Try Both: Look for barcodes first, fall back to OCR\n" +
                "          (recommended for unknown content)\n" +
                "Text: Use OCR only (Tesseract)\n" +
                "Barcode: Look for barcodes only (ZXing)\n" +
                "         Supports QR, Code128, DataMatrix, etc."));

        // === SELECTION SECTION ===

        selectRegionButton = new ToggleButton("Draw Region");
        selectRegionButton.setTooltip(new Tooltip(
                "Click to enable drawing mode, then drag on the\n" +
                "image to select a specific area to scan.\n\n" +
                "After drawing, set Scope to 'Selection' and click Scan."));
        selectRegionButton.setOnAction(e -> {
            regionSelectionMode = selectRegionButton.isSelected();
            if (!regionSelectionMode) {
                hasSelection = false;
                drawBoundingBoxes();
            }
            // Auto-switch scope when entering selection mode
            if (regionSelectionMode) {
                scopeCombo.setValue("Selection");
            }
            updateScanButtonState();
        });

        Button clearSelectionBtn = new Button("Clear");
        clearSelectionBtn.setTooltip(new Tooltip("Clear the current selection"));
        clearSelectionBtn.setOnAction(e -> {
            hasSelection = false;
            selectRegionButton.setSelected(false);
            regionSelectionMode = false;
            scopeCombo.setValue("Full Image");
            drawBoundingBoxes();
            updateScanButtonState();
        });

        // === OCR SETTINGS SECTION ===

        // PSM Mode dropdown - Sparse Text is best default for slide labels
        Label psmLabel = new Label("Mode:");
        psmCombo = new ComboBox<>();
        psmCombo.getItems().addAll(PSMOption.values());
        psmCombo.setValue(PSMOption.SPARSE_TEXT);
        psmCombo.setTooltip(new Tooltip(
                "OCR text detection mode:\n\n" +
                "Sparse Text: Best for labels - finds text scattered across the image\n" +
                "Single Block: For labels with one paragraph of text\n" +
                "Single Line/Word: For very simple labels\n\n" +
                "Only affects text decoding, not barcode scanning."));

        // Confidence slider
        Label confLabel = new Label("Min Conf:");
        confSlider = new Slider(0, 100, OCRPreferences.getMinConfidence() * 100);
        confSlider.setPrefWidth(80);
        confSlider.setShowTickMarks(true);
        confSlider.setMajorTickUnit(50);
        confSlider.setTooltip(new Tooltip(
                "Minimum confidence for text detection:\n\n" +
                "Lower = show more text (may include errors)\n" +
                "Higher = show only confident detections\n\n" +
                "Only affects text decoding, not barcode scanning."));

        Label confValue = new Label(String.format("%.0f%%", confSlider.getValue()));
        confSlider.valueProperty().addListener((obs, old, newVal) -> {
                confValue.setText(String.format("%.0f%%", newVal.doubleValue()));
                OCRPreferences.setMinConfidence(newVal.doubleValue() / 100.0);
        });

        // Preprocessing options
        invertCheckBox = new CheckBox("Invert");
        invertCheckBox.setTooltip(new Tooltip(
                "Flip dark and light colors.\n\n" +
                "Enable for light text on dark background.\n" +
                "Affects both text and barcode scanning."));

        thresholdCheckBox = new CheckBox("Enhance");
        thresholdCheckBox.setSelected(true);
        thresholdCheckBox.setTooltip(new Tooltip(
                "Improve image contrast before scanning.\n\n" +
                "Helps with faded labels or poor lighting.\n" +
                "Usually best to leave enabled."));

        // Keep reference for backward compatibility
        runOCRButton = scanButton;

        return new ToolBar(
                // Scan section
                scanButton,
                progressIndicator,
                new Separator(),
                scopeLabel,
                scopeCombo,
                decodeLabel,
                regionTypeCombo,
                new Separator(),
                // Selection section
                selectRegionButton,
                clearSelectionBtn,
                new Separator(),
                // OCR Settings section
                psmLabel,
                psmCombo,
                confLabel,
                confSlider,
                confValue,
                invertCheckBox,
                thresholdCheckBox
        );
    }

    private SplitPane createMainContent() {
        SplitPane mainSplit = new SplitPane();

        // Left panel: Project entry list
        VBox entryListPanel = createEntryListPanel();

        // Right panel: Image and Fields stacked vertically
        SplitPane rightSplit = new SplitPane();
        rightSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);

        VBox imagePanel = createImagePanel();
        VBox fieldsPanel = createFieldsPanel();

        rightSplit.getItems().addAll(imagePanel, fieldsPanel);
        rightSplit.setDividerPositions(0.55);

        mainSplit.getItems().addAll(entryListPanel, rightSplit);
        mainSplit.setDividerPositions(0.2);

        return mainSplit;
    }

    /**
     * Creates the project entry list panel.
     */
    private VBox createEntryListPanel() {
        VBox panel = new VBox(5);
        panel.setPadding(new Insets(10));
        panel.setMinWidth(150);
        panel.setPrefWidth(200);

        Label titleLabel = new Label("Project Images");
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Create ListView with project entries
        entryListView = new ListView<>();
        entryListView.getItems().addAll(project.getImageList());

        // Custom cell factory to show entry names
        entryListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ProjectImageEntry<?> entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    String name = entry.getImageName();
                    setText(name);
                    setTooltip(new Tooltip(name));
                }
            }
        });

        // Handle selection changes with dirty state check
        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, oldEntry, newEntry) -> {
            if (newEntry != null && newEntry != oldEntry) {
                if (isDirty && !fieldEntries.isEmpty()) {
                    // Prompt before switching
                    ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.YES);
                    ButtonType discardBtn = new ButtonType("Discard", ButtonBar.ButtonData.NO);
                    ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                    var result = Dialogs.builder()
                            .title("Unsaved Changes")
                            .contentText("Save current field mappings before switching images?")
                            .buttons(saveBtn, discardBtn, cancelBtn)
                            .showAndWait()
                            .orElse(cancelBtn);

                    if (result == saveBtn) {
                        saveTemplate();
                    } else if (result == cancelBtn) {
                        // Revert selection - need to do this on next frame to avoid listener recursion
                        Platform.runLater(() -> entryListView.getSelectionModel().select(oldEntry));
                        return;
                    }
                    // Discard or saved - continue with switch
                }
                // Reset dirty state and undo history for new image
                isDirty = false;
                lastSavedState = "";
                clearUndoHistory();
                if (stage != null) {
                    String title = stage.getTitle();
                    if (title.endsWith(" *")) {
                        stage.setTitle(title.substring(0, title.length() - 2));
                    }
                }
                onEntrySelected(newEntry);
            }
        });

        VBox.setVgrow(entryListView, Priority.ALWAYS);

        // Entry count label
        Label countLabel = new Label(String.format("%d images", project.getImageList().size()));
        countLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        panel.getChildren().addAll(titleLabel, entryListView, countLabel);
        return panel;
    }

    /**
     * Handles selection of a project entry from the list.
     */
    private void onEntrySelected(ProjectImageEntry<?> entry) {
        selectedEntry = entry;

        // Save current field entries and image dimensions for metadata key preservation
        if (!fieldEntries.isEmpty() && labelImage != null) {
            previousFieldEntries = new ArrayList<>(fieldEntries);
            previousImageWidth = labelImage.getWidth();
            previousImageHeight = labelImage.getHeight();
            logger.info("Saved {} field entries from previous image ({}x{}) for key preservation",
                    previousFieldEntries.size(), previousImageWidth, previousImageHeight);
            for (OCRFieldEntry e : previousFieldEntries) {
                BoundingBox b = e.getBoundingBox();
                if (b != null) {
                    logger.info("  - '{}' at ({}, {}, {}, {})",
                            e.getMetadataKey(), b.getX(), b.getY(), b.getWidth(), b.getHeight());
                }
            }
        } else {
            logger.info("No field entries to save (entries={}, labelImage={})",
                    fieldEntries.size(), labelImage != null ? "present" : "null");
        }

        // Clear detected fields but preserve region selection
        fieldEntries.clear();
        currentResult = null;

        // Load label image for the entry
        loadLabelImageForEntry(entry);

        // Update UI
        updateMetadataPreview();
        drawBoundingBoxes();

        // Auto-run OCR if enabled and we have a label image
        if (labelImage != null && OCRPreferences.isAutoRunOnEntrySwitch()) {
            Platform.runLater(this::runOCR);
        }
    }

    /**
     * Loads the label image for a project entry.
     */
    private void loadLabelImageForEntry(ProjectImageEntry<?> entry) {
        try {
            ImageData<?> imageData = entry.readImageData();
            if (imageData != null && LabelImageUtility.isLabelImageAvailable(imageData)) {
                labelImage = LabelImageUtility.retrieveLabelImage(imageData);
                updateImageDisplay();
            } else {
                labelImage = null;
                showNoLabelPlaceholder();
            }
        } catch (IOException e) {
            logger.error("Failed to load image data for entry: {}", entry.getImageName(), e);
            labelImage = null;
            showNoLabelPlaceholder();
        }
    }

    /**
     * Updates the image display with the current label image.
     */
    private void updateImageDisplay() {
        if (labelImage != null) {
            updateDisplayImage();
            imageView.setVisible(true);
            overlayCanvas.setVisible(true);
            noLabelLabel.setVisible(false);
            // Fit image to pane after a short delay to allow layout
            Platform.runLater(() -> {
                fitImageToPane();
                drawBoundingBoxes();
            });
        } else {
            showNoLabelPlaceholder();
        }
    }

    /**
     * Updates the displayed image with the current min/max display range.
     * Does NOT modify the original labelImage used for OCR.
     */
    private void updateDisplayImage() {
        if (labelImage == null) return;

        int min = (int) displayMinSlider.getValue();
        int max = (int) displayMaxSlider.getValue();

        // Avoid division by zero
        if (max <= min) max = min + 1;

        int w = labelImage.getWidth();
        int h = labelImage.getHeight();

        // If range is full (0-255), just show the original
        if (min == 0 && max >= 255) {
            imageView.setImage(SwingFXUtils.toFXImage(labelImage, null));
            return;
        }

        // Build lookup table for speed
        int[] lut = new int[256];
        double scale = 255.0 / (max - min);
        for (int i = 0; i < 256; i++) {
            int val = (int) ((i - min) * scale);
            lut[i] = Math.max(0, Math.min(255, val));
        }

        // Apply lookup table to create display image
        WritableImage displayImage = new WritableImage(w, h);
        PixelWriter pw = displayImage.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = labelImage.getRGB(x, y);
                int r = lut[(rgb >> 16) & 0xFF];
                int g = lut[(rgb >> 8) & 0xFF];
                int b = lut[rgb & 0xFF];
                int a = (rgb >> 24) & 0xFF;
                pw.setArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        imageView.setImage(displayImage);
    }

    /**
     * Auto-adjusts the display range based on image histogram.
     * Sets min/max to the 2nd and 98th percentile of pixel values.
     */
    private void autoAdjustDisplayRange() {
        if (labelImage == null) return;

        int w = labelImage.getWidth();
        int h = labelImage.getHeight();
        int totalPixels = w * h;

        // Build histogram from grayscale values
        int[] histogram = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = labelImage.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;
                histogram[gray]++;
            }
        }

        // Find 2nd and 98th percentile
        int lowTarget = (int) (totalPixels * 0.02);
        int highTarget = (int) (totalPixels * 0.98);

        int cumulative = 0;
        int minVal = 0;
        for (int i = 0; i < 256; i++) {
            cumulative += histogram[i];
            if (cumulative >= lowTarget) {
                minVal = i;
                break;
            }
        }

        cumulative = 0;
        int maxVal = 255;
        for (int i = 0; i < 256; i++) {
            cumulative += histogram[i];
            if (cumulative >= highTarget) {
                maxVal = i;
                break;
            }
        }

        // Ensure at least some range
        if (maxVal - minVal < 10) {
            minVal = Math.max(0, minVal - 5);
            maxVal = Math.min(255, maxVal + 5);
        }

        logger.info("Auto display range: {}-{}", minVal, maxVal);
        displayMinSlider.setValue(minVal);
        displayMaxSlider.setValue(maxVal);
    }

    /**
     * Fits the image to the scroll pane while maintaining aspect ratio.
     * Ensures the entire image is visible without scrolling.
     */
    private void fitImageToPane() {
        if (labelImage == null || imageScrollPane == null) return;

        double paneWidth = imageScrollPane.getViewportBounds().getWidth();
        double paneHeight = imageScrollPane.getViewportBounds().getHeight();

        // If viewport not yet sized, use scroll pane dimensions with padding
        if (paneWidth <= 0) paneWidth = imageScrollPane.getWidth() - 20;
        if (paneHeight <= 0) paneHeight = imageScrollPane.getHeight() - 20;

        if (paneWidth <= 0 || paneHeight <= 0) return;

        double imgWidth = labelImage.getWidth();
        double imgHeight = labelImage.getHeight();

        // Calculate scale to fit both dimensions
        double scaleX = paneWidth / imgWidth;
        double scaleY = paneHeight / imgHeight;
        double scale = Math.min(scaleX, scaleY);

        // Apply scaled dimensions
        imageView.setFitWidth(imgWidth * scale);
        imageView.setFitHeight(imgHeight * scale);
    }

    /**
     * Shows a placeholder when no label image is available.
     */
    private void showNoLabelPlaceholder() {
        imageView.setImage(null);
        imageView.setVisible(false);
        overlayCanvas.setVisible(false);
        noLabelLabel.setVisible(true);
    }

    private VBox createImagePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label titleLabel = new Label(resources.getString("label.image"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Image display with overlay
        imageStack = new StackPane();

        imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // Placeholder for no label
        noLabelLabel = new Label("No Label Found\n\nSelect an image from the list,\nor this image has no label.");
        noLabelLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: gray; -fx-text-alignment: center;");
        noLabelLabel.setVisible(true);

        // Overlay canvas for bounding boxes and selection
        overlayCanvas = new Canvas();
        overlayCanvas.setVisible(false);

        // Mouse handlers for region selection and refresh
        overlayCanvas.setOnMousePressed(e -> {
            if (regionSelectionMode) {
                selectionStartX = e.getX();
                selectionStartY = e.getY();
                selectionEndX = e.getX();
                selectionEndY = e.getY();
                hasSelection = false;
                drawBoundingBoxes();
            } else {
                drawBoundingBoxes();
            }
        });

        overlayCanvas.setOnMouseDragged(e -> {
            if (regionSelectionMode) {
                selectionEndX = e.getX();
                selectionEndY = e.getY();
                hasSelection = true;
                drawBoundingBoxes();
            }
        });

        overlayCanvas.setOnMouseReleased(e -> {
            if (regionSelectionMode && hasSelection) {
                selectionEndX = e.getX();
                selectionEndY = e.getY();
                double width = Math.abs(selectionEndX - selectionStartX);
                double height = Math.abs(selectionEndY - selectionStartY);
                if (width < 5 || height < 5) {
                    hasSelection = false;
                    drawBoundingBoxes();
                    updateScanButtonState();
                } else {
                    // Valid selection - show context menu
                    drawBoundingBoxes();
                    updateScanButtonState();
                    showRegionContextMenu(e.getScreenX(), e.getScreenY());
                }
            }
        });

        imageStack.getChildren().addAll(noLabelLabel, imageView, overlayCanvas);

        // Bind canvas size to image size
        imageView.fitWidthProperty().addListener((obs, old, newVal) -> updateCanvasSize());
        imageView.fitHeightProperty().addListener((obs, old, newVal) -> updateCanvasSize());

        imageScrollPane = new ScrollPane(imageStack);
        imageScrollPane.setFitToWidth(true);
        imageScrollPane.setFitToHeight(true);
        VBox.setVgrow(imageScrollPane, Priority.ALWAYS);

        // Auto-refresh bounding boxes when scroll pane is resized
        imageScrollPane.widthProperty().addListener((obs, old, newVal) -> {
            fitImageToPane();
            Platform.runLater(this::drawBoundingBoxes);
        });
        imageScrollPane.heightProperty().addListener((obs, old, newVal) -> {
            fitImageToPane();
            Platform.runLater(this::drawBoundingBoxes);
        });

        // Zoom controls
        HBox zoomControls = new HBox(10);
        zoomControls.setAlignment(Pos.CENTER_LEFT);

        Button fitButton = new Button("Fit");
        fitButton.setOnAction(e -> {
            fitImageToPane();
            Platform.runLater(this::drawBoundingBoxes);
        });

        Button actualButton = new Button("100%");
        actualButton.setOnAction(e -> {
            if (labelImage != null) {
                imageView.setFitWidth(labelImage.getWidth());
                imageView.setFitHeight(labelImage.getHeight());
            }
            Platform.runLater(this::drawBoundingBoxes);
        });

        zoomControls.getChildren().addAll(fitButton, actualButton);

        // Color legend for bounding boxes
        HBox legendBox = new HBox(15);
        legendBox.setAlignment(Pos.CENTER_LEFT);
        legendBox.setPadding(new Insets(2, 0, 0, 0));

        Label legendLabel = new Label("Box colors:");
        legendLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        Label textLegend = new Label("Text");
        textLegend.setStyle("-fx-font-size: 10px; -fx-text-fill: #32CD32; -fx-font-weight: bold;"); // LIME

        Label barcodeLegend = new Label("Barcode");
        barcodeLegend.setStyle("-fx-font-size: 10px; -fx-text-fill: #1E90FF; -fx-font-weight: bold;"); // DODGERBLUE

        Label autoLegend = new Label("Try Both");
        autoLegend.setStyle("-fx-font-size: 10px; -fx-text-fill: #BA55D3; -fx-font-weight: bold;"); // MEDIUMPURPLE

        Label selectedLegend = new Label("Selected");
        selectedLegend.setStyle("-fx-font-size: 10px; -fx-text-fill: #FFD700; -fx-font-weight: bold;"); // YELLOW/GOLD

        legendBox.getChildren().addAll(legendLabel, textLegend, barcodeLegend, autoLegend, selectedLegend);

        // Combine zoom and legend in one row
        HBox controlsRow = new HBox(20);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        controlsRow.getChildren().addAll(zoomControls, legendBox);

        // Display range controls for label preview (min/max windowing)
        HBox bcControls = new HBox(8);
        bcControls.setAlignment(Pos.CENTER_LEFT);

        Label minLabel = new Label("Min:");
        minLabel.setStyle("-fx-font-size: 11px;");
        displayMinSlider = new Slider(0, 255, 0);
        displayMinSlider.setPrefWidth(90);
        displayMinSlider.setTooltip(new Tooltip(
                "Set the minimum display value (black point).\n" +
                "Pixels at or below this become black.\n" +
                "Does not affect OCR processing."));

        Label maxLabel = new Label("Max:");
        maxLabel.setStyle("-fx-font-size: 11px;");
        displayMaxSlider = new Slider(0, 255, 255);
        displayMaxSlider.setPrefWidth(90);
        displayMaxSlider.setTooltip(new Tooltip(
                "Set the maximum display value (white point).\n" +
                "Pixels at or above this become white.\n" +
                "Does not affect OCR processing."));

        Label rangeValueLabel = new Label("0-255");
        rangeValueLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // Update display when sliders change
        displayMinSlider.valueProperty().addListener((obs, old, val) -> {
            rangeValueLabel.setText(String.format("%.0f-%.0f",
                    displayMinSlider.getValue(), displayMaxSlider.getValue()));
            updateDisplayImage();
        });
        displayMaxSlider.valueProperty().addListener((obs, old, val) -> {
            rangeValueLabel.setText(String.format("%.0f-%.0f",
                    displayMinSlider.getValue(), displayMaxSlider.getValue()));
            updateDisplayImage();
        });

        Button autoButton = new Button("Auto");
        autoButton.setStyle("-fx-font-size: 10px;");
        autoButton.setTooltip(new Tooltip(
                "Auto-adjust display range based on image content.\n" +
                "Sets min/max to the 2nd and 98th percentile of pixel values."));
        autoButton.setOnAction(e -> autoAdjustDisplayRange());

        Button resetBCButton = new Button("Reset");
        resetBCButton.setStyle("-fx-font-size: 10px;");
        resetBCButton.setTooltip(new Tooltip("Reset display range to full (0-255)"));
        resetBCButton.setOnAction(e -> {
            displayMinSlider.setValue(0);
            displayMaxSlider.setValue(255);
        });

        bcControls.getChildren().addAll(
                minLabel, displayMinSlider,
                maxLabel, displayMaxSlider,
                rangeValueLabel, autoButton, resetBCButton);

        panel.getChildren().addAll(titleLabel, imageScrollPane, controlsRow, bcControls);
        return panel;
    }

    private VBox createFieldsPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));

        Label titleLabel = new Label(resources.getString("label.detectedFields"));
        titleLabel.setStyle("-fx-font-weight: bold;");

        // Fields table
        fieldsTable = new TableView<>(fieldEntries);
        fieldsTable.setEditable(true);
        fieldsTable.setPlaceholder(new Label("Click Scan to detect text and barcodes"));
        fieldsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Field number column - matches numbers displayed on bounding boxes
        TableColumn<OCRFieldEntry, String> numCol = new TableColumn<>("#");
        numCol.setCellValueFactory(data -> {
            int index = fieldEntries.indexOf(data.getValue()) + 1;
            return new SimpleStringProperty(String.valueOf(index));
        });
        numCol.setMinWidth(30);
        numCol.setMaxWidth(40);
        numCol.setPrefWidth(35);
        numCol.setSortable(false);

        // Text column - flexible width, user can resize
        TableColumn<OCRFieldEntry, String> textCol = new TableColumn<>(resources.getString("column.text"));
        textCol.setCellValueFactory(data -> data.getValue().textProperty());
        textCol.setCellFactory(TextFieldTableCell.forTableColumn());
        textCol.setOnEditCommit(e -> {
            saveStateForUndo();
            e.getRowValue().setText(e.getNewValue());
            updateMetadataPreview();
        });
        textCol.setMinWidth(80);
        textCol.setPrefWidth(150);

        // Decode As column - ComboBox for region type selection
        TableColumn<OCRFieldEntry, RegionType> typeCol = new TableColumn<>("Decode As");
        typeCol.setCellValueFactory(data -> data.getValue().regionTypeProperty());
        typeCol.setCellFactory(col -> new RegionTypeCell());
        typeCol.setMinWidth(80);
        typeCol.setMaxWidth(100);
        typeCol.setPrefWidth(90);

        // Metadata key column - flexible width, user can resize
        TableColumn<OCRFieldEntry, String> keyCol = new TableColumn<>(resources.getString("column.metadataKey"));
        keyCol.setCellValueFactory(data -> data.getValue().metadataKeyProperty());
        keyCol.setCellFactory(col -> new MetadataKeyCell());
        keyCol.setOnEditCommit(e -> {
            String newKey = e.getNewValue();
            MetadataKeyValidator.ValidationResult validation = MetadataKeyValidator.validateKey(newKey);
            if (validation.isValid()) {
                saveStateForUndo();
                e.getRowValue().setMetadataKey(newKey);
            } else {
                Dialogs.showWarningNotification("Invalid Key", validation.getErrorMessage());
                fieldsTable.refresh();
            }
            updateMetadataPreview();
        });
        keyCol.setMinWidth(100);
        keyCol.setPrefWidth(150);

        // Confidence column - fixed width (always shows e.g. "93%")
        TableColumn<OCRFieldEntry, String> confCol = new TableColumn<>("Conf");
        confCol.setCellValueFactory(data -> new SimpleStringProperty(
                String.format("%.0f%%", data.getValue().getConfidence() * 100)));
        confCol.setMinWidth(50);
        confCol.setMaxWidth(60);
        confCol.setPrefWidth(55);

        // Column order: #, Decode As, Text, Metadata Key, Confidence
        fieldsTable.getColumns().addAll(numCol, typeCol, textCol, keyCol, confCol);
        VBox.setVgrow(fieldsTable, Priority.ALWAYS);

        // Handle selection
        fieldsTable.getSelectionModel().selectedIndexProperty().addListener((obs, old, newVal) -> {
            selectedIndex = newVal.intValue();
            drawBoundingBoxes();
        });

        // Button bar for field actions
        HBox buttonBar = new HBox(10);
        buttonBar.setAlignment(Pos.CENTER_LEFT);

        Button addButton = new Button(resources.getString("button.addField"));
        addButton.setOnAction(e -> addManualField());

        Button removeButton = new Button("Remove");
        removeButton.setTooltip(new Tooltip("Remove the selected field from the list"));
        removeButton.setOnAction(e -> {
            OCRFieldEntry selected = fieldsTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                saveStateForUndo();
                fieldEntries.remove(selected);
                updateMetadataPreview();
                drawBoundingBoxes();
            }
        });
        // Disable when nothing is selected
        removeButton.disableProperty().bind(
                fieldsTable.getSelectionModel().selectedItemProperty().isNull());

        Button clearButton = new Button(resources.getString("button.clearAll"));
        clearButton.setOnAction(e -> {
            if (!fieldEntries.isEmpty()) {
                saveStateForUndo();
                fieldEntries.clear();
                updateMetadataPreview();
                drawBoundingBoxes();
            }
        });

        buttonBar.getChildren().addAll(addButton, removeButton, clearButton);

        // Text filter toolbar
        HBox filterBar = createFilterBar();

        // Template toolbar
        HBox templateBar = new HBox(10);
        templateBar.setAlignment(Pos.CENTER_LEFT);

        Button saveTemplateBtn = new Button("Save Template...");
        saveTemplateBtn.setTooltip(new Tooltip(
                "Save current field positions and metadata keys to a template file.\n" +
                "Templates can be reused to process similar labels automatically."));
        saveTemplateBtn.setOnAction(e -> saveTemplate());

        Button loadTemplateBtn = new Button("Load Template...");
        loadTemplateBtn.setTooltip(new Tooltip(
                "Load a saved template to apply its field positions and metadata keys.\n" +
                "Use 'Apply Template' to extract text from the loaded positions."));
        loadTemplateBtn.setOnAction(e -> loadTemplate());

        Button applyTemplateBtn = new Button("Apply Template");
        applyTemplateBtn.setTooltip(new Tooltip(
                "Run OCR using the loaded template's fixed positions.\n" +
                "Each field will be extracted from its saved location with 20% dilation."));
        applyTemplateBtn.setOnAction(e -> applyTemplateToCurrentImage());
        applyTemplateBtn.setDisable(true);

        // Enable apply template button when template is loaded
        useFixedPositionsCheckBox = new CheckBox("Use Fixed Positions");
        useFixedPositionsCheckBox.setTooltip(new Tooltip(
                "When enabled, uses saved bounding box positions from the template\n" +
                "instead of running OCR detection. Each box is dilated 20% to\n" +
                "account for slight variations in label positioning."));
        useFixedPositionsCheckBox.setDisable(true);
        useFixedPositionsCheckBox.selectedProperty().addListener((obs, old, newVal) -> {
            applyTemplateBtn.setDisable(!newVal || currentTemplate == null);
        });

        templateBar.getChildren().addAll(saveTemplateBtn, loadTemplateBtn,
                new Separator(javafx.geometry.Orientation.VERTICAL),
                useFixedPositionsCheckBox, applyTemplateBtn);

        // Metadata preview
        Label previewLabel = new Label(resources.getString("label.metadataPreview"));
        previewLabel.setStyle("-fx-font-weight: bold;");

        metadataPreview = new TextArea();
        metadataPreview.setEditable(false);
        metadataPreview.setPrefRowCount(3);
        metadataPreview.setStyle("-fx-font-family: monospace;");

        panel.getChildren().addAll(titleLabel, fieldsTable, buttonBar, filterBar, templateBar, previewLabel, metadataPreview);
        return panel;
    }

    /**
     * Creates the filter bar with checkboxes for text filtering operations.
     * Filters are applied automatically when OCR results are displayed and
     * re-applied when checkboxes are toggled.
     *
     * Blue checkboxes = "Keep" filters (keep specific character types)
     * Red checkboxes = "Remove/Transform" filters (remove or replace characters)
     */
    private HBox createFilterBar() {
        HBox filterBar = new HBox(3);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(2, 0, 2, 0));

        Label filterLabel = new Label("Text Filters:");
        filterLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        filterLabel.setTooltip(new Tooltip(
                "Text post-processing filters (apply to OCR text only).\n\n" +
                "Filters are applied automatically when text is detected.\n" +
                "Toggle filters on/off to re-filter the displayed text.\n\n" +
                "Blue = Keep specific character types\n" +
                "Red = Remove or transform characters"));

        // Create filter checkboxes with color coding
        // Blue = "keep" filters, Red = "remove/transform" filters
        filterCheckBoxes.clear();

        // Keep filters (blue) - these keep specific character types
        String keepStyle = "-fx-font-size: 10px; -fx-text-fill: #0066cc;";
        // Remove/transform filters (red) - these remove or replace characters
        String removeStyle = "-fx-font-size: 10px; -fx-text-fill: #cc3300;";

        for (TextFilters.TextFilter filter : TextFilters.ALL_FILTERS) {
            CheckBox cb = new CheckBox(filter.getButtonLabel());

            // Determine if this is a "keep" or "remove" filter based on name
            boolean isKeepFilter = filter.getName().contains("Only") ||
                                   filter.getName().equals("Alphanumeric") ||
                                   filter.getName().equals("Filename Safe");

            cb.setStyle(isKeepFilter ? keepStyle : removeStyle);
            cb.setTooltip(new Tooltip(filter.getTooltip()));
            cb.selectedProperty().addListener((obs, old, selected) -> reapplyFilters());
            filterCheckBoxes.put(filter, cb);

            // Wrap checkbox in a container with right margin for spacing
            HBox cbContainer = new HBox(cb);
            cbContainer.setPadding(new Insets(0, 8, 0, 0));
            filterBar.getChildren().add(cbContainer);
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
                "After filtering, click 'Match' to correct OCR mistakes by finding the\n" +
                "closest match from your list.\n\n" +
                "Example: If your list contains 'Sample_001' and OCR detected 'Samp1e_0O1',\n" +
                "the matcher will correct it to 'Sample_001'.\n\n" +
                "File format:\n" +
                "  - CSV: Uses first column (header row auto-skipped)\n" +
                "  - TSV/TXT: Uses first column or whole line\n" +
                "  - One valid value per line\n\n" +
                "Uses Levenshtein distance with OCR-aware weighting\n" +
                "(0/O, 1/l/I confusions have lower penalty)."));

        Button matchBtn = new Button("Match");
        matchBtn.setStyle("-fx-font-size: 10px; -fx-padding: 2 6 2 6;");
        matchBtn.setDisable(true); // Enabled when vocabulary is loaded
        matchBtn.setTooltip(new Tooltip("Match detected text against loaded vocabulary to correct OCR errors"));
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
        // Use gray keyword instead of hex for dark mode compatibility
        vocabularyStatusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        // Store match button reference for enabling/disabling
        loadVocabBtn.setUserData(matchBtn);

        filterBar.getChildren().addAll(loadVocabBtn, helpLabel, ocrWeightsCheckBox, matchBtn, vocabularyStatusLabel);

        return filterBar;
    }

    /**
     * Applies all currently selected (checked) filters to the given text.
     * Filters are applied in order as displayed in the filter bar.
     *
     * @param text The original text to filter
     * @return The filtered text
     */
    private String applyActiveFilters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        for (Map.Entry<TextFilters.TextFilter, CheckBox> entry : filterCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                result = entry.getKey().apply(result);
            }
        }
        return result;
    }

    /**
     * Re-applies active filters to all field entries.
     * Called when filter checkboxes are toggled.
     * Uses the original OCR text stored in each entry and applies current filter selection.
     */
    private void reapplyFilters() {
        if (fieldEntries.isEmpty()) {
            return;
        }

        for (OCRFieldEntry entry : fieldEntries) {
            String original = entry.getOriginalText();
            String filtered = applyActiveFilters(original);
            entry.setText(filtered);
        }

        fieldsTable.refresh();
        updateMetadataPreview();
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

            // Enable the Match button - find it in the filter bar
            // The Match button is after the help label in the filter bar
            for (javafx.scene.Node node : ((HBox) vocabularyStatusLabel.getParent()).getChildren()) {
                if (node instanceof Button && "Match".equals(((Button) node).getText())) {
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
     * Applies vocabulary matching to correct OCR errors in detected text fields.
     * Uses fuzzy matching (Levenshtein distance) to find the closest match
     * from the loaded vocabulary for each detected text value.
     */
    private void applyVocabularyMatching() {
        if (textMatcher == null || !textMatcher.hasVocabulary()) {
            Dialogs.showWarningNotification("No Vocabulary",
                    "Please load a vocabulary file first using 'Load List...'");
            return;
        }

        if (fieldEntries.isEmpty()) {
            Dialogs.showWarningNotification("No Fields",
                    "Run OCR first to detect text fields.");
            return;
        }

        // Apply OCR weights setting from checkbox
        textMatcher.setUseOCRWeights(ocrWeightsCheckBox.isSelected());

        int corrected = 0;
        int exact = 0;
        int noMatch = 0;
        StringBuilder report = new StringBuilder();

        for (OCRFieldEntry entry : fieldEntries) {
            String original = entry.getText();
            if (original == null || original.isEmpty()) {
                noMatch++;
                continue;
            }

            TextMatcher.MatchResult match = textMatcher.findBestMatch(original);

            if (match == null) {
                noMatch++;
                report.append(String.format("  '%s' -> (no match found)\n", original));
            } else if (match.isExactMatch()) {
                exact++;
            } else {
                // Found a fuzzy match - apply correction
                entry.setText(match.getMatchedValue());
                corrected++;
                report.append(String.format("  '%s' -> '%s' (%.0f%% similar)\n",
                        original, match.getMatchedValue(), match.getSimilarity() * 100));
            }
        }

        if (corrected > 0) {
            fieldsTable.refresh();
            updateMetadataPreview();
        }

        // Log what mode was used
        String modeInfo = ocrWeightsCheckBox.isSelected() ? "OCR-weighted" : "standard";
        logger.info("Vocabulary matching ({}): corrected={}, exact={}, noMatch={}",
                modeInfo, corrected, exact, noMatch);

        if (corrected > 0) {
            Dialogs.showInfoNotification("Matching Complete",
                    String.format("Corrected %d field(s) using %s matching.", corrected, modeInfo));
        } else if (exact > 0) {
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
        buttonBar.setPadding(new Insets(10));
        buttonBar.setAlignment(Pos.CENTER_RIGHT);

        // Show selected entry name
        Label selectedLabel = new Label();
        selectedLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: gray;");
        entryListView.getSelectionModel().selectedItemProperty().addListener((obs, old, newVal) -> {
            if (newVal != null) {
                selectedLabel.setText("Applying to: " + newVal.getImageName());
            } else {
                selectedLabel.setText("");
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button applyButton = new Button(resources.getString("button.apply"));
        applyButton.setDefaultButton(true);
        applyButton.setOnAction(e -> applyMetadata());

        Button cancelButton = new Button(resources.getString("button.cancel"));
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        buttonBar.getChildren().addAll(selectedLabel, spacer, applyButton, cancelButton);
        return buttonBar;
    }

    /**
     * Updates the scan button state based on current scope and selection.
     */
    private void updateScanButtonState() {
        boolean selectionScope = "Selection".equals(scopeCombo.getValue());
        boolean canScan = labelImage != null && (!selectionScope || hasSelection);
        scanButton.setDisable(!canScan);

        // Update button text to hint at action
        if (selectionScope && !hasSelection) {
            scanButton.setText("Scan (draw region first)");
        } else {
            scanButton.setText("Scan");
        }
    }

    /**
     * Performs unified scanning based on current scope and decode type settings.
     * Consolidates "Run OCR", "Scan Region", and "Find Barcodes" into a single operation.
     */
    private void performUnifiedScan() {
        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "Please select an image with a label first.");
            return;
        }

        String scope = scopeCombo.getValue();
        RegionType decodeType = regionTypeCombo.getValue();

        if ("Selection".equals(scope)) {
            if (!hasSelection) {
                Dialogs.showWarningNotification("No Selection",
                        "Please draw a rectangle on the image first,\nor change Scope to 'Full Image'.");
                return;
            }
            // Scan selected region
            scanSelectedRegion();
        } else {
            // Full image scan
            if (decodeType == RegionType.BARCODE) {
                // Barcode-only scan of full image
                autoDetectBarcodes();
            } else if (decodeType == RegionType.AUTO) {
                // Auto mode: try barcodes first, then OCR
                performAutoFullImageScan();
            } else {
                // Text-only scan (traditional OCR)
                runOCR();
            }
        }
    }

    /**
     * Performs AUTO mode scanning on full image: barcodes first, then OCR.
     */
    private void performAutoFullImageScan() {
        if (labelImage == null) return;

        logger.info("Starting AUTO mode full image scan: barcodes then OCR");
        progressIndicator.setVisible(true);

        BufferedImage scanImage = preprocessForOCR(labelImage);

        // First try barcode detection
        OCRController.getInstance().decodeBarcodeAsync(scanImage)
                .thenAccept(barcodeResult -> {
                    if (barcodeResult.hasBarcode()) {
                        // Found barcodes - add them and then run OCR for any text
                        Platform.runLater(() -> {
                            addAutoDetectedBarcodes(barcodeResult);
                            logger.info("AUTO: Found {} barcode(s), now running OCR",
                                    barcodeResult.getBarcodeCount());
                        });
                    }
                    // Also run OCR to find text
                    runOCRAfterBarcodes(barcodeResult.hasBarcode());
                })
                .exceptionally(ex -> {
                    // Barcode failed, just run OCR
                    logger.debug("Barcode detection failed, running OCR only: {}", ex.getMessage());
                    runOCRAfterBarcodes(false);
                    return null;
                });
    }

    /**
     * Runs OCR as part of AUTO mode scanning after barcode detection.
     */
    private void runOCRAfterBarcodes(boolean foundBarcodes) {
        PSMOption selectedPSM = psmCombo.getValue();
        OCRConfiguration.PageSegMode psm = selectedPSM != null ? selectedPSM.getMode() : OCRConfiguration.PageSegMode.AUTO;

        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(psm)
                .language(OCRPreferences.getLanguage())
                .minConfidence(confSlider.getValue() / 100.0)
                .autoRotate(OCRPreferences.isAutoRotate())
                .detectOrientation(OCRPreferences.isDetectOrientation())
                .enhanceContrast(thresholdCheckBox.isSelected())
                .enablePreprocessing(true)
                .build();

        BufferedImage imageToProcess = preprocessForOCR(labelImage);

        OCRController.getInstance().performOCRAsync(imageToProcess, config)
                .thenAccept(result -> Platform.runLater(() -> {
                    // Don't clear existing barcodes - add OCR results
                    int barcodeCount = (int) fieldEntries.stream()
                            .filter(e -> e.getRegionType() == RegionType.BARCODE)
                            .count();

                    if (foundBarcodes) {
                        // Append OCR results to existing barcode results
                        addOCRResultsWithoutClearing(result);
                    } else {
                        currentResult = result;
                        populateFieldsTable(result);
                    }

                    drawBoundingBoxes();
                    progressIndicator.setVisible(false);

                    long displayCount = result.getDisplayBlockCount();
                    String modeInfo = selectedPSM != null ? selectedPSM.toString() : "Auto";
                    if (foundBarcodes) {
                        Dialogs.showInfoNotification("Auto Scan Complete",
                                String.format("Found %d barcode(s) + %d text region(s)",
                                        barcodeCount, displayCount));
                    } else {
                        Dialogs.showInfoNotification("Scan Complete",
                                String.format("Detected %d text region(s) (Mode: %s)",
                                        displayCount, modeInfo));
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        if (foundBarcodes) {
                            Dialogs.showInfoNotification("Partial Scan Complete",
                                    "Found barcodes but OCR failed: " + ex.getMessage());
                        } else {
                            Dialogs.showErrorMessage("Scan Failed", ex.getMessage());
                        }
                    });
                    return null;
                });
    }

    /**
     * Adds OCR results without clearing existing field entries (used in AUTO mode).
     */
    private void addOCRResultsWithoutClearing(OCRResult result) {
        String prefix = OCRPreferences.getMetadataPrefix();
        int startSize = fieldEntries.size();
        int index = startSize;

        for (TextBlock block : result.getTextBlocks()) {
            if (block.getType() == TextBlock.BlockType.LINE && !block.isEmpty()) {
                String suggestedKey = prefix + "field_" + index;
                String originalText = block.getText();
                OCRFieldEntry entry = new OCRFieldEntry(
                        originalText,
                        suggestedKey,
                        block.getConfidence(),
                        block.getBoundingBox()
                );
                entry.setText(applyActiveFilters(originalText));
                fieldEntries.add(entry);
                index++;
            }
        }

        // If no lines were added, use words as fallback
        if (index == startSize) {
            for (TextBlock block : result.getTextBlocks()) {
                if (block.getType() == TextBlock.BlockType.WORD && !block.isEmpty()) {
                    String suggestedKey = prefix + "field_" + index;
                    String originalText = block.getText();
                    OCRFieldEntry entry = new OCRFieldEntry(
                            originalText,
                            suggestedKey,
                            block.getConfidence(),
                            block.getBoundingBox()
                    );
                    entry.setText(applyActiveFilters(originalText));
                    fieldEntries.add(entry);
                    index++;
                }
            }
            logger.info("No LINE blocks found, used {} WORD blocks as fallback", index - startSize);
        }

        int added = index - startSize;
        logger.info("Added {} field entries from OCR ({} lines, {} words in raw result)",
                added, result.getLineCount(), result.getWordCount());

        updateMetadataPreview();
    }

    private void runOCR() {
        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "Please select an image with a label first.");
            return;
        }

        progressIndicator.setVisible(true);

        PSMOption selectedPSM = psmCombo.getValue();
        OCRConfiguration.PageSegMode psm = selectedPSM != null ? selectedPSM.getMode() : OCRConfiguration.PageSegMode.AUTO;

        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(psm)
                .language(OCRPreferences.getLanguage())
                .minConfidence(confSlider.getValue() / 100.0)
                .autoRotate(OCRPreferences.isAutoRotate())
                .detectOrientation(OCRPreferences.isDetectOrientation())
                .enhanceContrast(thresholdCheckBox.isSelected())
                .enablePreprocessing(true)
                .build();

        BufferedImage imageToProcess = preprocessForOCR(labelImage);

        OCRController.getInstance().performOCRAsync(imageToProcess, config)
                .thenAccept(result -> Platform.runLater(() -> {
                    currentResult = result;
                    populateFieldsTable(result);
                    drawBoundingBoxes();
                    progressIndicator.setVisible(false);

                    long displayCount = result.getDisplayBlockCount();
                    String modeInfo = selectedPSM != null ? selectedPSM.toString() : "Auto";
                    Dialogs.showInfoNotification("OCR Complete",
                            String.format("Detected %d text region(s) in %dms (Mode: %s)",
                                    displayCount, result.getProcessingTimeMs(), modeInfo));
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        Dialogs.showErrorMessage("OCR Failed", ex.getMessage());
                    });
                    return null;
                });
    }

    private BufferedImage preprocessForOCR(BufferedImage source) {
        BufferedImage result = source;
        if (invertCheckBox.isSelected()) {
            result = invertImage(result);
        }
        return result;
    }

    private BufferedImage invertImage(BufferedImage source) {
        BufferedImage inverted = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int r = 255 - ((rgb >> 16) & 0xFF);
                int g = 255 - ((rgb >> 8) & 0xFF);
                int b = 255 - (rgb & 0xFF);
                inverted.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }

        return inverted;
    }

    private void populateFieldsTable(OCRResult result) {
        if (!fieldEntries.isEmpty()) {
            saveStateForUndo();
        }
        fieldEntries.clear();

        String prefix = OCRPreferences.getMetadataPrefix();
        int index = 0;

        for (TextBlock block : result.getTextBlocks()) {
            if (block.getType() == TextBlock.BlockType.LINE && !block.isEmpty()) {
                String suggestedKey = findMatchingMetadataKey(block.getBoundingBox(), prefix + "field_" + index);
                String originalText = block.getText();
                OCRFieldEntry entry = new OCRFieldEntry(
                        originalText,
                        suggestedKey,
                        block.getConfidence(),
                        block.getBoundingBox()
                );
                entry.setText(applyActiveFilters(originalText));
                fieldEntries.add(entry);
                index++;
            }
        }

        // If no lines were found, use words as fallback
        if (fieldEntries.isEmpty()) {
            for (TextBlock block : result.getTextBlocks()) {
                if (block.getType() == TextBlock.BlockType.WORD && !block.isEmpty()) {
                    String suggestedKey = findMatchingMetadataKey(block.getBoundingBox(), prefix + "field_" + index);
                    String originalText = block.getText();
                    OCRFieldEntry entry = new OCRFieldEntry(
                            originalText,
                            suggestedKey,
                            block.getConfidence(),
                            block.getBoundingBox()
                    );
                    entry.setText(applyActiveFilters(originalText));
                    fieldEntries.add(entry);
                    index++;
                }
            }
            logger.info("No LINE blocks found, used {} WORD blocks as fallback", index);
        }

        logger.info("Populated {} field entries from OCR ({} lines, {} words in raw result)",
                fieldEntries.size(), result.getLineCount(), result.getWordCount());

        updateMetadataPreview();
    }

    /**
     * Finds a metadata key from previous field entries if the bounding box overlaps by at least 50%.
     * This preserves user-defined metadata keys when switching between slides with similar label layouts.
     *
     * @param newBox The bounding box of the new field
     * @param defaultKey The default key to use if no match is found
     * @return The matched metadata key or the default key
     */
    private String findMatchingMetadataKey(BoundingBox newBox, String defaultKey) {
        if (newBox == null || previousFieldEntries.isEmpty()) {
            logger.info("findMatchingMetadataKey: no previous entries or null box, using default: {}", defaultKey);
            return defaultKey;
        }

        // Need both current and previous image dimensions for proper normalization
        if (labelImage == null || previousImageWidth <= 0 || previousImageHeight <= 0) {
            logger.info("findMatchingMetadataKey: missing image dimensions (prev={}x{}), using default: {}",
                    previousImageWidth, previousImageHeight, defaultKey);
            return defaultKey;
        }

        // Normalize NEW bounding box using CURRENT image dimensions
        double currImgWidth = labelImage.getWidth();
        double currImgHeight = labelImage.getHeight();

        double newNormX = newBox.getX() / currImgWidth;
        double newNormY = newBox.getY() / currImgHeight;
        double newNormW = newBox.getWidth() / currImgWidth;
        double newNormH = newBox.getHeight() / currImgHeight;

        logger.info("findMatchingMetadataKey: checking {} previous centroids against new box at norm({},{},{},{})",
                previousFieldEntries.size(),
                String.format("%.3f", newNormX), String.format("%.3f", newNormY),
                String.format("%.3f", newNormW), String.format("%.3f", newNormH));

        for (OCRFieldEntry prevEntry : previousFieldEntries) {
            BoundingBox prevBox = prevEntry.getBoundingBox();
            if (prevBox == null) continue;

            // Calculate centroid of PREVIOUS bounding box (normalized using PREVIOUS image dimensions)
            double prevCentroidX = (prevBox.getX() + prevBox.getWidth() / 2.0) / previousImageWidth;
            double prevCentroidY = (prevBox.getY() + prevBox.getHeight() / 2.0) / previousImageHeight;

            String prevKey = prevEntry.getMetadataKey();

            // Check if previous centroid falls within new bounding box
            boolean centroidInBox = prevCentroidX >= newNormX && prevCentroidX <= (newNormX + newNormW) &&
                                    prevCentroidY >= newNormY && prevCentroidY <= (newNormY + newNormH);

            logger.info("  Prev '{}' centroid ({},{}) in new box? {}",
                    prevKey,
                    String.format("%.3f", prevCentroidX), String.format("%.3f", prevCentroidY),
                    centroidInBox ? "YES" : "no");

            if (centroidInBox) {
                if (prevKey != null && !prevKey.isEmpty()) {
                    logger.info("  -> MATCH! Reusing metadata key '{}'", prevKey);
                    return prevKey;
                }
            }
        }

        logger.info("  -> No centroid match found, using default: {}", defaultKey);
        return defaultKey;
    }

    private void drawBoundingBoxes() {
        if (overlayCanvas == null || labelImage == null) return;

        updateCanvasSize();

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());

        // Draw selection rectangle if in selection mode
        if (hasSelection || regionSelectionMode) {
            drawSelectionRectangle(gc);
        }

        if (fieldEntries.isEmpty()) return;

        // Calculate scale factor
        double scaleX = imageView.getBoundsInLocal().getWidth() / labelImage.getWidth();
        double scaleY = imageView.getBoundsInLocal().getHeight() / labelImage.getHeight();
        double scale = Math.min(scaleX, scaleY);

        int index = 0;
        for (OCRFieldEntry entry : fieldEntries) {
            BoundingBox bbox = entry.getBoundingBox();
            if (bbox == null) continue;

            double x = bbox.getX() * scale;
            double y = bbox.getY() * scale;
            double w = bbox.getWidth() * scale;
            double h = bbox.getHeight() * scale;

            // Determine color based on region type
            Color strokeColor;
            String typeLabel;
            if (index == selectedIndex) {
                strokeColor = Color.YELLOW;
                gc.setLineWidth(3);
            } else {
                switch (entry.getRegionType()) {
                    case BARCODE:
                        strokeColor = Color.DODGERBLUE;
                        break;
                    case AUTO:
                        strokeColor = Color.MEDIUMPURPLE;
                        break;
                    case TEXT:
                    default:
                        strokeColor = Color.LIME;
                        break;
                }
                gc.setLineWidth(2);
            }
            gc.setStroke(strokeColor);
            gc.strokeRect(x, y, w, h);

            // Draw label with type indicator
            typeLabel = entry.getRegionType().getShortLabel();

            gc.setFill(Color.rgb(0, 0, 0, 0.7));
            gc.fillRect(x, y - 16, Math.min(w, 60), 16);
            gc.setFill(Color.WHITE);
            gc.fillText(String.format("%d[%s]", index + 1, typeLabel), x + 3, y - 3);

            index++;
        }
    }

    private void drawSelectionRectangle(GraphicsContext gc) {
        if (!hasSelection && !regionSelectionMode) return;

        double x = Math.min(selectionStartX, selectionEndX);
        double y = Math.min(selectionStartY, selectionEndY);
        double w = Math.abs(selectionEndX - selectionStartX);
        double h = Math.abs(selectionEndY - selectionStartY);

        if (w < 2 || h < 2) return;

        gc.setFill(Color.rgb(0, 120, 255, 0.2));
        gc.fillRect(x, y, w, h);

        gc.setStroke(Color.rgb(0, 120, 255));
        gc.setLineWidth(2);
        gc.setLineDashes(5, 5);
        gc.strokeRect(x, y, w, h);
        gc.setLineDashes(null);

        gc.setFill(Color.rgb(0, 120, 255, 0.9));
        gc.fillRect(x, y - 18, 80, 18);
        gc.setFill(Color.WHITE);
        gc.fillText("Selection", x + 4, y - 4);
    }

    private void scanSelectedRegion() {
        if (!hasSelection || labelImage == null) {
            Dialogs.showWarningNotification("No Selection",
                    "Please draw a rectangle on the image first.");
            return;
        }

        double scaleX = imageView.getBoundsInLocal().getWidth() / labelImage.getWidth();
        double scaleY = imageView.getBoundsInLocal().getHeight() / labelImage.getHeight();
        double scale = Math.min(scaleX, scaleY);

        int imgX = (int) Math.max(0, Math.min(selectionStartX, selectionEndX) / scale);
        int imgY = (int) Math.max(0, Math.min(selectionStartY, selectionEndY) / scale);
        int imgW = (int) Math.min(labelImage.getWidth() - imgX, Math.abs(selectionEndX - selectionStartX) / scale);
        int imgH = (int) Math.min(labelImage.getHeight() - imgY, Math.abs(selectionEndY - selectionStartY) / scale);

        if (imgW < 5 || imgH < 5) {
            Dialogs.showWarningNotification("Selection Too Small",
                    "Please draw a larger selection area.");
            return;
        }

        RegionType selectedType = regionTypeCombo.getValue();
        logger.info("Scanning region as {}: x={}, y={}, w={}, h={}", selectedType, imgX, imgY, imgW, imgH);

        BufferedImage regionImage = labelImage.getSubimage(imgX, imgY, imgW, imgH);

        if (invertCheckBox.isSelected()) {
            regionImage = invertImage(regionImage);
        }

        progressIndicator.setVisible(true);

        final int offsetX = imgX;
        final int offsetY = imgY;
        final BufferedImage finalRegionImage = regionImage;
        final RegionType finalType = selectedType;

        // Use unified decoding based on selected type
        if (finalType == RegionType.BARCODE) {
            // Direct barcode scanning - no OCR config needed
            OCRController.getInstance().decodeBarcodeAsync(finalRegionImage)
                    .thenAccept(result -> Platform.runLater(() -> {
                        progressIndicator.setVisible(false);

                        if (!result.hasBarcode()) {
                            Dialogs.showInfoNotification("Barcode Scan Complete",
                                    "No barcode detected in the selected region.\n\n" +
                                    "Try:\n" +
                                    "- Selecting a tighter area around the barcode\n" +
                                    "- Toggling the Invert checkbox\n" +
                                    "- Using AUTO mode to fall back to OCR");
                        } else {
                            addBarcodeResult(result, offsetX, offsetY);
                            String formats = result.getBarcodeCount() == 1
                                    ? result.getFormat()
                                    : result.getBarcodeCount() + " barcodes";
                            Dialogs.showInfoNotification("Barcode Scan Complete",
                                    String.format("Found %s: %s", formats, result.getCombinedText()));
                        }

                        resetRegionSelection();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            progressIndicator.setVisible(false);
                            Dialogs.showErrorMessage("Barcode Scan Failed", ex.getMessage());
                        });
                        return null;
                    });
        } else if (finalType == RegionType.AUTO) {
            // AUTO mode: try barcode first, fall back to OCR
            OCRConfiguration config = OCRConfiguration.builder()
                    .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
                    .language(OCRPreferences.getLanguage())
                    .minConfidence(0.1)
                    .autoRotate(OCRPreferences.isAutoRotate())
                    .detectOrientation(OCRPreferences.isDetectOrientation())
                    .enhanceContrast(thresholdCheckBox.isSelected())
                    .enablePreprocessing(true)
                    .build();

            java.awt.Rectangle region = new java.awt.Rectangle(0, 0, imgW, imgH);

            OCRController.getInstance().decodeRegionAsync(finalRegionImage, region, RegionType.AUTO, config)
                    .thenAccept(result -> Platform.runLater(() -> {
                        progressIndicator.setVisible(false);

                        if (!result.hasText()) {
                            Dialogs.showInfoNotification("Auto Scan Complete",
                                    "No content detected in the selected region.\n\n" +
                                    "Try:\n" +
                                    "- Selecting a tighter area around the content\n" +
                                    "- Toggling the Invert checkbox\n" +
                                    "- Specifying TEXT or BARCODE type explicitly");
                        } else {
                            addUnifiedResult(result, offsetX, offsetY);
                            String typeInfo = result.getSourceType() == RegionType.BARCODE
                                    ? String.format("[%s]", result.getFormat())
                                    : "[Text]";
                            Dialogs.showInfoNotification("Auto Scan Complete",
                                    String.format("Found %s: %s", typeInfo, result.getText()));
                        }

                        resetRegionSelection();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            progressIndicator.setVisible(false);
                            Dialogs.showErrorMessage("Auto Scan Failed", ex.getMessage());
                        });
                        return null;
                    });
        } else {
            // TEXT mode: use OCR as before
            OCRConfiguration config = OCRConfiguration.builder()
                    .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
                    .language(OCRPreferences.getLanguage())
                    .minConfidence(0.1)
                    .autoRotate(OCRPreferences.isAutoRotate())
                    .detectOrientation(OCRPreferences.isDetectOrientation())
                    .enhanceContrast(thresholdCheckBox.isSelected())
                    .enablePreprocessing(true)
                    .build();

            OCRController.getInstance().performOCRAsync(finalRegionImage, config)
                    .thenAccept(result -> Platform.runLater(() -> {
                        progressIndicator.setVisible(false);

                        if (result.getBlockCount() == 0) {
                            Dialogs.showInfoNotification("Region Scan Complete",
                                    "No text detected in the selected region.\n\n" +
                                    "Try:\n" +
                                    "- Selecting a tighter area around the text\n" +
                                    "- Toggling the Invert checkbox\n" +
                                    "- Making sure the text is clearly visible");
                        } else {
                            addRegionResults(result, offsetX, offsetY, RegionType.TEXT);
                            Dialogs.showInfoNotification("Region Scan Complete",
                                    String.format("Found %d text region(s) in the selected area.",
                                            result.getDisplayBlockCount()));
                        }

                        resetRegionSelection();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            progressIndicator.setVisible(false);
                            Dialogs.showErrorMessage("Region Scan Failed", ex.getMessage());
                        });
                        return null;
                    });
        }
    }

    /**
     * Resets the region selection state after scanning.
     */
    private void resetRegionSelection() {
        selectRegionButton.setSelected(false);
        regionSelectionMode = false;
        hasSelection = false;
        scopeCombo.setValue("Full Image");
        drawBoundingBoxes();
        updateScanButtonState();
    }

    /**
     * Shows a context menu after the user finishes drawing a region.
     * Allows quick selection of scan type without using the toolbar dropdowns.
     */
    private void showRegionContextMenu(double screenX, double screenY) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem scanAutoItem = new MenuItem("Scan (Try Both)");
        scanAutoItem.setOnAction(e -> {
            regionTypeCombo.setValue(RegionType.AUTO);
            scopeCombo.setValue("Selection");
            scanSelectedRegion();
        });

        MenuItem scanTextItem = new MenuItem("Scan as Text");
        scanTextItem.setOnAction(e -> {
            regionTypeCombo.setValue(RegionType.TEXT);
            scopeCombo.setValue("Selection");
            scanSelectedRegion();
        });

        MenuItem scanBarcodeItem = new MenuItem("Scan as Barcode");
        scanBarcodeItem.setOnAction(e -> {
            regionTypeCombo.setValue(RegionType.BARCODE);
            scopeCombo.setValue("Selection");
            scanSelectedRegion();
        });

        SeparatorMenuItem separator = new SeparatorMenuItem();

        MenuItem keepSelectionItem = new MenuItem("Keep Selection");
        keepSelectionItem.setOnAction(e -> {
            // Just close the menu, keep the selection for manual scanning
            scopeCombo.setValue("Selection");
        });

        MenuItem cancelItem = new MenuItem("Clear Selection");
        cancelItem.setOnAction(e -> {
            hasSelection = false;
            selectRegionButton.setSelected(false);
            regionSelectionMode = false;
            scopeCombo.setValue("Full Image");
            drawBoundingBoxes();
            updateScanButtonState();
        });

        contextMenu.getItems().addAll(
                scanAutoItem,
                scanTextItem,
                scanBarcodeItem,
                separator,
                keepSelectionItem,
                cancelItem
        );

        // Auto-hide when focus is lost
        contextMenu.setAutoHide(true);

        contextMenu.show(overlayCanvas, screenX, screenY);
    }

    /**
     * Automatically detects all barcodes in the current label image.
     * Scans the entire image and adds each detected barcode as a field entry.
     */
    private void autoDetectBarcodes() {
        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "Please select an image with a label first.");
            return;
        }

        logger.info("Starting automatic barcode detection on full label image: {}x{} type={}",
                labelImage.getWidth(), labelImage.getHeight(), labelImage.getType());
        progressIndicator.setVisible(true);

        // Prepare image - apply inversion if checkbox is selected
        BufferedImage scanImage = labelImage;
        if (invertCheckBox.isSelected()) {
            scanImage = invertImage(labelImage);
        }

        final BufferedImage finalImage = scanImage;

        OCRController.getInstance().decodeBarcodeAsync(finalImage)
                .thenAccept(result -> Platform.runLater(() -> {
                    progressIndicator.setVisible(false);

                    if (!result.hasBarcode()) {
                        // Try with inverted image if original failed and invert wasn't already applied
                        if (!invertCheckBox.isSelected()) {
                            tryAutoDetectWithInversion();
                        } else {
                            Dialogs.showInfoNotification("No Barcodes Found",
                                    "No barcodes were detected in the label image.\n\n" +
                                    "Tips:\n" +
                                    "- Ensure the barcode is clearly visible\n" +
                                    "- Try toggling the Invert checkbox\n" +
                                    "- Use 'Select Region' to manually select the barcode area");
                        }
                    } else {
                        addAutoDetectedBarcodes(result);

                        String message = result.getBarcodeCount() == 1
                                ? String.format("Found 1 barcode [%s]: %s",
                                        result.getFormat(), result.getText())
                                : String.format("Found %d barcodes", result.getBarcodeCount());

                        Dialogs.showInfoNotification("Barcodes Detected", message);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        Dialogs.showErrorMessage("Barcode Detection Failed", ex.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Tries barcode detection with an inverted image as a fallback.
     */
    private void tryAutoDetectWithInversion() {
        logger.debug("Retrying barcode detection with inverted image");
        progressIndicator.setVisible(true);

        BufferedImage invertedImage = invertImage(labelImage);

        OCRController.getInstance().decodeBarcodeAsync(invertedImage)
                .thenAccept(result -> Platform.runLater(() -> {
                    progressIndicator.setVisible(false);

                    if (!result.hasBarcode()) {
                        Dialogs.showInfoNotification("No Barcodes Found",
                                "No barcodes were detected in the label image.\n\n" +
                                "Tips:\n" +
                                "- Ensure the barcode is clearly visible\n" +
                                "- The barcode may be damaged or low quality\n" +
                                "- Use 'Select Region' to manually select the barcode area");
                    } else {
                        addAutoDetectedBarcodes(result);

                        String message = result.getBarcodeCount() == 1
                                ? String.format("Found 1 barcode [%s]: %s (using inverted image)",
                                        result.getFormat(), result.getText())
                                : String.format("Found %d barcodes (using inverted image)",
                                        result.getBarcodeCount());

                        Dialogs.showInfoNotification("Barcodes Detected", message);
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        progressIndicator.setVisible(false);
                        Dialogs.showErrorMessage("Barcode Detection Failed", ex.getMessage());
                    });
                    return null;
                });
    }

    /**
     * Adds auto-detected barcodes to the fields table.
     */
    private void addAutoDetectedBarcodes(BarcodeResult result) {
        saveStateForUndo();
        String prefix = OCRPreferences.getMetadataPrefix();
        int startIndex = fieldEntries.size();

        for (BarcodeResult.DecodedBarcode barcode : result.getBarcodes()) {
            String suggestedKey = prefix + "barcode_" + startIndex;

            // Use the bounding box from ZXing detection
            BoundingBox bbox = barcode.getBoundingBox();

            OCRFieldEntry entry = new OCRFieldEntry(
                    barcode.getText(),
                    suggestedKey,
                    1.0f, // Barcodes have 100% confidence when decoded
                    bbox,
                    RegionType.BARCODE
            );
            entry.setBarcodeFormat(barcode.getFormat());
            fieldEntries.add(entry);

            logger.info("Auto-detected barcode [{}]: '{}' at {}",
                    barcode.getFormat(), barcode.getText(), bbox);

            startIndex++;
        }

        updateMetadataPreview();
        drawBoundingBoxes();
    }

    /**
     * Adds a barcode result to the fields table.
     */
    private void addBarcodeResult(BarcodeResult result, int offsetX, int offsetY) {
        saveStateForUndo();
        String prefix = OCRPreferences.getMetadataPrefix();
        int startIndex = fieldEntries.size();

        for (BarcodeResult.DecodedBarcode barcode : result.getBarcodes()) {
            String suggestedKey = prefix + "barcode_" + startIndex;

            BoundingBox bbox = barcode.getBoundingBox();
            BoundingBox adjustedBbox = null;
            if (bbox != null) {
                adjustedBbox = new BoundingBox(
                        bbox.getX() + offsetX,
                        bbox.getY() + offsetY,
                        bbox.getWidth(),
                        bbox.getHeight()
                );
            }

            OCRFieldEntry entry = new OCRFieldEntry(
                    barcode.getText(),
                    suggestedKey,
                    1.0f, // Barcodes have 100% confidence when decoded
                    adjustedBbox,
                    RegionType.BARCODE
            );
            entry.setBarcodeFormat(barcode.getFormat());
            fieldEntries.add(entry);
            startIndex++;
        }

        updateMetadataPreview();
        drawBoundingBoxes();
    }

    /**
     * Adds a unified decoder result to the fields table.
     */
    private void addUnifiedResult(UnifiedDecoderService.DecodedResult result, int offsetX, int offsetY) {
        saveStateForUndo();
        String prefix = OCRPreferences.getMetadataPrefix();
        int startIndex = fieldEntries.size();

        String suggestedKey = prefix + (result.getSourceType() == RegionType.BARCODE ? "barcode_" : "field_") + startIndex;

        BoundingBox bbox = result.getBoundingBox();
        BoundingBox adjustedBbox = null;
        if (bbox != null) {
            adjustedBbox = new BoundingBox(
                    bbox.getX() + offsetX,
                    bbox.getY() + offsetY,
                    bbox.getWidth(),
                    bbox.getHeight()
            );
        }

        OCRFieldEntry entry = new OCRFieldEntry(
                result.getText(),
                suggestedKey,
                result.getConfidence(),
                adjustedBbox,
                result.getSourceType()
        );

        if (result.getSourceType() == RegionType.BARCODE && result.getFormat() != null) {
            entry.setBarcodeFormat(result.getFormat());
        }

        // Apply active filters to display text (for text results)
        if (result.getSourceType() == RegionType.TEXT) {
            entry.setText(applyActiveFilters(result.getText()));
        }

        fieldEntries.add(entry);
        updateMetadataPreview();
        drawBoundingBoxes();
    }

    private void addRegionResults(OCRResult result, int offsetX, int offsetY) {
        addRegionResults(result, offsetX, offsetY, RegionType.TEXT);
    }

    private void addRegionResults(OCRResult result, int offsetX, int offsetY, RegionType regionType) {
        saveStateForUndo();
        String prefix = OCRPreferences.getMetadataPrefix();
        int startSize = fieldEntries.size();
        int startIndex = startSize;

        for (TextBlock block : result.getTextBlocks()) {
            if (block.getType() == TextBlock.BlockType.LINE && !block.isEmpty()) {
                String suggestedKey = prefix + "region_" + startIndex;

                BoundingBox originalBox = block.getBoundingBox();
                BoundingBox adjustedBox = null;
                if (originalBox != null) {
                    adjustedBox = new BoundingBox(
                            originalBox.getX() + offsetX,
                            originalBox.getY() + offsetY,
                            originalBox.getWidth(),
                            originalBox.getHeight()
                    );
                }

                // Store original text, but display filtered version
                String originalText = block.getText();
                OCRFieldEntry entry = new OCRFieldEntry(
                        originalText,
                        suggestedKey,
                        block.getConfidence(),
                        adjustedBox,
                        regionType
                );
                // Apply active filters to display text
                entry.setText(applyActiveFilters(originalText));
                fieldEntries.add(entry);
                startIndex++;
            }
        }

        // If no lines were added, try words as fallback
        if (fieldEntries.size() == startSize) {
            for (TextBlock block : result.getTextBlocks()) {
                if (block.getType() == TextBlock.BlockType.WORD && !block.isEmpty()) {
                    String suggestedKey = prefix + "region_" + startIndex;

                    BoundingBox originalBox = block.getBoundingBox();
                    BoundingBox adjustedBox = null;
                    if (originalBox != null) {
                        adjustedBox = new BoundingBox(
                                originalBox.getX() + offsetX,
                                originalBox.getY() + offsetY,
                                originalBox.getWidth(),
                                originalBox.getHeight()
                        );
                    }

                    // Store original text, but display filtered version
                    String originalText = block.getText();
                    OCRFieldEntry entry = new OCRFieldEntry(
                            originalText,
                            suggestedKey,
                            block.getConfidence(),
                            adjustedBox,
                            regionType
                    );
                    // Apply active filters to display text
                    entry.setText(applyActiveFilters(originalText));
                    fieldEntries.add(entry);
                    startIndex++;
                }
            }
        }

        updateMetadataPreview();
        drawBoundingBoxes();
    }

    private void updateCanvasSize() {
        if (imageView != null && overlayCanvas != null) {
            overlayCanvas.setWidth(imageView.getBoundsInLocal().getWidth());
            overlayCanvas.setHeight(imageView.getBoundsInLocal().getHeight());
        }
    }

    private void updateMetadataPreview() {
        StringBuilder sb = new StringBuilder();

        for (OCRFieldEntry entry : fieldEntries) {
            String key = entry.getMetadataKey();
            String value = entry.getText();

            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                sb.append(key).append(": ").append(value).append("\n");
            }
        }

        metadataPreview.setText(sb.toString());

        // Update dirty state by comparing current state to last saved state
        String currentState = computeFieldsStateHash();
        if (!currentState.equals(lastSavedState) && !fieldEntries.isEmpty()) {
            markDirty();
        }
    }

    /**
     * Computes a hash of the current field entries state for dirty tracking.
     */
    private String computeFieldsStateHash() {
        StringBuilder sb = new StringBuilder();
        for (OCRFieldEntry entry : fieldEntries) {
            sb.append(entry.getText()).append("|");
            sb.append(entry.getMetadataKey()).append("|");
            sb.append(entry.getRegionType()).append("|");
            BoundingBox bb = entry.getBoundingBox();
            if (bb != null) {
                sb.append(bb.getX()).append(",").append(bb.getY()).append(",");
                sb.append(bb.getWidth()).append(",").append(bb.getHeight());
            }
            sb.append(";");
        }
        return sb.toString();
    }

    /**
     * Marks the dialog as having unsaved changes.
     */
    private void markDirty() {
        if (!isDirty) {
            isDirty = true;
            // Update title to indicate unsaved changes
            String currentTitle = stage.getTitle();
            if (!currentTitle.endsWith("*")) {
                stage.setTitle(currentTitle + " *");
            }
        }
    }

    /**
     * Resets the dirty state after saving.
     */
    private void resetDirtyState() {
        isDirty = false;
        lastSavedState = computeFieldsStateHash();
        // Remove asterisk from title
        String currentTitle = stage.getTitle();
        if (currentTitle.endsWith(" *")) {
            stage.setTitle(currentTitle.substring(0, currentTitle.length() - 2));
        }
    }

    private void addManualField() {
        saveStateForUndo();
        String prefix = OCRPreferences.getMetadataPrefix();
        String key = prefix + "field_" + fieldEntries.size();
        OCRFieldEntry entry = new OCRFieldEntry("", key, 1.0f, null);
        fieldEntries.add(entry);
        fieldsTable.getSelectionModel().select(entry);
        updateMetadataPreview();
    }

    /**
     * Saves current field entries as a template with bounding box positions.
     */
    private void saveTemplate() {
        if (fieldEntries.isEmpty()) {
            Dialogs.showWarningNotification("No Fields",
                    "Run OCR first to detect fields before saving a template.");
            return;
        }

        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "A label image is required to save bounding box positions.");
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

        // Create template from current fields
        String templateName = file.getName().replace(".json", "");
        OCRTemplate template = new OCRTemplate(templateName);

        // Build current OCR configuration
        PSMOption selectedPSM = psmCombo.getValue();
        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(selectedPSM != null ? selectedPSM.getMode() : OCRConfiguration.PageSegMode.AUTO)
                .language(OCRPreferences.getLanguage())
                .minConfidence(confSlider.getValue() / 100.0)
                .enhanceContrast(thresholdCheckBox.isSelected())
                .build();
        template.setConfiguration(config);

        // Add field mappings with normalized bounding boxes
        int imgWidth = labelImage.getWidth();
        int imgHeight = labelImage.getHeight();

        for (int i = 0; i < fieldEntries.size(); i++) {
            OCRFieldEntry entry = fieldEntries.get(i);
            String key = entry.getMetadataKey();
            String text = entry.getText();
            BoundingBox bbox = entry.getBoundingBox();
            RegionType type = entry.getRegionType();

            OCRTemplate.FieldMapping mapping;
            if (bbox != null) {
                // Normalize coordinates to 0-1 range
                double normX = bbox.getX() / (double) imgWidth;
                double normY = bbox.getY() / (double) imgHeight;
                double normW = bbox.getWidth() / (double) imgWidth;
                double normH = bbox.getHeight() / (double) imgHeight;

                mapping = new OCRTemplate.FieldMapping(i, key, text, normX, normY, normW, normH, type);
            } else {
                mapping = new OCRTemplate.FieldMapping(i, key, text);
                mapping.setRegionType(type);
            }
            template.addFieldMapping(mapping);
        }

        template.setUseFixedPositions(true);
        template.setDilationFactor(1.2); // 20% dilation

        try {
            template.saveToFile(file);
            currentTemplate = template;
            useFixedPositionsCheckBox.setDisable(false);
            useFixedPositionsCheckBox.setSelected(true);

            // Reset dirty state after successful save
            resetDirtyState();

            Dialogs.showInfoNotification("Template Saved",
                    String.format("Saved template '%s' with %d field positions.",
                            templateName, template.getFieldMappings().size()));
        } catch (IOException e) {
            logger.error("Error saving template", e);
            Dialogs.showErrorMessage("Save Error", "Failed to save template: " + e.getMessage());
        }
    }

    /**
     * Loads a template from file.
     */
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

            // Update UI to show template info
            useFixedPositionsCheckBox.setDisable(!currentTemplate.hasBoundingBoxData());
            useFixedPositionsCheckBox.setSelected(currentTemplate.hasBoundingBoxData());

            // Show template fields as a preview
            fieldEntries.clear();
            String prefix = OCRPreferences.getMetadataPrefix();
            for (OCRTemplate.FieldMapping mapping : currentTemplate.getFieldMappings()) {
                OCRFieldEntry entry = new OCRFieldEntry(
                        mapping.getExampleText() != null ? mapping.getExampleText() : "",
                        mapping.getMetadataKey() != null ? mapping.getMetadataKey() : prefix + "field_" + mapping.getFieldIndex(),
                        1.0f,
                        null, // Will be populated when applying template
                        mapping.getRegionType()
                );
                fieldEntries.add(entry);
            }
            updateMetadataPreview();
            drawBoundingBoxes();

            Dialogs.showInfoNotification("Template Loaded",
                    String.format("Loaded template '%s' with %d fields.%s",
                            currentTemplate.getName(),
                            currentTemplate.getFieldMappings().size(),
                            currentTemplate.hasBoundingBoxData() ? "\nFixed positions available." : ""));
        } catch (IOException e) {
            logger.error("Error loading template", e);
            Dialogs.showErrorMessage("Load Error", "Failed to load template: " + e.getMessage());
        }
    }

    /**
     * Applies the loaded template to extract content from fixed positions.
     * Uses the appropriate decoder (OCR or barcode) based on each field's regionType.
     */
    private void applyTemplateToCurrentImage() {
        if (currentTemplate == null) {
            Dialogs.showWarningNotification("No Template",
                    "Please load a template first.");
            return;
        }

        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "Please select an image with a label first.");
            return;
        }

        if (!currentTemplate.hasBoundingBoxData()) {
            Dialogs.showWarningNotification("No Position Data",
                    "The loaded template does not contain bounding box positions.\n" +
                    "Use regular OCR instead, or load a different template.");
            return;
        }

        progressIndicator.setVisible(true);
        fieldEntries.clear();

        int imgWidth = labelImage.getWidth();
        int imgHeight = labelImage.getHeight();
        double dilation = currentTemplate.getDilationFactor();
        boolean invert = invertCheckBox.isSelected();
        boolean enhance = thresholdCheckBox.isSelected();

        // Build OCR config - used for TEXT and AUTO modes
        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(OCRConfiguration.PageSegMode.SINGLE_BLOCK)
                .language(OCRPreferences.getLanguage())
                .minConfidence(0.1) // Very low threshold for fixed positions
                .enhanceContrast(enhance)
                .build();

        // Collect region extraction data on the FX thread before going async
        List<TemplateRegionTask> tasks = new ArrayList<>();
        for (OCRTemplate.FieldMapping mapping : currentTemplate.getFieldMappings()) {
            if (!mapping.isEnabled() || !mapping.hasBoundingBox()) continue;

            int[] box = mapping.getScaledBoundingBox(imgWidth, imgHeight, dilation);
            if (box == null || box[2] < 5 || box[3] < 5) continue;

            BufferedImage regionImage;
            try {
                regionImage = labelImage.getSubimage(box[0], box[1], box[2], box[3]);
            } catch (Exception e) {
                logger.warn("Failed to extract region for field {}: {}", mapping.getFieldIndex(), e.getMessage());
                continue;
            }

            if (invert) {
                regionImage = invertImage(regionImage);
            }

            tasks.add(new TemplateRegionTask(regionImage, mapping.getMetadataKey(),
                    box, mapping.getRegionType()));
        }

        // Process all regions SEQUENTIALLY on a single background thread.
        // Tesseract is NOT thread-safe - concurrent calls crash the JVM.
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            List<OCRFieldEntry> results = new ArrayList<>();

            for (TemplateRegionTask task : tasks) {
                try {
                    java.awt.Rectangle region = new java.awt.Rectangle(
                            0, 0, task.box[2], task.box[3]);

                    UnifiedDecoderService.DecodedResult result =
                            OCRController.getInstance().decodeRegion(
                                    task.regionImage, region, task.regionType, config);

                    BoundingBox bbox = new BoundingBox(
                            task.box[0], task.box[1], task.box[2], task.box[3]);

                    OCRFieldEntry entry = new OCRFieldEntry(
                            result.getText(),
                            task.metadataKey,
                            result.getConfidence(),
                            bbox,
                            result.getSourceType() != null ? result.getSourceType() : task.regionType
                    );

                    if (result.getSourceType() == RegionType.BARCODE && result.getFormat() != null) {
                        entry.setBarcodeFormat(result.getFormat());
                    }

                    results.add(entry);
                } catch (Exception e) {
                    logger.warn("Failed to decode template field '{}': {}",
                            task.metadataKey, e.getMessage());
                }
            }

            // Update UI on FX thread after all regions are processed
            Platform.runLater(() -> {
                fieldEntries.addAll(results);

                // Sort by metadata key
                fieldEntries.sort((a, b) -> {
                    String keyA = a.getMetadataKey().replaceAll("\\D+", "");
                    String keyB = b.getMetadataKey().replaceAll("\\D+", "");
                    try {
                        return Integer.compare(
                                keyA.isEmpty() ? 0 : Integer.parseInt(keyA),
                                keyB.isEmpty() ? 0 : Integer.parseInt(keyB)
                        );
                    } catch (NumberFormatException e) {
                        return a.getMetadataKey().compareTo(b.getMetadataKey());
                    }
                });

                // Apply active filters to text fields only
                for (OCRFieldEntry entry : fieldEntries) {
                    if (entry.getRegionType() == RegionType.TEXT) {
                        entry.setText(applyActiveFilters(entry.getOriginalText()));
                    }
                }

                drawBoundingBoxes();
                updateMetadataPreview();
                progressIndicator.setVisible(false);

                long textCount = fieldEntries.stream()
                        .filter(e -> e.getRegionType() == RegionType.TEXT).count();
                long barcodeCount = fieldEntries.stream()
                        .filter(e -> e.getRegionType() == RegionType.BARCODE).count();

                String message = String.format("Extracted from %d positions", fieldEntries.size());
                if (textCount > 0 && barcodeCount > 0) {
                    message += String.format(" (%d text, %d barcode)", textCount, barcodeCount);
                }
                Dialogs.showInfoNotification("Template Applied", message);
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                progressIndicator.setVisible(false);
                logger.error("Template apply failed", ex);
                Dialogs.showErrorMessage("Template Apply Failed",
                        "Error processing template: " + ex.getMessage());
            });
            return null;
        });
    }

    /**
     * Holds pre-extracted data for a single template region to be decoded on a background thread.
     */
    private static class TemplateRegionTask {
        final BufferedImage regionImage;
        final String metadataKey;
        final int[] box;
        final RegionType regionType;

        TemplateRegionTask(BufferedImage regionImage, String metadataKey,
                           int[] box, RegionType regionType) {
            this.regionImage = regionImage;
            this.metadataKey = metadataKey;
            this.box = box;
            this.regionType = regionType;
        }
    }

    private void applyMetadata() {
        if (selectedEntry == null) {
            Dialogs.showWarningNotification("No Image Selected",
                    "Please select an image from the project list.");
            return;
        }

        if (labelImage == null) {
            Dialogs.showWarningNotification("No Label Image",
                    "The selected image has no label. Cannot apply OCR metadata.");
            return;
        }

        Map<String, String> metadata = new HashMap<>();
        Map<Integer, String> fieldMappings = new HashMap<>();

        int fieldIndex = 0;
        for (OCRFieldEntry entry : fieldEntries) {
            String key = entry.getMetadataKey();
            String value = entry.getText();

            if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                MetadataKeyValidator.ValidationResult validation = MetadataKeyValidator.validateKey(key);
                if (validation.isValid()) {
                    metadata.put(key, value);
                    fieldMappings.put(fieldIndex, key);
                }
            }
            fieldIndex++;
        }

        if (metadata.isEmpty()) {
            Dialogs.showWarningNotification("No Metadata",
                    "No valid metadata fields to apply.");
            return;
        }

        int count = OCRMetadataManager.setMetadataBatch(selectedEntry, metadata, project);

        if (count > 0) {
            // Add workflow step - non-critical, should never crash the application
            try {
                addWorkflowStep(fieldMappings);
            } catch (Throwable t) {
                logger.error("Failed to add workflow step (metadata was still saved): {}", t.getMessage(), t);
            }

            Dialogs.showInfoNotification("Metadata Applied",
                    String.format("Applied %d metadata fields to: %s", count, selectedEntry.getImageName()));

            // Don't close - allow user to continue with other images
        } else {
            Dialogs.showErrorMessage("Apply Failed",
                    "Failed to apply metadata. Check the log for details.");
        }
    }

    private void addWorkflowStep(Map<Integer, String> fieldMappings) {
        // Only add workflow step to the currently open image.
        // Avoid readImageData() for non-current images as it is expensive
        // (opens image servers for large WSI files) and can cause errors.
        ImageData<?> currentImageData = qupath.getImageData();
        if (currentImageData == null || selectedEntry == null) {
            logger.debug("No current ImageData available for workflow step");
            return;
        }

        // Check if selected entry matches the currently open image
        ImageData<?> imageData = null;
        try {
            var currentServer = currentImageData.getServer();
            if (currentServer != null) {
                var currentUris = currentServer.getURIs();
                var entryUris = selectedEntry.getURIs();
                String currentUri = currentUris.isEmpty() ? null : currentUris.iterator().next().toString();
                String entryUri = entryUris.isEmpty() ? null : entryUris.iterator().next().toString();

                if (currentUri != null && currentUri.equals(entryUri)) {
                    imageData = currentImageData;
                }
            }
        } catch (Exception e) {
            logger.debug("Error comparing URIs for workflow step: {}", e.getMessage());
            return;
        }

        if (imageData == null) {
            logger.debug("Selected entry is not the currently open image, skipping workflow step");
            return;
        }

        PSMOption selectedPSM = psmCombo.getValue();
        boolean invert = invertCheckBox.isSelected();
        boolean enhance = thresholdCheckBox.isSelected();
        double confidence = confSlider.getValue() / 100.0;

        StringBuilder script = new StringBuilder();
        script.append("// OCR for Labels - Auto-generated script\n");
        script.append("// Run this script to reproduce OCR results on similar images\n");
        script.append("import qupath.ext.ocr4labels.OCR4Labels\n\n");

        script.append("if (!OCR4Labels.hasLabelImage()) {\n");
        script.append("    println \"No label image available\"\n");
        script.append("    return\n");
        script.append("}\n\n");

        script.append("def results = OCR4Labels.builder()\n");

        if (selectedPSM != null) {
            switch (selectedPSM.getMode()) {
                case SPARSE_TEXT:
                    script.append("    .sparseText()\n");
                    break;
                case AUTO:
                    script.append("    .autoDetect()\n");
                    break;
                case SINGLE_BLOCK:
                    script.append("    .singleBlock()\n");
                    break;
                case SINGLE_LINE:
                    script.append("    .singleLine()\n");
                    break;
                case SINGLE_WORD:
                    script.append("    .singleWord()\n");
                    break;
                default:
                    script.append("    .sparseText()\n");
            }
        }

        if (enhance) {
            script.append("    .enhance()\n");
        } else {
            script.append("    .noEnhance()\n");
        }

        if (invert) {
            script.append("    .invert()\n");
        }

        script.append("    .minConfidence(").append(String.format("%.2f", confidence)).append(")\n");
        script.append("    .run()\n\n");

        script.append("// Apply detected text to metadata fields\n");
        for (Map.Entry<Integer, String> entry : fieldMappings.entrySet()) {
            int idx = entry.getKey();
            String key = entry.getValue();
            script.append("if (results.size() > ").append(idx).append(") {\n");
            script.append("    OCR4Labels.setMetadataValue(\"").append(key).append("\", results[").append(idx).append("])\n");
            script.append("}\n");
        }

        script.append("\nprintln \"Applied \" + results.size() + \" OCR fields\"\n");

        try {
            imageData.getHistoryWorkflow().addStep(
                    new DefaultScriptableWorkflowStep(
                            "OCR Label Recognition",
                            script.toString()
                    )
            );
            logger.info("Added OCR workflow step for: {}", selectedEntry.getImageName());
        } catch (Exception e) {
            logger.warn("Failed to add workflow step: {}", e.getMessage());
        }
    }

    // ========== Undo/Redo Support ==========

    /**
     * Snapshot of a field entry for undo/redo operations.
     */
    private static class FieldSnapshot {
        final String originalText;
        final String text;
        final String metadataKey;
        final RegionType regionType;
        final float confidence;
        final BoundingBox boundingBox;
        final String barcodeFormat;

        FieldSnapshot(OCRFieldEntry entry) {
            this.originalText = entry.getOriginalText();
            this.text = entry.getText();
            this.metadataKey = entry.getMetadataKey();
            this.regionType = entry.getRegionType();
            this.confidence = entry.getConfidence();
            this.boundingBox = entry.getBoundingBox();
            this.barcodeFormat = entry.getBarcodeFormat();
        }

        OCRFieldEntry toEntry() {
            OCRFieldEntry entry = new OCRFieldEntry(originalText, metadataKey, confidence, boundingBox, regionType);
            entry.setText(text);
            if (barcodeFormat != null) {
                entry.setBarcodeFormat(barcodeFormat);
            }
            return entry;
        }
    }

    /**
     * Saves the current state for undo. Call this before making changes.
     */
    private void saveStateForUndo() {
        if (isUndoRedoOperation) return;

        List<FieldSnapshot> snapshot = new ArrayList<>();
        for (OCRFieldEntry entry : fieldEntries) {
            snapshot.add(new FieldSnapshot(entry));
        }
        undoStack.push(snapshot);

        // Limit undo stack size
        while (undoStack.size() > MAX_UNDO_LEVELS) {
            undoStack.removeLast();
        }

        // Clear redo stack when new action is performed
        redoStack.clear();
    }

    /**
     * Undoes the last change to field entries.
     */
    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }

        // Save current state to redo stack
        List<FieldSnapshot> currentSnapshot = new ArrayList<>();
        for (OCRFieldEntry entry : fieldEntries) {
            currentSnapshot.add(new FieldSnapshot(entry));
        }
        redoStack.push(currentSnapshot);

        // Restore previous state
        isUndoRedoOperation = true;
        try {
            List<FieldSnapshot> previousState = undoStack.pop();
            fieldEntries.clear();
            for (FieldSnapshot snapshot : previousState) {
                fieldEntries.add(snapshot.toEntry());
            }
            fieldsTable.refresh();
            updateMetadataPreview();
            drawBoundingBoxes();
        } finally {
            isUndoRedoOperation = false;
        }
    }

    /**
     * Redoes the last undone change.
     */
    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }

        // Save current state to undo stack
        List<FieldSnapshot> currentSnapshot = new ArrayList<>();
        for (OCRFieldEntry entry : fieldEntries) {
            currentSnapshot.add(new FieldSnapshot(entry));
        }
        undoStack.push(currentSnapshot);

        // Restore redo state
        isUndoRedoOperation = true;
        try {
            List<FieldSnapshot> redoState = redoStack.pop();
            fieldEntries.clear();
            for (FieldSnapshot snapshot : redoState) {
                fieldEntries.add(snapshot.toEntry());
            }
            fieldsTable.refresh();
            updateMetadataPreview();
            drawBoundingBoxes();
        } finally {
            isUndoRedoOperation = false;
        }
    }

    /**
     * Clears the undo/redo history. Called when switching images.
     */
    private void clearUndoHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    // ========== Inner Classes ==========

    /**
     * Data class for table entries.
     * Stores both original OCR text and filtered display text.
     * Implements FieldEntryProvider for template integration.
     */
    public static class OCRFieldEntry implements OCRTemplate.FieldEntryProvider {
        private final String originalText;  // Original OCR result, never modified
        private final SimpleStringProperty text;  // Displayed text (may be filtered)
        private final SimpleStringProperty metadataKey;
        private final ObjectProperty<RegionType> regionType;
        private final float confidence;
        private final BoundingBox boundingBox;
        private String barcodeFormat;  // For barcode regions: the detected format

        public OCRFieldEntry(String text, String metadataKey, float confidence, BoundingBox boundingBox) {
            this(text, metadataKey, confidence, boundingBox, RegionType.TEXT);
        }

        public OCRFieldEntry(String text, String metadataKey, float confidence, BoundingBox boundingBox,
                             RegionType regionType) {
            this.originalText = text;
            this.text = new SimpleStringProperty(text);
            this.metadataKey = new SimpleStringProperty(metadataKey);
            this.regionType = new SimpleObjectProperty<>(regionType != null ? regionType : RegionType.TEXT);
            this.confidence = confidence;
            this.boundingBox = boundingBox;
        }

        public String getOriginalText() { return originalText; }

        @Override
        public String getText() { return text.get(); }
        public void setText(String value) { text.set(value); }
        public SimpleStringProperty textProperty() { return text; }

        @Override
        public String getMetadataKey() { return metadataKey.get(); }
        public void setMetadataKey(String value) { metadataKey.set(value); }
        public SimpleStringProperty metadataKeyProperty() { return metadataKey; }

        @Override
        public RegionType getRegionType() { return regionType.get(); }
        public void setRegionType(RegionType value) { regionType.set(value != null ? value : RegionType.TEXT); }
        public ObjectProperty<RegionType> regionTypeProperty() { return regionType; }

        public float getConfidence() { return confidence; }
        public BoundingBox getBoundingBox() { return boundingBox; }

        public String getBarcodeFormat() { return barcodeFormat; }
        public void setBarcodeFormat(String format) { this.barcodeFormat = format; }
    }

    /**
     * Custom cell for metadata key editing with validation feedback.
     */
    private static class MetadataKeyCell extends TextFieldTableCell<OCRFieldEntry, String> {
        public MetadataKeyCell() {
            super(new javafx.util.converter.DefaultStringConverter());
        }

        @Override
        public void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);

            if (!empty && item != null) {
                MetadataKeyValidator.ValidationResult validation =
                        MetadataKeyValidator.validateKey(item);
                if (!validation.isValid()) {
                    // Use red border instead of background for dark mode compatibility
                    setStyle("-fx-border-color: #cc3333; -fx-border-width: 2px;");
                    setTooltip(new Tooltip(validation.getErrorMessage()));
                } else {
                    setStyle("");
                    setTooltip(null);
                }
            }
        }
    }

    /**
     * Custom cell for region type selection with ComboBox.
     */
    private class RegionTypeCell extends TableCell<OCRFieldEntry, RegionType> {
        private final ComboBox<RegionType> comboBox;

        public RegionTypeCell() {
            comboBox = new ComboBox<>();
            comboBox.getItems().addAll(RegionType.values());
            comboBox.setOnAction(e -> {
                OCRFieldEntry entry = getTableView().getItems().get(getIndex());
                entry.setRegionType(comboBox.getValue());
                drawBoundingBoxes(); // Update overlay colors
            });
            comboBox.setMaxWidth(Double.MAX_VALUE);
        }

        @Override
        protected void updateItem(RegionType item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                comboBox.setValue(item);
                setGraphic(comboBox);
                setText(null);
            }
        }
    }

    /**
     * User-friendly PSM options for the dropdown.
     */
    public enum PSMOption {
        AUTO("Auto (default)", OCRConfiguration.PageSegMode.AUTO),
        AUTO_OSD("Auto + Orientation", OCRConfiguration.PageSegMode.AUTO_OSD),
        SINGLE_BLOCK("Single Block", OCRConfiguration.PageSegMode.SINGLE_BLOCK),
        SINGLE_LINE("Single Line", OCRConfiguration.PageSegMode.SINGLE_LINE),
        SINGLE_WORD("Single Word", OCRConfiguration.PageSegMode.SINGLE_WORD),
        SPARSE_TEXT("Sparse Text", OCRConfiguration.PageSegMode.SPARSE_TEXT),
        SPARSE_TEXT_OSD("Sparse + Orientation", OCRConfiguration.PageSegMode.SPARSE_TEXT_OSD);

        private final String displayName;
        private final OCRConfiguration.PageSegMode mode;

        PSMOption(String displayName, OCRConfiguration.PageSegMode mode) {
            this.displayName = displayName;
            this.mode = mode;
        }

        public OCRConfiguration.PageSegMode getMode() {
            return mode;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
