package cn.nihility.rbac.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.common.config.UploadLimitProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link MultipartSizeLimitFilter} 的单元测试：普通 multipart 请求超过默认上限被拦截，
 * 大文件上传白名单路径与非 multipart 请求放行（add-video-management change design.md
 * Decision 4）。
 */
class MultipartSizeLimitFilterTest {

    /** 被测过滤器，使用默认配置（10MB，白名单 /api/videos/upload）。 */
    private final MultipartSizeLimitFilter filter = new MultipartSizeLimitFilter(new UploadLimitProperties());

    /** 20MB 请求体长度。 */
    private static final long TWENTY_MB = 20L * 1024 * 1024;

    /**
     * 图片上传超过 10MB 被拦截，返回业务码 413，不进入后续过滤链。
     */
    @Test
    void rejectsOversizedOrdinaryMultipart() throws ServletException, IOException {
        MockHttpServletRequest request = multipart("/api/images/upload", TWENTY_MB);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getContentAsString()).contains("\"code\":413");
    }

    /**
     * 视频上传路径不受默认上限限制。
     */
    @Test
    void allowsLargeVideoUpload() throws ServletException, IOException {
        MockHttpServletRequest request = multipart("/api/videos/upload", TWENTY_MB);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    /**
     * 未超过上限的普通 multipart 请求、以及非 multipart 请求放行。
     */
    @Test
    void allowsSmallMultipartAndNonMultipart() throws ServletException, IOException {
        MockFilterChain smallChain = new MockFilterChain();
        filter.doFilter(multipart("/api/images/upload", 1024), new MockHttpServletResponse(), smallChain);
        assertThat(smallChain.getRequest()).isNotNull();

        MockHttpServletRequest json = new MockHttpServletRequest("POST", "/api/roles");
        json.setContentType("application/json");
        json.setContent(new byte[(int) (TWENTY_MB / 1024)]);
        MockFilterChain jsonChain = new MockFilterChain();
        filter.doFilter(json, new MockHttpServletResponse(), jsonChain);
        assertThat(jsonChain.getRequest()).isNotNull();
    }

    /**
     * 构造指定路径与 Content-Length 的 multipart 请求（不实际分配请求体内存）。
     *
     * @param path          请求路径
     * @param contentLength 声明的请求体长度
     * @return 模拟请求
     */
    private static MockHttpServletRequest multipart(String path, long contentLength) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path) {
            @Override
            public long getContentLengthLong() {
                return contentLength;
            }
        };
        request.setContentType("multipart/form-data; boundary=xyz");
        return request;
    }
}
