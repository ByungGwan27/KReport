package kr.co.kreport.engine.layout;

import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.expression.EvalContext;
import kr.co.kreport.engine.expression.ExpressionEvaluator;
import kr.co.kreport.engine.expression.Values;
import kr.co.kreport.template.GroupDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 행마다 어느 그룹 인스턴스에 속하는지 미리 계산해 둔 색인.
 *
 * <p>레이아웃(그룹 헤더/푸터를 언제 끼워 넣을지)과 집계(어느 범위로 합산할지)가
 * 같은 판정을 써야 한다. 두 곳에서 따로 계산하면 데이터에 null 이나 공백 키가 섞였을 때
 * 그룹 경계가 미세하게 어긋나 합계와 소계가 맞지 않는 사고로 이어진다.</p>
 *
 * <p>정렬은 데이터셋 SQL 이 책임진다. 엔진은 인접한 동일 키만 한 그룹으로 본다.</p>
 */
public final class GroupIndex {

    private final List<String> groupNames;

    /** [그룹 레벨][행 번호] = 그룹 인스턴스 번호(0부터) */
    private final int[][] instanceIndex;

    /** [그룹 레벨] = 인스턴스 개수 */
    private final int[] instanceCount;

    private final int rowCount;

    private GroupIndex(List<String> groupNames, int[][] instanceIndex, int[] instanceCount, int rowCount) {
        this.groupNames = groupNames;
        this.instanceIndex = instanceIndex;
        this.instanceCount = instanceCount;
        this.rowCount = rowCount;
    }

    /**
     * @param groups 바깥 그룹부터 안쪽 그룹 순서로 정렬된 그룹 정의
     */
    public static GroupIndex build(DataTable data, List<GroupDef> groups, EvalContext context) {
        int rowCount = data.size();
        int levels = groups.size();

        List<String> names = new ArrayList<>(levels);
        for (GroupDef g : groups) {
            names.add(g.getName());
        }

        int[][] index = new int[levels][rowCount];
        int[] counts = new int[levels];
        Object[] previousKeys = new Object[levels];

        for (int row = 0; row < rowCount; row++) {
            context.setRow(data.row(row), row);
            boolean outerChanged = false;

            for (int level = 0; level < levels; level++) {
                Object key = ExpressionEvaluator.evalQuietly(groups.get(level).getExpression(), context);
                // 바깥 그룹이 바뀌면 안쪽 그룹도 무조건 새 인스턴스다
                boolean changed = outerChanged || row == 0 || !Values.equal(key, previousKeys[level]);
                if (changed) {
                    counts[level]++;
                    outerChanged = true;
                }
                previousKeys[level] = key;
                index[level][row] = counts[level] - 1;
            }
        }
        return new GroupIndex(names, index, counts, rowCount);
    }

    public static GroupIndex empty(int rowCount) {
        return new GroupIndex(List.of(), new int[0][0], new int[0], rowCount);
    }

    public List<String> getGroupNames() {
        return groupNames;
    }

    public int levelCount() {
        return groupNames.size();
    }

    /** 그룹명에 대응하는 레벨. 없으면 -1 */
    public int levelOf(String groupName) {
        return groupNames.indexOf(groupName);
    }

    public int instanceIndex(int level, int rowIndex) {
        if (level < 0 || level >= instanceIndex.length || rowIndex < 0 || rowIndex >= rowCount) {
            return -1;
        }
        return instanceIndex[level][rowIndex];
    }

    public int instanceIndex(String groupName, int rowIndex) {
        return instanceIndex(levelOf(groupName), rowIndex);
    }

    public int instanceCount(int level) {
        return level < 0 || level >= instanceCount.length ? 0 : instanceCount[level];
    }

    /** 해당 행에서 이 레벨의 그룹이 새로 시작하는가 */
    public boolean isGroupStart(int level, int rowIndex) {
        if (rowIndex == 0) {
            return true;
        }
        return instanceIndex(level, rowIndex) != instanceIndex(level, rowIndex - 1);
    }

    /** 해당 행에서 이 레벨의 그룹이 끝나는가 */
    public boolean isGroupEnd(int level, int rowIndex) {
        if (rowIndex >= rowCount - 1) {
            return true;
        }
        return instanceIndex(level, rowIndex) != instanceIndex(level, rowIndex + 1);
    }

    /**
     * 이 행이 속한 그룹 인스턴스의 행 구간 {@code [시작, 끝]}.
     * 그룹 단위로 집계하는 차트가 어느 행까지 읽어야 하는지 정할 때 쓴다.
     */
    public int[] rowRange(int level, int rowIndex) {
        int instance = instanceIndex(level, rowIndex);
        if (instance < 0) {
            return new int[]{0, rowCount - 1};
        }
        int start = rowIndex;
        while (start > 0 && instanceIndex(level, start - 1) == instance) {
            start--;
        }
        int end = rowIndex;
        while (end < rowCount - 1 && instanceIndex(level, end + 1) == instance) {
            end++;
        }
        return new int[]{start, end};
    }

    public int[] rowRange(String groupName, int rowIndex) {
        return rowRange(levelOf(groupName), rowIndex);
    }

    /** 그룹 인스턴스 안에서 이 행이 몇 번째인지 (1부터) */
    public int rowNumberInGroup(int level, int rowIndex) {
        int instance = instanceIndex(level, rowIndex);
        if (instance < 0) {
            return rowIndex + 1;
        }
        int start = rowIndex;
        while (start > 0 && instanceIndex(level, start - 1) == instance) {
            start--;
        }
        return rowIndex - start + 1;
    }

    /** 그룹별 현재 인스턴스 번호 묶음. 페이지 헤더/푸터를 나중에 그릴 때 복원용으로 쓴다. */
    public Map<String, Integer> snapshot(int rowIndex) {
        Map<String, Integer> snap = new HashMap<>();
        for (int level = 0; level < groupNames.size(); level++) {
            snap.put(groupNames.get(level), instanceIndex(level, rowIndex));
        }
        return snap;
    }
}
