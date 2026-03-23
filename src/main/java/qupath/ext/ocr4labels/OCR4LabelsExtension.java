package qupath.ext.ocr4labels;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.ocr4labels.controller.OCRController;
import qupath.ext.ocr4labels.preferences.OCRPreferences;
import qupath.ext.ocr4labels.utilities.LabelImageUtility;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.scripting.ScriptEditor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * QuPath extension for performing OCR on slide label images
 * and creating metadata fields from detected text.
 *
 * <p>This extension allows users to:
 * <ul>
 *   <li>Access label images from whole slide image files</li>
 *   <li>Perform OCR to extract text from labels</li>
 *   <li>Map OCR regions to metadata fields</li>
 *   <li>Apply OCR settings across all images in a project</li>
 * </ul>
 *
 * @author Michael Nelson
 */
public class OCR4LabelsExtension implements QuPathExtension, GitHubProject {

    private static final Logger logger = LoggerFactory.getLogger(OCR4LabelsExtension.class);

    // Load extension metadata from resource bundle
    private static final ResourceBundle resources =
            ResourceBundle.getBundle("qupath.ext.ocr4labels.ui.strings");

    private static final String EXTENSION_NAME = resources.getString("name");
    private static final String EXTENSION_DESCRIPTION = resources.getString("description");
    private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.6.0");
    private static final GitHubRepo EXTENSION_REPOSITORY =
            GitHubRepo.create(EXTENSION_NAME, "uw-loci", "qupath-extension-ocr4labels");

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }

    @Override
    public String getDescription() {
        return EXTENSION_DESCRIPTION;
    }

    @Override
    public Version getQuPathVersion() {
        return EXTENSION_QUPATH_VERSION;
    }

    @Override
    public GitHubRepo getRepository() {
        return EXTENSION_REPOSITORY;
    }

    @Override
    public void installExtension(QuPathGUI qupath) {
        logger.info("Installing extension: {}", EXTENSION_NAME);

        // Register persistent preferences
        OCRPreferences.installPreferences();

        // Build menu on FX thread
        Platform.runLater(() -> addMenuItems(qupath));
    }

    private void addMenuItems(QuPathGUI qupath) {
        // Create or get the top level Extensions > OCR for Labels menu
        var extensionMenu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);

        // === WORKFLOW MENU ITEMS ===

        // 1) Single Image OCR (requires image with label)
        MenuItem singleImageOCR = new MenuItem(resources.getString("menu.singleImageOCR"));
        singleImageOCR.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> {
                            var imageData = qupath.getImageData();
                            if (imageData == null) {
                                return true;
                            }
                            return !LabelImageUtility.isLabelImageAvailable(imageData);
                        },
                        qupath.imageDataProperty()
                )
        );
        singleImageOCR.setOnAction(e ->
                OCRController.getInstance().runSingleImageOCR(qupath));

        // 2) Project-Wide OCR (requires project)
        MenuItem projectOCR = new MenuItem(resources.getString("menu.projectOCR"));
        projectOCR.disableProperty().bind(
                Bindings.createBooleanBinding(
                        () -> qupath.getProject() == null,
                        qupath.projectProperty()
                )
        );
        projectOCR.setOnAction(e ->
                OCRController.getInstance().runProjectOCR(qupath));

        // 3) OCR Settings
        MenuItem settingsItem = new MenuItem(resources.getString("menu.settings"));
        settingsItem.setOnAction(e ->
                OCRController.getInstance().showSettings(qupath));

        // 4) Example Scripts submenu
        Menu exampleScriptsMenu = createExampleScriptsMenu(qupath);

        // Add all menu items
        extensionMenu.getItems().addAll(
                singleImageOCR,
                projectOCR,
                new SeparatorMenuItem(),
                settingsItem,
                new SeparatorMenuItem(),
                exampleScriptsMenu
        );

        logger.info("Menu items added for extension: {}", EXTENSION_NAME);
    }

    /**
     * Creates the Example Scripts submenu with script loading options.
     */
    private Menu createExampleScriptsMenu(QuPathGUI qupath) {
        Menu menu = new Menu("Example Scripts");

        // Basic OCR Detection
        MenuItem basicOCR = new MenuItem("Basic OCR Detection");
        basicOCR.setOnAction(e -> openExampleScript(qupath, "basic_ocr.groovy",
                "Basic OCR Detection - Run on a single image to test OCR"));

        // Custom Field Mapping
        MenuItem customMapping = new MenuItem("Custom Field Mapping");
        customMapping.setOnAction(e -> openExampleScript(qupath, "custom_mapping.groovy",
                "Custom Field Mapping - Map specific fields to metadata keys"));

        // Conditional Processing
        MenuItem conditionalProcessing = new MenuItem("Conditional Processing");
        conditionalProcessing.setOnAction(e -> openExampleScript(qupath, "conditional_processing.groovy",
                "Conditional Processing - Different settings based on image name"));

        // Batch Processing Template
        MenuItem batchTemplate = new MenuItem("Batch Processing Template");
        batchTemplate.setOnAction(e -> openExampleScript(qupath, "batch_template.groovy",
                "Batch Processing Template - Use with Run for Project"));

        menu.getItems().addAll(
                basicOCR,
                customMapping,
                conditionalProcessing,
                batchTemplate
        );

        return menu;
    }

    /**
     * Opens an example script in QuPath's Script Editor.
     */
    private void openExampleScript(QuPathGUI qupath, String scriptName, String description) {
        try {
            // Load script from resources
            String resourcePath = "/scripts/" + scriptName;
            InputStream is = getClass().getResourceAsStream(resourcePath);

            if (is == null) {
                logger.error("Script resource not found: {}", resourcePath);
                qupath.getScriptEditor().showScript(description,
                        "// Error: Script file not found: " + scriptName + "\n" +
                        "// Please check the extension installation.");
                return;
            }

            String scriptContent;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                scriptContent = reader.lines().collect(Collectors.joining("\n"));
            }

            // Open in Script Editor
            ScriptEditor editor = qupath.getScriptEditor();
            editor.showScript(description, scriptContent);

            logger.info("Opened example script: {}", scriptName);

        } catch (Exception e) {
            logger.error("Failed to open example script: {}", scriptName, e);
        }
    }
}
