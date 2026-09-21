package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.common.BizException;
import com.todo.entity.Tag;
import com.todo.entity.Task;
import com.todo.entity.TaskTag;
import com.todo.mapper.TagMapper;
import com.todo.mapper.TaskMapper;
import com.todo.mapper.TaskTagMapper;
import com.todo.support.QuadrantResolver;
import com.todo.support.TaskStatus;
import com.todo.support.UserContext;
import com.todo.vo.TagBriefVO;
import com.todo.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 日历服务。
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final TaskMapper taskMapper;
    private final TaskTagMapper taskTagMapper;
    private final TagMapper tagMapper;

    /**
     * 日历任务列表。
     *
     * @param startTime 起始时间（含）
     * @param endTime   结束时间（不含）
     * @param dateField 映射字段：due_time / start_time。默认 due_time
     */
    public List<TaskVO> tasks(OffsetDateTime startTime, OffsetDateTime endTime, String dateField) {
        if (startTime == null || endTime == null) {
            throw BizException.paramInvalid("日历查询必须指定 startTime 和 endTime");
        }

        String field = StringUtils.hasText(dateField) ? dateField.trim() : "due_time";

        List<Task> tasks;
        switch (field) {
            case "due_time" -> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                    .eq(Task::getIsDelete, false)
                    .eq(Task::getUserId, UserContext.getUserId())
                    .ne(Task::getStatus, TaskStatus.CANCELED)
                    .ge(Task::getDueTime, startTime)
                    .lt(Task::getDueTime, endTime));
            case "start_time" -> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                    .eq(Task::getIsDelete, false)
                    .eq(Task::getUserId, UserContext.getUserId())
                    .ne(Task::getStatus, TaskStatus.CANCELED)
                    .ge(Task::getStartTime, startTime)
                    .lt(Task::getStartTime, endTime));
            default -> throw BizException.paramInvalid("不支持的日历字段: " + field);
        }

        return toVOList(tasks);
    }

    /**
     * 日历热力图：统计每天的任务数和完成数。
     */
    public Map<String, Map<String, Long>> heatmap(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw BizException.paramInvalid("热力图查询必须指定 startTime 和 endTime");
        }

        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getCreateTime, Task::getFinishTime, Task::getStatus)
                .eq(Task::getIsDelete, false)
                .eq(Task::getUserId, UserContext.getUserId())
                .ne(Task::getStatus, TaskStatus.CANCELED)
                .and(w -> w
                        .and(inner -> inner.ge(Task::getCreateTime, startTime).lt(Task::getCreateTime, endTime))
                        .or()
                        .and(inner -> inner.ge(Task::getFinishTime, startTime).lt(Task::getFinishTime, endTime))));

        Map<String, long[]> byDay = new HashMap<>();
        for (Task task : tasks) {
            if (task.getCreateTime() != null) {
                String day = task.getCreateTime().toLocalDate().toString();
                byDay.computeIfAbsent(day, k -> new long[]{0, 0})[0]++;
            }
            if (task.getFinishTime() != null && task.getStatus() == TaskStatus.DONE) {
                String day = task.getFinishTime().toLocalDate().toString();
                byDay.computeIfAbsent(day, k -> new long[]{0, 0})[1]++;
            }
        }

        Map<String, Map<String, Long>> result = new HashMap<>();
        byDay.forEach((date, counts) -> {
            Map<String, Long> entry = new HashMap<>();
            entry.put("taskCount", counts[0]);
            entry.put("doneCount", counts[1]);
            result.put(date, entry);
        });
        return result;
    }

    private List<TaskVO> toVOList(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now(ZONE);
        Map<Long, List<TagBriefVO>> tagsByTask = loadTags(tasks.stream().map(Task::getId).toList());

        return tasks.stream().map(task -> {
            TaskVO vo = new TaskVO();
            vo.setId(task.getId());
            vo.setTitle(task.getTitle());
            vo.setStartTime(task.getStartTime());
            vo.setDueTime(task.getDueTime());
            vo.setFinishTime(task.getFinishTime());
            vo.setStatus(task.getStatus());

            boolean important = Boolean.TRUE.equals(task.getIsImportant());
            boolean urgent = Boolean.TRUE.equals(task.getIsUrgent());
            vo.setIsImportant(important);
            vo.setIsUrgent(urgent);
            vo.setQuadrant(QuadrantResolver.quadrant(important, urgent));
            vo.setPriority(task.getPriority());
            vo.setTags(tagsByTask.getOrDefault(task.getId(), List.of()));
            vo.setCreateTime(task.getCreateTime());
            return vo;
        }).toList();
    }

    private Map<Long, List<TagBriefVO>> loadTags(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        List<TaskTag> links = taskTagMapper.selectByTaskIds(taskIds);
        if (links.isEmpty()) {
            return Map.of();
        }

        var tagIdsList = links.stream().map(TaskTag::getTagId).collect(Collectors.toSet());
        Map<Long, Tag> tagsById = tagMapper.selectBatchIds(tagIdsList).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<Long, List<TagBriefVO>> result = new HashMap<>();
        for (TaskTag link : links) {
            Tag tag = tagsById.get(link.getTagId());
            if (tag == null) continue;
            TagBriefVO brief = new TagBriefVO();
            brief.setId(tag.getId());
            brief.setName(tag.getName());
            brief.setColor(tag.getColor());
            result.computeIfAbsent(link.getTaskId(), k -> new ArrayList<>()).add(brief);
        }
        result.values().forEach(list -> list.sort(Comparator.comparing(TagBriefVO::getId)));
        return result;
    }
}
