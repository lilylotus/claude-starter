package cn.nihility.rbac.sync.sign;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 对外拉取接口的请求校验入口（app-sync-notify-pull-api change design.md Decision 9/10）：
 * 解析 {@code X-App-Key} 定位应用与其对外接口配置，{@code needSign=true} 时执行验签；
 * 校验通过后把应用配置标记进 {@link OpenApiCallerContext}，供 controller/service 层读取。
 * 请求的 {@code dataType} 是否落在该应用允许同步的数据域内不在本拦截器内校验——按
 * spec 要求"不在范围内时返回空结果而不是报错"，与拦截器"拒绝请求"的语义不符，留给
 * {@code SyncPullService} 处理。
 *
 * <p>作为 Spring MVC {@link HandlerInterceptor}（运行在 {@code DispatcherServlet} 分发
 * 范围内），{@code preHandle} 中抛出的 {@link BusinessException} 会被
 * {@code GlobalExceptionHandler} 捕获并统一包装为 {@code { code, message, data } }
 * 响应，不需要手写响应体。
 */
@Component
@RequiredArgsConstructor
public class OpenApiSignInterceptor implements HandlerInterceptor {

    /** 验签失败/应用不可用时使用的业务状态码。 */
    private static final int UNAUTHORIZED_CODE = 401;

    /** 签名 URL 段（不含 body 签名部分）的十六进制长度。 */
    private static final int URL_SIGN_HEX_LENGTH = 64;

    /** 应用对外接口配置数据访问接口。 */
    private final AppConfigMapper appConfigMapper;

    /** 应用数据访问接口。 */
    private final AppMapper appMapper;

    /** 签名算法编解码接口。 */
    private final SignAlgorithmCodec signAlgorithmCodec;

    /** 应用对外接口凭证相关配置，提供 SM4 解密主密钥。 */
    private final AppSecretProperties appSecretProperties;

    /** nonce 单进程内去重存储。 */
    private final NonceStore nonceStore;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String accessKey = request.getHeader(SignConstants.HEADER_APP_KEY);
        if (!StringUtils.hasText(accessKey)) {
            throw new BusinessException(UNAUTHORIZED_CODE, "缺少 X-App-Key 请求头");
        }

        AppConfigEntity appConfig = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAccessKey, accessKey));
        if (appConfig == null || !isAppEnabled(appConfig.getAppRefId())) {
            // 不区分"key 不存在"和"应用不可用"，避免信息泄露（design.md Decision 9）。
            throw new BusinessException(UNAUTHORIZED_CODE, "应用不存在或已停用");
        }

        if (Boolean.TRUE.equals(appConfig.getNeedSign()) && !verifySignature(request, appConfig)) {
            throw new BusinessException(UNAUTHORIZED_CODE, "签名校验失败");
        }

        OpenApiCallerContext.set(appConfig);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        OpenApiCallerContext.clear();
    }

    /**
     * 判断给定应用是否处于启用状态。
     *
     * @param appRefId 应用 id
     * @return 是否启用
     */
    private boolean isAppEnabled(Long appRefId) {
        AppEntity app = appMapper.selectById(appRefId);
        return app != null && Objects.equals(app.getStatus(), AppStatus.ENABLED);
    }

    /**
     * 按 design.md Decision 10 校验签名：签名方法须与应用当前签名算法一致、时间戳未过期、
     * nonce 未被重放、重新计算的 urlSign 与请求携带的 {@code signature} 一致。拉取接口均为
     * GET 请求，不携带请求体，因此只校验 64 位十六进制的 urlSign（不涉及 128 位的 body 签名）。
     *
     * @param request   当前请求
     * @param appConfig 应用对外接口配置
     * @return 验签是否通过
     */
    private boolean verifySignature(HttpServletRequest request, AppConfigEntity appConfig) {
        Map<String, String> queryParams = extractQueryParams(request);
        String signature = queryParams.remove(SignConstants.QUERY_KEY_SIGNATURE);
        if (!StringUtils.hasText(signature) || signature.length() != URL_SIGN_HEX_LENGTH) {
            return false;
        }

        String signMethod = queryParams.get(SignConstants.QUERY_KEY_SIGN_METHOD);
        if (!Objects.equals(signMethod, SignConstants.signMethodOf(appConfig.getSignAlgorithm()))) {
            return false;
        }

        String tsText = queryParams.get(SignConstants.QUERY_KEY_TS);
        if (!isTimestampValid(tsText)) {
            return false;
        }

        String nonce = queryParams.get(SignConstants.QUERY_KEY_NONCE);
        if (!StringUtils.hasText(nonce) || !nonceStore.tryConsume(appConfig.getAccessKey(), nonce)) {
            return false;
        }

        String secretKey = Sm4JdkUtils.decrypt(appConfig.getSecretKey(), appSecretProperties.getSm4Key());
        String urlSign = signAlgorithmCodec.hmac(appConfig.getSignAlgorithm(), secretKey,
                SignCanonicalizer.canonicalize(queryParams));
        return signature.equalsIgnoreCase(urlSign);
    }

    /**
     * 把请求的全部 URL 查询参数提取为 Map（每个 key 只取第一个值）。
     *
     * @param request 当前请求
     * @return 查询参数 Map
     */
    private Map<String, String> extractQueryParams(HttpServletRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                result.put(key, values[0]);
            }
        });
        return result;
    }

    /**
     * 校验时间戳是否落在时效窗口内。
     *
     * @param tsText 13 位毫秒时间戳文本
     * @return 是否有效
     */
    private boolean isTimestampValid(String tsText) {
        if (!StringUtils.hasText(tsText)) {
            return false;
        }
        try {
            long ts = Long.parseLong(tsText);
            return Math.abs(System.currentTimeMillis() - ts) <= SignConstants.TIMESTAMP_WINDOW_MILLIS;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
