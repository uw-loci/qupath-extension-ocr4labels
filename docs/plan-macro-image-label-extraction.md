# Plan: Macro Image Label Extraction

**Date:** 2026-04-01
**Status:** Proposed
**Issue:** Some slide scanners only provide a "macro" associated image with the label embedded on one side of a much longer image. No separate "label" associated image exists.

## Problem Description

Certain slide scanners (e.g., some Hamamatsu, 3DHISTECH, Leica models) produce a single "macro" associated image that combines:
- A low-resolution tissue overview (the majority of the image)
- A label region on one end (left, right, top, or bottom)

The current OCR4Labels workflow expects a dedicated "label" associated image. When only a macro image exists, the extension cannot find a label to process.

### Visual Layout Examples

```
Horizontal macro (label on left):
+--------+---------------------------+
| LABEL  |     TISSUE OVERVIEW       |
| region |                           |
+--------+---------------------------+

Horizontal macro (label on right):
+---------------------------+--------+
|     TISSUE OVERVIEW       | LABEL  |
|                           | region |
+---------------------------+--------+

The label text itself may be rotated 90 or 270 degrees
relative to the macro image orientation.
```

## Proposed Approach

### Phase 1: Macro Image Fallback in LabelImageUtility

**File:** `LabelImageUtility.java`

When no "label"/"barcode" associated image is found, fall back to searching for a "macro" associated image. Add "macro" to the fallback keyword list (not the primary list, since we still prefer dedicated label images when available).

```java
// Current: searches for "label", "barcode" keywords only
// Proposed: if no label found, try "macro" keyword as fallback
```

**Changes:**
1. Add a new method `retrieveLabelFromMacroImage(ImageData)` that:
   - Finds the "macro" associated image
   - Passes it to the label region extractor (Phase 2)
   - Returns the extracted label portion, or null
2. Modify `retrieveLabelImage()` to call the macro fallback when no dedicated label image is found

### Phase 2: Label Region Detection from Macro Images

**New class:** `MacroLabelExtractor` in the `utilities/` package

This is the core algorithm. The label portion of a macro image has distinct characteristics:
- Labels are typically white/bright with printed text
- Tissue overview is typically darker, more colorful, and textured
- The label is on one end of the image (not in the middle)
- Labels have a characteristic aspect ratio (~1:2 to ~1:3)

**Algorithm:**

1. **Aspect ratio analysis** - Macro images with labels are typically elongated (width >> height or vice versa). Determine the "long axis" of the image.

2. **Brightness profile along long axis** - Compute mean brightness in slices perpendicular to the long axis. The label region will show as a consistently bright zone at one end.

   ```
   Brightness profile (horizontal macro, label on left):
   
   High |####
        |####
        |####....
        |    ......................
   Low  |       tissue overview
        +-----------------------------------> x
         ^label^
   ```

3. **Edge detection** - Find the transition point between the bright label and the tissue region:
   - Scan brightness profile from each end
   - Find where brightness drops below a threshold (or changes significantly)
   - The label-tissue boundary is typically sharp

4. **Label extraction** - Crop the image at the detected boundary, keeping the label side with some margin.

5. **Orientation detection** - The extracted label may need rotation:
   - If the macro is horizontal but the label text reads vertically, rotate 90 or 270 degrees
   - Use Tesseract's OSD (orientation/script detection) on the extracted region
   - Try multiple rotations (0, 90, 180, 270) and pick the one that yields the best OCR confidence
   - The existing `detectOrientation()` in OCREngine already does this for 180-degree flips; extend to handle 90/270

**Key parameters (should be configurable via preferences):**
- `macroLabelBrightnessThreshold` - brightness level to distinguish label from tissue (default: 180)
- `macroLabelMinFraction` - minimum fraction of image that label should occupy (default: 0.1 = 10%)
- `macroLabelMaxFraction` - maximum fraction (default: 0.4 = 40%)

### Phase 3: UI Integration

**Changes to OCRDialog:**
1. When loading a label image fails but a macro image is available, show a notification: "No dedicated label image found. Extracting label from macro image..."
2. Display the extracted label region (not the full macro) in the image preview
3. Add a "Source" indicator showing whether the image came from "label", "barcode", or "macro (extracted)"

**Changes to OCRPreferences:**
1. Add preference: `enableMacroLabelExtraction` (default: true)
2. Add preference: `macroImageKeywords` (default: "macro") - keywords to match macro associated images
3. Optionally: Add preference for which end to look for the label (auto, left, right, top, bottom)

**Changes to BatchOCRDialog:**
1. Apply the same macro fallback logic during batch processing
2. Log when macro extraction is used vs. dedicated label image

### Phase 4: Multi-Rotation OCR Strategy

For labels extracted from macro images, text orientation is uncertain. Implement a rotation-retry strategy:

1. Run OCR at 0 degrees
2. If confidence is low (<50% average), try 90, 180, 270 degrees
3. Keep the result with highest average confidence
4. Cache the winning rotation for subsequent images from the same scanner (they'll likely all be the same)

This can reuse and extend the existing `detectOrientation()` and `rotateImage()` methods in `OCREngine`.

## Implementation Order

1. **Phase 2 first** - `MacroLabelExtractor` is the core logic and can be unit tested independently with sample images
2. **Phase 1** - Wire the extractor into `LabelImageUtility` as a fallback
3. **Phase 4** - Multi-rotation OCR (may already work partially via existing orientation detection)
4. **Phase 3** - UI polish (notifications, preferences)

## Testing Strategy

- Unit tests with synthetic macro images (bright rectangle on one end of a larger dark image)
- Integration tests with real macro images from different scanner vendors (if available)
- Edge cases:
  - Very small labels (10% of macro)
  - Labels with dark backgrounds (inverted labels)
  - Labels with barcodes (need to verify barcode detection still works on extracted region)
  - Macro images where the label is barely distinguishable from tissue

## Risk Assessment

- **Medium risk:** Label detection heuristics may not work for all scanner vendors. The brightness-based approach assumes labels are brighter than tissue, which is usually true but not guaranteed.
- **Low risk:** Rotation detection may occasionally pick wrong orientation if label text is minimal.
- **Mitigation:** Make the feature opt-in initially and provide manual override (user can specify which end the label is on).

## Open Questions

1. Should users be able to manually specify the label region within a macro image? (e.g., draw a box on the macro to define the label area, similar to template regions)
2. Are there scanner formats where the macro image is vertical rather than horizontal?
3. Should we support labels that are in the middle of a macro image (not at an end)?
