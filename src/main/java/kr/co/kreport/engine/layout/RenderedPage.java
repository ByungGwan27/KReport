package kr.co.kreport.engine.layout;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 좌표가 확정된 페이지 한 장 */
@Getter
@Setter
public class RenderedPage {

    /** 1부터 시작하는 출력 페이지 번호 */
    private int pageNo;

    /** 그룹 페이지 번호 재시작이 걸린 경우의 표시용 번호 */
    private int displayPageNo;

    private double width;
    private double height;

    private final List<RenderedElement> elements = new ArrayList<>();

    /** 이 페이지가 마지막으로 소비한 데이터 행 번호. 페이지 머리말/꼬리말을 뒤늦게 그릴 때 복원용 */
    private int lastRowIndex = -1;

    public void add(RenderedElement element) {
        elements.add(element);
    }

    public void addAll(List<RenderedElement> list) {
        elements.addAll(list);
    }
}
