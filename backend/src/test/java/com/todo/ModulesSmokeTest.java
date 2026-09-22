package com.todo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.support.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 统计、日历、时间线、数据迁移模块行为测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAuthSupport.class)
class ModulesSmokeTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 统计模块
    // ------------------------------------------------------------------

    @Test
    @DisplayName("统计概览：返回结构完整，字段齐全")
    void statsOverviewStructure() throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/stats/overview")).andReturn());
        assertThat(data.has("totalTasks")).isTrue();
        assertThat(data.has("todoCount")).isTrue();
        assertThat(data.has("doingCount")).isTrue();
        assertThat(data.has("doneCount")).isTrue();
        assertThat(data.has("canceledCount")).isTrue();
        assertThat(data.has("overdueCount")).isTrue();
        assertThat(data.has("completionRate")).isTrue();
        assertThat(data.has("overdueRate")).isTrue();
        assertThat(data.has("todayNewCount")).isTrue();
        assertThat(data.has("todayDoneCount")).isTrue();
    }

    @Test
    @DisplayName("趋势：默认 7 天，返回 items 数组")
    void trendDefault7Days() throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/stats/trend")).andReturn());
        assertThat(data.get("items")).hasSize(7);
        JsonNode first = data.get("items").get(0);
        assertThat(first.has("date")).isTrue();
        assertThat(first.has("newCount")).isTrue();
        assertThat(first.has("doneCount")).isTrue();
    }

    @Test
    @DisplayName("趋势：按配置的统计周期返回，未指定天数时以配置为准")
    void trendFollowsConfiguredWindow() throws Exception {
        // 顺带覆盖「保存新增配置项」这条路径：stats_window_days 不是 V1 建的列，靠这里补齐
        mockMvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stats_window_days\":\"30\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats_window_days").value("30"));

        JsonNode data = data(mockMvc.perform(get("/api/stats/trend")).andReturn());
        assertThat(data.get("days").asInt()).isEqualTo(30);
        assertThat(data.get("items")).hasSize(30);
    }

    @Test
    @DisplayName("趋势：超出上限的配置被收敛到 90")
    void trendClampsConfiguredWindow() throws Exception {
        mockMvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stats_window_days\":\"9999\"}"))
                .andExpect(status().isOk());

        JsonNode data = data(mockMvc.perform(get("/api/stats/trend")).andReturn());
        assertThat(data.get("days").asInt()).isEqualTo(90);
    }

    @Test
    @DisplayName("象限分布：返回 4 个象限")
    void quadrantStatsReturns4() throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/stats/quadrant")).andReturn());
        assertThat(data.get("items")).hasSize(4);
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("标签分布：返回结构正确")
    void tagStatsStructure() throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/stats/tags")).andReturn());
        assertThat(data.has("items")).isTrue();
    }

    @Test
    @DisplayName("完成率统计：week 范围返回正确字段")
    void completionWeekRange() throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/stats/completion?range=week")).andReturn());
        assertThat(data.has("base")).isTrue();
        assertThat(data.has("completionRate")).isTrue();
        assertThat(data.has("overdueRate")).isTrue();
        assertThat(data.has("days")).isTrue();
        assertThat(data.has("avgNewPerDay")).isTrue();
        assertThat(data.has("importantRate")).isTrue();
    }

    @Test
    @DisplayName("完成率统计：不支持的范围返回 400")
    void completionInvalidRange() throws Exception {
        mockMvc.perform(get("/api/stats/completion?range=invalid"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // 日历模块
    // ------------------------------------------------------------------

    @Test
    @DisplayName("日历查询：不传参数返回 400")
    void calendarRequiresParams() throws Exception {
        mockMvc.perform(get("/api/calendar/tasks"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("日历查询：正常范围返回列表")
    void calendarTasksInRange() throws Exception {
        String start = OffsetDateTime.now(ZONE).minusMonths(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String end = OffsetDateTime.now(ZONE).plusMonths(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        mockMvc.perform(get("/api/calendar/tasks")
                        .param("startTime", start)
                        .param("endTime", end))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("热力图：返回按日期分组的数据")
    void heatmapReturnsByDate() throws Exception {
        String start = OffsetDateTime.now(ZONE).minusMonths(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String end = OffsetDateTime.now(ZONE).plusMonths(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        JsonNode data = data(mockMvc.perform(get("/api/calendar/heatmap")
                .param("startTime", start)
                .param("endTime", end)).andReturn());
        // data 是个 map，只要结构正确即可
        assertThat(data).isNotNull();
    }

    // ------------------------------------------------------------------
    // 时间线
    // ------------------------------------------------------------------

    @Test
    @DisplayName("时间线：默认分页返回")
    void timelineDefaultPage() throws Exception {
        mockMvc.perform(get("/api/timeline/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray());
    }

    @Test
    @DisplayName("时间线：创建任务后日志可见")
    void timelineShowsCreateLog() throws Exception {
        long taskId = createTestTask("timeline-log-task");

        JsonNode page = data(mockMvc.perform(get("/api/timeline/logs?operateTypes=1")).andReturn());
        assertThat(page.get("total").asLong()).isGreaterThan(0);

        boolean found = false;
        for (JsonNode record : page.get("records")) {
            if (record.get("taskId").asLong() == taskId
                    && record.get("operateType").asInt() == 1) {
                // 不测中文文本（MockMvc 环境编码不确定），只测操作类型码
                assertThat(record.get("operateType").asInt()).isEqualTo(1);
                assertThat(record.has("operateTime")).isTrue();
                found = true;
                break;
            }
        }
        assertThat(found).as("创建日志应在时间线中可见").isTrue();
    }

    // ------------------------------------------------------------------
    // 数据迁移
    // ------------------------------------------------------------------

    @Test
    @DisplayName("导出：返回完整结构")
    void exportReturnsFullStructure() throws Exception {
        createTestTask("export-test-task");

        JsonNode data = data(mockMvc.perform(get("/api/migration/export")).andReturn());
        assertThat(data.has("version")).isTrue();
        assertThat(data.has("exportTime")).isTrue();
        assertThat(data.has("tasks")).isTrue();
        assertThat(data.has("tags")).isTrue();
        assertThat(data.has("taskTags")).isTrue();
        assertThat(data.has("logs")).isTrue();
    }

    @Test
    @DisplayName("导入：正常 JSON 文件成功导入")
    void importValidJson() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "version", "1.0",
                "tasks", java.util.List.of(
                        Map.of("title", "import-task-1", "content", "content-1", "status", 0),
                        Map.of("title", "import-task-2", "isImportant", true, "isUrgent", false)
                ),
                "tags", java.util.List.of(),
                "taskTags", java.util.List.of(),
                "logs", java.util.List.of()
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.json", "application/json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        JsonNode result = objectMapper.readTree(
                mockMvc.perform(multipart("/api/migration/import").file(file))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("data");

        assertThat(result.get("successCount").asInt()).isEqualTo(2);
        assertThat(result.get("duplicateCount").asInt()).isZero();
        assertThat(result.get("failCount").asInt()).isZero();
    }

    @Test
    @DisplayName("导入：重复标题跳过而非报错")
    void importSkipsDuplicateTitles() throws Exception {
        createTestTask("dup-title-test");

        String json = objectMapper.writeValueAsString(Map.of(
                "version", "1.0",
                "tasks", java.util.List.of(
                        Map.of("title", "dup-title-test"),
                        Map.of("title", "brand-new-task")
                ),
                "tags", java.util.List.of(),
                "taskTags", java.util.List.of(),
                "logs", java.util.List.of()
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.json", "application/json", json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        JsonNode result = objectMapper.readTree(
                mockMvc.perform(multipart("/api/migration/import").file(file))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("data");

        assertThat(result.get("successCount").asInt()).isEqualTo(1);
        assertThat(result.get("duplicateCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("备份：创建和列表")
    void backupLifecycle() throws Exception {
        createTestTask("backup-test-task");

        JsonNode backup = data(mockMvc.perform(post("/api/migration/backup")).andReturn());
        assertThat(backup.get("id").asLong()).isGreaterThan(0);
        assertThat(backup.get("fileFormat").asText()).isEqualTo("json");
        assertThat(backup.get("taskCount").asInt()).isGreaterThan(0);

        JsonNode backups = data(mockMvc.perform(get("/api/migration/backups")).andReturn());
        assertThat(backups).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("清空数据：确认清空")
    void clearAllWorks() throws Exception {
        createTestTask("clear-test-task");

        mockMvc.perform(delete("/api/migration/data"))
                .andExpect(status().isOk());

        JsonNode page = data(mockMvc.perform(get("/api/tasks")).andReturn());
        assertThat(page.get("total").asLong()).isZero();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private long createTestTask(String title) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", title,
                "isImportant", true,
                "isUrgent", false));
        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
