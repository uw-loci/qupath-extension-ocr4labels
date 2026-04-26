package qupath.ext.ocr4labels.ui;

import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.Optional;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.LabelExtractionConfig;
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.lib.images.ImageData;

/**
 * Dialog for configuring label extraction from an associated image.
 *
 * <p>Users select which associated image to use (e.g., "macro"),
 * draw a crop rectangle over the label region, set rotation and
 * flip options, and preview the result. The configuration is saved
 * in the OCR template for batch processing.
 */
public class LabelExtractionDialog {

    private static final Logger logger = LoggerFactory.getLogger(LabelExtractionDialog.class);

    private final ImageData<?> imageData;

    // UI components
    private ComboBox<String> sourceCombo;
    private ImageView sourceView;
    private Canvas overlayCanvas;
    private Label cropInfoLabel;
    private ComboBox<String> rotationCombo;
    private CheckBox flipHCheck;
    private CheckBox flipVCheck;
    private ImageView previewView;

    // Crop rectangle state (image pixel coordinates)
    private double dragStartX, dragStartY;
    private int cropX, cropY, cropW, cropH;
    private boolean hasCrop = false;

    // Current source image
    private BufferedImage currentSourceImage;

    public LabelExtractionDialog(ImageData<?> imageData) {
        this.imageData = imageData;
    }

    /**
     * Shows the dialog and returns the configured extraction config,
     * or empty if the user cancels.
     */
    public Optional<LabelExtractionConfig> showAndWait() {
        return showAndWait(null);
    }

    /**
     * Shows the dialog pre-populated with an existing config.
     */
    public Optional<LabelExtractionConfig> showAndWait(LabelExtractionConfig existing) {
        Dialog<LabelExtractionConfig> dialog = new Dialog<>();
        dialog.setTitle("Configure Label Source");
        dialog.setHeaderText("Select an associated image and define the label region");
        dialog.setResizable(true);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Source image selector
        sourceCombo = new ComboBox<>();
        sourceCombo.setTooltip(new Tooltip("Select which associated image contains the label"));
        try {
            Collection<String> names = imageData.getServer().getAssociatedImageList();
            sourceCombo.getItems().addAll(names);
        } catch (Exception e) {
            logger.warn("Could not list associated images: {}", e.getMessage());
        }
        Label sourceLabel = new Label("Source Image:");
        sourceLabel.setTooltip(sourceCombo.getTooltip());

        HBox sourceRow = new HBox(8, sourceLabel, sourceCombo);
        sourceRow.setAlignment(Pos.CENTER_LEFT);

        // Source image display with crop overlay
        StackPane imagePane = new StackPane();
        sourceView = new ImageView();
        sourceView.setPreserveRatio(true);
        sourceView.setFitWidth(500);
        sourceView.setFitHeight(400);

        overlayCanvas = new Canvas(500, 400);
        overlayCanvas.setMouseTransparent(false);

        imagePane.getChildren().addAll(sourceView, overlayCanvas);
        imagePane.setStyle("-fx-border-color: #666; -fx-border-width: 1;");

        // Mouse handlers for crop rectangle drawing
        overlayCanvas.setOnMousePressed(e -> {
            dragStartX = e.getX();
            dragStartY = e.getY();
        });
        overlayCanvas.setOnMouseDragged(e -> {
            drawCropRect(dragStartX, dragStartY, e.getX(), e.getY());
        });
        overlayCanvas.setOnMouseReleased(e -> {
            finalizeCropRect(dragStartX, dragStartY, e.getX(), e.getY());
        });

        // Crop info
        cropInfoLabel = new Label("Draw a rectangle on the image to define the label region");
        cropInfoLabel.setStyle("-fx-font-size: 11; -fx-opacity: 0.7;");

        // Transform controls
        rotationCombo = new ComboBox<>();
        rotationCombo.getItems().addAll("0", "90", "180", "270");
        rotationCombo.setValue("0");
        rotationCombo.setTooltip(new Tooltip("Rotate the extracted label (degrees clockwise)"));
        Label rotLabel = new Label("Rotation:");
        rotLabel.setTooltip(rotationCombo.getTooltip());

        flipHCheck = new CheckBox("Flip Horizontal");
        flipHCheck.setTooltip(new Tooltip("Mirror the extracted label horizontally"));
        flipVCheck = new CheckBox("Flip Vertical");
        flipVCheck.setTooltip(new Tooltip("Mirror the extracted label vertically"));

        Button previewBtn = new Button("Preview");
        previewBtn.setTooltip(new Tooltip("Preview the result of crop + rotate + flip"));
        previewBtn.setOnAction(e -> updatePreview());

        VBox controlsBox = new VBox(8,
                new Label("Transform:"),
                new HBox(8, rotLabel, rotationCombo),
                flipHCheck, flipVCheck,
                new Separator(),
                previewBtn);
        controlsBox.setPadding(new Insets(10));
        controlsBox.setPrefWidth(180);

        // Preview area
        previewView = new ImageView();
        previewView.setPreserveRatio(true);
        previewView.setFitWidth(180);
        previewView.setFitHeight(200);
        VBox previewBox = new VBox(5, new Label("Preview:"), previewView);
        controlsBox.getChildren().add(previewBox);

        // Wire source combo
        sourceCombo.setOnAction(e -> loadSourceImage(sourceCombo.getValue()));

        // Layout
        BorderPane content = new BorderPane();
        content.setTop(sourceRow);
        BorderPane.setMargin(sourceRow, new Insets(0, 0, 8, 0));

        VBox centerBox = new VBox(4, imagePane, cropInfoLabel);
        content.setCenter(centerBox);
        content.setRight(controlsBox);
        content.setPadding(new Insets(10));

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefSize(750, 550);

        // Pre-populate from existing config
        if (existing != null) {
            if (existing.getSourceImageName() != null
                    && sourceCombo.getItems().contains(existing.getSourceImageName())) {
                sourceCombo.setValue(existing.getSourceImageName());
                loadSourceImage(existing.getSourceImageName());
            }
            if (existing.hasCropRegion()) {
                cropX = existing.getCropX();
                cropY = existing.getCropY();
                cropW = existing.getCropWidth();
                cropH = existing.getCropHeight();
                hasCrop = true;
                drawStoredCropRect();
                updateCropLabel();
            }
            rotationCombo.setValue(String.valueOf(existing.getRotation()));
            flipHCheck.setSelected(existing.isFlipHorizontal());
            flipVCheck.setSelected(existing.isFlipVertical());
        }

        // Result converter
        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK && sourceCombo.getValue() != null) {
                int rot = 0;
                try {
                    rot = Integer.parseInt(rotationCombo.getValue());
                } catch (NumberFormatException ignored) {}

                return new LabelExtractionConfig(
                        sourceCombo.getValue(),
                        cropX, cropY, cropW, cropH,
                        rot, flipHCheck.isSelected(), flipVCheck.isSelected());
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private void loadSourceImage(String name) {
        if (name == null) return;
        try {
            Object img = imageData.getServer().getAssociatedImage(name);
            if (img instanceof BufferedImage) {
                currentSourceImage = (BufferedImage) img;
                javafx.scene.image.Image fxImg = SwingFXUtils.toFXImage(currentSourceImage, null);
                sourceView.setImage(fxImg);

                // Resize overlay canvas to match displayed image
                double dispW = sourceView.getBoundsInLocal().getWidth();
                double dispH = sourceView.getBoundsInLocal().getHeight();
                if (dispW > 0 && dispH > 0) {
                    overlayCanvas.setWidth(dispW);
                    overlayCanvas.setHeight(dispH);
                } else {
                    overlayCanvas.setWidth(sourceView.getFitWidth());
                    overlayCanvas.setHeight(sourceView.getFitHeight());
                }

                // Clear crop
                hasCrop = false;
                cropX = cropY = cropW = cropH = 0;
                clearOverlay();
                cropInfoLabel.setText("Draw a rectangle on the image to define the label region");
                logger.info("Loaded associated image '{}': {}x{}", name,
                        currentSourceImage.getWidth(), currentSourceImage.getHeight());
            }
        } catch (Exception e) {
            logger.warn("Failed to load associated image '{}': {}", name, e.getMessage());
        }
    }

    private void drawCropRect(double x1, double y1, double x2, double y2) {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
        gc.setStroke(Color.LIME);
        gc.setLineWidth(2);
        gc.setLineDashes(5, 5);
        double rx = Math.min(x1, x2);
        double ry = Math.min(y1, y2);
        double rw = Math.abs(x2 - x1);
        double rh = Math.abs(y2 - y1);
        gc.strokeRect(rx, ry, rw, rh);
    }

    private void finalizeCropRect(double x1, double y1, double x2, double y2) {
        if (currentSourceImage == null) return;

        // Convert display coordinates to image pixel coordinates
        double dispW = overlayCanvas.getWidth();
        double dispH = overlayCanvas.getHeight();
        int imgW = currentSourceImage.getWidth();
        int imgH = currentSourceImage.getHeight();

        // Account for preserveRatio -- find actual displayed bounds
        double scaleX = dispW / imgW;
        double scaleY = dispH / imgH;
        double scale = Math.min(scaleX, scaleY);
        double offsetX = (dispW - imgW * scale) / 2.0;
        double offsetY = (dispH - imgH * scale) / 2.0;

        cropX = (int) Math.max(0, (Math.min(x1, x2) - offsetX) / scale);
        cropY = (int) Math.max(0, (Math.min(y1, y2) - offsetY) / scale);
        cropW = (int) Math.min(imgW - cropX, Math.abs(x2 - x1) / scale);
        cropH = (int) Math.min(imgH - cropY, Math.abs(y2 - y1) / scale);
        hasCrop = cropW > 5 && cropH > 5;

        if (hasCrop) {
            updateCropLabel();
            logger.info("Crop region: ({},{}) {}x{}", cropX, cropY, cropW, cropH);
        }
    }

    private void drawStoredCropRect() {
        if (currentSourceImage == null || !hasCrop) return;

        double dispW = overlayCanvas.getWidth();
        double dispH = overlayCanvas.getHeight();
        int imgW = currentSourceImage.getWidth();
        int imgH = currentSourceImage.getHeight();
        double scale = Math.min(dispW / imgW, dispH / imgH);
        double offsetX = (dispW - imgW * scale) / 2.0;
        double offsetY = (dispH - imgH * scale) / 2.0;

        double dx = offsetX + cropX * scale;
        double dy = offsetY + cropY * scale;
        double dw = cropW * scale;
        double dh = cropH * scale;

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, dispW, dispH);
        gc.setStroke(Color.LIME);
        gc.setLineWidth(2);
        gc.setLineDashes(5, 5);
        gc.strokeRect(dx, dy, dw, dh);
    }

    private void updateCropLabel() {
        cropInfoLabel.setText(String.format("Crop: (%d, %d) %d x %d px", cropX, cropY, cropW, cropH));
    }

    private void clearOverlay() {
        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, overlayCanvas.getWidth(), overlayCanvas.getHeight());
    }

    private void updatePreview() {
        if (currentSourceImage == null) return;

        LabelExtractionConfig config = new LabelExtractionConfig(
                sourceCombo.getValue(),
                cropX, cropY, cropW, cropH,
                Integer.parseInt(rotationCombo.getValue()),
                flipHCheck.isSelected(), flipVCheck.isSelected());

        BufferedImage result = LabelImageUtility.retrieveImageWithExtraction(imageData, config);
        if (result != null) {
            previewView.setImage(SwingFXUtils.toFXImage(result, null));
        }
    }
}
