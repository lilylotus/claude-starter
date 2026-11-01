package cn.nihility.rbac.common.filter;

import cn.nihility.rbac.common.config.UploadLimitProperties;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.common.util.JacksonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * multipart 上传大小守卫（add-video-management change design.md Decision 4）：Spring 的
 * multipart 上限是全局配置，为支持大视频上传放开后，本过滤器在请求进入业务处理（以及
 * multipart 解析落临时文件）之前，按 {@code Content-Length} 拦截超过
 * {@code rbac.upload.default-max-request-size} 的普通 multipart 请求；
 * {@code rbac.upload.large-upload-paths} 中登记的路径（视频上传）不受此限制。缺少
 * {@code Content-Length} 的分块请求不在此拦截，由全局 multipart 上限兜底。拦截时沿用项目
 * 过滤器的错误约定：HTTP 200 + 响应体业务码 413，由前端响应拦截器统一提示。
 */
@RequiredArgsConstructor
public class MultipartSizeLimitFilter extends OncePerRequestFilter {

    /** 上传文件过大的业务错误码。 */
    public static final int PAYLOAD_TOO_LARGE_CODE = 413;

    /** Ant 风格路径匹配器。 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 上传大小守卫配置。 */
    private final UploadLimitProperties uploadLimitProperties;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isOversizedOrdinaryMultipart(request)) {
            long limitMegabytes = uploadLimitProperties.getDefaultMaxRequestSize().toMegabytes();
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(JacksonUtils.toJson(
                    Result.error(PAYLOAD_TOO_LARGE_CODE, "上传文件过大，不能超过 " + limitMegabytes + "MB")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 判断是否为超过默认上限、且不在大文件上传路径白名单内的 multipart 请求。
     *
     * @param request 当前请求
     * @return 需要拦截时返回 {@code true}
     */
    private boolean isOversizedOrdinaryMultipart(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE)) {
            return false;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength <= uploadLimitProperties.getDefaultMaxRequestSize().toBytes()) {
            return false;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return uploadLimitProperties.getLargeUploadPaths().stream()
                .noneMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }
}
