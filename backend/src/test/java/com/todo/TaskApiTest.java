package com.todo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.support.QuadrantResolver;
import com.todo.support.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 任务模块行为测试。
 *
 * <p>覆盖容易出错、且手工点页面难以发现的边界：
 * 清空字段、乐观锁、幂等更新、状态流转约束、象限不静默改优先级、
 * 标签 AND 语义、软删任务的标签计数、看板统计口径。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAuthSupport.class)
class TaskApiTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 新建与默认值
    // ------------------------------------------------------------------

    @Test
    @DisplayName("新建任务：状态默认待办，优先级取象限建议值")
    void createAppliesQuadrantDefaults() throws Exception {
        JsonNode task = createTask("重要且紧急的事", true, true, null);

        assertThat(task.get("status").asInt()).isEqualTo(TaskStatus.TODO);
        assertThat(task.get("quadrant").asInt()).isEqualTo(QuadrantResolver.Q1);
        assertThat(task.get("priority").asInt())
                .as("Q1 未指定优先级时应取建议值 1")
                .isEqualTo(QuadrantResolver.suggestPriority(true, true));
        assertThat(task.get("version").asInt()).isZero();
        assertThat(task.get("overdue").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("新建任务：用户显式指定的优先级不被象限覆盖")
    void explicitPriorityWins() throws Exception {
        Map<String, Object> body = baseTask("不紧急但优先级拉满", false, false);
        body.put("priority", 1);

        JsonNode task = data(postJson("/api/tasks", body));

        assertThat(task.get("quadrant").asInt()).isEqualTo(QuadrantResolver.Q4);
        assertThat(task.get("priority").asInt())
                .as("Q4 建议优先级是 4，但用户选了 1，必须保留 1")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("新建任务：截止时间早于开始时间被拒绝")
    void rejectDueTimeBeforeStartTime() throws Exception {
        Map<String, Object> body = baseTask("时间倒挂", true, true);
        body.put("startTime", iso("2026-10-10T10:00:00"));
        body.put("dueTime", iso("2026-10-01T10:00:00"));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.msg").value(containsString("截止时间不能早于开始时间")));
    }

    // ------------------------------------------------------------------
    // 更新：清空字段与乐观锁
    // ------------------------------------------------------------------

    @Test
    @DisplayName("更新任务：可以清空截止时间（null 必须真正落库）")
    void updateCanClearDueTime() throws Exception {
        JsonNode created = createTask("带截止时间的任务", true, true, "2026-12-31T23:59:59");
        assertThat(created.get("dueTime").isNull()).isFalse();

        Map<String, Object> body = baseTask("带截止时间的任务", true, true);
        body.put("dueTime", null);
        body.put("version", created.get("version").asInt());

        JsonNode updated = data(putJson("/api/tasks/" + created.get("id").asLong(), body));

        assertThat(updated.get("dueTime").isNull())
                .as("MyBatis-Plus 的 updateById 默认忽略 null，这里必须走显式 set 才能清空")
                .isTrue();
    }

    @Test
    @DisplayName("更新任务：版本号过期返回 409 冲突")
    void staleVersionIsRejected() throws Exception {
        JsonNode created = createTask("并发编辑目标", true, true, null);
        long id = created.get("id").asLong();

        Map<String, Object> first = baseTask("第一次改名", true, true);
        first.put("version", 0);
        putJson("/api/tasks/" + id, first);

        Map<String, Object> second = baseTask("第二次改名", true, true);
        second.put("version", 0);

        mockMvc.perform(put("/api/tasks/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.msg").value(containsString("刷新")));
    }

    @Test
    @DisplayName("更新任务：内容无变化时不涨版本，避免误报冲突")
    void noOpUpdateKeepsVersion() throws Exception {
        JsonNode created = createTask("内容不变的任务", true, true, null);
        long id = created.get("id").asLong();

        Map<String, Object> body = baseTask("内容不变的任务", true, true);
        body.put("version", 0);

        JsonNode updated = data(putJson("/api/tasks/" + id, body));

        assertThat(updated.get("version").asInt())
                .as("空操作不应消耗版本号")
                .isZero();
    }

    // ------------------------------------------------------------------
    // 状态流转
    // ------------------------------------------------------------------

    @Test
    @DisplayName("状态流转：进行中 → 已完成写入完成时间，重新打开时清空")
    void statusTransitionManagesFinishTime() throws Exception {
        long id = createTask("流转测试", true, true, null).get("id").asLong();

        JsonNode doing = data(patchJson("/api/tasks/" + id + "/status", Map.of("status", TaskStatus.DOING)));
        assertThat(doing.get("status").asInt()).isEqualTo(TaskStatus.DOING);
        assertThat(doing.get("finishTime").isNull()).isTrue();

        JsonNode done = data(patchJson("/api/tasks/" + id + "/status", Map.of("status", TaskStatus.DONE)));
        assertThat(done.get("status").asInt()).isEqualTo(TaskStatus.DONE);
        assertThat(done.get("finishTime").isNull()).as("完成时间应写入").isFalse();

        JsonNode reopened = data(patchJson("/api/tasks/" + id + "/status", Map.of("status", TaskStatus.TODO)));
        assertThat(reopened.get("status").asInt()).isEqualTo(TaskStatus.TODO);
        assertThat(reopened.get("finishTime").isNull())
                .as("退出已完成状态必须清空完成时间，否则会被算成逾期完成")
                .isTrue();
    }

    @Test
    @DisplayName("状态流转：已完成不能直接取消")
    void doneCannotBeCanceled() throws Exception {
        long id = createTask("非法流转测试", true, true, null).get("id").asLong();
        patchJson("/api/tasks/" + id + "/status", Map.of("status", TaskStatus.DONE));

        mockMvc.perform(patch("/api/tasks/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":3}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.msg").value(containsString("不允许的状态流转")));
    }

    @Test
    @DisplayName("状态流转：重复设置同一状态是幂等的")
    void sameStatusIsIdempotent() throws Exception {
        long id = createTask("幂等测试", true, true, null).get("id").asLong();

        JsonNode first = data(patchJson("/api/tasks/" + id + "/status", Map.of("status", TaskStatus.DOING)));
        JsonNode second = data(patchJson("/api/tasks/" + id + "/status", Map.of("status", TaskStatus.DOING)));

        assertThat(second.get("status").asInt()).isEqualTo(TaskStatus.DOING);
        assertThat(second.get("version").asInt())
                .as("同状态重复提交不应反复涨版本")
                .isEqualTo(first.get("version").asInt());
    }

    // ------------------------------------------------------------------
    // 象限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("象限变更：只返回优先级建议，不静默改优先级")
    void quadrantChangeSuggestsButDoesNotApplyPriority() throws Exception {
        Map<String, Object> body = baseTask("拖拽测试", false, false);
        body.put("priority", 4);
        long id = data(postJson("/api/tasks", body)).get("id").asLong();

        JsonNode moved = data(patchJson("/api/tasks/" + id + "/quadrant",
                Map.of("isImportant", true, "isUrgent", true)));

        assertThat(moved.get("quadrant").asInt()).isEqualTo(QuadrantResolver.Q1);
        assertThat(moved.get("priority").asInt())
                .as("象限变更不得覆盖用户设定的优先级")
                .isEqualTo(4);
        assertThat(moved.get("suggestPriority").asInt())
                .as("应返回建议值供前端二次确认")
                .isEqualTo(QuadrantResolver.suggestPriority(true, true));
    }

    @Test
    @DisplayName("象限变更：优先级已与建议值一致时不返回 suggestPriority")
    void noSuggestionWhenPriorityAlreadyMatches() throws Exception {
        JsonNode created = createTask("已在 Q1 且优先级为 1", true, true, null);
        long id = created.get("id").asLong();
        assertThat(created.get("priority").asInt()).isEqualTo(1);

        JsonNode moved = data(patchJson("/api/tasks/" + id + "/quadrant",
                Map.of("isImportant", true, "isUrgent", true)));

        assertThat(moved.get("suggestPriority").isNull())
                .as("优先级已等于建议值，不应再弹二次确认")
                .isTrue();
    }

    // ------------------------------------------------------------------
    // 逾期派生
    // ------------------------------------------------------------------

    @Test
    @DisplayName("逾期：未结束且已过截止时间才算逾期")
    void overdueIsDerivedFromStatusAndDueTime() throws Exception {
        JsonNode overdue = createTask("已逾期", true, true, "2020-01-01T00:00:00");
        assertThat(overdue.get("overdue").asBoolean()).isTrue();
        assertThat(overdue.get("overdueDays").asLong()).isGreaterThan(0);

        JsonNode future = createTask("未到期", true, true, "2099-01-01T00:00:00");
        assertThat(future.get("overdue").asBoolean()).isFalse();
        assertThat(future.get("overdueDays").asLong()).isZero();

        long futureId = future.get("id").asLong();
        JsonNode done = data(patchJson("/api/tasks/" + futureId + "/status", Map.of("status", TaskStatus.DONE)));
        assertThat(done.get("overdue").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("逾期：已完成的任务即使超过截止时间也不再显示为逾期")
    void completedTaskIsNotShownAsOverdue() throws Exception {
        long id = createTask("逾期后完成", true, true, "2020-01-01T00:00:00").get("id").asLong();

        JsonNode done = data(patchJson("/api/tasks/" + id + "/status", Map.of("status", TaskStatus.DONE)));

        assertThat(done.get("overdue").asBoolean())
                .as("逾期完成计入逾期率，但不显示为逾期中")
                .isFalse();
    }

    // ------------------------------------------------------------------
    // 标签
    // ------------------------------------------------------------------

    @Test
    @DisplayName("标签筛选：多标签为 AND 语义，任务需同时具备全部标签")
    void tagFilterUsesAndSemantics() throws Exception {
        long tagA = createTag("筛选用标签A");
        long tagB = createTag("筛选用标签B");

        Map<String, Object> both = baseTask("同时有两个标签", true, true);
        both.put("tagIds", List.of(tagA, tagB));
        long bothId = data(postJson("/api/tasks", both)).get("id").asLong();

        Map<String, Object> onlyA = baseTask("只有标签A", true, true);
        onlyA.put("tagIds", List.of(tagA));
        long onlyAId = data(postJson("/api/tasks", onlyA)).get("id").asLong();

        List<Long> bothMatches = queryTaskIds("tagIds=" + tagA + "," + tagB);
        assertThat(bothMatches).containsExactly(bothId);

        List<Long> singleMatches = queryTaskIds("tagIds=" + tagA);
        assertThat(singleMatches).containsExactlyInAnyOrder(bothId, onlyAId);
    }

    @Test
    @DisplayName("标签计数：软删除的任务不计入标签任务数")
    void softDeletedTaskIsExcludedFromTagCount() throws Exception {
        long tagId = createTag("计数用标签");

        Map<String, Object> body = baseTask("待删除任务", true, true);
        body.put("tagIds", List.of(tagId));
        long taskId = data(postJson("/api/tasks", body)).get("id").asLong();

        assertThat(tagTaskCount(tagId)).isEqualTo(1);

        mockMvc.perform(delete("/api/tasks/" + taskId)).andExpect(status().isOk());

        assertThat(tagTaskCount(tagId))
                .as("任务已软删除，标签上不应继续挂着这个任务")
                .isZero();
    }

    @Test
    @DisplayName("标签上限：超过 20 个标签被拒绝")
    void rejectTooManyTags() throws Exception {
        List<Long> tagIds = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tagIds.add(createTag("上限标签" + i));
        }

        Map<String, Object> body = baseTask("标签超限", true, true);
        body.put("tagIds", tagIds);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.msg").value(containsString("最多关联 20 个标签")));
    }

    // ------------------------------------------------------------------
    // 批量操作
    // ------------------------------------------------------------------

    @Test
    @DisplayName("批量状态变更：混入非法流转时整体失败，不做部分提交")
    void batchStatusIsAllOrNothing() throws Exception {
        long okId = createTask("批量-可流转", true, true, null).get("id").asLong();
        long doneId = createTask("批量-已完成", true, true, null).get("id").asLong();
        patchJson("/api/tasks/" + doneId + "/status", Map.of("status", TaskStatus.DONE));

        // 已完成 → 已取消 是非法流转
        mockMvc.perform(post("/api/tasks/batch/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("ids", List.of(okId, doneId), "status", TaskStatus.CANCELED))))
                .andExpect(status().isUnprocessableEntity());

        JsonNode untouched = data(mockMvc.perform(get("/api/tasks/" + okId)).andReturn());
        assertThat(untouched.get("status").asInt())
                .as("整体回滚后，原本合法的那个任务也不能被改动")
                .isEqualTo(TaskStatus.TODO);
    }

    @Test
    @DisplayName("批量标签：replace 覆盖，remove 移除")
    void batchTagModes() throws Exception {
        long tagA = createTag("批量标签A");
        long tagB = createTag("批量标签B");

        Map<String, Object> body = baseTask("批量标签任务", true, true);
        body.put("tagIds", List.of(tagA));
        long taskId = data(postJson("/api/tasks", body)).get("id").asLong();

        Map<String, Object> replace = new HashMap<>();
        replace.put("ids", List.of(taskId));
        replace.put("tagIds", List.of(tagB));
        replace.put("mode", "replace");
        postJson("/api/tasks/batch/tags", replace);

        JsonNode afterReplace = data(mockMvc.perform(get("/api/tasks/" + taskId)).andReturn());
        assertThat(afterReplace.get("tags")).hasSize(1);
        assertThat(afterReplace.get("tags").get(0).get("id").asLong()).isEqualTo(tagB);

        Map<String, Object> remove = new HashMap<>();
        remove.put("ids", List.of(taskId));
        remove.put("tagIds", List.of(tagB));
        remove.put("mode", "remove");
        postJson("/api/tasks/batch/tags", remove);

        JsonNode afterRemove = data(mockMvc.perform(get("/api/tasks/" + taskId)).andReturn());
        assertThat(afterRemove.get("tags")).isEmpty();
    }

    @Test
    @DisplayName("批量删除：不存在的任务 ID 返回 404")
    void batchDeleteRejectsUnknownId() throws Exception {
        long existing = createTask("批量删除-存在", true, true, null).get("id").asLong();

        mockMvc.perform(post("/api/tasks/batch/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("ids", List.of(existing, 999999999L)))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // 看板与列表
    // ------------------------------------------------------------------

    @Test
    @DisplayName("看板：四个象限按序返回，已取消任务不计入")
    void boardExcludesCanceledTasks() throws Exception {
        long canceledId = createTask("看板-将被取消", true, true, null).get("id").asLong();
        patchJson("/api/tasks/" + canceledId + "/status", Map.of("status", TaskStatus.CANCELED));

        JsonNode board = data(mockMvc.perform(get("/api/quadrant/board")).andReturn());

        assertThat(board.get("quadrants")).hasSize(4);
        for (int i = 0; i < 4; i++) {
            assertThat(board.get("quadrants").get(i).get("quadrant").asInt()).isEqualTo(i + 1);
        }

        List<Long> allIds = new ArrayList<>();
        board.get("quadrants").forEach(group -> group.get("tasks")
                .forEach(task -> allIds.add(task.get("id").asLong())));
        assertThat(allIds)
                .as("已取消任务不应出现在看板中")
                .doesNotContain(canceledId);
    }

    @Test
    @DisplayName("看板：空象限完成率为 null 而非 0")
    void emptyQuadrantHasNullCompletionRate() throws Exception {
        JsonNode board = data(mockMvc.perform(get("/api/quadrant/board")).andReturn());

        JsonNode emptyGroup = null;
        for (JsonNode group : board.get("quadrants")) {
            if (group.get("total").asLong() == 0) {
                emptyGroup = group;
                break;
            }
        }

        if (emptyGroup != null) {
            assertThat(emptyGroup.get("completionRate").isNull())
                    .as("分母为 0 时返回 null，前端展示「—」而不是 0%")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("列表：仅看逾期时只返回未结束且已过期的任务")
    void overdueOnlyFilter() throws Exception {
        long overdueId = createTask("筛选-已逾期", true, true, "2020-01-01T00:00:00").get("id").asLong();
        long futureId = createTask("筛选-未到期", true, true, "2099-01-01T00:00:00").get("id").asLong();

        List<Long> ids = queryTaskIds("overdueOnly=true");
        assertThat(ids).contains(overdueId);
        assertThat(ids).doesNotContain(futureId);
    }

    @Test
    @DisplayName("列表：不支持的排序字段被拒绝，而不是拼接进 SQL")
    void rejectUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/tasks").param("sortBy", "id; DROP TABLE task"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("不支持的排序字段")));
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private Map<String, Object> baseTask(String title, boolean important, boolean urgent) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("isImportant", important);
        body.put("isUrgent", urgent);
        return body;
    }

    private JsonNode createTask(String title, boolean important, boolean urgent, String dueTime)
            throws Exception {
        Map<String, Object> body = baseTask(title, important, urgent);
        if (dueTime != null) {
            body.put("dueTime", iso(dueTime));
        }
        return data(postJson("/api/tasks", body));
    }

    private long createTag(String name) throws Exception {
        return data(postJson("/api/tags", Map.of("name", name, "color", "#3388FF")))
                .get("id").asLong();
    }

    private long tagTaskCount(long tagId) throws Exception {
        JsonNode tags = data(mockMvc.perform(get("/api/tags")).andReturn());
        for (JsonNode tag : tags) {
            if (tag.get("id").asLong() == tagId) {
                return tag.get("taskCount").asLong();
            }
        }
        throw new AssertionError("标签不存在: " + tagId);
    }

    private List<Long> queryTaskIds(String queryString) throws Exception {
        JsonNode page = data(mockMvc.perform(get("/api/tasks?" + queryString)).andReturn());
        List<Long> ids = new ArrayList<>();
        page.get("records").forEach(record -> ids.add(record.get("id").asLong()));
        return ids;
    }

    private String iso(String localDateTime) {
        return OffsetDateTime.of(
                        java.time.LocalDateTime.parse(localDateTime,
                                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                        ZONE.getRules().getOffset(java.time.Instant.now()))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private MvcResult postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult putJson(String url, Object body) throws Exception {
        return mockMvc.perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private MvcResult patchJson(String url, Object body) throws Exception {
        return mockMvc.perform(patch(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }
}
