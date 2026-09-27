package kr.co.kreport.export;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import kr.co.kreport.template.ElementType;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/**
 * 바코드와 QR 코드를 PNG 로 만든다.
 *
 * <p>출력물의 진위 확인용 QR 은 공문서 리포트에서 사실상 기본 요소라 엔진에 내장했다.</p>
 */
@Slf4j
public final class BarcodeRenderer {

    /** 화면/인쇄 모두에서 번지지 않도록 pt 당 픽셀 배율을 올려 굽는다 */
    private static final int SCALE = 4;

    private BarcodeRenderer() {
    }

    public static BufferedImage render(ElementType type, String value, double widthPt, double heightPt) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int width = Math.max(1, (int) Math.round(widthPt * SCALE));
        int height = Math.max(1, (int) Math.round(heightPt * SCALE));

        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 0);

            BarcodeFormat format;
            if (type == ElementType.QRCODE) {
                format = BarcodeFormat.QR_CODE;
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                // QR 은 정사각이라 짧은 변에 맞춘다
                int side = Math.min(width, height);
                width = side;
                height = side;
            } else {
                format = BarcodeFormat.CODE_128;
            }

            BitMatrix matrix = new MultiFormatWriter().encode(value, format, width, height, hints);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (Exception e) {
            log.warn("바코드 생성 실패 (type={}, value={}): {}", type, value, e.getMessage());
            return null;
        }
    }

    public static byte[] renderPng(ElementType type, String value, double widthPt, double heightPt) {
        BufferedImage image = render(type, value, widthPt, heightPt);
        if (image == null) {
            return null;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            log.warn("바코드 PNG 변환 실패: {}", e.getMessage());
            return null;
        }
    }

    public static String renderDataUri(ElementType type, String value, double widthPt, double heightPt) {
        byte[] png = renderPng(type, value, widthPt, heightPt);
        if (png == null) {
            return null;
        }
        return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png);
    }
}
