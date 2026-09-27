package kr.co.kreport.export;

import kr.co.kreport.engine.chart.ChartShape;
import kr.co.kreport.engine.chart.ChartTextMetrics;
import kr.co.kreport.engine.layout.RenderedElement;
import kr.co.kreport.engine.layout.RenderedPage;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.template.ElementStyle;
import kr.co.kreport.template.HorizontalAlign;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * PDFBox 로 PDF 를 만든다.
 *
 * <p>PDF 좌표계는 좌하단이 원점이고 텍스트는 베이스라인 기준으로 찍힌다.
 * 레이아웃 결과는 좌상단 원점의 상자 좌표이므로 이 클래스에서 한 번에 뒤집는다.
 * 변환을 여기 한 곳에 몰아 두면 다른 익스포터가 좌표계를 신경 쓸 필요가 없다.</p>
 */
@Slf4j
@Component
public class PdfExporter implements ReportExporter {

    private final PdfFontProvider fontProvider;
    private final ReportImageLoader imageLoader;

    public PdfExporter(PdfFontProvider fontProvider, ReportImageLoader imageLoader) {
        this.fontProvider = fontProvider;
        this.imageLoader = imageLoader;
    }

    @Override
    public ExportFormat format() {
        return ExportFormat.PDF;
    }

    @Override
    public void export(RenderedReport report, OutputStream out) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PdfFontProvider.Fonts fonts = fontProvider.fontsFor(document);
            writeMetadata(document, report);

            for (RenderedPage page : report.getPages()) {
                PDPage pdPage = new PDPage(new PDRectangle((float) page.getWidth(), (float) page.getHeight()));
                document.addPage(pdPage);

                try (PDPageContentStream cs =
                             new PDPageContentStream(document, pdPage, PDPageContentStream.AppendMode.APPEND, true)) {
                    for (RenderedElement e : page.getElements()) {
                        draw(document, cs, fonts, e, page.getHeight());
                    }
                }
            }
            document.save(out);
        }
    }

    private void writeMetadata(PDDocument document, RenderedReport report) {
        var info = document.getDocumentInformation();
        info.setTitle(report.getTemplate().getName());
        info.setSubject(report.getTemplate().getDescription());
        info.setCreator("KReport");
        info.setCreationDate(Calendar.getInstance());
    }

    private void draw(PDDocument document, PDPageContentStream cs, PdfFontProvider.Fonts fonts,
                      RenderedElement e, double pageHeight) throws IOException {
        switch (e.getType()) {
            case RECT -> drawBox(cs, e, pageHeight);
            case LINE -> drawLine(cs, e, pageHeight);
            case IMAGE -> drawImage(document, cs, e, pageHeight);
            case BARCODE, QRCODE -> drawBarcode(document, cs, e, pageHeight);
            case CHART -> drawChart(cs, fonts, e, pageHeight);
            case LABEL, TEXT -> {
                drawBox(cs, e, pageHeight);
                drawText(cs, fonts, e, pageHeight);
            }
        }
    }

    // ---------------------------------------------------------------- 차트

    /**
     * 차트를 그린다. 도형 좌표는 요소 상자의 좌상단 기준이므로
     * 여기서 페이지 좌표로 한 번만 옮긴다.
     */
    private void drawChart(PDPageContentStream cs, PdfFontProvider.Fonts fonts,
                           RenderedElement e, double pageHeight) throws IOException {
        List<ChartShape> shapes = e.getChartShapes();
        if (shapes == null || shapes.isEmpty()) {
            return;
        }
        double originX = e.getX();
        double originY = pageHeight - e.getY();

        for (ChartShape shape : shapes) {
            switch (shape) {
                case ChartShape.Rect s -> drawChartRect(cs, s, originX, originY);
                case ChartShape.Line s -> {
                    setStroke(cs, s.stroke(), s.strokeWidth());
                    cs.moveTo((float) (originX + s.x1()), (float) (originY - s.y1()));
                    cs.lineTo((float) (originX + s.x2()), (float) (originY - s.y2()));
                    cs.stroke();
                }
                case ChartShape.Circle s -> drawChartCircle(cs, s, originX, originY);
                case ChartShape.Polyline s -> drawChartPolyline(cs, s, originX, originY);
                case ChartShape.Sector s -> drawChartSector(cs, s, originX, originY);
                case ChartShape.Text s -> drawChartText(cs, fonts, s, originX, originY);
            }
        }
    }

    private void drawChartRect(PDPageContentStream cs, ChartShape.Rect s,
                               double originX, double originY) throws IOException {
        if (!setFill(cs, s.fill())) {
            return;
        }
        double x = originX + s.x();
        double top = originY - s.y();
        double w = s.width();
        double h = s.height();
        double r = Math.min(s.cornerRadius(), Math.min(w / 2, h));

        if (s.roundedEnd() == ChartShape.RoundedEnd.NONE && s.cornerRadius() <= 0.05) {
            cs.addRect((float) x, (float) (top - h), (float) w, (float) h);
            cs.fill();
            return;
        }
        if (r <= 0.05) {
            cs.addRect((float) x, (float) (top - h), (float) w, (float) h);
            cs.fill();
            return;
        }

        // 값이 끝나는 쪽 두 모서리만 둥글린다
        if (s.roundedEnd() == ChartShape.RoundedEnd.RIGHT) {
            cs.moveTo((float) x, (float) top);
            cs.lineTo((float) (x + w - r), (float) top);
            cs.curveTo((float) (x + w), (float) top, (float) (x + w), (float) top,
                    (float) (x + w), (float) (top - r));
            cs.lineTo((float) (x + w), (float) (top - h + r));
            cs.curveTo((float) (x + w), (float) (top - h), (float) (x + w), (float) (top - h),
                    (float) (x + w - r), (float) (top - h));
            cs.lineTo((float) x, (float) (top - h));
        } else {
            cs.moveTo((float) x, (float) (top - h));
            cs.lineTo((float) x, (float) (top - r));
            cs.curveTo((float) x, (float) top, (float) x, (float) top,
                    (float) (x + r), (float) top);
            cs.lineTo((float) (x + w - r), (float) top);
            cs.curveTo((float) (x + w), (float) top, (float) (x + w), (float) top,
                    (float) (x + w), (float) (top - r));
            cs.lineTo((float) (x + w), (float) (top - h));
        }
        cs.closePath();
        cs.fill();
    }

    /** 베지어 곡선 네 개로 원을 그린다. PDF 에는 원 명령이 없다. */
    private void drawChartCircle(PDPageContentStream cs, ChartShape.Circle s,
                                 double originX, double originY) throws IOException {
        double cx = originX + s.cx();
        double cy = originY - s.cy();
        double r = s.r();
        double k = r * 0.5523;

        boolean filled = setFill(cs, s.fill());
        boolean stroked = s.strokeWidth() > 0 && setStroke(cs, s.stroke(), s.strokeWidth());
        if (!filled && !stroked) {
            return;
        }

        cs.moveTo((float) (cx + r), (float) cy);
        cs.curveTo((float) (cx + r), (float) (cy + k), (float) (cx + k), (float) (cy + r),
                (float) cx, (float) (cy + r));
        cs.curveTo((float) (cx - k), (float) (cy + r), (float) (cx - r), (float) (cy + k),
                (float) (cx - r), (float) cy);
        cs.curveTo((float) (cx - r), (float) (cy - k), (float) (cx - k), (float) (cy - r),
                (float) cx, (float) (cy - r));
        cs.curveTo((float) (cx + k), (float) (cy - r), (float) (cx + r), (float) (cy - k),
                (float) (cx + r), (float) cy);
        cs.closePath();

        if (filled && stroked) {
            cs.fillAndStroke();
        } else if (filled) {
            cs.fill();
        } else {
            cs.stroke();
        }
    }

    private void drawChartPolyline(PDPageContentStream cs, ChartShape.Polyline s,
                                   double originX, double originY) throws IOException {
        if (s.points().isEmpty()) {
            return;
        }
        boolean area = s.fill() != null;
        if (area) {
            float[] rgb = rgb(s.fill());
            if (rgb == null) {
                return;
            }
            // PDF 의 알파는 그래픽 상태로 다뤄야 해서, 옅은 채움은 흰 지면과 섞은 색으로 대신한다.
            float a = (float) s.fillOpacity();
            cs.setNonStrokingColor(
                    rgb[0] * a + (1 - a), rgb[1] * a + (1 - a), rgb[2] * a + (1 - a));
        } else if (!setStroke(cs, s.stroke(), s.strokeWidth())) {
            return;
        }

        double[] first = s.points().get(0);
        cs.moveTo((float) (originX + first[0]), (float) (originY - first[1]));
        for (int i = 1; i < s.points().size(); i++) {
            double[] p = s.points().get(i);
            cs.lineTo((float) (originX + p[0]), (float) (originY - p[1]));
        }

        if (area) {
            cs.closePath();
            cs.fill();
        } else {
            cs.setLineJoinStyle(1);
            cs.setLineCapStyle(1);
            cs.stroke();
        }
    }

    /** 부채꼴. 호를 짧은 베지어 조각으로 나눠 그린다. */
    private void drawChartSector(PDPageContentStream cs, ChartShape.Sector s,
                                 double originX, double originY) throws IOException {
        double sweep = s.endAngle() - s.startAngle();
        if (sweep <= 0.01 || !setFill(cs, s.fill())) {
            return;
        }
        double cx = originX + s.cx();
        double cy = originY - s.cy();

        List<double[]> outer = arcPoints(cx, cy, s.outerR(), s.startAngle(), s.endAngle());
        if (outer.isEmpty()) {
            return;
        }

        if (s.innerR() <= 0.01) {
            cs.moveTo((float) cx, (float) cy);
            for (double[] p : outer) {
                cs.lineTo((float) p[0], (float) p[1]);
            }
        } else {
            List<double[]> inner = arcPoints(cx, cy, s.innerR(), s.endAngle(), s.startAngle());
            cs.moveTo((float) outer.get(0)[0], (float) outer.get(0)[1]);
            for (double[] p : outer) {
                cs.lineTo((float) p[0], (float) p[1]);
            }
            for (double[] p : inner) {
                cs.lineTo((float) p[0], (float) p[1]);
            }
        }
        cs.closePath();
        cs.fill();
    }

    /**
     * 호를 잇는 점 목록. 1.5도마다 한 점이면 리포트 크기의 원에서 각진 티가 나지 않는다.
     */
    private List<double[]> arcPoints(double cx, double cy, double r, double from, double to) {
        List<double[]> points = new ArrayList<>();
        double sweep = to - from;
        int steps = Math.max(2, (int) Math.ceil(Math.abs(sweep) / 1.5));
        for (int i = 0; i <= steps; i++) {
            double angle = from + sweep * i / steps;
            double rad = Math.toRadians(angle - 90);
            points.add(new double[]{cx + r * Math.cos(rad), cy - r * Math.sin(rad)});
        }
        return points;
    }

    private void drawChartText(PDPageContentStream cs, PdfFontProvider.Fonts fonts,
                               ChartShape.Text s, double originX, double originY) throws IOException {
        if (s.text() == null || s.text().isEmpty() || !setFill(cs, s.fill())) {
            return;
        }
        PDFont font = fonts.pick(s.bold());
        float fontSize = (float) s.fontSize();
        String text = sanitize(font, s.text());

        float width = textWidth(font, fontSize, text);
        double x = originX + s.x() - switch (s.anchor()) {
            case START -> 0;
            case MIDDLE -> width / 2.0;
            case END -> width;
        };
        // SVG 와 같은 식으로 베이스라인을 구해 화면과 PDF 가 같은 높이에 찍히게 한다
        double baseline = ChartTextMetrics.baselineY(s.y(), s.fontSize(), s.baseline());

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset((float) x, (float) (originY - baseline));
        cs.showText(text);
        cs.endText();
    }

    private boolean setFill(PDPageContentStream cs, String color) throws IOException {
        float[] rgb = rgb(color);
        if (rgb == null) {
            return false;
        }
        cs.setNonStrokingColor(rgb[0], rgb[1], rgb[2]);
        return true;
    }

    private boolean setStroke(PDPageContentStream cs, String color, double width) throws IOException {
        float[] rgb = rgb(color);
        if (rgb == null) {
            return false;
        }
        cs.setStrokingColor(rgb[0], rgb[1], rgb[2]);
        cs.setLineWidth((float) Math.max(0.1, width));
        return true;
    }

    /** 배경과 테두리 */
    private void drawBox(PDPageContentStream cs, RenderedElement e, double pageHeight) throws IOException {
        ElementStyle s = e.getStyle();
        if (s == null) {
            return;
        }
        float x = (float) e.getX();
        float bottom = (float) (pageHeight - e.getBottom());
        float w = (float) e.getWidth();
        float h = (float) e.getHeight();

        float[] background = rgb(s.getBackgroundColor());
        if (background != null) {
            cs.setNonStrokingColor(background[0], background[1], background[2]);
            cs.addRect(x, bottom, w, h);
            cs.fill();
        }
        if (!s.hasBorder()) {
            return;
        }
        float[] border = rgb(s.getBorderColor());
        if (border == null) {
            border = new float[]{0, 0, 0};
        }
        cs.setStrokingColor(border[0], border[1], border[2]);

        strokeEdge(cs, s.getBorderTop(), x, bottom + h, x + w, bottom + h);
        strokeEdge(cs, s.getBorderBottom(), x, bottom, x + w, bottom);
        strokeEdge(cs, s.getBorderLeft(), x, bottom, x, bottom + h);
        strokeEdge(cs, s.getBorderRight(), x + w, bottom, x + w, bottom + h);
    }

    private void strokeEdge(PDPageContentStream cs, double width,
                            float x1, float y1, float x2, float y2) throws IOException {
        if (width <= 0) {
            return;
        }
        cs.setLineWidth((float) width);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private void drawLine(PDPageContentStream cs, RenderedElement e, double pageHeight) throws IOException {
        float[] color = rgb(e.getStyle() == null ? null : e.getStyle().getBorderColor());
        if (color == null) {
            color = new float[]{0, 0, 0};
        }
        cs.setStrokingColor(color[0], color[1], color[2]);

        boolean horizontal = e.getHeight() <= e.getWidth();
        cs.setLineWidth((float) Math.max(0.5, horizontal ? e.getHeight() : e.getWidth()));

        if (horizontal) {
            float y = (float) (pageHeight - e.getY() - e.getHeight() / 2);
            cs.moveTo((float) e.getX(), y);
            cs.lineTo((float) e.getRight(), y);
        } else {
            float x = (float) (e.getX() + e.getWidth() / 2);
            cs.moveTo(x, (float) (pageHeight - e.getY()));
            cs.lineTo(x, (float) (pageHeight - e.getBottom()));
        }
        cs.stroke();
    }

    private void drawText(PDPageContentStream cs, PdfFontProvider.Fonts fonts,
                          RenderedElement e, double pageHeight) throws IOException {
        String text = e.getText();
        if (text == null || text.isEmpty()) {
            return;
        }
        ElementStyle s = e.getStyle() == null ? new ElementStyle() : e.getStyle();
        PDFont font = fonts.pick(s.isBold());
        float fontSize = (float) s.getFontSize();

        double innerWidth = e.getWidth() - s.getPaddingLeft() - s.getPaddingRight();
        List<String> lines = s.isWrap()
                ? wrap(font, fontSize, sanitize(font, text), innerWidth)
                : List.of(clip(font, fontSize, sanitize(font, text), innerWidth));

        float lineHeight = fontSize * 1.25f;
        float blockHeight = lineHeight * lines.size();
        float firstBaselineFromTop = switch (s.getValign()) {
            case TOP -> fontSize * 0.85f;
            case BOTTOM -> (float) e.getHeight() - blockHeight + fontSize * 0.85f;
            case MIDDLE -> (float) (e.getHeight() - blockHeight) / 2 + fontSize * 0.85f;
        };

        float[] color = rgb(s.getColor());
        if (color == null) {
            color = new float[]{0, 0, 0};
        }
        cs.setNonStrokingColor(color[0], color[1], color[2]);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            float lineWidth = textWidth(font, fontSize, line);
            float x = switch (s.getAlign()) {
                case LEFT -> (float) (e.getX() + s.getPaddingLeft());
                case RIGHT -> (float) (e.getRight() - s.getPaddingRight()) - lineWidth;
                case CENTER -> (float) (e.getX() + (e.getWidth() - lineWidth) / 2);
            };
            float baselineFromTop = firstBaselineFromTop + lineHeight * i;
            float y = (float) (pageHeight - e.getY() - baselineFromTop);

            cs.beginText();
            cs.setFont(font, fontSize);
            cs.newLineAtOffset(x, y);
            cs.showText(line);
            cs.endText();

            if (s.isUnderline()) {
                cs.setLineWidth(Math.max(0.4f, fontSize * 0.05f));
                cs.setStrokingColor(color[0], color[1], color[2]);
                cs.moveTo(x, y - fontSize * 0.13f);
                cs.lineTo(x + lineWidth, y - fontSize * 0.13f);
                cs.stroke();
            }
        }
    }

    private void drawImage(PDDocument document, PDPageContentStream cs,
                           RenderedElement e, double pageHeight) throws IOException {
        BufferedImage image = imageLoader.load(e.getSource());
        if (image == null) {
            return;
        }
        PDImageXObject xObject = LosslessFactory.createFromImage(document, image);
        cs.drawImage(xObject, (float) e.getX(), (float) (pageHeight - e.getBottom()),
                (float) e.getWidth(), (float) e.getHeight());
    }

    private void drawBarcode(PDDocument document, PDPageContentStream cs,
                             RenderedElement e, double pageHeight) throws IOException {
        BufferedImage image = BarcodeRenderer.render(e.getType(), e.getText(), e.getWidth(), e.getHeight());
        if (image == null) {
            return;
        }
        PDImageXObject xObject = LosslessFactory.createFromImage(document, image);
        // QR 은 정사각으로 구워지므로 원래 상자 안에서 비율을 유지한다
        double ratio = (double) image.getWidth() / image.getHeight();
        double w = e.getWidth();
        double h = e.getHeight();
        if (w / h > ratio) {
            w = h * ratio;
        } else {
            h = w / ratio;
        }
        cs.drawImage(xObject, (float) e.getX(), (float) (pageHeight - e.getY() - h), (float) w, (float) h);
    }

    // ---------------------------------------------------------------- 텍스트 계산

    private float textWidth(PDFont font, float fontSize, String text) {
        try {
            return font.getStringWidth(text) / 1000 * fontSize;
        } catch (Exception e) {
            return text.length() * fontSize * 0.5f;
        }
    }

    /**
     * 폰트에 글리프가 없는 문자를 걸러 낸다.
     * showText 는 표현 불가 문자를 만나면 예외를 던지는데, 그 한 글자 때문에
     * 리포트 전체 출력이 실패하는 것은 곤란하다.
     */
    private String sanitize(PDFont font, String text) {
        try {
            font.getStringWidth(text);
            return text;
        } catch (Exception ignored) {
            StringBuilder sb = new StringBuilder(text.length());
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                try {
                    font.getStringWidth(ch);
                    sb.append(ch);
                } catch (Exception e) {
                    sb.append('?');
                }
            }
            return sb.toString();
        }
    }

    /** 폭을 넘으면 말줄임표로 자른다 */
    private String clip(PDFont font, float fontSize, String text, double maxWidth) {
        if (maxWidth <= 0 || textWidth(font, fontSize, text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        double limit = maxWidth - textWidth(font, fontSize, ellipsis);
        if (limit <= 0) {
            return "";
        }
        int end = text.length();
        while (end > 0 && textWidth(font, fontSize, text.substring(0, end)) > limit) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    /**
     * 줄바꿈. 공백이 있으면 단어 단위로, 없으면(한글 문장) 글자 단위로 끊는다.
     */
    private List<String> wrap(PDFont font, float fontSize, String text, double maxWidth) {
        List<String> lines = new ArrayList<>();
        if (maxWidth <= 0) {
            lines.add(text);
            return lines;
        }
        StringBuilder current = new StringBuilder();

        for (String token : splitKeepingDelimiters(text)) {
            String candidate = current + token;
            if (textWidth(font, fontSize, candidate) <= maxWidth || current.isEmpty()) {
                current.append(token);
                continue;
            }
            lines.add(current.toString().stripTrailing());
            current = new StringBuilder(token.stripLeading());
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    /** 공백을 유지한 채 단어 단위로 쪼갠다. 공백 없는 긴 문자열은 글자 단위로. */
    private List<String> splitKeepingDelimiters(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            word.append(c);
            boolean boundary = c == ' ' || c == '\t' || isCjk(c);
            if (boundary) {
                tokens.add(word.toString());
                word.setLength(0);
            }
        }
        if (!word.isEmpty()) {
            tokens.add(word.toString());
        }
        return tokens;
    }

    private boolean isCjk(char c) {
        return c >= 0xAC00 && c <= 0xD7A3   // 한글 음절
                || c >= 0x3130 && c <= 0x318F   // 호환 자모
                || c >= 0x4E00 && c <= 0x9FFF;  // 한자
    }

    /** {@code #RRGGBB} 를 0~1 범위 RGB 로. 형식이 아니면 null */
    private float[] rgb(String hex) {
        if (hex == null) {
            return null;
        }
        String v = hex.trim();
        if (!v.startsWith("#")) {
            return null;
        }
        v = v.substring(1);
        if (v.length() == 3) {
            v = "" + v.charAt(0) + v.charAt(0) + v.charAt(1) + v.charAt(1) + v.charAt(2) + v.charAt(2);
        }
        if (v.length() != 6) {
            return null;
        }
        try {
            int rgb = Integer.parseInt(v, 16);
            return new float[]{
                    ((rgb >> 16) & 0xFF) / 255f,
                    ((rgb >> 8) & 0xFF) / 255f,
                    (rgb & 0xFF) / 255f
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
