package com.todo.controller;

import com.todo.common.R;
import com.todo.service.TaskService;
import com.todo.vo.QuadrantBoardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 四象限看板接口，见设计方案 §6.4。
 */
@RestController
@RequestMapping("/api/quadrant")
@RequiredArgsConstructor
public class QuadrantController {

    private final TaskService taskService;

    @GetMapping("/board")
    public R<QuadrantBoardVO> board() {
        return R.ok(taskService.quadrantBoard());
    }
}
