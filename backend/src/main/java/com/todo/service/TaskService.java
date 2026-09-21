package com.todo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.todo.common.BizException;
import com.todo.common.PageResult;
import com.todo.dto.TaskQueryRequest;
import com.todo.dto.TaskSaveRequest;
import com.todo.entity.Tag;
import com.todo.entity.Task;
import com.todo.entity.TaskTag;
import com.todo.mapper.TagMapper;
import com.todo.mapper.TaskMapper;
import com.todo.mapper.TaskTagMapper;
import com.todo.support.OperateType;
import com.todo.support.OverdueCalculator;
import com.todo.support.QuadrantResolver;
import com.todo.support.StatusTransition;
import com.todo.support.TaskStatus;
import com.todo.support.UserContext;
import com.todo.vo.QuadrantBoardVO;
import com.todo.vo.QuadrantGroupVO;
import com.todo.vo.TagBriefVO;
import com.todo.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务服务，见设计方案 §4 与 §6.4。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_TAGS_PER_TASK = 20;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int SNAPSHOT_TEXT_LIMIT = 200;

    /** 排序字段白名单：避免把请求参数直接拼进 ORDER BY 造成注入 */
    private static final Map<String, SFunction<Task, ?>> SORT_FIELDS = Map.of(
            "createTime", Task::getCreateTime,
            "updateTime", Task::getUpdateTime,
            "dueTime", Task::getDueTime,
            "priority", Task::getPriority,
            "sortOrder", Task::getSortOrder,
            "status", Task::getStatus);

    /** 时间范围可作用的字段白名单 */
    private static final Map<String, SFunction<Task, ?>> TIME_FIELDS = Map.of(
            "create_time", Task::getCreateTime,
            "due_time", Task::getDueTime,
            "start_time", Task::getStartTime,
            "finish_time", Task::getFinishTime);

    private static final Map<String, String> FIELD_LABELS = Map.of(
            "title", "标题",
            "content", "备注",
            "start_time", "开始时间",
            "due_time", "截止时间",
            "priority", "优先级",
            "is_important", "重要",
            "is_urgent", "紧急",
            "tag_ids", "标签");

    private final TaskMapper taskMapper;
    private final TagMapper tagMapper;
    private final TaskTagMapper taskTagMapper;
    private final TagService tagService;
    private final TaskLogService taskLogService;

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public PageResult<TaskVO> page(TaskQueryRequest query) {
        long current = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        long size = query.getSize() == null || query.getSize() < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(query.getSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<Task> wrapper = buildWrapper(query);
        wrapper.eq(Task::getUserId, UserContext.getUserId());
        applySort(wrapper, query);

        Page<Task> result = taskMapper.selectPage(new Page<>(current, size), wrapper);

        OffsetDateTime now = OffsetDateTime.now(ZONE);
        Map<Long, List<TagBriefVO>> tagsByTask =
                loadTags(result.getRecords().stream().map(Task::getId).toList());

        List<TaskVO> rows = result.getRecords().stream()
                .map(task -> toVO(task, tagsByTask.getOrDefault(task.getId(), List.of()), now))
                .toList();
        return PageResult.of(result, rows);
    }

    public TaskVO detail(Long id) {
        Task task = getExisting(id);
        return toVO(task, loadTags(List.of(id)).getOrDefault(id, List.of()),
                OffsetDateTime.now(ZONE));
    }

    /** 四象限看板。已取消任务不计入，与统计口径一致（设计方案 §4.7） */
    public QuadrantBoardVO quadrantBoard() {
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .eq(Task::getUserId, UserContext.getUserId())
                .in(Task::getStatus, List.of(TaskStatus.TODO, TaskStatus.DOING, TaskStatus.DONE))
                .orderByAsc(Task::getSortOrder)
                .orderByAsc(Task::getId));

        OffsetDateTime now = OffsetDateTime.now(ZONE);
        Map<Long, List<TagBriefVO>> tagsByTask =
                loadTags(tasks.stream().map(Task::getId).toList());

        Map<Integer, List<Task>> grouped = new HashMap<>();
        for (Task task : tasks) {
            grouped.computeIfAbsent(quadrantOf(task), key -> new ArrayList<>()).add(task);
        }

        List<QuadrantGroupVO> quadrants = new ArrayList<>(4);
        for (int quadrant = QuadrantResolver.Q1; quadrant <= QuadrantResolver.Q4; quadrant++) {
            List<Task> groupTasks = grouped.getOrDefault(quadrant, List.of());

            QuadrantGroupVO group = new QuadrantGroupVO();
            group.setQuadrant(quadrant);
            group.setName(QuadrantResolver.quadrantName(quadrant));
            group.setAction(QuadrantResolver.action(quadrant));
            group.setSuggestPriority(QuadrantResolver.suggestPriority(
                    quadrant == QuadrantResolver.Q1 || quadrant == QuadrantResolver.Q2,
                    quadrant == QuadrantResolver.Q1 || quadrant == QuadrantResolver.Q3));
            group.setTasks(groupTasks.stream()
                    .map(task -> toVO(task, tagsByTask.getOrDefault(task.getId(), List.of()), now))
                    .toList());

            long total = groupTasks.size();
            long done = groupTasks.stream().filter(t -> Objects.equals(t.getStatus(), TaskStatus.DONE)).count();
            group.setTotal(total);
            group.setDoneCount(done);
            // 分母为 0 时返回 null，前端展示「—」而不是 0%
            group.setCompletionRate(total == 0 ? null : round2(done * 100.0 / total));

            quadrants.add(group);
        }

        QuadrantBoardVO board = new QuadrantBoardVO();
        board.setQuadrants(quadrants);
        board.setTotal((long) tasks.size());
        return board;
    }

    // ------------------------------------------------------------------
    // 写操作
    // ------------------------------------------------------------------

    @Transactional
    public TaskVO create(TaskSaveRequest request) {
        assertTimeOrder(request.getStartTime(), request.getDueTime());
        List<Long> tagIds = tagService.assertAllExist(request.getTagIds());
        assertTagLimit(tagIds);

        boolean important = Boolean.TRUE.equals(request.getIsImportant());
        boolean urgent = Boolean.TRUE.equals(request.getIsUrgent());

        Task task = new Task();
        task.setTitle(request.getTitle().trim());
        task.setContent(normalizeContent(request.getContent()));
        task.setStartTime(request.getStartTime());
        task.setDueTime(request.getDueTime());
        task.setIsImportant(important);
        task.setIsUrgent(urgent);
        // 用户显式选择的优先级优先，否则按象限建议值（设计方案 §4.3）
        task.setPriority(request.getPriority() != null
                ? request.getPriority()
                : QuadrantResolver.suggestPriority(important, urgent));
        task.setStatus(TaskStatus.TODO);
        task.setSortOrder(nextSortOrder(important, urgent));
        task.setVersion(0);
        task.setIsDelete(false);
        task.setUserId(UserContext.getUserId());

        taskMapper.insert(task);

        if (!tagIds.isEmpty()) {
            taskTagMapper.insertBatch(task.getId(), tagIds);
        }

        taskLogService.record(task.getId(), OperateType.CREATE, "创建任务：" + task.getTitle(), null);
        return detail(task.getId());
    }

    @Transactional
    public TaskVO update(Long id, TaskSaveRequest request) {
        Task existing = getExisting(id);
        assertTimeOrder(request.getStartTime(), request.getDueTime());
        List<Long> tagIds = tagService.assertAllExist(request.getTagIds());
        assertTagLimit(tagIds);

        if (request.getVersion() != null && !request.getVersion().equals(existing.getVersion())) {
            throw BizException.conflict("任务已被修改，请刷新后重试");
        }

        boolean important = Boolean.TRUE.equals(request.getIsImportant());
        boolean urgent = Boolean.TRUE.equals(request.getIsUrgent());
        int priority = request.getPriority() != null
                ? request.getPriority()
                : (existing.getPriority() != null
                        ? existing.getPriority()
                        : QuadrantResolver.suggestPriority(important, urgent));

        String title = request.getTitle().trim();
        String content = normalizeContent(request.getContent());
        List<Long> beforeTagIds = taskTagMapper.selectTagIdsByTaskId(id);

        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        collectChange(changes, "title", existing.getTitle(), title);
        collectChange(changes, "content", existing.getContent(), content);
        collectChange(changes, "start_time", existing.getStartTime(), request.getStartTime());
        collectChange(changes, "due_time", existing.getDueTime(), request.getDueTime());
        collectChange(changes, "priority", existing.getPriority(), priority);
        collectChange(changes, "is_important", existing.getIsImportant(), important);
        collectChange(changes, "is_urgent", existing.getIsUrgent(), urgent);
        collectChange(changes, "tag_ids", sorted(beforeTagIds), sorted(tagIds));

        if (changes.isEmpty()) {
            // 无实际变更时不涨版本、不写日志，避免时间线被空操作刷屏
            return detail(id);
        }

        LambdaUpdateWrapper<Task> update = Wrappers.<Task>lambdaUpdate()
                .eq(Task::getId, id)
                .eq(Task::getVersion, existing.getVersion())
                .set(Task::getTitle, title)
                .set(Task::getContent, content)
                .set(Task::getStartTime, request.getStartTime())
                .set(Task::getDueTime, request.getDueTime())
                .set(Task::getIsImportant, important)
                .set(Task::getIsUrgent, urgent)
                .set(Task::getPriority, priority)
                .set(Task::getVersion, existing.getVersion() + 1);

        if (taskMapper.update(null, update) == 0) {
            throw BizException.conflict("任务已被修改，请刷新后重试");
        }

        replaceTags(id, tagIds);
        taskLogService.record(id, OperateType.MODIFY, describe(changes) + " 变更", changes);
        return detail(id);
    }

    /**
     * 删除任务（软删除）。
     *
     * <p>保留 task_tag 关联，以便将来支持恢复；标签计数查询已排除软删任务，
     * 因此不会出现「标签下挂着看不见的任务」。
     */
    @Transactional
    public void delete(Long id) {
        Task task = getExisting(id);
        taskMapper.deleteById(id);
        taskLogService.record(id, OperateType.DELETE, "删除任务：" + task.getTitle(), null);
    }

    @Transactional
    public TaskVO changeStatus(Long id, Integer status) {
        Task task = getExisting(id);
        if (!TaskStatus.isValid(status)) {
            throw BizException.business("非法的任务状态值: " + status);
        }
        StatusTransition.assertCanTransit(task.getStatus(), status);

        if (Objects.equals(task.getStatus(), status)) {
            return detail(id);
        }
        applyStatus(task, status, OffsetDateTime.now(ZONE));
        return detail(id);
    }

    /**
     * 象限变更（看板拖拽）。
     *
     * <p>按设计方案 §4.3，此处<b>不静默修改优先级</b>，
     * 仅在与当前优先级不一致时返回 {@code suggestPriority} 供前端二次确认。
     */
    @Transactional
    public TaskVO changeQuadrant(Long id, boolean important, boolean urgent) {
        Task task = getExisting(id);
        boolean oldImportant = Boolean.TRUE.equals(task.getIsImportant());
        boolean oldUrgent = Boolean.TRUE.equals(task.getIsUrgent());

        if (oldImportant != important || oldUrgent != urgent) {
            LambdaUpdateWrapper<Task> update = Wrappers.<Task>lambdaUpdate()
                    .eq(Task::getId, id)
                    .eq(Task::getVersion, task.getVersion())
                    .set(Task::getIsImportant, important)
                    .set(Task::getIsUrgent, urgent)
                    .set(Task::getVersion, task.getVersion() + 1);
            if (taskMapper.update(null, update) == 0) {
                throw BizException.conflict("任务已被修改，请刷新后重试");
            }

            int from = QuadrantResolver.quadrant(oldImportant, oldUrgent);
            int to = QuadrantResolver.quadrant(important, urgent);
            taskLogService.record(id, OperateType.MODIFY,
                    "象限变更：" + QuadrantResolver.quadrantName(from)
                            + " → " + QuadrantResolver.quadrantName(to),
                    Map.of("quadrant", Map.of("old", from, "new", to)));
        }

        TaskVO vo = detail(id);
        int suggest = QuadrantResolver.suggestPriority(important, urgent);
        if (!Objects.equals(suggest, task.getPriority())) {
            vo.setSuggestPriority(suggest);
        }
        return vo;
    }

    /**
     * 拖拽排序。
     *
     * <p>刻意不写操作日志：一次拖拽只是一次视觉调整，写日志会让时间线充满噪声，
     * 见设计方案 §5.5 的噪声控制原则。
     */
    @Transactional
    public void changeOrder(Long id, Integer sortOrder) {
        Task task = getExisting(id);
        if (sortOrder == null) {
            throw BizException.paramInvalid("排序序号不能为空");
        }
        if (Objects.equals(task.getSortOrder(), sortOrder)) {
            return;
        }

        LambdaUpdateWrapper<Task> update = Wrappers.<Task>lambdaUpdate()
                .eq(Task::getId, id)
                .eq(Task::getVersion, task.getVersion())
                .set(Task::getSortOrder, sortOrder)
                .set(Task::getVersion, task.getVersion() + 1);
        if (taskMapper.update(null, update) == 0) {
            throw BizException.conflict("任务已被修改，请刷新后重试");
        }
    }

    /** 批量状态变更。先整体校验，任一非法则全部回滚，避免部分成功 */
    @Transactional
    public int batchChangeStatus(List<Long> ids, Integer status) {
        List<Long> distinct = normalizeIds(ids);
        if (!TaskStatus.isValid(status)) {
            throw BizException.business("非法的任务状态值: " + status);
        }

        List<Task> tasks = loadAll(distinct);
        for (Task task : tasks) {
            StatusTransition.assertCanTransit(task.getStatus(), status);
        }

        OffsetDateTime now = OffsetDateTime.now(ZONE);
        int changed = 0;
        for (Task task : tasks) {
            if (!Objects.equals(task.getStatus(), status)) {
                applyStatus(task, status, now);
                changed++;
            }
        }
        return changed;
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        List<Long> distinct = normalizeIds(ids);
        List<Task> tasks = loadAll(distinct);
        for (Task task : tasks) {
            taskMapper.deleteById(task.getId());
            taskLogService.record(task.getId(), OperateType.DELETE, "删除任务：" + task.getTitle(), null);
        }
        return tasks.size();
    }

    /** 批量修改标签，mode 取 add / remove / replace */
    @Transactional
    public int batchChangeTags(List<Long> ids, List<Long> tagIds, String mode) {
        List<Long> distinct = normalizeIds(ids);
        String normalizedMode = mode == null ? "add" : mode.trim().toLowerCase();
        if (!Set.of("add", "remove", "replace").contains(normalizedMode)) {
            throw BizException.paramInvalid("不支持的标签变更模式: " + mode);
        }

        List<Long> targetTagIds = tagService.assertAllExist(tagIds);
        assertTagLimit(targetTagIds);
        List<Task> tasks = loadAll(distinct);

        for (Task task : tasks) {
            List<Long> current = taskTagMapper.selectTagIdsByTaskId(task.getId());
            List<Long> next = switch (normalizedMode) {
                case "remove" -> current.stream().filter(tagId -> !targetTagIds.contains(tagId)).toList();
                case "replace" -> targetTagIds;
                default -> union(current, targetTagIds);
            };
            assertTagLimit(next);

            if (sorted(current).equals(sorted(next))) {
                continue;
            }
            replaceTags(task.getId(), next);
            taskLogService.record(task.getId(), OperateType.MODIFY, "标签变更",
                    Map.of("tag_ids", Map.of("old", current, "new", next)));
        }
        return tasks.size();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private LambdaQueryWrapper<Task> buildWrapper(TaskQueryRequest query) {
        LambdaQueryWrapper<Task> wrapper = Wrappers.lambdaQuery();

        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(w -> w.like(Task::getTitle, keyword).or().like(Task::getContent, keyword));
        }

        if (query.getStatus() != null && !query.getStatus().isEmpty()) {
            List<Integer> statuses = query.getStatus().stream().filter(Objects::nonNull).distinct().toList();
            if (statuses.isEmpty()) {
                throw BizException.paramInvalid("状态筛选值不能为空");
            }
            statuses.forEach(status -> {
                if (!TaskStatus.isValid(status)) {
                    throw BizException.paramInvalid("非法的状态筛选值: " + status);
                }
            });
            wrapper.in(Task::getStatus, statuses);
        }

        if (query.getQuadrant() != null) {
            int quadrant = query.getQuadrant();
            if (quadrant < QuadrantResolver.Q1 || quadrant > QuadrantResolver.Q4) {
                throw BizException.paramInvalid("象限取值范围为 1-4");
            }
            wrapper.eq(Task::getIsImportant,
                            quadrant == QuadrantResolver.Q1 || quadrant == QuadrantResolver.Q2)
                    .eq(Task::getIsUrgent,
                            quadrant == QuadrantResolver.Q1 || quadrant == QuadrantResolver.Q3);
        }

        if (query.getTagIds() != null && !query.getTagIds().isEmpty()) {
            List<Long> tagIds = query.getTagIds().stream().filter(Objects::nonNull).distinct().toList();
            if (tagIds.isEmpty()) {
                throw BizException.paramInvalid("标签筛选值不能为空");
            }
            // AND 语义：任务必须同时包含所选全部标签（设计方案 §4.4）
            List<Long> matched = taskTagMapper.selectTaskIdsHavingAllTags(tagIds, tagIds.size());
            if (matched.isEmpty()) {
                // 用恒假条件表达「无匹配」，避免 in 空集合被优化掉导致全量返回
                wrapper.eq(Task::getId, -1L);
            } else {
                wrapper.in(Task::getId, matched);
            }
        }

        if (query.getStartTime() != null || query.getEndTime() != null) {
            String field = query.getTimeField();
            SFunction<Task, ?> column = field == null
                    ? Task::getCreateTime
                    : TIME_FIELDS.get(field.trim().toLowerCase());
            if (column == null) {
                throw BizException.paramInvalid("不支持的时间字段: " + field);
            }
            wrapper.ge(query.getStartTime() != null, column, query.getStartTime());
            wrapper.lt(query.getEndTime() != null, column, query.getEndTime());
        }

        if (Boolean.TRUE.equals(query.getOverdueOnly())) {
            wrapper.in(Task::getStatus, List.of(TaskStatus.TODO, TaskStatus.DOING))
                    .isNotNull(Task::getDueTime)
                    .lt(Task::getDueTime, OffsetDateTime.now(ZONE));
        }

        return wrapper;
    }

    private void applySort(LambdaQueryWrapper<Task> wrapper, TaskQueryRequest query) {
        SFunction<Task, ?> column = query.getSortBy() == null
                ? null
                : SORT_FIELDS.get(query.getSortBy().trim());

        if (query.getSortBy() != null && column == null) {
            throw BizException.paramInvalid("不支持的排序字段: " + query.getSortBy());
        }

        boolean asc = !"desc".equalsIgnoreCase(query.getSortOrder());
        if (column == null) {
            // 默认按创建时间倒序
            wrapper.orderByDesc(Task::getCreateTime);
        } else {
            wrapper.orderBy(true, asc, column);
        }
        // 次级排序保证分页结果稳定，避免同值记录在翻页时重复或丢失
        wrapper.orderByDesc(Task::getId);
    }

    private void applyStatus(Task task, int status, OffsetDateTime now) {
        int currentVersion = task.getVersion() == null ? 0 : task.getVersion();

        LambdaUpdateWrapper<Task> update = Wrappers.<Task>lambdaUpdate()
                .eq(Task::getId, task.getId())
                .eq(Task::getVersion, currentVersion)
                .set(Task::getStatus, status)
                .set(Task::getVersion, currentVersion + 1);

        if (status == TaskStatus.DONE) {
            update.set(Task::getFinishTime, now);
        } else {
            // 退出已完成状态时清空完成时间（设计方案 §4.1）
            update.setSql("finish_time = NULL");
        }

        if (taskMapper.update(null, update) == 0) {
            throw BizException.conflict("任务已被修改，请刷新后重试");
        }

        taskLogService.record(task.getId(), OperateType.forStatus(status),
                "状态变更：" + TaskStatus.text(task.getStatus()) + " → " + TaskStatus.text(status),
                Map.of("status", Map.of("old", task.getStatus(), "new", status)));
    }

    private Task getExisting(Long id) {
        Task task = taskMapper.selectOne(Wrappers.<Task>lambdaQuery()
                .eq(Task::getId, id)
                .eq(Task::getUserId, UserContext.getUserId()));
        if (task == null) {
            throw BizException.notFound("任务不存在: " + id);
        }
        return task;
    }

    private List<Task> loadAll(List<Long> ids) {
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .in(Task::getId, ids)
                .eq(Task::getUserId, UserContext.getUserId()));
        if (tasks.size() != ids.size()) {
            Set<Long> found = tasks.stream().map(Task::getId).collect(Collectors.toSet());
            List<Long> missing = ids.stream().filter(id -> !found.contains(id)).toList();
            throw BizException.notFound("任务不存在: " + missing);
        }
        return tasks;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            throw BizException.paramInvalid("任务 ID 列表不能为空");
        }
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            throw BizException.paramInvalid("任务 ID 列表不能为空");
        }
        return distinct;
    }

    private void replaceTags(Long taskId, List<Long> tagIds) {
        taskTagMapper.deleteByTaskId(taskId);
        if (!tagIds.isEmpty()) {
            taskTagMapper.insertBatch(taskId, tagIds);
        }
    }

    private int nextSortOrder(boolean important, boolean urgent) {
        Task last = taskMapper.selectOne(Wrappers.<Task>lambdaQuery()
                .eq(Task::getUserId, UserContext.getUserId())
                .eq(Task::getIsImportant, important)
                .eq(Task::getIsUrgent, urgent)
                .orderByDesc(Task::getSortOrder)
                .last("LIMIT 1"));
        if (last == null || last.getSortOrder() == null) {
            return 0;
        }
        return last.getSortOrder() + 1;
    }

    private Map<Long, List<TagBriefVO>> loadTags(List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Map.of();
        }
        List<TaskTag> links = taskTagMapper.selectByTaskIds(taskIds);
        if (links.isEmpty()) {
            return Map.of();
        }

        Set<Long> tagIds = links.stream().map(TaskTag::getTagId).collect(Collectors.toSet());
        Map<Long, Tag> tagsById = tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));

        Map<Long, List<TagBriefVO>> result = new HashMap<>();
        for (TaskTag link : links) {
            Tag tag = tagsById.get(link.getTagId());
            if (tag == null) {
                continue;
            }
            TagBriefVO brief = new TagBriefVO();
            brief.setId(tag.getId());
            brief.setName(tag.getName());
            brief.setColor(tag.getColor());
            result.computeIfAbsent(link.getTaskId(), key -> new ArrayList<>()).add(brief);
        }
        result.values().forEach(list -> list.sort(Comparator.comparing(TagBriefVO::getId)));
        return result;
    }

    private TaskVO toVO(Task task, List<TagBriefVO> tags, OffsetDateTime now) {
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setTitle(task.getTitle());
        vo.setContent(task.getContent());
        vo.setStartTime(task.getStartTime());
        vo.setDueTime(task.getDueTime());
        vo.setFinishTime(task.getFinishTime());
        vo.setStatus(task.getStatus());
        vo.setStatusText(TaskStatus.text(task.getStatus()));

        boolean important = Boolean.TRUE.equals(task.getIsImportant());
        boolean urgent = Boolean.TRUE.equals(task.getIsUrgent());
        vo.setIsImportant(important);
        vo.setIsUrgent(urgent);

        int quadrant = QuadrantResolver.quadrant(important, urgent);
        vo.setQuadrant(quadrant);
        vo.setQuadrantName(QuadrantResolver.quadrantName(quadrant));
        vo.setPriority(task.getPriority());
        vo.setSortOrder(task.getSortOrder());
        vo.setTags(tags);

        boolean overdue = OverdueCalculator.isOverdue(task.getStatus(), task.getDueTime(), now);
        vo.setOverdue(overdue);
        vo.setOverdueDays(overdue
                ? OverdueCalculator.overdueDays(task.getStatus(), task.getDueTime(), now)
                : 0L);

        vo.setVersion(task.getVersion());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        return vo;
    }

    private int quadrantOf(Task task) {
        return QuadrantResolver.quadrant(
                Boolean.TRUE.equals(task.getIsImportant()),
                Boolean.TRUE.equals(task.getIsUrgent()));
    }

    /**
     * {@code content} 列是 {@code NOT NULL DEFAULT ''}。
     *
     * <p>新建时靠数据库默认值兜底不会暴露问题，但更新走的是显式 SET，
     * 传 null 会直接违反非空约束，因此统一在此归一化。
     */
    private String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    private void assertTimeOrder(OffsetDateTime startTime, OffsetDateTime dueTime) {        if (startTime != null && dueTime != null && dueTime.isBefore(startTime)) {
            throw BizException.business("截止时间不能早于开始时间");
        }
    }

    private void assertTagLimit(List<Long> tagIds) {
        if (tagIds != null && tagIds.size() > MAX_TAGS_PER_TASK) {
            throw BizException.business("单个任务最多关联 " + MAX_TAGS_PER_TASK + " 个标签");
        }
    }

    private void collectChange(Map<String, Map<String, Object>> changes,
                               String field, Object before, Object after) {
        if (Objects.equals(before, after)) {
            return;
        }
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("old", abbreviate(before));
        delta.put("new", abbreviate(after));
        changes.put(field, delta);
    }

    /** 备注可能很长，快照里做截断，避免日志表被正文撑爆 */
    private Object abbreviate(Object value) {
        if (value instanceof String text && text.length() > SNAPSHOT_TEXT_LIMIT) {
            return text.substring(0, SNAPSHOT_TEXT_LIMIT) + "…";
        }
        return value;
    }

    private String describe(Map<String, Map<String, Object>> changes) {
        return changes.keySet().stream()
                .map(field -> FIELD_LABELS.getOrDefault(field, field))
                .collect(Collectors.joining("、"));
    }

    private List<Long> sorted(List<Long> values) {
        return values.stream().sorted().toList();
    }

    private List<Long> union(List<Long> left, List<Long> right) {
        Set<Long> merged = new HashSet<>(left);
        merged.addAll(right);
        return new ArrayList<>(merged);
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
