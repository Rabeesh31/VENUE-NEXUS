package com.venuenexus.resource.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Uses com.google.zxing:core only (module matrix); drawing is done by MatrixQrImages. */
@Component
public class ZxingQrRenderer implements QrRenderer {

    @Override
    public String svg(String content) {
        return MatrixQrImages.svg(matrix(content));
    }

    @Override
    public byte[] png(String content) {
        return MatrixQrImages.png(matrix(content));
    }

    private boolean[][] matrix(String content) {
        try {
            BitMatrix bm = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.Q,
                           EncodeHintType.MARGIN, 0));
            boolean[][] m = new boolean[bm.getHeight()][bm.getWidth()];
            for (int y = 0; y < bm.getHeight(); y++)
                for (int x = 0; x < bm.getWidth(); x++)
                    m[y][x] = bm.get(x, y);
            return m;
        } catch (WriterException e) {
            throw new IllegalStateException("QR encode failed", e);
        }
    }
}
