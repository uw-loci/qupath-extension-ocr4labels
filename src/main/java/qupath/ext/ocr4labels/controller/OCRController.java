package qupath.ext.ocr4labels.controller;

import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.model.BarcodeResult;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.OCRResult;
import qupath.ext.ocr4labels.model.RegionType;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.ext.ocr4labels.service.BarcodeEngine;
import qupath.ext.ocr4labels.service.OCREngine;
import qupath.ext.ocr4labels.service.UnifiedDecoderService;
import qupath.ext.ocr4labels.ui.BatchOCRDialog;
import qupath.ext.ocr4labels.ui.OCRDialog;
import qupath.ext.ocr4labels.ui.OCRSettingsDialog;
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Main controller for OCR operations.
 * Handles workflow orchestration between UI and services.
 */
public class OCRController {

    private static final Logger logger = LoggerFactory.getLogger(OCRController.class);

    private static OCRController instance;

    private OCREngine ocrEngine;
    private BarcodeEngine barcodeEngine;
    private UnifiedDecoderService unifiedDecoder;
    private boolean engineInitialized = false;

    private OCRController() {
        this.ocrEngine = new OCREngine();
        this.barcodeEngine = new BarcodeEngine();
    }

    /**
     * Gets the singleton instance of the controller.
     */
    public static synchronized OCRController getInstance() {
        if (instance == null) {
            instance = new OCRController();
        }
        return instance;
    }

    /**
     * Runs OCR on the current image's label.
     * Opens a dialog that allows navigation through all project images.
     *
     * @param qupath The QuPath GUI instance
     */
    public void runSingleImageOCR(QuPathGUI qupath) {
        logger.info("Starting OCR workflow");

        // Ensure we have a project
        var project = qupath.getProject();
        if (project == null) {
            Dialogs.showErrorMessage("OCR Error",
                    "No project is currently open.\n" +
                    "Please open a project to use the OCR dialog.");
            return;
        }

        if (project.getImageList().isEmpty()) {
            Dialogs.showErrorMessage("OCR Error",
                    "The project contains no images.");
            return;
        }

        // Ensure OCR engine is initialized
        if (!ensureEngineInitialized(qupath)) {
            return;
        }

        // Show the OCR dialog (it handles project navigation internally)
        OCRDialog.show(qupath, ocrEngine);
    }

    /**
     * Runs OCR on all images in the current project.
     *
     * @param qupath The QuPath GUI instance
     */
    public void runProjectOCR(QuPathGUI qupath) {
        logger.info("Starting project-wide OCR workflow");

        var project = qupath.getProject();
        if (project == null) {
            Dialogs.showErrorMessage("OCR Error", "No project is currently open.");
            return;
        }

        if (project.getImageList().isEmpty()) {
            Dialogs.showErrorMessage("OCR Error", "The project contains no images.");
            return;
        }

        // Ensure OCR engine is initialized
        if (!ensureEngineInitialized(qupath)) {
            return;
        }

        // Count images with labels
        long imagesWithLabels = project.getImageList().stream()
                .filter(entry -> {
                    try {
                        ImageData<?> data = entry.readImageData();
                        return data != null && LabelImageUtility.isLabelImageAvailable(data);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

        if (imagesWithLabels == 0) {
            Dialogs.showWarningNotification("OCR Warning",
                    "No images in the project have label images available.");
            return;
        }

        // Show batch processing dialog
        BatchOCRDialog.show(qupath, ocrEngine);
    }

    /**
     * Shows the OCR settings dialog.
     *
     * @param qupath The QuPath GUI instance
     */
    public void showSettings(QuPathGUI qupath) {
        logger.info("Opening OCR settings dialog");
        OCRSettingsDialog.show(qupath);
    }

    /**
     * Performs OCR on an image asynchronously.
     *
     * @param image  The image to process
     * @param config The OCR configuration
     * @return CompletableFuture containing the OCR result
     */
    public CompletableFuture<OCRResult> performOCRAsync(BufferedImage image, OCRConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!engineInitialized) {
                    throw new OCREngine.OCRException("OCR engine not initialized");
                }
                return ocrEngine.processImage(image, config);
            } catch (OCREngine.OCRException e) {
                logger.error("OCR processing failed", e);
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Performs OCR synchronously (blocking).
     *
     * @param image  The image to process
     * @param config The OCR configuration
     * @return The OCR result
     * @throws OCREngine.OCRException if processing fails
     */
    public OCRResult performOCR(BufferedImage image, OCRConfiguration config)
            throws OCREngine.OCRException {
        if (!engineInitialized) {
            throw new OCREngine.OCRException("OCR engine not initialized");
        }
        return ocrEngine.processImage(image, config);
    }

    /**
     * Performs barcode decoding on an image.
     *
     * @param image The image to scan
     * @return BarcodeResult containing decoded barcodes
     */
    public BarcodeResult decodeBarcode(BufferedImage image) {
        return barcodeEngine.decodeWithRetry(image);
    }

    /**
     * Performs barcode decoding asynchronously.
     *
     * @param image The image to scan
     * @return CompletableFuture containing the barcode result
     */
    public CompletableFuture<BarcodeResult> decodeBarcodeAsync(BufferedImage image) {
        return CompletableFuture.supplyAsync(() -> barcodeEngine.decodeWithRetry(image));
    }

    /**
     * Performs unified decoding based on region type.
     *
     * @param image The image to process
     * @param region The region to decode (null for full image)
     * @param regionType The type of content (TEXT, BARCODE, or AUTO)
     * @param config OCR configuration (used for TEXT and AUTO fallback)
     * @return The decoded result
     */
    public UnifiedDecoderService.DecodedResult decodeRegion(
            BufferedImage image,
            java.awt.Rectangle region,
            RegionType regionType,
            OCRConfiguration config) {
        if (unifiedDecoder == null) {
            return UnifiedDecoderService.DecodedResult.error(
                    "Unified decoder not initialized", 0);
        }
        return unifiedDecoder.decodeRegion(image, region, regionType, config);
    }

    /**
     * Performs unified decoding asynchronously.
     *
     * @param image The image to process
     * @param region The region to decode (null for full image)
     * @param regionType The type of content (TEXT, BARCODE, or AUTO)
     * @param config OCR configuration
     * @return CompletableFuture containing the decoded result
     */
    public CompletableFuture<UnifiedDecoderService.DecodedResult> decodeRegionAsync(
            BufferedImage image,
            java.awt.Rectangle region,
            RegionType regionType,
            OCRConfiguration config) {
        return CompletableFuture.supplyAsync(() ->
                decodeRegion(image, region, regionType, config));
    }

    /**
     * Gets the barcode engine for direct access.
     */
    public BarcodeEngine getBarcodeEngine() {
        return barcodeEngine;
    }

    /**
     * Gets the unified decoder service.
     */
    public UnifiedDecoderService getUnifiedDecoder() {
        return unifiedDecoder;
    }

    /**
     * Ensures the OCR engine is initialized.
     * Prompts user for tessdata path if not configured.
     *
     * @return true if engine is ready, false otherwise
     */
    private boolean ensureEngineInitialized(QuPathGUI qupath) {
        if (engineInitialized && ocrEngine.isInitialized()) {
            return true;
        }

        String tessdataPath = OCRPreferences.getTessdataPath();

        // Check if path is configured
        if (tessdataPath == null || tessdataPath.isEmpty()) {
            tessdataPath = promptForTessdataPath();
            if (tessdataPath == null) {
                return false;
            }
        }

        // Verify path exists
        File tessdataDir = new File(tessdataPath);
        if (!tessdataDir.exists() || !tessdataDir.isDirectory()) {
            Dialogs.showErrorMessage("OCR Configuration Error",
                    "Tessdata directory not found: " + tessdataPath + "\n\n" +
                            "Please configure the tessdata path in OCR Settings.");
            return false;
        }

        // Check for language file
        String language = OCRPreferences.getLanguage();
        File langFile = new File(tessdataDir, language + ".traineddata");
        if (!langFile.exists()) {
            boolean openSettings = Dialogs.showConfirmDialog(
                    "OCR Setup Required",
                    "The language data file was not found:\n" +
                    "  " + langFile.getName() + "\n\n" +
                    "OCR requires this file to recognize text on labels.\n\n" +
                    "Would you like to open OCR Settings to download it?\n\n" +
                    "(Look for the 'Required Downloads' section)");

            if (openSettings) {
                OCRSettingsDialog.show(qupath);
            }
            return false;
        }

        // Check for OSD file if orientation detection is enabled
        if (OCRPreferences.isDetectOrientation()) {
            File osdFile = new File(tessdataDir, "osd.traineddata");
            if (!osdFile.exists()) {
                boolean proceed = Dialogs.showConfirmDialog(
                        "Orientation Detection Unavailable",
                        "The orientation detection file (osd.traineddata) was not found.\n\n" +
                        "Without this file, rotated or sideways labels may not be read correctly.\n\n" +
                        "Options:\n" +
                        "  - Click 'Yes' to continue without orientation detection\n" +
                        "  - Click 'No' to cancel and download the file from OCR Settings\n\n" +
                        "Continue anyway?");

                if (!proceed) {
                    OCRSettingsDialog.show(qupath);
                    return false;
                }
                // Disable orientation detection for this session since file is missing
                logger.info("Continuing without orientation detection (osd.traineddata not found)");
            }
        }

        // Initialize engine
        try {
            ocrEngine.initialize(tessdataPath, language);
            unifiedDecoder = new UnifiedDecoderService(ocrEngine, barcodeEngine);
            engineInitialized = true;
            logger.info("OCR engine and unified decoder initialized successfully");
            return true;
        } catch (OCREngine.OCRException e) {
            Dialogs.showErrorMessage("OCR Initialization Error",
                    "Failed to initialize OCR engine:\n" + e.getMessage());
            return false;
        }
    }

    /**
     * Prompts the user to select the tessdata directory.
     *
     * @return The selected path, or null if cancelled
     */
    private String promptForTessdataPath() {
        // Show info dialog first
        Dialogs.showMessageDialog("OCR Setup Required",
                "To use OCR, you need to configure the Tesseract data directory.\n\n" +
                        "1. Download language data from: https://github.com/tesseract-ocr/tessdata\n" +
                        "2. Place the .traineddata file(s) in a 'tessdata' folder\n" +
                        "3. Select that folder in the next dialog");

        // Show directory chooser using JavaFX DirectoryChooser
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Tessdata Directory");
        File selectedDir = chooser.showDialog(null);

        if (selectedDir != null && selectedDir.isDirectory()) {
            String path = selectedDir.getAbsolutePath();
            OCRPreferences.setTessdataPath(path);
            logger.info("Tessdata path set to: {}", path);
            return path;
        }

        return null;
    }

    /**
     * Gets the current OCR configuration from preferences.
     */
    public OCRConfiguration getCurrentConfiguration() {
        return OCRConfiguration.builder()
                .language(OCRPreferences.getLanguage())
                .minConfidence(OCRPreferences.getMinConfidence())
                .autoRotate(OCRPreferences.isAutoRotate())
                .enhanceContrast(OCRPreferences.isEnhanceContrast())
                .detectOrientation(OCRPreferences.isDetectOrientation())
                .pageSegMode(OCRConfiguration.PageSegMode.values()[
                        Math.min(OCRPreferences.getPageSegMode(),
                                OCRConfiguration.PageSegMode.values().length - 1)])
                .build();
    }

    /**
     * Checks if the OCR engine is initialized.
     */
    public boolean isEngineInitialized() {
        return engineInitialized && ocrEngine.isInitialized();
    }

    /**
     * Disposes resources held by the controller.
     */
    public void dispose() {
        if (ocrEngine != null) {
            ocrEngine.dispose();
        }
        unifiedDecoder = null;
        engineInitialized = false;
        logger.info("OCR controller disposed");
    }
}
