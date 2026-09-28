package qupath.ext.ocr4labels.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import qupath.ext.ocr4labels.model.OCRConfiguration;
import qupath.ext.ocr4labels.model.RegionType;

/** Opt-in probe against a real Tesseract: set OCR4LABELS_TESSDATA (and jna.library.path). */
@EnabledIfEnvironmentVariable(named = "OCR4LABELS_TESSDATA", matches = ".+")
class RegionDecodeProbeTest {

    static BufferedImage render(String text, int fontSize, int bg, int pad) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, fontSize);
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D g0 = tmp.createGraphics();
        int w = g0.getFontMetrics(font).stringWidth(text) + 2 * pad;
        int h = g0.getFontMetrics(font).getHeight() + 2 * pad;
        g0.dispose();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(bg, bg, bg));
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.setFont(font);
        g.drawString(text, pad, pad + g.getFontMetrics().getAscent());
        g.dispose();
        return img;
    }

    static BufferedImage border(BufferedImage region, int pad, int fill) {
        BufferedImage out = new BufferedImage(region.getWidth() + 2 * pad, region.getHeight() + 2 * pad,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new Color(fill, fill, fill));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());
        g.drawImage(region, pad, pad, null);
        g.dispose();
        return out;
    }

    @Test
    void borderProbe() throws Exception {
        OCREngine engine = new OCREngine();
        engine.initialize(System.getenv("OCR4LABELS_TESSDATA"), "eng");
        UnifiedDecoderService decoder = new UnifiedDecoderService(engine);
        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(OCRConfiguration.PageSegMode.AUTO).language("eng").minConfidence(0.1)
                .autoRotate(true).detectOrientation(true).enhanceContrast(false)
                .enablePreprocessing(true).literalText(true).build();
        for (int fontSize : new int[] {14, 22}) {
            for (int bg : new int[] {230, 200, 170, 140}) {
                for (String mode : new String[] {"none", "white", "matched"}) {
                    BufferedImage img = render("610 TOMO", fontSize, bg, 3);
                    int pad = Math.max(8, Math.round(img.getHeight() * 0.2f));
                    if (mode.equals("white")) img = border(img, pad, 255);
                    if (mode.equals("matched")) img = border(img, pad, bg);
                    var r = decoder.decodeRegion(img, null, RegionType.AUTO, config);
                    System.out.printf("BORDER font=%d bg=%d border=%s -> [%s]%n", fontSize, bg, mode, r.getText());
                }
            }
        }
    }

    /** Full-image Scan, as the dialog runs it, on the image named by PROBE_IMAGE. */
    @Test
    @EnabledIfEnvironmentVariable(named = "PROBE_IMAGE", matches = ".+")
    void fullImageProbe() throws Exception {
        OCREngine engine = new OCREngine();
        engine.initialize(System.getenv("OCR4LABELS_TESSDATA"), "eng");
        BufferedImage img = qupath.ext.ocr4labels.utilities.LabelImageUtility.normalizeToEightBit(
                javax.imageio.ImageIO.read(new java.io.File(System.getenv("PROBE_IMAGE"))));
        if (System.getenv("PROBE_SAVE") != null) {
            javax.imageio.ImageIO.write(img, "png", new java.io.File(System.getenv("PROBE_SAVE")));
        }
        for (String psmName : System.getenv().getOrDefault("PROBE_PSMS", "AUTO").split(",")) {
            OCRConfiguration config = OCRConfiguration.builder()
                    .pageSegMode(OCRConfiguration.PageSegMode.valueOf(psmName)).language("eng")
                    .minConfidence(Double.parseDouble(System.getenv().getOrDefault("PROBE_MINCONF", "0.5")))
                    .autoRotate(true).detectOrientation(true).enhanceContrast(false)
                    .enablePreprocessing(true).literalText(true).build();
            var result = engine.processImage(img, config);
            for (var b : result.getTextBlocks()) {
                System.out.printf("FULL psm=%s %s [%s] conf=%.2f box=%s%n", psmName, b.getType(), b.getText(),
                        b.getConfidence(), b.getBoundingBox());
            }
        }
    }

    /** A drawn region (PROBE_REGION=x,y,w,h) on PROBE_IMAGE, cropped as the dialog does. */
    @Test
    @EnabledIfEnvironmentVariable(named = "PROBE_REGION", matches = ".+")
    void drawnRegionProbe() throws Exception {
        OCREngine engine = new OCREngine();
        engine.initialize(System.getenv("OCR4LABELS_TESSDATA"), "eng");
        UnifiedDecoderService decoder = new UnifiedDecoderService(engine);
        BufferedImage label = qupath.ext.ocr4labels.utilities.LabelImageUtility.normalizeToEightBit(
                javax.imageio.ImageIO.read(new java.io.File(System.getenv("PROBE_IMAGE"))));
        String[] p = System.getenv("PROBE_REGION").split(",");
        java.awt.Rectangle drawn = new java.awt.Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        boolean old = System.getenv("PROBE_OLD") != null;
        java.awt.Rectangle snapped = old ? drawn
                : qupath.ext.ocr4labels.utilities.RegionCrop.snapToInk(label, drawn, false);
        for (RegionType type : new RegionType[] {RegionType.TEXT, RegionType.AUTO}) {
          for (double mf : new double[] {0.1, 0.25, 0.4}) {
            int[] m = new int[] {(int) Math.round(snapped.height * mf), (int) Math.round(snapped.height * mf)};
            if (old) {
                double d = type == RegionType.TEXT ? 0.15 : 0.25;
                m = new int[] {(int) Math.round(snapped.width * d / 2), (int) Math.round(snapped.height * d / 2)};
            }
            java.awt.Rectangle box = new java.awt.Rectangle(snapped.x - m[0], snapped.y - m[1],
                    snapped.width + 2 * m[0], snapped.height + 2 * m[1])
                    .intersection(new java.awt.Rectangle(0, 0, label.getWidth(), label.getHeight()));
            BufferedImage crop = label.getSubimage(box.x, box.y, box.width, box.height);
            int[] q = qupath.ext.ocr4labels.utilities.RegionCrop.quietBorder(crop.getWidth(), crop.getHeight(), type);
            if (old) {
                int pad = Math.max(8, Math.round(crop.getHeight() * 0.2f));
                q = new int[] {pad, pad};
            }
            BufferedImage padded = new BufferedImage(crop.getWidth() + 2 * q[0], crop.getHeight() + 2 * q[1],
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = padded.createGraphics();
            g.setColor(old ? Color.WHITE : new Color(qupath.ext.ocr4labels.utilities.RegionCrop.backgroundRgb(crop)));
            g.fillRect(0, 0, padded.getWidth(), padded.getHeight());
            g.drawImage(crop, q[0], q[1], null);
            g.dispose();
            for (String psmName : System.getenv().getOrDefault("PROBE_PSMS", "AUTO").split(",")) {
                OCRConfiguration config = OCRConfiguration.builder()
                        .pageSegMode(OCRConfiguration.PageSegMode.valueOf(psmName)).language("eng")
                        .minConfidence(0.1).autoRotate(true).detectOrientation(true)
                        .enhanceContrast(false).enablePreprocessing(true).literalText(true).build();
                var r = decoder.decodeRegion(padded, null, type, config);
                System.out.printf("DRAWN %s drawn=%s type=%s margin=%.2f -> [%s] conf=%.2f%n", old ? "OLD" : "NEW",
                        drawn, type, mf, r.getText(), r.getConfidence());
            }
          }
        }
    }

    @Test
    void noisyProbe() throws Exception {
        OCREngine engine = new OCREngine();
        engine.initialize(System.getenv("OCR4LABELS_TESSDATA"), "eng");
        UnifiedDecoderService decoder = new UnifiedDecoderService(engine);
        OCRConfiguration config = OCRConfiguration.builder()
                .pageSegMode(OCRConfiguration.PageSegMode.valueOf(
                        System.getProperty("probe.psm", System.getenv().getOrDefault("PROBE_PSM", "AUTO")))).language("eng").minConfidence(0.1)
                .autoRotate(true).detectOrientation(true).enhanceContrast(false)
                .enablePreprocessing(true).literalText(true).build();
        java.util.Random rnd = new java.util.Random(1);
        for (String text : new String[] {"610 TOMO", "H&E 12", "S-24-0193"}) {
            for (int fontSize : new int[] {12, 16, 22}) {
                for (int bg : new int[] {220, 180}) {
                    for (String mode : new String[] {"white", "matched"}) {
                        BufferedImage img = render(text, fontSize, bg, 3);
                        for (int y = 0; y < img.getHeight(); y++) {
                            for (int x = 0; x < img.getWidth(); x++) {
                                int v = (img.getRGB(x, y) & 0xff) + (int) (rnd.nextGaussian() * 12);
                                v = Math.max(0, Math.min(255, v));
                                img.setRGB(x, y, (v << 16) | (v << 8) | v);
                            }
                        }
                        int pad = Math.max(8, Math.round(img.getHeight() * 0.2f));
                        img = border(img, pad, mode.equals("white") ? 255 : bg);
                        var r = decoder.decodeRegion(img, null, RegionType.AUTO, config);
                        System.out.printf("NOISY '%s' font=%d bg=%d border=%s -> [%s]%n",
                                text, fontSize, bg, mode, r.getText());
                    }
                }
            }
        }
    }

    @Test
    void probe() throws Exception {
        OCREngine engine = new OCREngine();
        engine.initialize(System.getenv("OCR4LABELS_TESSDATA"), "eng");
        UnifiedDecoderService decoder = new UnifiedDecoderService(engine);
        for (OCRConfiguration.PageSegMode psm : new OCRConfiguration.PageSegMode[] {
                OCRConfiguration.PageSegMode.AUTO, OCRConfiguration.PageSegMode.SINGLE_BLOCK,
                OCRConfiguration.PageSegMode.SINGLE_LINE}) {
            for (boolean orient : new boolean[] {true, false}) {
                for (int bg : new int[] {255, 225}) {
                    for (int pad : new int[] {2, 12}) {
                        for (RegionType type : new RegionType[] {RegionType.TEXT, RegionType.AUTO}) {
                            OCRConfiguration config = OCRConfiguration.builder()
                                    .pageSegMode(psm).language("eng").minConfidence(0.1)
                                    .autoRotate(true).detectOrientation(orient)
                                    .enhanceContrast(false).enablePreprocessing(true)
                                    .literalText(true).build();
                            BufferedImage img = render("610 TOMO", 22, bg, pad);
                            var r = decoder.decodeRegion(img, null, type, config);
                            System.out.printf("PROBE psm=%s orient=%s bg=%d pad=%d type=%s -> [%s]%n",
                                    psm, orient, bg, pad, type, r.getText());
                        }
                    }
                }
            }
        }
    }
}
