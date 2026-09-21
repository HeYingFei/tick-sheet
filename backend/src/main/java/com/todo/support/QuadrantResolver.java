package com.todo.support;

/**
 * 象限与优先级映射，见设计方案 §4.3。
 *
 * <p>规则：象限与优先级<b>正交但提供智能默认值</b>。
 * <ul>
 *   <li>新建时若用户只选象限未选优先级，按建议值填充</li>
 *   <li>用户显式选择的优先级永远优先，不被象限覆盖</li>
 *   <li>拖拽变更象限时<b>不静默修改</b>优先级，只返回建议值由前端二次确认</li>
 * </ul>
 */
public final class QuadrantResolver {

    /** 第一象限：重要且紧急 */
    public static final int Q1 = 1;
    /** 第二象限：重要不紧急 */
    public static final int Q2 = 2;
    /** 第三象限：紧急不重要 */
    public static final int Q3 = 3;
    /** 第四象限：不重要不紧急 */
    public static final int Q4 = 4;

    private QuadrantResolver() {
    }

    /**
     * 定位象限编号。
     */
    public static int quadrant(boolean important, boolean urgent) {
        if (important && urgent) {
            return Q1;
        }
        if (important) {
            return Q2;
        }
        if (urgent) {
            return Q3;
        }
        return Q4;
    }

    /**
     * 象限对应的建议优先级：Q1→1(极高)、Q2→2(高)、Q3→3(中)、Q4→4(低)。
     */
    public static int suggestPriority(boolean important, boolean urgent) {
        return quadrant(important, urgent);
    }

    public static String quadrantName(int quadrant) {
        return switch (quadrant) {
            case Q1 -> "重要且紧急";
            case Q2 -> "重要不紧急";
            case Q3 -> "紧急不重要";
            case Q4 -> "不重要不紧急";
            default -> "未知";
        };
    }

    public static String action(int quadrant) {
        return switch (quadrant) {
            case Q1 -> "立即处理";
            case Q2 -> "重点规划";
            case Q3 -> "简化处理";
            case Q4 -> "闲置舍弃";
            default -> "";
        };
    }
}
