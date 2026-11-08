package com.venuenexus.resource.qr;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MatrixQrImagesTest {
    private static boolean[][] sample() {
        boolean[][] m = new boolean[21][21];
        for (int y = 0; y < 21; y++) for (int x = 0; x < 21; x++) m[y][x] = (x + y) % 3 == 0;
        return m;
    }

    @Test void svgHasQuietZone() {
        String svg = MatrixQrImages.svg(sample());
        assertTrue(svg.contains("viewBox=\"0 0 29 29\""));
        assertTrue(svg.startsWith("<svg") && svg.endsWith("</svg>"));
    }

    @Test void pngIsValidAndSizedWithQuietZone() throws Exception {
        var img = ImageIO.read(new ByteArrayInputStream(MatrixQrImages.png(sample())));
        assertEquals(29 * MatrixQrImages.PNG_PIXELS_PER_MODULE, img.getWidth());
        assertEquals(0xFFFFFF, img.getRGB(0, 0) & 0xFFFFFF);
    }
}
