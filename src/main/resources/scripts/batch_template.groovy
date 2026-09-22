/**
 * Batch OCR Processing Template
 * -----------------------------
 * Run this script on multiple images via:
 *   Run -> Run for Project
 *
 * This template processes all images in your project that have
 * label images and applies OCR results as metadata.
 *
 * BEFORE RUNNING:
 * 1. Test OCR on a single image to verify settings work
 * 2. Customize the field mappings below
 * 3. Save this script
 * 4. Use "Run for Project" to process all images
 */
import qupath.ext.ocr4labels.OCR4Labels

def imageName = OCR4Labels.getCurrentImageName()

// Skip images without labels
if (!OCR4Labels.hasLabelImage()) {
    println "SKIP: ${imageName} - no label image"
    return
}

// ============================================================
// CONFIGURE OCR SETTINGS
// ============================================================
// Adjust these settings based on your test results

def results = OCR4Labels.builder()
    .autoDetect()           // Default; try .sparseText() for widely scattered text
    .enhance()              // Improve contrast
    // .invert()            // Uncomment for dark backgrounds
    .minConfidence(0.5)     // Adjust if missing text (lower) or too much noise (higher)
    // .detectOrientation() // Uncomment if some labels are rotated
    // .autoRotate()        // Uncomment to auto-correct rotation
    .run()

// ============================================================

// Check results
if (results.isEmpty()) {
    println "WARN: ${imageName} - no text detected"
    return
}

// ============================================================
// CUSTOMIZE FIELD MAPPINGS
// ============================================================
// Map detected field indices to metadata keys.
// Modify based on your label layout.

// Option 1: Map by index (assumes consistent label layout)
if (results.size() > 0) {
    OCR4Labels.setMetadataValue("OCR_field_0", results[0])
}
if (results.size() > 1) {
    OCR4Labels.setMetadataValue("OCR_field_1", results[1])
}
if (results.size() > 2) {
    OCR4Labels.setMetadataValue("OCR_field_2", results[2])
}

// Option 2: Parse specific patterns (uncomment and customize)
// results.each { text ->
//     // Example: Extract case ID from pattern like "CASE-12345"
//     if (text =~ /CASE-\d+/) {
//         OCR4Labels.setMetadataValue("CaseID", text)
//     }
//     // Example: Extract date
//     if (text =~ /\d{2}[-\/]\d{2}[-\/]\d{4}/) {
//         OCR4Labels.setMetadataValue("Date", text)
//     }
// }

// ============================================================

// Store field count for reference
OCR4Labels.setMetadataValue("OCR_field_count", String.valueOf(results.size()))

println "OK: ${imageName} - ${results.size()} fields"
