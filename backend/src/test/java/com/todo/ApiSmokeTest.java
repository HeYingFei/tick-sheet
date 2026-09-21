package com.todo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口冒烟测试：打通「数据库 → Service → REST → 统一返回体」全链路。
 *
 * <p>每个测试方法运行在独立事务中并回滚，不会污染开发库数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAuthSupport.class)
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/config 返回初始化配置")
    void getConfigReturnsSeededValues() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.theme_mode").value("light"))
                .andExpect(jsonPath("$.data.time_format").value("YYYY-MM-DD HH:mm"))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    @DisplayName("PUT /api/config 拒绝未定义的配置键")
    void updateConfigRejectsUnknownKey() throws Exception {
        mockMvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"not_a_real_key\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("不支持的配置项")));
    }

    @Test
    @DisplayName("PUT /api/config 写入后可读回")
    void updateConfigPersistsValue() throws Exception {
        mockMvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme_mode\":\"dark\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.theme_mode").value("dark"));

        mockMvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.data.theme_mode").value("dark"));
    }

    @Test
    @DisplayName("标签全生命周期：创建 → 列表 → 改名 → 删除")
    void tagLifecycle() throws Exception {
        long tagId = createTag("冒烟测试标签", "#123456");

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + tagId + ")].name").value("冒烟测试标签"));

        mockMvc.perform(put("/api/tags/" + tagId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"冒烟测试标签已改名\",\"color\":\"#654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("冒烟测试标签已改名"))
                .andExpect(jsonPath("$.data.color").value("#654321"));

        mockMvc.perform(delete("/api/tags/" + tagId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 软删除后不再出现在列表中
        MvcResult result = mockMvc.perform(get("/api/tags")).andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.findValuesAsText("id")).doesNotContain(String.valueOf(tagId));
    }

    @Test
    @DisplayName("标签重名返回 409 冲突")
    void duplicateTagNameReturnsConflict() throws Exception {
        createTag("重名测试标签", "#111111");

        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"重名测试标签\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value(containsString("已存在")));
    }

    @Test
    @DisplayName("标签名称为空返回 400")
    void blankTagNameReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("标签颜色格式非法返回 400")
    void invalidTagColorReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"颜色测试标签\",\"color\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value(containsString("颜色")));
    }

    @Test
    @DisplayName("操作不存在的标签返回 404")
    void missingTagReturnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/tags/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("请求体非法 JSON 返回 400 而非 500")
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(put("/api/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 预检来源不限于 localhost：用局域网 IP 或 IDE 预览域名访问时同样要放行，
     * 否则浏览器侧只会看到 Invalid CORS request。
     *
     * <p>若把 {@code app.cors.allowed-origin-patterns} 收紧为具体域名，本用例需同步调整。
     */
    @Test
    @DisplayName("跨域预检：非 localhost 来源也应放行")
    void corsPreflightAllowsNonLocalhostOrigin() throws Exception {
        mockMvc.perform(options("/api/tasks")
                        .header("Origin", "http://192.168.1.50:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.1.50:5173"));
    }

    private long createTag(String name, String color) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("name", name, "color", color))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("data").get("id").asLong();
    }
}
