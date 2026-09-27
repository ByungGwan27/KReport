package kr.co.kreport.engine.layout;

import kr.co.kreport.template.BandType;
import kr.co.kreport.template.ElementStyle;
import kr.co.kreport.template.ElementType;
import lombok.Getter;
import lombok.Setter;

/**
 * 좌표가 확정된 출력 단위. 페이지 좌상단을 원점으로 하는 절대 좌표(pt)를 가진다.
 *
 * <p>익스포터는 이 결과만 보고 그린다. 밴드, 그룹, 표현식 같은 개념은 여기까지 오지 않기 때문에
 * 출력 형식을 하나 더 붙일 때 레이아웃 로직을 다시 건드릴 일이 없다.</p>
 */
@Getter
@Setter
public class RenderedElement {

    private ElementType type;

    private double x;
    private double y;
    private double width;
    private double height;

    /** 포맷까지 적용된 최종 출력 문자열 */
    private String text;

    /**
     * 포맷 적용 전 원본 값. XLSX 로 내보낼 때 숫자를 문자열이 아닌 수치 셀로 쓰기 위해 보관한다.
     * 셀이 문자열이면 받는 쪽에서 합계를 못 내므로 실무에서 바로 불만이 나오는 지점이다.
     */
    private Object rawValue;

    /** IMAGE 의 원본 경로 */
    private String source;

    private ElementStyle style;

    /** 디자이너에서 부여한 요소 식별자. 뷰어에서 클릭 대상을 되짚을 때 쓴다. */
    private String elementId;

    /**
     * 이 요소가 나온 밴드.
     *
     * <p>표 형태로 되뽑을 때 본문 행만 골라내려면 출처를 알아야 한다. 좌표만으로 추려 보면
     * 컬럼과 같은 자리에 놓인 머리말 라벨이나 소계 칸까지 데이터 행으로 딸려 들어간다.</p>
     */
    private BandType bandType;

    /** 적용된 출력 포맷. 엑셀 셀 서식으로 옮길 때 쓴다. */
    private String format;

    /**
     * CHART 요소의 그리기 명령. 좌표는 이 요소 상자의 좌상단 기준이다.
     * 축 계산과 마크 배치가 여기서 이미 끝나 있어 익스포터는 옮겨 그리기만 한다.
     */
    private java.util.List<kr.co.kreport.engine.chart.ChartShape> chartShapes;

    public static RenderedElement of(ElementType type, double x, double y,
                                     double width, double height, ElementStyle style) {
        RenderedElement e = new RenderedElement();
        e.type = type;
        e.x = x;
        e.y = y;
        e.width = width;
        e.height = height;
        e.style = style;
        return e;
    }

    public double getRight() {
        return x + width;
    }

    public double getBottom() {
        return y + height;
    }

    public boolean isTextual() {
        return type == ElementType.LABEL || type == ElementType.TEXT;
    }
}
