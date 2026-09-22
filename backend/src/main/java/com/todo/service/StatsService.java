package com.todo.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.todo.entity.Task;
import com.todo.entity.TaskTag;
import com.todo.mapper.TaskMapper;
import com.todo.mapper.TaskTagMapper;
import com.todo.support.OverdueCalculator;
import com.todo.support.QuadrantResolver;
import com.todo.support.TaskStatus;
import com.todo.support.UserContext;
import com.todo.vo.CompletionStatsVO;
import com.todo.vo.QuadrantStatsVO;
import com.todo.vo.StatsOverviewVO;
import com.todo.vo.TagStatsVO;
import com.todo.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统计服务，口径见设计方案 §4.7。
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 用户未配置趋势窗口时的默认天数 */
    private static final int DEFAULT_TREND_DAYS = 7;
    /** 趋势窗口上限，避免一次拉取过多数据 */
    private static final int MAX_TREND_DAYS = 90;

    private final TaskMapper taskMapper;
    private final TaskTagMapper taskTagMapper;
    private final TagService tagService;
    /** 趋势窗口默认取自用户配置 stats_window_days */
    private final ConfigService configService;

    /** 仪表盘概览 */
    public StatsOverviewVO overview() {
        List<Task> all = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getStatus, Task::getDueTime, Task::getFinishTime, Task::getCreateTime)
                .eq(Task::getIsDelete, false)
                .eq(Task::getUserId, UserContext.getUserId()));

        OffsetDateTime now = OffsetDateTime.now(ZONE);
        LocalDate today = now.toLocalDate();
        OffsetDateTime todayStart = today.atStartOfDay(ZONE).toOffsetDateTime();
        OffsetDateTime tomorrowStart = today.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();

        long total = 0, todo = 0, doing = 0, done = 0, canceled = 0, overdue = 0;
        long todayNew = 0, todayDone = 0;

        for (Task task : all) {
            total++;
            switch (task.getStatus()) {
                case TaskStatus.TODO -> {
                    todo++;
                    if (OverdueCalculator.isOverdue(task.getStatus(), task.getDueTime(), now)) {
                        overdue++;
                    }
                }
                case TaskStatus.DOING -> {
                    doing++;
                    if (OverdueCalculator.isOverdue(task.getStatus(), task.getDueTime(), now)) {
                        overdue++;
                    }
                }
                case TaskStatus.DONE -> {
                    done++;
                    if (OverdueCalculator.isCompletedLate(task.getStatus(), task.getDueTime(), task.getFinishTime())) {
                        overdue++;
                    }
                }
                case TaskStatus.CANCELED -> canceled++;
            }

            if (task.getCreateTime() != null
                    && !task.getCreateTime().isBefore(todayStart)
                    && task.getCreateTime().isBefore(tomorrowStart)) {
                todayNew++;
            }
            if (task.getFinishTime() != null
                    && !task.getFinishTime().isBefore(todayStart)
                    && task.getFinishTime().isBefore(tomorrowStart)) {
                todayDone++;
            }
        }

        long base = total - canceled;
        StatsOverviewVO vo = new StatsOverviewVO();
        vo.setTotalTasks(total);
        vo.setTodoCount(todo);
        vo.setDoingCount(doing);
        vo.setDoneCount(done);
        vo.setCanceledCount(canceled);
        vo.setOverdueCount(overdue);
        vo.setCompletionRate(base == 0 ? null : round2(done * 100.0 / base));
        vo.setOverdueRate(base == 0 ? null : round2(overdue * 100.0 / base));
        vo.setTodayNewCount(todayNew);
        vo.setTodayDoneCount(todayDone);
        return vo;
    }

    /**
     * 近 N 日趋势。
     *
     * <p>未显式指定天数时取用户配置的 {@code stats_window_days}，使首页与统计页共用同一口径。
     */
    public TrendVO trend(Integer days) {
        int n = resolveTrendDays(days);
        LocalDate today = OffsetDateTime.now(ZONE).toLocalDate();
        LocalDate startDate = today.minusDays(n - 1);
        OffsetDateTime rangeStart = startDate.atStartOfDay(ZONE).toOffsetDateTime();
        OffsetDateTime rangeEnd = today.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime();

        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getCreateTime, Task::getFinishTime, Task::getStatus)
                .eq(Task::getIsDelete, false)
                .eq(Task::getUserId, UserContext.getUserId())
                .and(w -> w
                        .between(Task::getCreateTime, rangeStart, rangeEnd)
                        .or()
                        .between(Task::getFinishTime, rangeStart, rangeEnd)));

        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            byDay.put(d, new long[]{0, 0});
        }

        for (Task task : tasks) {
            LocalDate createDay = task.getCreateTime() != null ? task.getCreateTime().toLocalDate() : null;
            if (createDay != null && !createDay.isBefore(startDate) && !createDay.isAfter(today)) {
                byDay.get(createDay)[0]++;
            }
            LocalDate doneDay = task.getFinishTime() != null ? task.getFinishTime().toLocalDate() : null;
            if (doneDay != null && task.getStatus() == TaskStatus.DONE
                    && !doneDay.isBefore(startDate) && !doneDay.isAfter(today)) {
                byDay.get(doneDay)[1]++;
            }
        }

        TrendVO vo = new TrendVO();
        List<TrendVO.Item> items = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            long[] counts = byDay.get(d);
            TrendVO.Item item = new TrendVO.Item();
            item.setDate(d);
            item.setNewCount(counts[0]);
            item.setDoneCount(counts[1]);
            items.add(item);
        }
        vo.setDays(n);
        vo.setItems(items);
        return vo;
    }

    /** 象限分布 */
    public QuadrantStatsVO quadrant() {
        List<Task> tasks = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getIsImportant, Task::getIsUrgent, Task::getStatus)
                .eq(Task::getIsDelete, false)
                .eq(Task::getUserId, UserContext.getUserId())
                .ne(Task::getStatus, TaskStatus.CANCELED));

        long total = tasks.size();
        Map<Integer, Long> counts = new HashMap<>();
        for (int q = QuadrantResolver.Q1; q <= QuadrantResolver.Q4; q++) {
            counts.put(q, 0L);
        }
        for (Task task : tasks) {
            int q = QuadrantResolver.quadrant(Boolean.TRUE.equals(task.getIsImportant()),
                    Boolean.TRUE.equals(task.getIsUrgent()));
            counts.merge(q, 1L, Long::sum);
        }

        QuadrantStatsVO vo = new QuadrantStatsVO();
        vo.setTotal(total);
        vo.setItems(counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    QuadrantStatsVO.Item item = new QuadrantStatsVO.Item();
                    item.setQuadrant(entry.getKey());
                    item.setName(QuadrantResolver.quadrantName(entry.getKey()));
                    item.setCount(entry.getValue());
                    item.setPercentage(total == 0 ? null : round2(entry.getValue() * 100.0 / total));
                    return item;
                })
                .toList());
        return vo;
    }

    /** 标签分布 */
    public TagStatsVO tags() {
        Long userId = UserContext.getUserId();
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : taskTagMapper.countByTag(userId)) {
            Object tagId = row.get("tag_id");
            Object cnt = row.get("cnt");
            if (tagId instanceof Number id && cnt instanceof Number c) {
                counts.put(id.longValue(), c.longValue());
            }
        }

        TagStatsVO vo = new TagStatsVO();
        vo.setItems(tagService.listWithTaskCount().stream()
                .map(tag -> {
                    TagStatsVO.Item item = new TagStatsVO.Item();
                    item.setTagId(tag.getId());
                    item.setName(tag.getName());
                    item.setColor(tag.getColor());
                    item.setCount(counts.getOrDefault(tag.getId(), 0L));
                    return item;
                })
                .collect(Collectors.toList()));
        return vo;
    }

    /**
     * 完成率/逾期率，支持 today/week/month/year/custom 五种周期。
     */
    public CompletionStatsVO completion(String range, OffsetDateTime startTime, OffsetDateTime endTime) {
        OffsetDateTime[] period = resolveRange(range, startTime, endTime);
        OffsetDateTime start = period[0];
        OffsetDateTime end = period[1];

        // 基准集 = 周期内创建 ∪ 周期内到期（不含已取消、不含已删除）
        List<Task> createdInRange = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getId, Task::getStatus, Task::getDueTime, Task::getFinishTime, Task::getIsImportant)
                .eq(Task::getIsDelete, false)
                .eq(Task::getUserId, UserContext.getUserId())
                .ne(Task::getStatus, TaskStatus.CANCELED)
                .ge(Task::getCreateTime, start)
                .lt(Task::getCreateTime, end));

        List<Task> dueInRange = taskMapper.selectList(Wrappers.<Task>lambdaQuery()
                .select(Task::getId, Task::getStatus, Task::getDueTime, Task::getFinishTime, Task::getIsImportant)
                .eq(Task::getIsDelete, false)
                .eq(Task::getUserId, UserContext.getUserId())
                .ne(Task::getStatus, TaskStatus.CANCELED)
                .ge(Task::getDueTime, start)
                .lt(Task::getDueTime, end));

        // 按 ID 去重
        Map<Long, Task> merged = new HashMap<>();
        createdInRange.forEach(t -> merged.put(t.getId(), t));
        dueInRange.forEach(t -> merged.putIfAbsent(t.getId(), t));

        OffsetDateTime now = OffsetDateTime.now(ZONE);
        long base = merged.size();
        long doneCount = 0, overdueCount = 0, importantCount = 0;
        for (Task task : merged.values()) {
            if (task.getStatus() == TaskStatus.DONE) {
                doneCount++;
            }
            if (OverdueCalculator.countsAsOverdue(task.getStatus(), task.getDueTime(), task.getFinishTime(), now)) {
                overdueCount++;
            }
            if (Boolean.TRUE.equals(task.getIsImportant())) {
                importantCount++;
            }
        }

        // 新增任务数 = 周期内创建的（含已取消）
        long newCount = taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getIsDelete, false)
                .eq(Task::getUserId, UserContext.getUserId())
                .ge(Task::getCreateTime, start)
                .lt(Task::getCreateTime, end));

        long days = java.time.Duration.between(start, end).toDays();
        if (days <= 0) days = 1;

        CompletionStatsVO vo = new CompletionStatsVO();
        vo.setBase(base);
        vo.setDoneCount(doneCount);
        vo.setOverdueCount(overdueCount);
        vo.setCompletionRate(base == 0 ? null : round2(doneCount * 100.0 / base));
        vo.setOverdueRate(base == 0 ? null : round2(overdueCount * 100.0 / base));
        vo.setNewCount(newCount);
        vo.setDays(days);
        vo.setAvgNewPerDay(round2((double) newCount / days));
        vo.setImportantCount(importantCount);
        vo.setImportantRate(base == 0 ? null : round2(importantCount * 100.0 / base));
        return vo;
    }

    private OffsetDateTime[] resolveRange(String range, OffsetDateTime startTime, OffsetDateTime endTime) {
        LocalDate today = OffsetDateTime.now(ZONE).toLocalDate();
        return switch (range == null ? "week" : range.toLowerCase()) {
            case "today" -> new OffsetDateTime[]{
                    today.atStartOfDay(ZONE).toOffsetDateTime(),
                    today.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime()};
            case "week" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new OffsetDateTime[]{
                        monday.atStartOfDay(ZONE).toOffsetDateTime(),
                        monday.plusWeeks(1).atStartOfDay(ZONE).toOffsetDateTime()};
            }
            case "month" -> {
                LocalDate first = today.withDayOfMonth(1);
                yield new OffsetDateTime[]{
                        first.atStartOfDay(ZONE).toOffsetDateTime(),
                        first.plusMonths(1).atStartOfDay(ZONE).toOffsetDateTime()};
            }
            case "year" -> {
                LocalDate jan1 = today.withDayOfYear(1);
                yield new OffsetDateTime[]{
                        jan1.atStartOfDay(ZONE).toOffsetDateTime(),
                        jan1.plusYears(1).atStartOfDay(ZONE).toOffsetDateTime()};
            }
            case "custom" -> {
                if (startTime == null || endTime == null) {
                    throw com.todo.common.BizException.paramInvalid("自定义时间范围必须同时指定 startTime 和 endTime");
                }
                yield new OffsetDateTime[]{startTime, endTime};
            }
            default -> throw com.todo.common.BizException.paramInvalid("不支持的时间范围: " + range);
        };
    }

    /**
     * 解析生效的趋势窗口。
     *
     * <p>显式传参优先；未传或非法时读用户配置，配置也非法则回落默认值，最终收敛到 1..MAX。
     */
    private int resolveTrendDays(Integer days) {
        int requested = days != null && days >= 1
                ? days
                : parseTrendDays(configService.get("stats_window_days", String.valueOf(DEFAULT_TREND_DAYS)));
        return Math.max(1, Math.min(requested, MAX_TREND_DAYS));
    }

    private int parseTrendDays(String raw) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_TREND_DAYS;
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
