package com.todo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.config.AuthProperties;
import com.todo.support.JwtUtil;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户管理与权限测试。
 *
 * <p>默认身份是管理员（见 {@link TestAuthSupport}）；需要验证权限差异时用
 * {@link #asUser} 换掉 Authorization 头。用例运行在事务中并回滚，不会污染开发库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAuthSupport.class)
class UserApiTest {

    private static final String PASSWORD = "pass123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthProperties authProperties;

    @Test
    @DisplayName("用户管理：管理员可查看列表")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].username").exists())
                .andExpect(jsonPath("$.data.records[0].role").exists());
    }

    @Test
    @DisplayName("用户管理：普通用户访问返回 403")
    void normalUserGetsForbidden() throws Exception {
        long userId = createUser("perm-tester");

        mockMvc.perform(get("/api/users").header("Authorization", asUser(userId, "perm-tester")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("用户管理：建号后新用户可登录，且默认配置已随建号初始化")
    void createdUserCanLoginWithSeededConfig() throws Exception {
        createUser("fresh-user");

        String token = "Bearer " + login("fresh-user", PASSWORD);
        mockMvc.perform(get("/api/config").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats_window_days").exists())
                .andExpect(jsonPath("$.data.theme_mode").exists());
    }

    @Test
    @DisplayName("用户管理：新建用户的角色是普通用户")
    void createdUserIsNotAdmin() throws Exception {
        createUser("role-check");

        mockMvc.perform(get("/api/users").param("keyword", "role-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].role").value("user"));
    }

    @Test
    @DisplayName("用户管理：用户名重复返回 409")
    void duplicateUsernameReturnsConflict() throws Exception {
        createUser("dup-user");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("dup-user", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("用户管理：不能禁用当前登录的账号")
    void cannotDisableSelf() throws Exception {
        mockMvc.perform(patch("/api/users/" + TestAuthSupport.TEST_USER_ID + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.msg").value(Matchers.containsString("不能禁用")));
    }

    @Test
    @DisplayName("用户管理：禁用立即生效，已登录会话下一次请求即被拒")
    void disablingUserTakesEffectImmediately() throws Exception {
        long userId = createUser("to-disable");
        String token = asUser(userId, "to-disable");

        // 禁用前，该令牌可用
        mockMvc.perform(get("/api/config").header("Authorization", token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/users/" + userId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0}"))
                .andExpect(status().isOk());

        // 禁用后同一个令牌立刻失效，不必等它过期——这正是每请求查库换来的
        mockMvc.perform(get("/api/config").header("Authorization", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("注册入口已关闭")
    void registerIsClosed() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody", PASSWORD)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private long createUser(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    /** 为指定用户签发令牌，用于验证权限差异 */
    private String asUser(long userId, String username) {
        return "Bearer " + JwtUtil.sign(userId, username, authProperties.getSecret(),
                authProperties.getExpireMs());
    }

    private String body(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }
}
