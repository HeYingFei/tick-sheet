package com.todo.controller;

import com.todo.common.R;
import com.todo.service.StatsService;
import com.todo.vo.CompletionStatsVO;
import com.todo.vo.QuadrantStatsVO;
import com.todo.vo.StatsOverviewVO;
import com.todo.vo.TagStatsVO;
import com.todo.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * 统计接口，口径见设计方案 §4.7。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    public R<StatsOverviewVO> overview() {
        return R.ok(statsService.overview());
    }

    @GetMapping("/trend")
    public R<TrendVO> trend(@RequestParam(required = false) Integer days) {
        return R.ok(statsService.trend(days));
    }

    @GetMapping("/quadrant")
    public R<QuadrantStatsVO> quadrant() {
        return R.ok(statsService.quadrant());
    }

    @GetMapping("/tags")
    public R<TagStatsVO> tags() {
        return R.ok(statsService.tags());
    }

    @GetMapping("/completion")
    public R<CompletionStatsVO> completion(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime) {
        return R.ok(statsService.completion(range, startTime, endTime));
    }
}
