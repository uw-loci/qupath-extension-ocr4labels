/**
 * Basic OCR Detection
 * -------------------
 * Runs OCR on the current image's label and prints results.
 *
 * This script demonstrates the simplest use of the OCR4Labels API.
 * Run on a single image to test OCR settings before batch processing.
 */
import qupath.ext.ocr4labels.OCR4Labels

// Check if label image exists
if (!OCR4Labels.hasLabelImage()) {
    println "No label image found for this slide"
    println "Available associated images: " + OCR4Labels.getAssociatedImageNames()
    return
}

println "Processing: " + OCR4Labels.getCurrentImageName()

// Run OCR with default settings (auto page segmentation, enhanced contrast)
def results = OCR4Labels.builder()
    .autoDetect()
    .enhance()
    .minConfidence(0.5)
    .run()

// Print detected text
if (results.isEmpty()) {
    println "No text detected - try adjusting settings:"
    println "  - Lower confidence threshold"
    println "  - Try different page segmentation modes"
    println "  - Check if label needs inversion (dark background)"
} else {
    println "Detected ${results.size()} text fields:"
    results.eachWithIndex { text, i ->
        println "  Field ${i}: ${text}"
    }

    // Example: Set first field as case ID
    if (results.size() > 0) {
        OCR4Labels.setMetadataValue("OCR_CaseID", results[0])
        println "\nSet OCR_CaseID = ${results[0]}"
    }
}
