package com.todo.vo;

import lombok.Data;

import java.util.List;

/**
 * 四象限看板数据，见设计方案 §3.3 与 §7.7。
 */
@Data
public class QuadrantBoardVO {

    private List<QuadrantGroupVO> quadrants;

    /** 看板内全部任务总数，便于前端展示 */
    private Long total;
}
