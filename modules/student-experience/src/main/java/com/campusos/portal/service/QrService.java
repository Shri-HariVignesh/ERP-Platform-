package com.campusos.portal.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Renders a real, scannable QR as inline SVG. No external service, no image files. */
@Service
public class QrService {

    public String svg(String text, int px) {
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                            EncodeHintType.MARGIN, 1));
            int w = m.getWidth(), h = m.getHeight();
            StringBuilder sb = new StringBuilder();
            sb.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ")
              .append(w).append(' ').append(h).append("' width='").append(px)
              .append("' height='").append(px)
              .append("' shape-rendering='crispEdges' role='img' aria-label='Verification QR code'>");
            sb.append("<rect width='").append(w).append("' height='").append(h).append("' fill='#fff'/>");
            sb.append("<path fill='#000' d='");
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (m.get(x, y)) sb.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
            sb.append("'/></svg>");
            return sb.toString();
        } catch (Exception e) {
            return "<span class='muted'>QR unavailable</span>";
        }
    }
}
