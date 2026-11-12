package com.venuenexus.resource.qr;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** Turns a QR module matrix into SVG / PNG. Pure JDK, so it is unit-testable without ZXing. */
public final class MatrixQrImages {

    public static final int QUIET_ZONE = 4; // modules, per the QR spec
    public static final int PNG_PIXELS_PER_MODULE = 12;

    private MatrixQrImages() {}

    public static String svg(boolean[][] m) {
        int n = m.length;
        int size = n + 2 * QUIET_ZONE;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < n; y++) {
            int x = 0;
            while (x < n) {
                if (m[y][x]) {
                    int run = 1;
                    while (x + run < n && m[y][x + run]) run++;
                    path.append('M').append(x + QUIET_ZONE).append(' ').append(y + QUIET_ZONE)
                        .append('h').append(run).append("v1h-").append(run).append('z');
                    x += run;
                } else {
                    x++;
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + size + " " + size
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code\">"
                + "<rect width=\"" + size + "\" height=\"" + size + "\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + path + "\"/></svg>";
    }

    public static byte[] png(boolean[][] m) {
        int n = m.length;
        int px = PNG_PIXELS_PER_MODULE;
        int size = (n + 2 * QUIET_ZONE) * px;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int mx = x / px - QUIET_ZONE;
                int my = y / px - QUIET_ZONE;
                boolean dark = mx >= 0 && my >= 0 && mx < n && my < n && m[my][mx];
                img.setRGB(x, y, dark ? 0x000000 : 0xFFFFFF);
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PNG render failed", e);
        }
    }
}
