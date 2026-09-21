package com.todo.controller;

import com.todo.common.PageResult;
import com.todo.common.R;
import com.todo.dto.TaskQueryRequest;
import com.todo.dto.TaskRequests;
import com.todo.dto.TaskSaveRequest;
import com.todo.service.TaskService;
import com.todo.vo.TaskVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 任务接口，见设计方案 §6.4。
 *
 * <p>批量操作使用 POST 而非带请求体的 DELETE：部分代理与网关会丢弃
 * DELETE 请求体，用 POST 语义稳定且可预期。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public R<PageResult<TaskVO>> page(TaskQueryRequest query) {
        return R.ok(taskService.page(query));
    }

    @GetMapping("/{id}")
    public R<TaskVO> detail(@PathVariable Long id) {
        return R.ok(taskService.detail(id));
    }

    @PostMapping
    public R<TaskVO> create(@Valid @RequestBody TaskSaveRequest request) {
        return R.ok("任务已创建", taskService.create(request));
    }

    @PutMapping("/{id}")
    public R<TaskVO> update(@PathVariable Long id, @Valid @RequestBody TaskSaveRequest request) {
        return R.ok("任务已保存", taskService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return R.ok("任务已删除", null);
    }

    @PatchMapping("/{id}/status")
    public R<TaskVO> changeStatus(@PathVariable Long id,
                                  @Valid @RequestBody TaskRequests.StatusChange request) {
        return R.ok("状态已更新", taskService.changeStatus(id, request.getStatus()));
    }

    @PatchMapping("/{id}/quadrant")
    public R<TaskVO> changeQuadrant(@PathVariable Long id,
                                    @Valid @RequestBody TaskRequests.QuadrantChange request) {
        return R.ok("象限已更新",
                taskService.changeQuadrant(id, request.getIsImportant(), request.getIsUrgent()));
    }

    @PatchMapping("/{id}/order")
    public R<Void> changeOrder(@PathVariable Long id,
                               @Valid @RequestBody TaskRequests.OrderChange request) {
        taskService.changeOrder(id, request.getSortOrder());
        return R.ok("排序已更新", null);
    }

    @PostMapping("/batch/status")
    public R<Map<String, Integer>> batchStatus(@Valid @RequestBody TaskRequests.BatchStatus request) {
        int changed = taskService.batchChangeStatus(request.getIds(), request.getStatus());
        return R.ok("已更新 " + changed + " 个任务", Map.of("changed", changed));
    }

    @PostMapping("/batch/delete")
    public R<Map<String, Integer>> batchDelete(@Valid @RequestBody TaskRequests.BatchIds request) {
        int deleted = taskService.batchDelete(request.getIds());
        return R.ok("已删除 " + deleted + " 个任务", Map.of("deleted", deleted));
    }

    @PostMapping("/batch/tags")
    public R<Map<String, Integer>> batchTags(@Valid @RequestBody TaskRequests.BatchTags request) {
        int updated = taskService.batchChangeTags(
                request.getIds(), request.getTagIds(), request.getMode());
        return R.ok("已更新 " + updated + " 个任务的标签", Map.of("updated", updated));
    }
}
