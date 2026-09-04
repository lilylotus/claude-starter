package cn.nihility.rbac.auth.filter;

import cn.nihility.rbac.auth.constant.AuthErrorCode;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.AuthorizationService;
import cn.nihility.rbac.auth.service.PasswordService;
import cn.nihility.rbac.auth.service.TokenService;
import cn.nihility.rbac.common.result.Result;
import cn.nihility.rbac.common.util.JacksonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求身份校验过滤器（password-login-auth change design.md Decision 5）：运行在
 * {@code DispatcherServlet} 之前，校验 {@code identity-token}（access-key）与
 * {@code menu} 请求头，并对处于"待首次登录强制改密"状态的用户拦截除白名单外的一切业务请求。
 * 选用 Servlet {@link OncePerRequestFilter} 而非 {@code HandlerInterceptor}：覆盖面更统一，
 * 不依赖请求是否命中某个 {@code @RequestMapping}。
 * <p>
 * 本过滤器抛出的异常不会被 {@code GlobalExceptionHandler}（{@code @RestControllerAdvice}）
 * 捕获，因为那套机制只处理 {@code DispatcherServlet} 分发到 Controller 之后抛出的异常，
 * 所以校验不通过时直接手写 HTTP 响应，而不是 {@code throw}。
 * <p>
 * rbac-permission-authorization change 在首登拦截通过后追加第四步真正的权限判断：
 * 当前用户的角色权限点集合是否包含请求 {@code menu} 编码，不满足则拦截为 {@link
 * AuthErrorCode#FORBIDDEN}。修改密码接口是不区分权限点的自助操作——任何已登录用户都应当
 * 能修改自己的密码，该接口对应的资源编码未被登记进权限点种子数据（不属于 权限资源.txt
 * 里任何一个业务模块），如果对它也做权限点匹配判断，会导致包括默认账号在内的一切用户在
 * 首次登录强制改密这一步就被自己引入的鉴权机制卡死、永远无法完成首登流程；
 * permission-driven-ui-visibility change 新增的"查询当前用户权限编码"接口同理不应要求
 * 调用方"必须已拥有某个权限编码才能查询自己有哪些权限编码"，否则出现鸡生蛋悖论；
 * dashboard-real-data change 新增的"首页概览统计"接口同理——统计数字是登录后落地页的
 * 整体概览信息，不属于任何一个业务模块的管理权限范畴，不应要求账号必须拥有具体业务
 * 查看权限才能看到对应数字；同一 change 新增的"当前用户最近操作"接口同理——只返回当前
 * 登录账号自己的操作记录（按账号编码精确过滤），语义收窄为自助信息查询，不应要求账号
 * 必须拥有"操作日志管理"（{@code OperationLogManagement:log:view}）查看权限才能看到自己
 * 的操作记录；表单字段渲染元数据接口（{@code GET /api/form-fields/render-schema}）同理——
 * "我的申请"/"待我审批"页面共用的审批详情弹窗需要据此渲染新旧字段对照，调用者不应被要求
 * 额外持有被审批业务对象（组织/用户/任职/应用）的管理权限点，该接口本身也只返回字段展示
 * 名/控件类型/字典选项等渲染元数据，不涉及任何实际业务数据（fix-approval-detail-
 * render-schema-permission change design.md Decision 1）。因此这些接口（{@link
 * #FIRST_LOGIN_WHITELIST}）同时豁免首登拦截与权限判断，保持同一批白名单、同一个语义
 * （"自助操作，不受角色权限点约束"）。
 */
@RequiredArgsConstructor
public class IdentityAuthFilter extends OncePerRequestFilter {

    /** {@code identity-token} 请求头名称，值为当前 access-key。 */
    private static final String HEADER_IDENTITY_TOKEN = "identity-token";

    /** {@code menu} 请求头名称，值为本次操作对应的资源编码（三段式）。 */
    private static final String HEADER_MENU = "menu";

    /** {@code menu} 请求头格式：模块:资源:操作，如 {@code OrgManagement:org:view}。 */
    private static final Pattern MENU_PATTERN = Pattern.compile("^[A-Za-z]+:[A-Za-z]+:[A-Za-z]+$");

    /**
     * 完全豁免身份校验（含 {@code menu} 请求头校验）的路径：登录相关接口 +
     * springdoc/swagger-ui + 对外同步拉取接口 + SSO 协议运行时端点。{@code /open/api/sync/**}
     * 面向外部应用，鉴权只走 AccessKey + 签名（
     * {@code cn.nihility.rbac.sync.sign.OpenApiSignInterceptor}），不使用本过滤器基于登录
     * 会话的 {@code identity-token}/{@code menu} 校验（app-sync-notify-pull-api change
     * design.md Decision 9）。{@code /api/authn/**}（SSO 专用登录、CAS、OAuth2 协议端点）
     * 面向外部浏览器/应用，鉴权走本过滤器机制之外的 SSO Cookie 会话，同样不适用
     * （app-sso-protocol-runtime change design.md Decision 7）。
     */
    private static final List<String> FULL_WHITELIST = List.of(
            "/api/auth/public-key",
            "/api/auth/login",
            "/api/auth/refresh",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/open/api/sync/**",
            "/api/authn/**");

    /**
     * 仍需 {@code identity-token}/{@code menu} 校验，但豁免"首登强制改密"拦截与
     * "权限编码校验"的路径：修改密码、查询当前用户权限编码、首页概览统计、当前用户最近
     * 操作、仅查询/撤回本人审批申请的接口，以及表单字段渲染元数据查询接口，均为不区分
     * 权限点的自助操作。
     */
    private static final List<String> FIRST_LOGIN_WHITELIST = List.of(
            "/api/auth/password",
            "/api/auth/permissions",
            "/api/dashboard/stats",
            "/api/dashboard/recent-operations",
            "/api/approval-requests/mine",
            "/api/approval-requests/*/cancel",
            "/api/form-fields/render-schema");

    /** Ant 风格路径匹配器，用于维护白名单以及 {@link #FIXED_PERMISSION_MAPPINGS}。 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * "HTTP 方法 + Ant 风格路径 -&gt; 固定必须权限编码"映射表（production-approval-lifecycle
     * change tasks.md 5.5，已确认真实漏洞：此前完全信任客户端 {@code menu} 请求头决定校验
     * 哪个权限编码，只要调用方持有系统内任意一个权限编码，即可把该编码填进 {@code menu} 头
     * 调用任何其他接口而不被拦截）。命中本映射表的路径改为按映射表配置的固定权限编码做
     * {@link AuthorizationService#hasPermission} 校验，忽略客户端 {@code menu} 头的具体值；
     * 未命中的路径（存量接口）保持"用 {@code menu} 头值做权限校验"的既有行为不变，不在本轮
     * 重新梳理全部存量接口（工作量超出本轮范围）。覆盖范围限定在本 change（含前置
     * {@code workflow-approval-engine}/本 change 第4节）新增的流程设计/发布审核/业务绑定/
     * 试运行/模型启停接口，以及既有的审批 approve/reject 接口。
     */
    private static final List<PermissionMapping> FIXED_PERMISSION_MAPPINGS = List.of(
            new PermissionMapping("GET", "/api/workflow/process-models", "WorkflowDesign:model:view"),
            new PermissionMapping("GET", "/api/workflow/process-models/*", "WorkflowDesign:model:view"),
            new PermissionMapping("GET", "/api/workflow/process-models/*/versions", "WorkflowDesign:model:view"),
            new PermissionMapping("POST", "/api/workflow/process-models", "WorkflowDesign:model:edit"),
            new PermissionMapping("POST", "/api/workflow/process-models/*/copy", "WorkflowDesign:model:edit"),
            new PermissionMapping("PUT", "/api/workflow/process-models/*/draft", "WorkflowDesign:model:edit"),
            new PermissionMapping("POST", "/api/workflow/process-models/*/simulations", "WorkflowDesign:model:edit"),
            new PermissionMapping("POST", "/api/workflow/process-models/*/publish", "WorkflowDesign:model:publish"),
            new PermissionMapping("POST", "/api/workflow/process-models/*/disable", "WorkflowDesign:model:disable"),
            new PermissionMapping("POST", "/api/workflow/process-models/*/enable", "WorkflowDesign:model:disable"),
            new PermissionMapping("POST", "/api/workflow/process-models/*/enabled", "WorkflowDesign:model:disable"),
            new PermissionMapping("POST", "/api/workflow/process-models/*/reviews", "WorkflowDesign:model:review"),
            new PermissionMapping("POST", "/api/workflow/process-model-reviews/*/decisions", "WorkflowDesign:model:review"),
            new PermissionMapping("GET", "/api/workflow/process-bindings", "WorkflowDesign:binding:view"),
            new PermissionMapping("GET", "/api/workflow/process-bindings/*", "WorkflowDesign:binding:view"),
            new PermissionMapping("POST", "/api/workflow/process-bindings", "WorkflowDesign:binding:edit"),
            new PermissionMapping("PUT", "/api/workflow/process-bindings/*", "WorkflowDesign:binding:edit"),
            new PermissionMapping("POST", "/api/workflow/process-bindings/*/enable", "WorkflowDesign:binding:edit"),
            new PermissionMapping("POST", "/api/workflow/process-bindings/*/disable", "WorkflowDesign:binding:edit"),
            new PermissionMapping("POST", "/api/approval-requests/*/approve", "ApprovalManagement:request:approve"),
            new PermissionMapping("POST", "/api/approval-requests/*/reject", "ApprovalManagement:request:approve"));

    /** 会话令牌业务逻辑接口。 */
    private final TokenService tokenService;

    /** 密码业务逻辑接口，用于查询首登标识。 */
    private final PasswordService passwordService;

    /** 运行时鉴权业务逻辑接口，用于判断当前用户是否有权访问请求的 {@code menu} 编码。 */
    private final AuthorizationService authorizationService;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (matches(FULL_WHITELIST, path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String accessKey = request.getHeader(HEADER_IDENTITY_TOKEN);
        if (!StringUtils.hasText(accessKey)) {
            writeError(response, AuthErrorCode.UNAUTHORIZED, "未登录，请先登录");
            return;
        }

        Optional<Long> userIdOpt = tokenService.verifyAccessKey(accessKey);
        if (userIdOpt.isEmpty()) {
            writeError(response, AuthErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
            return;
        }

        String menu = request.getHeader(HEADER_MENU);
        if (!StringUtils.hasText(menu) || !MENU_PATTERN.matcher(menu).matches()) {
            writeError(response, AuthErrorCode.UNAUTHORIZED, "缺少合法的操作资源标识（menu 请求头）");
            return;
        }

        Long userId = userIdOpt.get();
        try {
            request.setAttribute("userId", userId);
            CurrentUserContext.setUserId(userId);

            boolean firstLoginExempt = matches(FIRST_LOGIN_WHITELIST, path);
            if (!firstLoginExempt && passwordService.isFirstLogin(userId)) {
                writeError(response, AuthErrorCode.FIRST_LOGIN_REQUIRED, "首次登录，请先修改密码");
                return;
            }

            String requiredPermission = resolveRequiredPermission(request.getMethod(), path, menu);
            if (!firstLoginExempt && !authorizationService.hasPermission(userId, requiredPermission)) {
                writeError(response, AuthErrorCode.FORBIDDEN, "无权限访问该资源");
                return;
            }

            filterChain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }

    /**
     * 判断请求路径是否命中给定的白名单（Ant 风格路径匹配）。
     *
     * @param patterns 白名单路径模式列表
     * @param path     当前请求路径
     * @return 是否命中
     */
    private boolean matches(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /**
     * 解析本次请求实际应校验的权限编码：命中 {@link #FIXED_PERMISSION_MAPPINGS} 时返回映射表
     * 配置的固定编码（忽略 {@code menu} 头具体值，杜绝伪造 {@code menu} 头绕过）；未命中时
     * 回退既有行为，直接使用 {@code menu} 头的值（存量接口，本轮不重新梳理）。
     *
     * @param httpMethod 请求 HTTP 方法
     * @param path       请求路径
     * @param menuHeader {@code menu} 请求头值（已校验过格式合法）
     * @return 本次请求实际应校验的权限编码
     */
    private String resolveRequiredPermission(String httpMethod, String path, String menuHeader) {
        for (PermissionMapping mapping : FIXED_PERMISSION_MAPPINGS) {
            if (mapping.method().equalsIgnoreCase(httpMethod) && PATH_MATCHER.match(mapping.pathPattern(), path)) {
                return mapping.permissionCode();
            }
        }
        return menuHeader;
    }

    /**
     * 固定权限映射表条目。
     *
     * @param method         HTTP 方法（忽略大小写比较）
     * @param pathPattern    Ant 风格路径模式
     * @param permissionCode 该接口实际要求的固定权限编码
     */
    private record PermissionMapping(String method, String pathPattern, String permissionCode) {
    }

    /**
     * 校验失败时直接手写 JSON 响应；HTTP 状态码保持 200，业务状态码通过响应体 {@code code}
     * 字段区分，与 {@code GlobalExceptionHandler}/{@code GlobalResponseAdvice} 的既有约定一致，
     * 便于前端统一按响应体 {@code code} 处理，不需要区分响应来自 Filter 还是 Controller。
     *
     * @param response 当前响应
     * @param code     业务状态码
     * @param message  提示信息
     */
    private void writeError(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JacksonUtils.toJson(Result.error(code, message)));
    }
}
