package com.venuenexus.resource.qr;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ZxingQrRendererTest {

    private final ZxingQrRenderer renderer = new ZxingQrRenderer();

    @Test
    void svgRendersValidSvgStructure() {
        String content = "https://venue.test/q/alJKG4A14KAgC0ZsK4bDZQ";
        String svg = renderer.svg(content);

        assertNotNull(svg);
        assertTrue(svg.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""), "Must start with svg tag and namespace");
        assertTrue(svg.endsWith("</svg>"), "Must close svg tag");
        assertTrue(svg.contains("viewBox=\"0 0 "), "Must declare viewBox");
        assertTrue(svg.contains("shape-rendering=\"crispEdges\""), "Must declare crispEdges");
        assertTrue(svg.contains("<rect width="), "Must contain background rect");
        assertTrue(svg.contains("<path fill=\"#000\" d=\""), "Must contain QR module path data");
    }

    @Test
    void pngRendersValidPngWithStandardHeaderAndDimensions() throws IOException {
        String content = "https://venue.test/q/alJKG4A14KAgC0ZsK4bDZQ";
        byte[] png = renderer.png(content);

        assertNotNull(png);
        assertTrue(png.length > 50, "PNG output must not be empty");

        // Verify standard 8-byte PNG header: 0x89 50 4E 47 0D 0A 1A 0A
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]); // 'P'
        assertEquals((byte) 0x4E, png[2]); // 'N'
        assertEquals((byte) 0x47, png[3]); // 'G'
        assertEquals((byte) 0x0D, png[4]);
        assertEquals((byte) 0x0A, png[5]);
        assertEquals((byte) 0x1A, png[6]);
        assertEquals((byte) 0x0A, png[7]);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(img, "PNG byte array must be decodable by ImageIO");
        assertTrue(img.getWidth() > 0, "Image width must be positive");
        assertEquals(img.getWidth(), img.getHeight(), "QR image must be square");
        // Verify quiet zone pixel at (0, 0) is white (0xFFFFFF)
        assertEquals(0xFFFFFF, img.getRGB(0, 0) & 0xFFFFFF, "Top-left quiet zone pixel must be white");
    }

    @Test
    void rendersVariousUrlsAndTokensWithoutException() {
        assertDoesNotThrow(() -> renderer.svg("https://example.com/q/token123"));
        assertDoesNotThrow(() -> renderer.png("https://example.com/q/token123"));
        assertDoesNotThrow(() -> renderer.svg("urn:venuenexus:resource:12345678-1234-1234-1234-123456789abc"));
    }
}
