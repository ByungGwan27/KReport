package kr.co.kreport.engine.chart;

/**
 * 차트 글자가 차지하는 자리 계산.
 *
 * <p>가로 폭과 세로 기준선을 한곳에 뒀다. 출력기(HTML·PDF)와 배치기가 서로 다른 식으로
 * 글자 자리를 재면, 배치기가 비워 둔 폭에 출력기가 더 넓은 글자를 그려 넣어 축 라벨이
 * 겹치는 식으로 어긋난다. SVG 의 {@code dominant-baseline} 에 맡기지 않는 것도 같은
 * 이유로, 뷰어마다 해석이 달라 같은 차트가 화면과 PDF 에서 다른 높이에 찍힌다.</p>
 */
public final class ChartTextMetrics {

    private ChartTextMetrics() {
    }

    /**
     * 기준점 y 와 정렬 방식으로부터 글자 베이스라인 y 를 구한다.
     */
    public static double baselineY(double y, double fontSize, ChartShape.Baseline baseline) {
        return switch (baseline) {
            case TOP -> y + fontSize * 0.8;
            case MIDDLE -> y + fontSize * 0.34;
            case BOTTOM -> y;
        };
    }

    /**
     * 글자 폭 어림. 한글은 글자 크기와 거의 같은 폭을, 라틴 문자와 숫자는 절반쯤을 쓴다.
     * 축 라벨 자리를 잡기 위한 것이라 정확한 폰트 메트릭까지는 필요 없다.
     */
    static double width(String text, double fontSize) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        double units = 0;
        for (int i = 0; i < text.length(); i++) {
            units += isWide(text.charAt(i)) ? 1.0 : 0.52;
        }
        return units * fontSize;
    }

    /** 여러 글자 중 가장 넓은 것의 폭. 축과 범례 자리를 잡을 때 쓴다. */
    static double widestWidth(Iterable<String> texts, double fontSize) {
        double widest = 0;
        for (String text : texts) {
            widest = Math.max(widest, width(text, fontSize));
        }
        return widest;
    }

    /** 자리에 안 들어가면 말줄임표로 자른다. 잘린 채 넘치는 것보다 낫다. */
    static String clip(String text, double fontSize, double maxWidth) {
        if (maxWidth <= 0 || width(text, fontSize) <= maxWidth) {
            return text;
        }
        double ellipsis = width("…", fontSize);
        int end = text.length();
        while (end > 0 && width(text.substring(0, end), fontSize) + ellipsis > maxWidth) {
            end--;
        }
        return end <= 0 ? "" : text.substring(0, end) + "…";
    }

    private static boolean isWide(char c) {
        return c >= 0xAC00 && c <= 0xD7A3     // 한글 음절
                || c >= 0x3130 && c <= 0x318F // 호환 자모
                || c >= 0x4E00 && c <= 0x9FFF // 한자
                || c >= 0xFF00 && c <= 0xFF60;// 전각
    }
}
