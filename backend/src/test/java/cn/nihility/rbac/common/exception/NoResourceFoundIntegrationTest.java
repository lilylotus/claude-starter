package cn.nihility.rbac.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 验证 {@link GlobalExceptionHandler#handleNoResourceFound} 上的 {@code @ResponseStatus}
 * 注解在真实 Spring MVC 请求链路里确实生效，返回 HTTP 404（脱离容器的纯单元测试
 * {@link GlobalExceptionHandlerTest} 直接 new 处理器调用，测不出注解是否被 Spring
 * 实际应用），起真实 MySQL/Redis 连接（fix-no-resource-found-returns-404 change
 * tasks.md 2.2）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class NoResourceFoundIntegrationTest {

    /** MockMvc 客户端。 */
    @Autowired
    private MockMvc mockMvc;

    /**
     * 请求一个未注册任何 Controller 路由、也不是静态资源的路径，应返回真实的 HTTP 404，
     * 响应体 {@code code} 为 404 且 {@code message} 包含具体请求路径。测试路径取自
     * {@code IdentityAuthFilter.FULL_WHITELIST} 里的 {@code /webjars/**}——该过滤器注册在
     * {@code /*}，对不在白名单内的路径（如 {@code /api/not-exist-xyz}）会在请求到达
     * {@code DispatcherServlet} 之前就先返回 HTTP 200 + {@code {code:401}}，本用例要验证的
     * 是 {@code DispatcherServlet} 层面 {@code NoResourceFoundException} 的处理，因此必须
     * 选一个能穿透过滤器白名单的路径（fix-no-resource-found-returns-404 change design.md
     * 补充说明）。{@code NoResourceFoundException#getResourcePath()} 返回的是相对资源处理器
     * 映射前缀（{@code /webjars/}）之后的部分，不含该前缀本身。
     */
    @Test
    void request_shouldReturn404_whenPathMatchesNoRouteOrResource() throws Exception {
        mockMvc.perform(get("/webjars/does-not-exist-xyz.js"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("请求的资源不存在：does-not-exist-xyz.js"));
    }
}
