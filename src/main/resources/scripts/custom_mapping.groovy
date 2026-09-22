/**
 * OCR with Custom Field Mapping
 * -----------------------------
 * Maps specific detected fields to metadata keys.
 *
 * Customize the field mappings below based on your label layout.
 * Field indices are 0-based (first field = 0, second = 1, etc.)
 */
import qupath.ext.ocr4labels.OCR4Labels

if (!OCR4Labels.hasLabelImage()) {
    println "SKIP: No label image available"
    return
}

def imageName = OCR4Labels.getCurrentImageName()
println "Processing: ${imageName}"

// Run OCR
def results = OCR4Labels.builder()
    .autoDetect()
    .enhance()
    .minConfidence(0.5)
    .run()

if (results.isEmpty()) {
    println "WARN: No text detected"
    return
}

println "Found ${results.size()} fields:"
results.eachWithIndex { text, i ->
    println "  [${i}] ${text}"
}

// ============================================================
// CUSTOMIZE YOUR FIELD MAPPINGS HERE
// ============================================================
// Map field indices to your desired metadata keys.
// Adjust based on your label layout.
//
// Example: If your label has:
//   Field 0: Patient ID
//   Field 1: Case Number
//   Field 2: Slide Number
//
// Then use:
//   0: "PatientID",
//   1: "CaseNumber",
//   2: "SlideNumber"

def mappings = [
    0: "OCR_field_0",      // First detected field
    1: "OCR_field_1",      // Second detected field
    2: "OCR_field_2"       // Third detected field
]

// ============================================================

// Apply mappings
println "\nApplying metadata:"
mappings.each { fieldIndex, metadataKey ->
    if (fieldIndex < results.size()) {
        def value = results[fieldIndex]
        OCR4Labels.setMetadataValue(metadataKey, value)
        println "  ${metadataKey} = ${value}"
    } else {
        println "  ${metadataKey} = (field ${fieldIndex} not available)"
    }
}

println "\nDone!"
