# QuPath Extension: OCR for Labels

A QuPath extension that performs OCR (Optical Character Recognition) and barcode scanning on slide label images, allowing users to create metadata fields from detected text and barcodes. Supports single-image processing, batch operations across entire projects, template-based workflows, and intelligent text correction using vocabulary matching.

## Features

- **Label Image Access**: Extract and display label images from whole slide image files
- **Tesseract OCR Integration**: High-quality text recognition via Tess4J wrapper
- **Barcode Scanning**: Automatic detection of 1D and 2D barcodes via ZXing library
- **Hybrid Templates**: Mix text and barcode regions in a single template
- **Interactive GUI**: Review, edit, and assign detected content to metadata fields
- **Project Navigation**: Browse all project images without closing the dialog
- **Batch Processing**: Apply OCR/barcode templates across entire projects
- **Template System**: Save and reuse field positions, types, and metadata key assignments
- **Text Filtering**: Clean up OCR results with one-click character filters
- **Vocabulary Matching**: Correct OCR errors by matching against known valid values
- **Rotated Label Support**: Automatic orientation detection for sideways or upside-down labels

## Requirements

- QuPath 0.6.0 or later
- Java 21+
- Tesseract language data files (see [Setup](#setup))

## Installation

1. Download the latest release JAR file
2. Drag and drop into QuPath, or copy to your QuPath extensions folder
3. Restart QuPath

## Setup

Before using OCR, you need to download Tesseract language data files:

1. Go to **Extensions > OCR for Labels > OCR Settings...**
2. In the **Required Downloads** section:
   - Click **eng.traineddata** to download the English language file (~4 MB)
   - Optionally click **osd.traineddata** for orientation detection (~10 MB)
3. Set the **Tessdata Path** to the folder containing the downloaded files
4. Click **OK** to save settings

**Note**: Barcode scanning (ZXing) requires no additional setup - it's included in the extension.

## Usage

### Single Image Scanning

1. Open an image with a label in QuPath
2. Go to **Extensions > OCR for Labels > Run OCR on Label**
3. The OCR Dialog opens showing all project images on the left
4. Select an image from the list (or use the currently open image)
5. Configure scan settings:
   - **Scope**: "Full Image" (entire label) or "Selection" (drawn rectangle)
   - **Type**: "Text" (OCR), "Barcode" (ZXing), or "Auto" (barcode first, then OCR)
   - **Min Conf**: Minimum confidence threshold for text results
6. Click **Scan** to detect content on the label
7. Review detected content in the table:
   - Edit the **Text** column to correct OCR mistakes
   - Edit the **Metadata Key** column to set field names
8. Click **Apply** to save metadata to the selected image

**Important:** Each click of **Scan** clears previous results and starts fresh. This lets you iteratively adjust settings (e.g., lower the Min Conf threshold, change Type, or switch Scope) and immediately see the effect without accumulating stale results. Use **Ctrl+Z** to undo if needed.

### Region Selection Scanning

For difficult regions or mixed content:

1. Set **Scope** to "Selection"
2. Draw a rectangle around the target area on the label image
3. Select the **Type**:
   - **Text**: Use OCR (Tesseract)
   - **Barcode**: Use barcode scanner (ZXing)
   - **Auto**: Try barcode first, fall back to OCR
4. Click **Scan**
5. Results replace any previous scan output in the fields table

### Creating Templates with Manual Regions

You can define template regions manually without scanning, which is useful when you know where fields are on the label but want to control exactly what areas get decoded:

1. Click **Draw Region** to enable drawing mode
2. Draw a rectangle around the target area
3. Set **Decode As** to the desired type (Text, Barcode, or Try Both)
4. Click **Add Region** to add it as a field entry (no scanning is performed)
5. Repeat steps 2-4 for each region on the label
6. Edit the **Metadata Key** column to set meaningful field names
7. Click **Save Template...** to save the regions for batch use

The saved template preserves each region's position, type, and metadata key. When applied to other slides, each region is cropped and decoded according to its type.

### Batch Processing

1. Open a project with multiple images
2. Go to **Extensions > OCR for Labels > Run OCR on Project...**
3. Create or load a template:
   - Click **Create from Current Image** to use the single-image dialog
   - Or click **Load Template...** to use a previously saved template
4. Review field mappings in the template table (includes Type column)
5. Click **Process All** to run OCR/barcode scanning on all images
6. Review and edit results in the results table
7. Click **Apply Metadata** to save to all images

---

## Barcode Support

### Supported Barcode Formats

The extension uses ZXing library to decode:

**1D Barcodes:**
- Code 128, Code 39, Code 93
- EAN-8, EAN-13, UPC-A, UPC-E
- ITF (Interleaved 2 of 5)
- Codabar

**2D Barcodes:**
- QR Code
- Data Matrix
- PDF417
- Aztec

### Region Types

Each field in a template can have one of three types:

| Type | Description | Use Case |
|------|-------------|----------|
| **Text** | Uses OCR (Tesseract) | Printed text, handwritten notes |
| **Barcode** | Uses barcode scanner (ZXing) | QR codes, barcodes |
| **Auto** | Tries barcode first, falls back to OCR | Unknown content or mixed labels |

### Visual Indicators

Region types are color-coded on the overlay:

| Color | Type | Label |
|-------|------|-------|
| Green | Text | `1[T]` |
| Blue | Barcode | `2[BC]` |
| Purple | Auto | `3[A]` |
| Yellow | Selected | (any type) |

---

## OCR Dialog Reference

### Toolbar Controls

| Control | Description |
|---------|-------------|
| **Scope** | "Full Image" scans the entire label; "Selection" scans a drawn rectangle |
| **Decode As** | Content type: Text (OCR), Barcode (ZXing), or Try Both (barcode then OCR) |
| **Scan** | Runs detection using current Scope and Decode As settings. Clears previous results each time |
| **Draw Region** | Toggle drawing mode to select an area on the label image |
| **Add Region** | Adds the drawn rectangle as a template field without scanning (for manual template creation) |
| **Clear** | Clears the current drawn selection |
| **Mode** | Page segmentation mode - controls how Tesseract analyzes the image layout |
| **Min Conf** | Minimum confidence threshold (0-100%) - text below this is filtered out |
| **Invert** | Inverts image colors - use for labels with light text on dark backgrounds |
| **Enhance** | Improves image contrast before OCR - recommended for faded labels |

### Page Segmentation Modes

| Mode | Best For |
|------|----------|
| **Auto** | General purpose, lets Tesseract decide |
| **Auto + Orientation** | Auto mode with rotation detection |
| **Single Block** | Labels with one block of text |
| **Single Line** | Single-line text like serial numbers |
| **Single Word** | Individual words or short codes |
| **Sparse Text** | Labels with scattered text at various positions (recommended) |
| **Sparse + Orientation** | Sparse text with rotation detection |

### Image Panel

| Control | Description |
|---------|-------------|
| **Fit** | Scales image to fit the panel |
| **100%** | Shows image at actual pixel size |
| **Mouse Drag** | When "Draw Region" is active, drag to draw selection rectangle |
| **Adjust Image** | Min/Max sliders to adjust display brightness/contrast (does not affect scanning) |
| **Auto** | Auto-adjusts display range based on image histogram (2nd-98th percentile) |
| **Reset** | Resets display range to full 0-255 |

### Detected Fields Table

| Column | Description |
|--------|-------------|
| **Text** | The detected text (editable - click to correct errors) |
| **Type** | Region type: Text, Barcode, or Auto (dropdown to change) |
| **Metadata Key** | The metadata field name (editable - set your custom key names) |
| **Conf** | Confidence percentage (100% for barcodes) |

### Field Buttons

| Button | Description |
|--------|-------------|
| **Add Field** | Manually add a new empty field row |
| **Remove** | Delete the selected field row |
| **Clear All** | Remove all detected fields |

### Text Filter Bar

Quick-access buttons to clean up detected text:

| Button | Name | Action |
|--------|------|--------|
| `abcABC` | Letters Only | Removes numbers, symbols, whitespace |
| `123` | Numbers Only | Keeps only digits 0-9 |
| `aA1` | Alphanumeric | Keeps letters and numbers |
| `-_.` | Filename Safe | Keeps characters valid in filenames |
| `&*!` | Standard Chars | Removes unusual/control characters |
| `_ _` | No Whitespace | Replaces spaces with underscores |

Each filter button has a tooltip showing the exact regex pattern used.

### Vocabulary Matching

Correct OCR errors by matching against a list of known valid values:

| Control | Description |
|---------|-------------|
| **Load List...** | Load a vocabulary file (CSV, TSV, or TXT) |
| **?** | Help tooltip explaining vocabulary matching |
| **OCR weights** | Toggle OCR-aware character weighting (see below) |
| **Match** | Apply vocabulary matching to all fields |
| *(N entries)* | Shows number of loaded vocabulary items |

#### Vocabulary File Format

The vocabulary loader accepts:
- **CSV files**: Uses first column, handles quoted values
- **TSV files**: Uses first column (tab-separated)
- **TXT files**: One value per line

Header rows are automatically skipped if they contain keywords like: sample, name, id, code, label, value, specimen, patient, slide, case, date.

#### OCR Weights Toggle

**OFF (default)**: Standard matching - all character substitutions have equal cost.
- Best for scientific sample names where letter/number mixtures are intentional
- `PBS_O1` and `PBS_01` are treated as significantly different

**ON**: OCR-weighted matching - common OCR confusions have reduced penalty:
- `0` <-> `O` (zero vs letter O): 0.3 cost
- `1` <-> `l` <-> `I` (one vs L vs I): 0.3 cost
- `5` <-> `S`, `8` <-> `B`, `2` <-> `Z`: 0.5 cost
- Best for natural text where OCR errors are likely mistakes

### Template Bar

| Control | Description |
|---------|-------------|
| **Save Template...** | Save field positions, types, and metadata keys to JSON file |
| **Load Template...** | Load a previously saved template |
| **Use Fixed Positions** | When checked, uses template bounding boxes instead of running OCR |
| **Apply Template** | Extracts content from fixed positions using appropriate decoder |

Templates are saved as JSON files and can be shared between users or sessions.

### Bottom Controls

| Control | Description |
|---------|-------------|
| **Applying to: [name]** | Shows which image will receive the metadata |
| **Apply** | Saves the metadata to the selected project image |
| **Cancel** | Closes the dialog without saving |

---

## Batch OCR Dialog Reference

### Header Section

Displays information about the current project:
- Number of images with labels found
- Total images in project
- Step-by-step workflow instructions

### Template Section

| Control | Description |
|---------|-------------|
| **Create from Current Image** | Opens single-image OCR dialog to create a template |
| **Load Template...** | Load template from JSON file |
| **Save Template...** | Save current field mappings to JSON file |

#### Template Table Columns

| Column | Description |
|--------|-------------|
| **Use** | Checkbox to enable/disable each field |
| **Field #** | Sequential field number |
| **Type** | Region type: Text, Barcode, or Auto |
| **Metadata Key** | The metadata field name |
| **Example Text** | Sample text from when template was created |

### Results Section

After clicking **Process All**, results appear in the table:

| Column | Description |
|--------|-------------|
| **Image Name** | Project image filename |
| **Status** | Processing status: Pending, Processing..., Done, Error, Applied |
| **[Field columns]** | One editable column per enabled template field |

Results table columns are editable - click any cell to correct values before applying.

### Filter Bar

Same text filters and vocabulary matching as the single-image dialog:
- Character filter buttons
- **Load List...** / **?** / **OCR weights** / **Match All**

The **Match All** button applies vocabulary matching across ALL processed images at once.

### Progress Section

| Control | Description |
|---------|-------------|
| **Progress Bar** | Visual progress during batch processing |
| **Status Label** | Real-time status updates (e.g., "Processing 5 of 20: image.svs") |

### Bottom Controls

| Control | Description |
|---------|-------------|
| **Process All** | Run OCR/barcode scanning on all images using current template |
| **Apply Metadata** | Save metadata to all successfully processed images |
| **Cancel** | Close dialog and cancel any running processing |

---

## Template JSON Format

Templates store field positions and types for batch processing:

```json
{
  "name": "Lab Label Template",
  "version": "2.0",
  "dilationFactor": 0.2,
  "fieldMappings": [
    {
      "fieldIndex": 0,
      "metadataKey": "sample_id",
      "exampleText": "PBS_001",
      "regionType": "TEXT",
      "normalizedX": 0.05,
      "normalizedY": 0.10,
      "normalizedWidth": 0.40,
      "normalizedHeight": 0.12,
      "enabled": true
    },
    {
      "fieldIndex": 1,
      "metadataKey": "barcode_id",
      "exampleText": "ABC123XYZ",
      "regionType": "BARCODE",
      "normalizedX": 0.75,
      "normalizedY": 0.30,
      "normalizedWidth": 0.20,
      "normalizedHeight": 0.35,
      "enabled": true
    }
  ]
}
```

**Key fields:**
- `regionType`: `"TEXT"`, `"BARCODE"`, or `"AUTO"` (defaults to `"TEXT"` if missing)
- `normalizedX/Y/Width/Height`: Position as fraction of image dimensions (0.0-1.0)
- `dilationFactor`: Expansion factor for bounding boxes (default 0.2 = 20%)

---

## Scripting and Macros

You can automate OCR and barcode operations using Groovy scripts in QuPath. This is useful for:
- Integrating into automated pipelines
- Processing images without the GUI
- Custom workflows

### Getting Started with Scripts

All scripts should be run in QuPath's script editor (**Automate > Script Editor**).

### Basic OCR Script

```groovy
import qupath.ext.ocr4labels.controller.OCRController
import qupath.ext.ocr4labels.model.OCRConfiguration
import qupath.ext.ocr4labels.utilities.LabelImageUtility

// Get current image
def imageData = getCurrentImageData()
if (imageData == null) {
    println "No image open"
    return
}

// Check for label image
if (!LabelImageUtility.isLabelImageAvailable(imageData)) {
    println "No label image available"
    return
}

// Get the label image
def labelImage = LabelImageUtility.retrieveLabelImage(imageData)

// Configure OCR
def config = OCRConfiguration.builder()
    .language("eng")
    .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
    .minConfidence(0.5)
    .enhanceContrast(true)
    .build()

// Get controller (initializes engine if needed)
def controller = OCRController.getInstance()

// Run OCR
def result = controller.performOCR(labelImage, config)

// Print results
println "Found ${result.getBlockCount()} text blocks:"
result.getTextBlocks().each { block ->
    println "  [${block.getType()}] '${block.getText()}' (${(block.getConfidence() * 100) as int}%)"
}
```

### Barcode Detection Script

```groovy
import qupath.ext.ocr4labels.controller.OCRController
import qupath.ext.ocr4labels.utilities.LabelImageUtility

// Get current image
def imageData = getCurrentImageData()
def labelImage = LabelImageUtility.retrieveLabelImage(imageData)

// Get controller
def controller = OCRController.getInstance()

// Scan for barcodes
def result = controller.decodeBarcode(labelImage)

if (result.hasBarcode()) {
    println "Found ${result.getBarcodeCount()} barcode(s):"
    result.getBarcodes().each { barcode ->
        println "  [${barcode.getFormat()}] ${barcode.getText()}"
        if (barcode.getBoundingBox() != null) {
            def box = barcode.getBoundingBox()
            println "    Position: x=${box.getX()}, y=${box.getY()}, w=${box.getWidth()}, h=${box.getHeight()}"
        }
    }
} else {
    println "No barcodes detected"
}
```

### Unified Decoding Script (Auto Mode)

```groovy
import qupath.ext.ocr4labels.controller.OCRController
import qupath.ext.ocr4labels.model.OCRConfiguration
import qupath.ext.ocr4labels.model.RegionType
import qupath.ext.ocr4labels.utilities.LabelImageUtility
import java.awt.Rectangle

// Get label image
def imageData = getCurrentImageData()
def labelImage = LabelImageUtility.retrieveLabelImage(imageData)

// Define region to scan (x, y, width, height in pixels)
def region = new Rectangle(100, 50, 200, 100)

// Configure OCR (used for TEXT and AUTO fallback)
def config = OCRConfiguration.builder()
    .language("eng")
    .pageSegMode(OCRConfiguration.PageSegMode.SINGLE_BLOCK)
    .minConfidence(0.3)
    .build()

// Get controller
def controller = OCRController.getInstance()

// Decode with AUTO mode (tries barcode first, falls back to OCR)
def result = controller.decodeRegion(labelImage, region, RegionType.AUTO, config)

if (result.hasText()) {
    println "Detected: ${result.getText()}"
    println "Source: ${result.getSourceType()}"
    if (result.getFormat() != null) {
        println "Format: ${result.getFormat()}"
    }
    println "Confidence: ${(result.getConfidence() * 100) as int}%"
} else {
    println "No content detected in region"
}
```

### Apply OCR Results to Metadata

```groovy
import qupath.ext.ocr4labels.controller.OCRController
import qupath.ext.ocr4labels.model.OCRConfiguration
import qupath.ext.ocr4labels.utilities.LabelImageUtility
import qupath.ext.ocr4labels.utilities.OCRMetadataManager

// Get current image and project
def imageData = getCurrentImageData()
def project = getProject()
def entry = project.getEntry(imageData)

// Get label and run OCR
def labelImage = LabelImageUtility.retrieveLabelImage(imageData)
def config = OCRConfiguration.builder()
    .language("eng")
    .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
    .minConfidence(0.5)
    .build()

def controller = OCRController.getInstance()
def result = controller.performOCR(labelImage, config)

// Build metadata map from results
def metadata = [:]
def prefix = "ocr_"

result.getTextBlocks().eachWithIndex { block, index ->
    if (block.getType().toString() == "LINE" && !block.isEmpty()) {
        metadata["${prefix}field_${index}"] = block.getText()
    }
}

// Apply metadata to image entry
if (!metadata.isEmpty()) {
    OCRMetadataManager.setMetadataBatch(entry, metadata, project)
    println "Applied ${metadata.size()} metadata fields"
    metadata.each { k, v -> println "  ${k}: ${v}" }
} else {
    println "No text detected"
}
```

### Batch Process All Project Images

```groovy
import qupath.ext.ocr4labels.controller.OCRController
import qupath.ext.ocr4labels.model.OCRConfiguration
import qupath.ext.ocr4labels.model.RegionType
import qupath.ext.ocr4labels.utilities.LabelImageUtility
import qupath.ext.ocr4labels.utilities.OCRMetadataManager
import java.awt.Rectangle

// Configuration
def config = OCRConfiguration.builder()
    .language("eng")
    .pageSegMode(OCRConfiguration.PageSegMode.SPARSE_TEXT)
    .minConfidence(0.5)
    .build()

// Define regions to extract (normalized 0-1 coordinates)
def regions = [
    [key: "sample_id", type: RegionType.TEXT, x: 0.05, y: 0.10, w: 0.40, h: 0.12],
    [key: "barcode",   type: RegionType.BARCODE, x: 0.70, y: 0.20, w: 0.25, h: 0.40]
]

def project = getProject()
def controller = OCRController.getInstance()

int processed = 0
int failed = 0

project.getImageList().each { entry ->
    try {
        def imageData = entry.readImageData()

        if (!LabelImageUtility.isLabelImageAvailable(imageData)) {
            println "Skipping ${entry.getImageName()} - no label"
            return
        }

        def labelImage = LabelImageUtility.retrieveLabelImage(imageData)
        int imgW = labelImage.getWidth()
        int imgH = labelImage.getHeight()

        def metadata = [:]

        regions.each { r ->
            // Convert normalized coordinates to pixels
            def rect = new Rectangle(
                (int)(r.x * imgW),
                (int)(r.y * imgH),
                (int)(r.w * imgW),
                (int)(r.h * imgH)
            )

            def result = controller.decodeRegion(labelImage, rect, r.type, config)

            if (result.hasText()) {
                metadata[r.key] = result.getText()
            }
        }

        if (!metadata.isEmpty()) {
            OCRMetadataManager.setMetadataBatch(entry, metadata, project)
            processed++
            println "Processed: ${entry.getImageName()} -> ${metadata}"
        }

    } catch (Exception e) {
        failed++
        println "Error processing ${entry.getImageName()}: ${e.getMessage()}"
    }
}

println "\nComplete: ${processed} processed, ${failed} failed"
```

### Load and Apply a Template

```groovy
import qupath.ext.ocr4labels.controller.OCRController
import qupath.ext.ocr4labels.model.OCRConfiguration
import qupath.ext.ocr4labels.model.OCRTemplate
import qupath.ext.ocr4labels.utilities.LabelImageUtility
import qupath.ext.ocr4labels.utilities.OCRMetadataManager
import java.awt.Rectangle

// Load template from file
def templateFile = new File("/path/to/template.json")
def template = OCRTemplate.loadFromFile(templateFile)

println "Loaded template: ${template.getName()}"
println "Fields: ${template.getFieldMappings().size()}"

// Get current image
def imageData = getCurrentImageData()
def project = getProject()
def entry = project.getEntry(imageData)
def labelImage = LabelImageUtility.retrieveLabelImage(imageData)

int imgW = labelImage.getWidth()
int imgH = labelImage.getHeight()
double dilation = template.getDilationFactor()

// Build OCR config from template (or use defaults)
def config = template.getConfiguration() ?: OCRConfiguration.builder()
    .language("eng")
    .pageSegMode(OCRConfiguration.PageSegMode.SINGLE_BLOCK)
    .minConfidence(0.1)
    .build()

def controller = OCRController.getInstance()
def metadata = [:]

template.getFieldMappings().each { mapping ->
    if (!mapping.isEnabled() || !mapping.hasBoundingBox()) return

    // Get scaled bounding box with dilation
    int[] box = mapping.getScaledBoundingBox(imgW, imgH, dilation)
    if (box == null || box[2] < 5 || box[3] < 5) return

    def rect = new Rectangle(box[0], box[1], box[2], box[3])
    def regionType = mapping.getRegionType()

    def result = controller.decodeRegion(labelImage, rect, regionType, config)

    if (result.hasText()) {
        metadata[mapping.getMetadataKey()] = result.getText()
        println "  ${mapping.getMetadataKey()}: ${result.getText()} [${result.getSourceType()}]"
    }
}

// Apply metadata
if (!metadata.isEmpty()) {
    OCRMetadataManager.setMetadataBatch(entry, metadata, project)
    println "Applied ${metadata.size()} fields to ${entry.getImageName()}"
}
```

### Script Reference: Key Classes

| Class | Purpose |
|-------|---------|
| `OCRController` | Main entry point - get via `getInstance()` |
| `OCRConfiguration` | OCR settings (language, confidence, mode) |
| `RegionType` | Enum: `TEXT`, `BARCODE`, `AUTO` |
| `OCRResult` | Result of OCR operation, contains `TextBlock` list |
| `BarcodeResult` | Result of barcode scan, contains `DecodedBarcode` list |
| `OCRTemplate` | Template with field mappings for batch processing |
| `LabelImageUtility` | Helper to check/retrieve label images |
| `OCRMetadataManager` | Helper to read/write metadata to project entries |

### Script Reference: Key Methods

**OCRController:**
```groovy
// OCR
performOCR(BufferedImage image, OCRConfiguration config) -> OCRResult
performOCRAsync(BufferedImage image, OCRConfiguration config) -> CompletableFuture<OCRResult>

// Barcode
decodeBarcode(BufferedImage image) -> BarcodeResult
decodeBarcodeAsync(BufferedImage image) -> CompletableFuture<BarcodeResult>

// Unified (supports TEXT, BARCODE, AUTO)
decodeRegion(BufferedImage image, Rectangle region, RegionType type, OCRConfiguration config) -> DecodedResult
decodeRegionAsync(...) -> CompletableFuture<DecodedResult>
```

**OCRConfiguration.builder():**
```groovy
.language(String)           // "eng", "deu", "fra", etc.
.pageSegMode(PageSegMode)   // SPARSE_TEXT, SINGLE_BLOCK, etc.
.minConfidence(double)      // 0.0 to 1.0
.enhanceContrast(boolean)   // true/false
.autoRotate(boolean)        // true/false
.detectOrientation(boolean) // true/false
.build()
```

---

## OCR Settings Dialog Reference

Access via **Extensions > OCR for Labels > OCR Settings...**

### Required Downloads

| Link | Description |
|------|-------------|
| **eng.traineddata** | English language data (~4 MB) - Required |
| **osd.traineddata** | Orientation/script detection (~10 MB) - Optional but recommended |
| **Browse all languages** | Link to Tesseract tessdata repository |

Status indicators show `[Installed]` or `[Not found]` for each file.

### Tessdata Location

| Setting | Description |
|---------|-------------|
| **Tessdata Path** | Folder containing .traineddata files |
| **Browse** | Opens folder chooser |
| **Language** | Language code (e.g., `eng`, `deu`, `fra`, `chi_sim`) |
| **Label Keywords** | Comma-separated keywords to identify label images in metadata |

### Text Detection Settings

| Setting | Description |
|---------|-------------|
| **Detection Mode** | Default page segmentation mode |
| **Confidence Threshold** | Default minimum confidence (0-100%) |

### Image Enhancement

| Setting | Description |
|---------|-------------|
| **Detect Text Orientation** | Enable automatic rotation detection |
| **Auto-Rotate** | Automatically correct rotated text |
| **Enhance Contrast** | Improve visibility for faded labels |
| **Auto-Run OCR** | Automatically run OCR when switching images in the dialog |

### QuPath Metadata

| Setting | Description |
|---------|-------------|
| **Key Prefix** | Text prepended to all metadata field names (e.g., `ocr_`) |

### Other Controls

| Control | Description |
|---------|-------------|
| **Reset to Defaults** | Restore all settings to original values |

---

## Workflow Examples

### Example 1: Basic Single-Image OCR

1. Open a slide with a label
2. **Extensions > OCR for Labels > Run OCR on Label**
3. Click **Run OCR**
4. Edit metadata keys: `PBS_B_010` -> Key: `Sample_ID`
5. Click **Apply**
6. Check metadata in QuPath's image properties

### Example 2: Automatic Barcode Detection

1. Open a slide with a QR code or barcode on the label
2. **Extensions > OCR for Labels > Run OCR on Label**
3. Click **Find Barcodes**
4. Barcode(s) appear in the table with blue bounding boxes
5. Edit metadata key: `ABC123XYZ` -> Key: `Specimen_ID`
6. Click **Apply**

### Example 3: Mixed Template (Text + Barcode)

1. Open a sample image with both text and barcode on label
2. **Extensions > OCR for Labels > Run OCR on Label**
3. Click **Run OCR** to detect text regions
4. Click **Find Barcodes** to detect barcode regions
5. Edit metadata keys for each field
6. Change Type column to **Barcode** for barcode fields (if not auto-detected)
7. Click **Save Template...** -> save as `mixed_template.json`
8. Use template for batch processing

### Example 4: Batch Processing with Template

1. Open a project with 50+ slides
2. **Extensions > OCR for Labels > Run OCR on Project...**
3. Click **Create from Current Image**
4. In the OCR dialog, run OCR and set metadata keys
5. Click **Save Template...** -> save as `lab_template.json`
6. Close OCR dialog
7. Click **Load Template...** -> select `lab_template.json`
8. Click **Process All** (wait for completion)
9. Review results, edit any errors
10. Click **Apply Metadata**

### Example 5: Vocabulary Matching for Sample Names

You have a spreadsheet of expected sample names and OCR sometimes misreads them:

1. Export sample names to `samples.txt`:
   ```
   PBS_001
   PBS_002
   Sample_A1
   Sample_B2
   ```
2. In OCR dialog, run OCR on label
3. Click **Load List...** -> select `samples.txt`
4. Status shows "(4 entries)"
5. If OCR detected `PBS_0O1`, click **Match**
6. Value corrects to `PBS_001`

For natural text (not scientific codes), enable **OCR weights** checkbox before matching.

---

## Troubleshooting

### No text detected

- Try different **Mode** settings (Sparse Text often works best)
- Lower the **Min Conf** slider
- Enable **Enhance** for faded labels
- Check **Invert** for light-on-dark text

### No barcodes detected

- Click **Find Barcodes** to scan the entire image
- Try **Invert** for light barcodes on dark backgrounds
- Ensure barcode is not too small or damaged
- Use **Select Region** to manually select the barcode area

### Rotated or upside-down text

- Download `osd.traineddata` in Settings
- Enable **Detect Text Orientation** in Settings
- Use **Auto + Orientation** or **Sparse + Orientation** mode

### Wrong characters detected

- Use text filters to clean up results
- Load a vocabulary file and use **Match** to correct
- Try enabling/disabling **OCR weights** depending on your text type

### Template not matching new images

- Ensure labels are consistently positioned
- The 20% dilation helps with slight variations
- For very different layouts, create a new template

### Barcode detected as wrong format

- ZXing auto-detects format; this is usually correct
- If issues persist, use **Select Region** with **Barcode** type
- Ensure the barcode is not partially obscured

---

## Building from Source

```bash
./gradlew build
```

The extension JAR will be created in `build/libs/`.

## License

MIT License - see [LICENSE](LICENSE) for details.

## Acknowledgments

- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) - Open source OCR engine
- [Tess4J](https://github.com/nguyenq/tess4j) - Java JNA wrapper for Tesseract
- [ZXing](https://github.com/zxing/zxing) - Open source barcode scanning library
- [QuPath](https://qupath.github.io/) - Open source software for bioimage analysis
