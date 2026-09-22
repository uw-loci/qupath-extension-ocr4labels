/**
 * Conditional OCR Processing
 * --------------------------
 * Apply different OCR settings based on image name or other criteria.
 *
 * Use this template when you have images from different sources
 * that require different processing settings.
 */
import qupath.ext.ocr4labels.OCR4Labels

def imageName = OCR4Labels.getCurrentImageName()
println "Processing: ${imageName}"

// Skip if no label image
if (!OCR4Labels.hasLabelImage()) {
    println "  SKIP: No label image"
    return
}

// Start building the OCR configuration
def builder = OCR4Labels.builder()

// ============================================================
// CUSTOMIZE CONDITIONS BASED ON YOUR IMAGE NAMING CONVENTIONS
// ============================================================

// Example 1: Dark background labels (vendor-specific)
if (imageName.contains("DARK") || imageName.startsWith("VendorX_")) {
    println "  Using inverted mode for dark label"
    builder.invert()
}

// Example 2: Rotated labels
if (imageName.contains("ROTATED") || imageName.contains("_90_")) {
    println "  Enabling orientation detection"
    builder.detectOrientation().autoRotate()
}

// Example 3: Faded or low-contrast labels
if (imageName.contains("OLD") || imageName.contains("_faded")) {
    println "  Using lower confidence for faded label"
    builder.minConfidence(0.3)
} else {
    builder.minConfidence(0.5)
}

// Example 4: Different modes for different label types
if (imageName.contains("_simple_")) {
    // Simple labels with just one line
    println "  Using single line mode"
    builder.singleLine()
} else if (imageName.contains("_block_")) {
    // Dense block of text
    println "  Using single block mode"
    builder.singleBlock()
} else {
    // Default: let Tesseract work out the layout
    builder.autoDetect()
}

// Always use enhancement
builder.enhance()

// ============================================================

// Run OCR with the customized settings
def results = builder.run()

if (results.isEmpty()) {
    println "  WARN: No text detected"
    return
}

println "  Found ${results.size()} fields"

// Apply standard mappings
// Customize these metadata keys as needed
if (results.size() >= 1) {
    OCR4Labels.setMetadataValue("OCR_field_0", results[0])
    println "  Set OCR_field_0 = ${results[0]}"
}
if (results.size() >= 2) {
    OCR4Labels.setMetadataValue("OCR_field_1", results[1])
    println "  Set OCR_field_1 = ${results[1]}"
}
if (results.size() >= 3) {
    OCR4Labels.setMetadataValue("OCR_field_2", results[2])
    println "  Set OCR_field_2 = ${results[2]}"
}

println "  OK"
