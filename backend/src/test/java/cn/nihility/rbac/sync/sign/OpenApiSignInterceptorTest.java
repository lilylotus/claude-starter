package cn.nihility.rbac.sync.sign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.sync.openapi.OpenApiCallerContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link OpenApiSignInterceptor} 的单元测试，重点覆盖 design.md Decision 10 的验签规则：
 * 正确签名放行、篡改参数拒绝、时间戳过期拒绝、nonce 重放拒绝、{@code needSign=false} 时
 * 无需签名参数、应用不存在/停用统一拒绝。
 */
@ExtendWith(MockitoExtension.class)
class OpenApiSignInterceptorTest {

    /** 测试用固定 AccessKey。 */
    private static final String ACCESS_KEY = "test-access-key";

    /** 测试用固定 SecretKey 明文。 */
    private static final String SECRET_KEY_PLAIN = "test-secret-key";

    @Mock
    private AppConfigMapper appConfigMapper;

    @Mock
    private AppMapper appMapper;

    private OpenApiSignInterceptor interceptor;

    private AppSecretProperties appSecretProperties;

    private NonceStore nonceStore;

    private SignAlgorithmCodec signAlgorithmCodec;

    private NotifySignatureAppender notifySignatureAppender;

    /**
     * 每个用例前构造真实的签名编解码/nonce 存储/SM4 配置组件（不 mock，验证真实的签名
     * 计算与去重逻辑），并按需打桩 Mapper 返回的应用配置。
     */
    @BeforeEach
    void setUp() {
        appSecretProperties = new AppSecretProperties();
        appSecretProperties.setSm4Key(Sm4JdkUtils.generateKey());
        nonceStore = new NonceStore();
        signAlgorithmCodec = new SignAlgorithmCodecImpl();
        notifySignatureAppender = new NotifySignatureAppender(signAlgorithmCodec);
        interceptor = new OpenApiSignInterceptor(appConfigMapper, appMapper, signAlgorithmCodec,
                appSecretProperties, nonceStore);
    }

    /**
     * 每个用例结束后清理线程标记，避免影响其他用例。
     */
    @AfterEach
    void tearDown() {
        OpenApiCallerContext.clear();
    }

    /**
     * 缺少 {@code appKey} 请求头时应直接拒绝。
     */
    @Test
    void preHandle_shouldRejectWhenAppKeyHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * AccessKey 不存在时应拒绝，且不泄露"key 不存在"与"应用不可用"的区别（统一错误信息）。
     */
    @Test
    void preHandle_shouldRejectWhenAppKeyNotFound() {
        when(appConfigMapper.selectOne(any())).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SignConstants.HEADER_APP_KEY, ACCESS_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在或已停用");
    }

    /**
     * 应用已停用时应拒绝，复用与"key 不存在"相同的错误文案。
     */
    @Test
    void preHandle_shouldRejectWhenAppDisabled() {
        AppConfigEntity appConfig = buildAppConfig(false);
        when(appConfigMapper.selectOne(any())).thenReturn(appConfig);
        when(appMapper.selectById(appConfig.getAppRefId()))
                .thenReturn(AppEntity.builder().id(1L).status(AppStatus.DISABLED).build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SignConstants.HEADER_APP_KEY, ACCESS_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应用不存在或已停用");
    }

    /**
     * {@code needSign=false} 时不校验签名参数，只要携带合法 AccessKey 即放行。
     */
    @Test
    void preHandle_shouldPassWithoutSignatureWhenNeedSignFalse() {
        AppConfigEntity appConfig = buildAppConfig(false);
        when(appConfigMapper.selectOne(any())).thenReturn(appConfig);
        when(appMapper.selectById(appConfig.getAppRefId()))
                .thenReturn(AppEntity.builder().id(1L).status(AppStatus.ENABLED).build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SignConstants.HEADER_APP_KEY, ACCESS_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
        assertThat(OpenApiCallerContext.getAppRefId()).isEqualTo(appConfig.getAppRefId());
    }

    /**
     * {@code needSign=true} 且签名正确时应放行。
     */
    @Test
    void preHandle_shouldPassWithValidSignature() {
        AppConfigEntity appConfig = buildAppConfig(true);
        when(appConfigMapper.selectOne(any())).thenReturn(appConfig);
        when(appMapper.selectById(appConfig.getAppRefId()))
                .thenReturn(AppEntity.builder().id(1L).status(AppStatus.ENABLED).build());

        MockHttpServletRequest request = signedRequest("dataType=ORG&bizIds=1,2,3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    /**
     * 签名方法与应用当前签名算法不一致时验签失败。
     */
    @Test
    void preHandle_shouldRejectWhenSignMethodMismatch() {
        AppConfigEntity appConfig = buildAppConfig(true);
        appConfig.setSignAlgorithm(SignAlgorithm.SM3);
        when(appConfigMapper.selectOne(any())).thenReturn(appConfig);
        when(appMapper.selectById(appConfig.getAppRefId()))
                .thenReturn(AppEntity.builder().id(1L).status(AppStatus.ENABLED).build());

        // 用 SHA256 的规则签名，但应用配置的签名算法是 SM3，模拟"签名方法不一致"。
        MockHttpServletRequest request = signedRequestWithAlgorithm("dataType=ORG", SignAlgorithm.SHA256);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名校验失败");
    }

    /**
     * 篡改业务参数后验签应失败。
     */
    @Test
    void preHandle_shouldRejectWhenParamTampered() {
        AppConfigEntity appConfig = buildAppConfig(true);
        when(appConfigMapper.selectOne(any())).thenReturn(appConfig);
        when(appMapper.selectById(appConfig.getAppRefId()))
                .thenReturn(AppEntity.builder().id(1L).status(AppStatus.ENABLED).build());

        MockHttpServletRequest request = signedRequest("dataType=ORG&bizIds=1,2,3");
        // 签名计算完成后，篡改业务参数（模拟中间人篡改请求）。
        request.setParameter("bizIds", "9,9,9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名校验失败");
    }

    /**
     * 时间戳超出时效窗口时验签失败。
     */
    @Test
    void preHandle_shouldRejectWhenTimestampExpired() {
        AppConfigEntity appConfig = buildAppConfig(true);
        when(appConfigMapper.selectOne(any())).thenReturn(appConfig);
        when(appMapper.selectById(appConfig.getAppRefId()))
                .thenReturn(AppEntity.builder().id(1L).status(AppStatus.ENABLED).build());

        long expiredTs = System.currentTimeMillis() - SignConstants.TIMESTAMP_WINDOW_MILLIS - 60_000L;
        MockHttpServletRequest request = signedRequestWithTimestamp("dataType=ORG", expiredTs);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名校验失败");
    }

    /**
     * 同一 nonce 在时效窗口内重复使用时，第二次请求验签应失败（防重放）。
     */
    @Test
    void preHandle_shouldRejectReplayedNonce() {
        AppConfigEntity appConfig = buildAppConfig(true);
        when(appConfigMapper.selectOne(any())).thenReturn(appConfig);
        when(appMapper.selectById(appConfig.getAppRefId()))
                .thenReturn(AppEntity.builder().id(1L).status(AppStatus.ENABLED).build());

        String nonce = UUID.randomUUID().toString().replace("-", "");
        MockHttpServletRequest first = signedRequestWithNonce("dataType=ORG", nonce);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(first, firstResponse, new Object())).isTrue();

        MockHttpServletRequest replay = signedRequestWithNonce("dataType=ORG", nonce);
        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        assertThatThrownBy(() -> interceptor.preHandle(replay, replayResponse, new Object()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签名校验失败");
    }

    /**
     * 构造一个应用对外接口配置实体，{@code secretKey} 已按 {@link #appSecretProperties} 的
     * SM4 密钥加密。
     *
     * @param needSign 是否需要签名/验签校验
     * @return 应用对外接口配置实体
     */
    private AppConfigEntity buildAppConfig(boolean needSign) {
        return AppConfigEntity.builder()
                .id(1L)
                .appRefId(1L)
                .appId("open-app-id")
                .accessKey(ACCESS_KEY)
                .secretKey(Sm4JdkUtils.encrypt(SECRET_KEY_PLAIN, appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(needSign)
                .build();
    }

    /**
     * 用 {@link NotifySignatureAppender} 相同的算法计算一次合法签名，构造携带该签名的
     * {@link MockHttpServletRequest}。
     *
     * @param businessQuery 业务查询参数（{@code k1=v1&k2=v2} 形式，不含签名参数）
     * @return 携带合法签名参数的请求
     */
    private MockHttpServletRequest signedRequest(String businessQuery) {
        return signedRequestWithAlgorithm(businessQuery, SignAlgorithm.SHA256);
    }

    private MockHttpServletRequest signedRequestWithAlgorithm(String businessQuery, String signAlgorithm) {
        return signedRequestInternal(businessQuery, signAlgorithm, System.currentTimeMillis(),
                UUID.randomUUID().toString().replace("-", ""));
    }

    private MockHttpServletRequest signedRequestWithTimestamp(String businessQuery, long ts) {
        return signedRequestInternal(businessQuery, SignAlgorithm.SHA256, ts,
                UUID.randomUUID().toString().replace("-", ""));
    }

    private MockHttpServletRequest signedRequestWithNonce(String businessQuery, String nonce) {
        return signedRequestInternal(businessQuery, SignAlgorithm.SHA256, System.currentTimeMillis(), nonce);
    }

    /**
     * 按给定业务参数、签名方法、时间戳、nonce 计算签名并构造请求。
     *
     * @param businessQuery 业务查询参数
     * @param signAlgorithm 签名算法
     * @param ts            时间戳
     * @param nonce         随机数
     * @return 携带签名参数的请求
     */
    private MockHttpServletRequest signedRequestInternal(String businessQuery, String signAlgorithm, long ts,
            String nonce) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SignConstants.HEADER_APP_KEY, ACCESS_KEY);

        java.util.Map<String, String> signParams = new java.util.LinkedHashMap<>();
        for (String pair : businessQuery.split("&")) {
            String[] kv = pair.split("=", 2);
            signParams.put(kv[0], kv[1]);
            request.setParameter(kv[0], kv[1]);
        }
        String signMethod = SignConstants.signMethodOf(signAlgorithm);
        signParams.put(SignConstants.HEADER_APP_KEY, ACCESS_KEY);
        signParams.put(SignConstants.HEADER_SIGN_METHOD, signMethod);
        signParams.put(SignConstants.HEADER_TIMESTAMP, String.valueOf(ts));
        signParams.put(SignConstants.HEADER_NONCE, nonce);

        String urlSign = signAlgorithmCodec.hmac(signAlgorithm, SECRET_KEY_PLAIN,
                SignCanonicalizer.canonicalize(signParams));

        request.addHeader(SignConstants.HEADER_SIGN_METHOD, signMethod);
        request.addHeader(SignConstants.HEADER_TIMESTAMP, String.valueOf(ts));
        request.addHeader(SignConstants.HEADER_NONCE, nonce);
        request.addHeader(SignConstants.HEADER_SIGNATURE, urlSign);
        return request;
    }
}
