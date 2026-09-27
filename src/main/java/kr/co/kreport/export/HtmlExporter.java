package kr.co.kreport.export;

import kr.co.kreport.engine.chart.ChartShape;
import kr.co.kreport.engine.chart.ChartTextMetrics;
import kr.co.kreport.engine.layout.RenderedElement;
import kr.co.kreport.engine.layout.RenderedPage;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.template.ElementStyle;
import kr.co.kreport.template.ElementType;
import kr.co.kreport.template.Orientation;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * 뷰어와 브라우저 인쇄에 쓰는 HTML 을 만든다.
 *
 * <p>좌표는 pt 단위를 그대로 CSS 에 쓴다. 브라우저의 pt 는 물리 단위라
 * 화면 배율과 무관하게 종이 위 위치와 일치하고, 결과적으로 브라우저 인쇄와 PDF 출력이
 * 같은 자리에 찍힌다.</p>
 */
@Component
public class HtmlExporter implements ReportExporter {

    @Override
    public ExportFormat format() {
        return ExportFormat.HTML;
    }

    @Override
    public void export(RenderedReport report, OutputStream out) throws IOException {
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        w.write("<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\">");
        w.write("<title>" + escape(report.getTemplate().getName()) + "</title>");
        w.write("<style>" + documentCss(report) + "</style></head><body>");
        w.write(renderPages(report));
        w.write("</body></html>");
        w.flush();
    }

    /** 뷰어 화면에 끼워 넣을 본문 조각만 생성한다 */
    public String renderPages(RenderedReport report) {
        StringBuilder sb = new StringBuilder(64 * 1024);
        for (RenderedPage page : report.getPages()) {
            sb.append("<div class=\"kr-page\" data-page=\"").append(page.getPageNo())
                    .append("\" style=\"width:").append(pt(page.getWidth()))
                    .append(";height:").append(pt(page.getHeight())).append("\">");
            for (RenderedElement e : page.getElements()) {
                sb.append(renderElement(e));
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    /**
     * 페이지 조각과 함께 쓰는 스타일.
     *
     * <p>뷰어와 디자이너는 이 CSS 를 자기 문서에 그대로 끼워 넣는다. 그래서 여기에는
     * {@code body} 같은 전역 선택자를 두지 않는다. 한 번 넣으면 호스트 화면의 배경과 글꼴까지
     * 바꿔 버리고, 원인을 찾기도 성가시다. 독립 HTML 로 내보낼 때만
     * {@link #documentCss} 가 전역 규칙을 덧붙인다.</p>
     */
    public String baseCss(RenderedReport report) {
        String size = report.getTemplate().getPage().getPaperSize().name()
                + (report.getTemplate().getPage().getOrientation() == Orientation.LANDSCAPE ? " landscape" : "");
        // CSS 에 퍼센트 표기가 섞여 있어 포맷 문자열 대신 연결로 조립한다
        return """
                .kr-page { position:relative; background:#fff; margin:12px auto; box-shadow:0 2px 10px rgba(0,0,0,.35); overflow:hidden; font-family:'Malgun Gothic','맑은 고딕',sans-serif; }
                .kr-el { position:absolute; box-sizing:border-box; overflow:hidden; }
                .kr-text { display:flex; }
                .kr-text > span { width:100%; }
                @media print {
                  .kr-page { margin:0; box-shadow:none; page-break-after:always; }
                  .kr-page:last-child { page-break-after:auto; }
                """
                + "  @page { size: " + size + "; margin:0; }\n"
                + "}\n";
    }

    /** 독립 HTML 파일용. 조각 스타일에 문서 전역 규칙을 더한다. */
    public String documentCss(RenderedReport report) {
        return "body { margin:0; background:#7a7a7a; }\n"
                + "@media print { body { background:#fff; } }\n"
                + baseCss(report);
    }

    private String renderElement(RenderedElement e) {
        String box = "left:" + pt(e.getX()) + ";top:" + pt(e.getY())
                + ";width:" + pt(e.getWidth()) + ";height:" + pt(e.getHeight());

        return switch (e.getType()) {
            case LINE -> renderLine(e, box);
            case RECT -> "<div class=\"kr-el\" style=\"" + box + ";" + boxDecoration(e.getStyle()) + "\"></div>";
            case IMAGE -> "<img class=\"kr-el\" style=\"" + box + ";object-fit:contain\" src=\""
                    + escape(e.getSource()) + "\" alt=\"\">";
            case BARCODE, QRCODE -> renderBarcode(e, box);
            case CHART -> renderChart(e, box);
            case LABEL, TEXT -> renderText(e, box);
        };
    }

    /**
     * 차트는 인라인 SVG 로 그린다. viewBox 를 pt 값 그대로 두어
     * 도형 좌표를 변환 없이 옮긴다.
     */
    private String renderChart(RenderedElement e, String box) {
        List<ChartShape> shapes = e.getChartShapes();
        if (shapes == null || shapes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(1024);
        sb.append("<svg class=\"kr-el\" style=\"").append(box).append("\" viewBox=\"0 0 ")
                .append(trim(e.getWidth())).append(' ').append(trim(e.getHeight()))
                .append("\" xmlns=\"http://www.w3.org/2000/svg\">");

        for (ChartShape shape : shapes) {
            sb.append(renderShape(shape));
        }
        return sb.append("</svg>").toString();
    }

    private String renderShape(ChartShape shape) {
        return switch (shape) {
            case ChartShape.Rect s -> renderRect(s);
            case ChartShape.Line s -> "<line x1=\"" + trim(s.x1()) + "\" y1=\"" + trim(s.y1())
                    + "\" x2=\"" + trim(s.x2()) + "\" y2=\"" + trim(s.y2())
                    + "\" stroke=\"" + color(s.stroke(), "#000") + "\" stroke-width=\""
                    + trim(s.strokeWidth()) + "\"/>";
            case ChartShape.Circle s -> "<circle cx=\"" + trim(s.cx()) + "\" cy=\"" + trim(s.cy())
                    + "\" r=\"" + trim(s.r()) + "\" fill=\"" + color(s.fill(), "none") + "\""
                    + (s.strokeWidth() > 0
                    ? " stroke=\"" + color(s.stroke(), "#fff") + "\" stroke-width=\"" + trim(s.strokeWidth()) + "\""
                    : "") + "/>";
            case ChartShape.Polyline s -> renderPolyline(s);
            case ChartShape.Sector s -> "<path d=\"" + ChartPathBuilder.sector(s)
                    + "\" fill=\"" + color(s.fill(), "#000") + "\"/>";
            case ChartShape.Text s -> renderChartText(s);
        };
    }

    private String renderRect(ChartShape.Rect s) {
        if (s.roundedEnd() == ChartShape.RoundedEnd.NONE) {
            String radius = s.cornerRadius() > 0 ? " rx=\"" + trim(s.cornerRadius()) + "\"" : "";
            return "<rect x=\"" + trim(s.x()) + "\" y=\"" + trim(s.y())
                    + "\" width=\"" + trim(s.width()) + "\" height=\"" + trim(s.height())
                    + "\"" + radius + " fill=\"" + color(s.fill(), "#000") + "\"/>";
        }
        return "<path d=\"" + ChartPathBuilder.roundedBar(s) + "\" fill=\""
                + color(s.fill(), "#000") + "\"/>";
    }

    private String renderPolyline(ChartShape.Polyline s) {
        StringBuilder points = new StringBuilder();
        for (double[] p : s.points()) {
            points.append(trim(p[0])).append(',').append(trim(p[1])).append(' ');
        }
        if (s.fill() != null) {
            return "<polygon points=\"" + points.toString().trim() + "\" fill=\""
                    + color(s.fill(), "#000") + "\" fill-opacity=\"" + trim(s.fillOpacity()) + "\"/>";
        }
        return "<polyline points=\"" + points.toString().trim() + "\" fill=\"none\" stroke=\""
                + color(s.stroke(), "#000") + "\" stroke-width=\"" + trim(s.strokeWidth())
                + "\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>";
    }

    private String renderChartText(ChartShape.Text s) {
        String anchor = switch (s.anchor()) {
            case START -> "start";
            case MIDDLE -> "middle";
            case END -> "end";
        };
        double y = ChartTextMetrics.baselineY(s.y(), s.fontSize(), s.baseline());
        return "<text x=\"" + trim(s.x()) + "\" y=\"" + trim(y) + "\" fill=\""
                + color(s.fill(), "#000") + "\" font-size=\"" + trim(s.fontSize())
                + "\" text-anchor=\"" + anchor + "\""
                + (s.bold() ? " font-weight=\"700\"" : "")
                + " font-family=\"'Malgun Gothic','맑은 고딕',sans-serif\">"
                + escape(s.text()) + "</text>";
    }

    private String renderLine(RenderedElement e, String box) {
        // 높이가 폭보다 작으면 가로선으로 본다
        boolean horizontal = e.getHeight() <= e.getWidth();
        double thickness = Math.max(0.5, horizontal ? e.getHeight() : e.getWidth());
        String color = color(e.getStyle() == null ? null : e.getStyle().getBorderColor(), "#000000");
        String border = horizontal
                ? "border-top:" + pt(thickness) + " solid " + color
                : "border-left:" + pt(thickness) + " solid " + color;
        return "<div class=\"kr-el\" style=\"" + box + ";" + border + "\"></div>";
    }

    private String renderBarcode(RenderedElement e, String box) {
        String uri = BarcodeRenderer.renderDataUri(e.getType(), e.getText(), e.getWidth(), e.getHeight());
        if (uri == null) {
            return "";
        }
        return "<img class=\"kr-el\" style=\"" + box + ";object-fit:contain\" src=\"" + uri + "\" alt=\"\">";
    }

    private String renderText(RenderedElement e, String box) {
        ElementStyle s = e.getStyle() == null ? new ElementStyle() : e.getStyle();
        StringBuilder css = new StringBuilder(box);

        css.append(";font-size:").append(pt(s.getFontSize()));
        css.append(";color:").append(color(s.getColor(), "#000000"));
        css.append(";padding-left:").append(pt(s.getPaddingLeft()));
        css.append(";padding-right:").append(pt(s.getPaddingRight()));
        css.append(";line-height:1.25");
        if (s.isBold()) {
            css.append(";font-weight:700");
        }
        if (s.isItalic()) {
            css.append(";font-style:italic");
        }
        if (s.isUnderline()) {
            css.append(";text-decoration:underline");
        }
        if (!"default".equals(s.getFontFamily())) {
            css.append(";font-family:'").append(escape(s.getFontFamily())).append("'");
        }
        css.append(";justify-content:").append(switch (s.getAlign()) {
            case LEFT -> "flex-start";
            case CENTER -> "center";
            case RIGHT -> "flex-end";
        });
        css.append(";align-items:").append(switch (s.getValign()) {
            case TOP -> "flex-start";
            case MIDDLE -> "center";
            case BOTTOM -> "flex-end";
        });
        css.append(";text-align:").append(s.getAlign().name().toLowerCase(Locale.ROOT));
        css.append(";white-space:").append(s.isWrap() ? "normal" : "nowrap");
        css.append(';').append(boxDecoration(s));

        return "<div class=\"kr-el kr-text\" style=\"" + css + "\"><span>"
                + escape(e.getText()) + "</span></div>";
    }

    private String boxDecoration(ElementStyle s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (s.getBackgroundColor() != null) {
            sb.append("background:").append(color(s.getBackgroundColor(), "transparent")).append(';');
        }
        String bc = color(s.getBorderColor(), "#000000");
        appendBorder(sb, "top", s.getBorderTop(), bc);
        appendBorder(sb, "right", s.getBorderRight(), bc);
        appendBorder(sb, "bottom", s.getBorderBottom(), bc);
        appendBorder(sb, "left", s.getBorderLeft(), bc);
        return sb.toString();
    }

    private void appendBorder(StringBuilder sb, String side, double width, String color) {
        if (width > 0) {
            sb.append("border-").append(side).append(':').append(pt(width))
                    .append(" solid ").append(color).append(';');
        }
    }

    private String pt(double value) {
        return trim(value) + "pt";
    }

    private String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /** 색상 문자열을 CSS 에 그대로 넣기 전에 형식을 확인한다 */
    private String color(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim();
        if (!v.matches("#[0-9a-fA-F]{3,8}") && !v.matches("[a-zA-Z]{3,20}")) {
            return fallback;
        }
        return v;
    }

    static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
