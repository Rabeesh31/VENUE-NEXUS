package com.venuenexus.resource.qr;

public interface QrRenderer {
    /** Printable vector output. */
    String svg(String content);

    /** Raster output, PNG bytes. */
    byte[] png(String content);
}
