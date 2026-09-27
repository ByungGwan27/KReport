package kr.co.kreport.engine.chart;

import java.util.List;

/**
 * 차트를 이루는 그리기 명령.
 *
 * <p>좌표는 차트 요소 상자의 좌상단을 원점으로 하는 pt 값이다. 익스포터는 자기 좌표계로
 * 옮겨 그리기만 한다. 축 계산과 마크 배치가 여기서 이미 끝나 있어, 출력 형식을 하나 더
 * 붙여도 차트 로직을 다시 짤 일이 없다.</p>
 */
public sealed interface ChartShape {

    /** 텍스트 가로 기준점 */
    enum Anchor {
        START, MIDDLE, END
    }

    /** 텍스트 세로 기준점 */
    enum Baseline {
        TOP, MIDDLE, BOTTOM
    }

    /**
     * 막대에서 값이 끝나는 쪽. 그쪽 모서리만 둥글린다.
     * 기준선 쪽까지 둥글리면 막대가 축에서 떠 보인다.
     */
    enum RoundedEnd {
        NONE, TOP, RIGHT
    }

    /** 채움 사각형. 막대와 범례 표식에 쓴다. */
    record Rect(double x, double y, double width, double height,
                String fill, double cornerRadius, RoundedEnd roundedEnd) implements ChartShape {

        public static Rect of(double x, double y, double width, double height, String fill) {
            return new Rect(x, y, width, height, fill, 0, RoundedEnd.NONE);
        }
    }

    /** 직선. 축과 눈금선에 쓴다. */
    record Line(double x1, double y1, double x2, double y2,
                String stroke, double strokeWidth) implements ChartShape {
    }

    /**
     * 연결선.
     *
     * @param fill 채우면 영역 차트가 된다. null 이면 선만.
     */
    record Polyline(List<double[]> points, String stroke, double strokeWidth,
                    String fill, double fillOpacity) implements ChartShape {
    }

    /** 원. 꺾은선의 값 표식에 쓴다. */
    record Circle(double cx, double cy, double r,
                  String fill, String stroke, double strokeWidth) implements ChartShape {
    }

    /**
     * 부채꼴. 원/도넛 그래프의 조각.
     *
     * @param startAngle 12시 방향이 0도, 시계 방향으로 증가(도 단위)
     * @param innerR     0보다 크면 도넛
     */
    record Sector(double cx, double cy, double outerR, double innerR,
                  double startAngle, double endAngle, String fill) implements ChartShape {
    }

    record Text(double x, double y, String text, String fill, double fontSize,
                boolean bold, Anchor anchor, Baseline baseline) implements ChartShape {
    }
}
