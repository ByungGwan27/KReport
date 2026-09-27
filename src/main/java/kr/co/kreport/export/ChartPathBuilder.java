package kr.co.kreport.export;

import kr.co.kreport.engine.chart.ChartShape;

import java.util.Locale;

/**
 * 차트 도형을 SVG path 문자열로 만든다.
 *
 * <p>둥근 모서리가 한쪽에만 있는 막대와 부채꼴은 {@code <rect>}, {@code <circle>} 로
 * 표현할 수 없어 path 가 필요하다.</p>
 */
final class ChartPathBuilder {

    private ChartPathBuilder() {
    }

    /**
     * 값이 끝나는 쪽 두 모서리만 둥근 막대.
     * 기준선 쪽까지 둥글리면 막대가 축에서 떠 보인다.
     */
    static String roundedBar(ChartShape.Rect s) {
        double x = s.x();
        double y = s.y();
        double w = s.width();
        double h = s.height();
        double r = Math.min(s.cornerRadius(), Math.min(w / 2, h));

        if (r <= 0.05) {
            return "M" + n(x) + " " + n(y) + "h" + n(w) + "v" + n(h) + "h" + n(-w) + "Z";
        }

        StringBuilder d = new StringBuilder(96);
        if (s.roundedEnd() == ChartShape.RoundedEnd.TOP) {
            d.append("M").append(n(x)).append(' ').append(n(y + h))
                    .append("L").append(n(x)).append(' ').append(n(y + r))
                    .append("Q").append(n(x)).append(' ').append(n(y)).append(' ')
                    .append(n(x + r)).append(' ').append(n(y))
                    .append("L").append(n(x + w - r)).append(' ').append(n(y))
                    .append("Q").append(n(x + w)).append(' ').append(n(y)).append(' ')
                    .append(n(x + w)).append(' ').append(n(y + r))
                    .append("L").append(n(x + w)).append(' ').append(n(y + h))
                    .append("Z");
        } else {
            d.append("M").append(n(x)).append(' ').append(n(y))
                    .append("L").append(n(x + w - r)).append(' ').append(n(y))
                    .append("Q").append(n(x + w)).append(' ').append(n(y)).append(' ')
                    .append(n(x + w)).append(' ').append(n(y + r))
                    .append("L").append(n(x + w)).append(' ').append(n(y + h - r))
                    .append("Q").append(n(x + w)).append(' ').append(n(y + h)).append(' ')
                    .append(n(x + w - r)).append(' ').append(n(y + h))
                    .append("L").append(n(x)).append(' ').append(n(y + h))
                    .append("Z");
        }
        return d.toString();
    }

    /** 부채꼴. 안쪽 반지름이 있으면 도넛 조각이 된다. */
    static String sector(ChartShape.Sector s) {
        double sweep = s.endAngle() - s.startAngle();
        if (sweep <= 0.01) {
            return "";
        }
        int largeArc = sweep > 180 ? 1 : 0;

        double[] outerStart = point(s.cx(), s.cy(), s.outerR(), s.startAngle());
        double[] outerEnd = point(s.cx(), s.cy(), s.outerR(), s.endAngle());

        StringBuilder d = new StringBuilder(128);
        if (s.innerR() <= 0.01) {
            d.append("M").append(n(s.cx())).append(' ').append(n(s.cy()))
                    .append("L").append(n(outerStart[0])).append(' ').append(n(outerStart[1]))
                    .append("A").append(n(s.outerR())).append(' ').append(n(s.outerR()))
                    .append(" 0 ").append(largeArc).append(" 1 ")
                    .append(n(outerEnd[0])).append(' ').append(n(outerEnd[1]))
                    .append("Z");
        } else {
            double[] innerEnd = point(s.cx(), s.cy(), s.innerR(), s.endAngle());
            double[] innerStart = point(s.cx(), s.cy(), s.innerR(), s.startAngle());
            d.append("M").append(n(outerStart[0])).append(' ').append(n(outerStart[1]))
                    .append("A").append(n(s.outerR())).append(' ').append(n(s.outerR()))
                    .append(" 0 ").append(largeArc).append(" 1 ")
                    .append(n(outerEnd[0])).append(' ').append(n(outerEnd[1]))
                    .append("L").append(n(innerEnd[0])).append(' ').append(n(innerEnd[1]))
                    .append("A").append(n(s.innerR())).append(' ').append(n(s.innerR()))
                    .append(" 0 ").append(largeArc).append(" 0 ")
                    .append(n(innerStart[0])).append(' ').append(n(innerStart[1]))
                    .append("Z");
        }
        return d.toString();
    }

    /** 12시 방향이 0도, 시계 방향으로 증가하는 각도의 좌표 */
    static double[] point(double cx, double cy, double r, double angleDegrees) {
        double rad = Math.toRadians(angleDegrees - 90);
        return new double[]{cx + r * Math.cos(rad), cy + r * Math.sin(rad)};
    }

    private static String n(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
