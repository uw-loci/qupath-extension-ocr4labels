package qupath.ext.ocr4labels.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import qupath.ext.ocr4labels.model.RegionType;

class RegionCropTest {

    /** "610 TOMO" in black on white, ink roughly x 40..190, y 42..70. */
    private static BufferedImage label(Color background, Color ink) {
        BufferedImage img = new BufferedImage(300, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, 300, 120);
        g.setColor(ink);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.drawString("610 TOMO", 40, 70);
        g.dispose();
        return img;
    }

    private static Rectangle inkBounds(BufferedImage img, boolean lightInk) {
        Rectangle r = null;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int v = img.getRGB(x, y) & 0xff;
                if (lightInk ? v > 127 : v < 128) {
                    r = r == null ? new Rectangle(x, y, 1, 1) : r.union(new Rectangle(x, y, 1, 1));
                }
            }
        }
        return r;
    }

    @Test
    void sloppyAndTightDrawingsSnapToTheSameBox() {
        BufferedImage img = label(Color.WHITE, Color.BLACK);
        Rectangle ink = inkBounds(img, false);

        Rectangle loose = RegionCrop.snapToInk(img, new Rectangle(10, 10, 280, 100), false);
        Rectangle tight = RegionCrop.snapToInk(img,
                new Rectangle(ink.x - 6, ink.y - 6, ink.width + 12, ink.height + 12), false);

        assertThat(loose).isEqualTo(tight);
        assertThat(loose.contains(ink)).isTrue();
        assertThat(loose.width).isLessThanOrEqualTo(ink.width + 2 * RegionCrop.SNAP_MARGIN + 2);
    }

    @Test
    void neverGrowsPastTheDrawnRectangle() {
        BufferedImage img = label(Color.WHITE, Color.BLACK);
        Rectangle drawn = new Rectangle(60, 40, 60, 40); // cuts through the text
        Rectangle snapped = RegionCrop.snapToInk(img, drawn, false);
        assertThat(drawn.contains(snapped)).isTrue();
    }

    @Test
    void lightTextOnDarkSnapsWhenInverted() {
        BufferedImage img = label(Color.BLACK, Color.WHITE);
        Rectangle ink = inkBounds(img, true);
        Rectangle snapped = RegionCrop.snapToInk(img, new Rectangle(10, 10, 280, 100), true);
        assertThat(snapped.contains(ink)).isTrue();
        assertThat(snapped.width).isLessThan(200);
    }

    @Test
    void blankRegionIsLeftAsDrawn() {
        BufferedImage img = label(Color.WHITE, Color.BLACK);
        Rectangle drawn = new Rectangle(200, 80, 60, 30);
        assertThat(RegionCrop.snapToInk(img, drawn, false)).isEqualTo(drawn);
    }

    @Test
    void textMarginFollowsHeightNotWidth() {
        assertThat(RegionCrop.margin(400, 20, RegionType.TEXT)).containsExactly(5, 5);
        assertThat(RegionCrop.margin(400, 20, RegionType.AUTO)).containsExactly(5, 5);
        assertThat(RegionCrop.margin(400, 20, RegionType.BARCODE)).containsExactly(50, 3);
    }

    @Test
    void quietBorderIsWiderWhereABarcodeIsPossible() {
        assertThat(RegionCrop.quietBorder(400, 20, RegionType.TEXT)).containsExactly(8, 8);
        assertThat(RegionCrop.quietBorder(400, 20, RegionType.AUTO)).containsExactly(40, 8);
    }

    @Test
    void borderColourIsTheRegionBackground() {
        BufferedImage grey = label(new Color(170, 170, 170), Color.BLACK);
        assertThat(RegionCrop.backgroundRgb(grey) & 0xffffff).isEqualTo(0xaaaaaa);
        BufferedImage dark = label(Color.BLACK, Color.WHITE);
        assertThat(RegionCrop.backgroundRgb(dark) & 0xffffff).isEqualTo(0x000000);
    }
}
