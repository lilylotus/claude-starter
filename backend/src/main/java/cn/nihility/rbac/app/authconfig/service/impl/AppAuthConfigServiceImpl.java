package cn.nihility.rbac.app.authconfig.service.impl;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigUpdateRequest;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingRow;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingSaveRequest;
import cn.nihility.rbac.app.authconfig.dto.AppUserinfoFieldMappingVO;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.entity.AppUserinfoFieldMappingEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.authconfig.mapper.AppUserinfoFieldMappingMapper;
import cn.nihility.rbac.app.authconfig.mapstruct.AppAuthConfigConvert;
import cn.nihility.rbac.app.authconfig.mapstruct.AppUserinfoFieldMappingConvert;
import cn.nihility.rbac.app.authconfig.service.AppAuthConfigService;
import cn.nihility.rbac.app.authconfig.support.AppUserinfoFieldMappingDefaults;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.support.AppScopeGuard;
import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.app.sync.support.TransformScriptValidator;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.loginlog.constant.LoginMethod;
import cn.nihility.rbac.metadata.constant.MetadataFieldStatus;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 应用单点登录协议配置业务逻辑实现（app-auth-protocol-config change design.md）。
 */
@Service
@RequiredArgsConstructor
public class AppAuthConfigServiceImpl implements AppAuthConfigService {

    /** 应用单点登录协议配置数据访问接口。 */
    private final AppAuthConfigMapper appAuthConfigMapper;

    /** 应用用户信息响应字段映射数据访问接口。 */
    private final AppUserinfoFieldMappingMapper appUserinfoFieldMappingMapper;

    /** 应用对外接口配置数据访问接口，仅用于查询该应用的 AppId 以计算协议接口地址。 */
    private final AppConfigMapper appConfigMapper;

    /** 应用数据访问接口，仅用于只读查询应用实体（校验存在性、管辖组织范围）。 */
    private final AppMapper appMapper;

    /** 元数据字段数据访问接口，校验用户信息字段映射请求中 {@code metadataFieldId} 的合法性。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 管辖组织范围解析业务逻辑接口，写操作按应用 {@code orgId} 复用管辖范围校验。 */
    private final OrgScopeService orgScopeService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /**
     * {@inheritDoc}
     */
    @Override
    public void createDefaultConfig(Long appRefId, String operator) {
        LocalDateTime now = LocalDateTime.now();
        AppAuthConfigEntity entity = AppAuthConfigEntity.builder()
                .appRefId(appRefId)
                .authProtocol(AuthProtocol.NONE)
                .servicePatterns(JacksonUtils.toJson(List.of()))
                .loginMethods(JacksonUtils.toJson(List.of(LoginMethod.PASSWORD)))
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        appAuthConfigMapper.insert(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppAuthConfigVO getByAppId(Long appRefId) {
        AppAuthConfigEntity entity = findOrCreateByAppRefId(appRefId);
        return toVO(appRefId, entity);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 协议类型与匹配列表的关联校验、去重规则见 {@link #assertProtocolPatternsValid}/
     * {@link #normalize}。CAS 与 OAuth2.0（及未来新增协议）共用同一份 {@code servicePatterns}
     * 存储，切换协议类型时以本次提交的新列表整体替换，不做新旧列表合并
     * （unify-app-auth-service-patterns change design.md Decision 1/Non-Goals）。
     */
    @Override
    public AppAuthConfigVO updateConfig(Long appRefId, AppAuthConfigUpdateRequest request) {
        AppEntity appEntity = AppScopeGuard.getExistingAppInScope(appMapper, orgScopeService, appRefId);
        AppAuthConfigEntity entity = findOrCreateByAppRefId(appRefId);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        List<String> servicePatterns = normalize(request.getServicePatterns());
        assertProtocolPatternsValid(request.getAuthProtocol(), servicePatterns);
        String logoutNotifyUrl = StringUtils.hasText(request.getLogoutNotifyUrl())
                ? request.getLogoutNotifyUrl().trim() : null;
        assertValidLogoutNotifyUrl(logoutNotifyUrl);
        List<String> loginMethods = normalizeLoginMethods(request.getLoginMethods());

        entity.setAuthProtocol(request.getAuthProtocol());
        entity.setServicePatterns(JacksonUtils.toJson(
                AuthProtocol.NONE.equals(request.getAuthProtocol()) ? List.of() : servicePatterns));
        entity.setLoginMethods(JacksonUtils.toJson(loginMethods));
        entity.setLogoutNotifyUrl(logoutNotifyUrl);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appAuthConfigMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                beforeSnapshot, toLogSnapshot(entity));
        return toVO(appRefId, entity);
    }

    /**
     * 校验匹配列表：协议类型为 CAS 或 OAUTH2 时 {@code servicePatterns} 至少一条规则，
     * 否则拒绝；协议类型为 NONE 时不校验（保存时统一清空，见调用方）。
     *
     * @param authProtocol    协议类型
     * @param servicePatterns 去重去空白后的回跳地址匹配列表
     */
    private void assertProtocolPatternsValid(String authProtocol, List<String> servicePatterns) {
        boolean requiresPatterns = AuthProtocol.CAS.equals(authProtocol) || AuthProtocol.OAUTH2.equals(authProtocol);
        if (requiresPatterns && servicePatterns.isEmpty()) {
            throw new BusinessException("协议类型为 CAS 或 OAuth2.0 时，回跳地址匹配列表至少需要一条规则");
        }
    }

    /**
     * 校验登出通知回调地址非空时为合法的 http/https URL（对齐
     * {@code AppConfigServiceImpl#assertValidNotifyUrl} 的既有实现），为空时跳过校验
     * （允许留空，见 add-sso-single-logout change spec.md"登出通知回调地址格式校验"）。
     *
     * @param logoutNotifyUrl 登出通知回调地址，可能为空
     */
    private void assertValidLogoutNotifyUrl(String logoutNotifyUrl) {
        if (!StringUtils.hasText(logoutNotifyUrl)) {
            return;
        }
        try {
            URI uri = new URI(logoutNotifyUrl);
            String scheme = uri.getScheme();
            boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!validScheme || !StringUtils.hasText(uri.getHost())) {
                throw new BusinessException("登出通知回调地址格式不正确，必须是 http/https 开头的合法 URL");
            }
        } catch (URISyntaxException e) {
            throw new BusinessException("登出通知回调地址格式不正确，必须是 http/https 开头的合法 URL");
        }
    }

    /**
     * 校验并规范化提交的登录认证方式列表：每一项只能是 {@code PASSWORD}/{@code SMS}/
     * {@code QRCODE}，出现取值范围外的字符串直接拒绝；{@code PASSWORD} 恒定包含，缺失时
     * 自动补齐而不是拒绝请求（add-sso-login-methods change design.md Decision 1）。
     *
     * @param loginMethods 原始登录认证方式列表，可能为 {@code null}
     * @return 规范化后的登录认证方式列表，{@code PASSWORD} 固定排在首位
     */
    private List<String> normalizeLoginMethods(List<String> loginMethods) {
        Set<String> result = new LinkedHashSet<>();
        result.add(LoginMethod.PASSWORD);
        if (loginMethods != null) {
            for (String loginMethod : loginMethods) {
                if (!StringUtils.hasText(loginMethod)) {
                    continue;
                }
                String trimmed = loginMethod.trim();
                if (!LoginMethod.ALL_VALUES.contains(trimmed)) {
                    throw new BusinessException("非法的登录认证方式：" + trimmed);
                }
                result.add(trimmed);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 把匹配规则列表做 trim + 去空白 + 去重处理。
     *
     * @param patterns 原始匹配规则列表，可能为 {@code null}
     * @return 规范化后的匹配规则列表
     */
    private List<String> normalize(List<String> patterns) {
        if (patterns == null) {
            return List.of();
        }
        Set<String> deduped = new LinkedHashSet<>();
        for (String pattern : patterns) {
            if (StringUtils.hasText(pattern)) {
                deduped.add(pattern.trim());
            }
        }
        return List.copyOf(deduped);
    }

    /**
     * 按 {@code appRefId} 查询认证配置，查不到时懒创建一条默认记录后返回（design.md
     * Migration Plan：兼容"表比应用数据新"的存量应用场景）。
     *
     * @param appRefId 应用 id
     * @return 应用单点登录协议配置实体
     */
    private AppAuthConfigEntity findOrCreateByAppRefId(Long appRefId) {
        AppAuthConfigEntity entity = appAuthConfigMapper.selectOne(
                new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appRefId));
        if (entity != null) {
            return entity;
        }
        createDefaultConfig(appRefId, Objects.toString(currentOperatorService.resolveUserId(), null));
        return appAuthConfigMapper.selectOne(
                new LambdaQueryWrapper<AppAuthConfigEntity>().eq(AppAuthConfigEntity::getAppRefId, appRefId));
    }

    /**
     * 实体转视图对象：MapStruct 映射 {@code authProtocol}，匹配列表由 JSON 文本解析
     * 回填，6 个只读协议接口地址按该应用的 AppId 实时计算回填。
     *
     * @param appRefId 应用 id
     * @param entity   应用单点登录协议配置实体
     * @return 应用单点登录协议配置视图对象
     */
    private AppAuthConfigVO toVO(Long appRefId, AppAuthConfigEntity entity) {
        AppAuthConfigVO vo = AppAuthConfigConvert.INSTANCE.toVO(entity);
        vo.setServicePatterns(parsePatterns(entity.getServicePatterns()));
        vo.setLoginMethods(parseLoginMethods(entity.getLoginMethods()));

        String appId = resolveAppId(appRefId);
        vo.setCasLoginUrl("/api/authn/cas/" + appId + "/login");
        vo.setCasServiceValidateUrl("/api/authn/cas/" + appId + "/p3/serviceValidate");
        vo.setCasLogoutUrl("/api/authn/cas/" + appId + "/logout");
        vo.setOauthAuthorizeUrl("/api/authn/oauth/authorize");
        vo.setOauthTokenUrl("/api/authn/oauth/token");
        vo.setOauthUserInfoUrl("/api/authn/oauth/userinfo");
        return vo;
    }

    /**
     * 解析匹配规则列表的 JSON 文本。
     *
     * @param patternsJson JSON 字符串数组文本，可能为空
     * @return 匹配规则列表，解析失败或为空时返回空列表
     */
    private List<String> parsePatterns(String patternsJson) {
        if (!StringUtils.hasText(patternsJson)) {
            return List.of();
        }
        return JacksonUtils.toObj(patternsJson, JacksonUtils.LIST_STRING_TYPE_REFERENCE);
    }

    /**
     * 解析登录认证方式列表的 JSON 文本，空/未设置的历史数据按仅允许口令登录处理（防御性
     * 兼容，迁移脚本已给存量行填充默认值，这里仅作为兜底）。
     *
     * @param loginMethodsJson JSON 字符串数组文本，可能为空
     * @return 登录认证方式列表，为空或解析结果为空列表时返回仅含 {@code PASSWORD} 的列表
     */
    private List<String> parseLoginMethods(String loginMethodsJson) {
        if (!StringUtils.hasText(loginMethodsJson)) {
            return List.of(LoginMethod.PASSWORD);
        }
        List<String> loginMethods = JacksonUtils.toObj(loginMethodsJson, JacksonUtils.LIST_STRING_TYPE_REFERENCE);
        return loginMethods == null || loginMethods.isEmpty() ? List.of(LoginMethod.PASSWORD) : loginMethods;
    }

    /**
     * 查询应用的对外应用标识（AppId），用于拼接协议接口地址。
     *
     * @param appRefId 应用 id
     * @return AppId，理论上因 {@code AppConfigServiceImpl#createDefaultConfig} 已预置必然存在
     */
    private String resolveAppId(Long appRefId) {
        AppConfigEntity appConfig = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
        return appConfig != null ? appConfig.getAppId() : "";
    }

    /**
     * 构造认证配置的操作日志字段快照，key 为中文字段名。
     *
     * @param entity 应用单点登录协议配置实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(AppAuthConfigEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("协议类型", entity.getAuthProtocol());
        snapshot.put("回跳地址匹配列表", entity.getServicePatterns());
        snapshot.put("允许的登录认证方式", entity.getLoginMethods());
        snapshot.put("登出通知回调地址", entity.getLogoutNotifyUrl());
        return snapshot;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 该应用无任何记录时现算默认两行（不落库），复用 {@link AppUserinfoFieldMappingDefaults}。
     */
    @Override
    public List<AppUserinfoFieldMappingVO> listUserinfoFieldMappings(Long appRefId) {
        List<AppUserinfoFieldMappingRow> rows = appUserinfoFieldMappingMapper.selectByAppRefId(appRefId);
        if (rows.isEmpty()) {
            rows = AppUserinfoFieldMappingDefaults.compute(metadataFieldMapper);
        }
        return AppUserinfoFieldMappingConvert.INSTANCE.toVOList(rows);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public List<AppUserinfoFieldMappingVO> replaceUserinfoFieldMappings(Long appRefId,
            List<AppUserinfoFieldMappingSaveRequest> requests) {
        AppEntity appEntity = AppScopeGuard.getExistingAppInScope(appMapper, orgScopeService, appRefId);
        List<AppUserinfoFieldMappingSaveRequest> effectiveRequests = requests == null ? List.of() : requests;
        assertUserinfoFieldMappingRequestsValid(effectiveRequests);

        LambdaQueryWrapper<AppUserinfoFieldMappingEntity> deleteWrapper =
                new LambdaQueryWrapper<AppUserinfoFieldMappingEntity>()
                        .eq(AppUserinfoFieldMappingEntity::getAppRefId, appRefId);
        Long beforeCount = appUserinfoFieldMappingMapper.selectCount(deleteWrapper);
        beforeCount = beforeCount == null ? 0L : beforeCount;
        appUserinfoFieldMappingMapper.delete(deleteWrapper);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        for (AppUserinfoFieldMappingSaveRequest request : effectiveRequests) {
            AppUserinfoFieldMappingEntity entity = AppUserinfoFieldMappingConvert.INSTANCE.toEntity(request);
            entity.setAppRefId(appRefId);
            entity.setTransformValue(TransformType.NO_TRANSFORM.equals(request.getTransformType())
                    ? null : request.getTransformValue());
            entity.setCreateBy(operator);
            entity.setCreateTime(now);
            entity.setUpdateBy(operator);
            entity.setUpdateTime(now);
            appUserinfoFieldMappingMapper.insert(entity);
        }

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                Map.of("用户信息字段映射", beforeCount + " 行"), Map.of("用户信息字段映射", effectiveRequests.size() + " 行"));
        return listUserinfoFieldMappings(appRefId);
    }

    /**
     * 逐项校验用户信息字段映射保存请求：同一请求列表内 {@code appFieldCode} 不允许重复；
     * {@code metadataFieldId} 非空时必须存在、状态启用、{@code bizType=USER}（为空视为合法
     * 的"用户ID"伪字段，跳过校验）；转换取值按 {@code transformType} 校验（对齐
     * {@code AppSyncConfigServiceImpl#assertRequestsValid} 的既有逻辑）。
     *
     * @param requests 本次提交的完整字段映射行列表
     */
    private void assertUserinfoFieldMappingRequestsValid(List<AppUserinfoFieldMappingSaveRequest> requests) {
        Set<String> seenAppFieldCodes = new HashSet<>();
        for (AppUserinfoFieldMappingSaveRequest request : requests) {
            if (!seenAppFieldCodes.add(request.getAppFieldCode())) {
                throw new BusinessException("应用字段编码不能重复：" + request.getAppFieldCode());
            }
        }

        for (AppUserinfoFieldMappingSaveRequest request : requests) {
            if (!TransformType.ALL_TYPES.contains(request.getTransformType())) {
                throw new BusinessException("非法的转换方式：" + request.getTransformType());
            }
            assertUserinfoMetadataFieldValid(request.getMetadataFieldId());
            assertTransformValueValid(request.getTransformType(), request.getTransformValue());
        }
    }

    /**
     * 校验用户信息字段映射请求中的本地字段：为空时视为固定的"用户ID"伪字段，合法，无需校验；
     * 非空时必须存在、状态启用、{@code bizType=USER}。
     *
     * @param metadataFieldId 本地字段 id，可能为 {@code null}
     */
    private void assertUserinfoMetadataFieldValid(Long metadataFieldId) {
        if (metadataFieldId == null) {
            return;
        }
        MetadataFieldEntity metadataField = metadataFieldMapper.selectById(metadataFieldId);
        if (metadataField == null || !Objects.equals(metadataField.getStatus(), MetadataFieldStatus.ENABLED)) {
            throw new BusinessException("本地字段不存在或未启用：" + metadataFieldId);
        }
        if (!Objects.equals(metadataField.getBizType(), FormFieldBizType.USER)) {
            throw new BusinessException("本地字段[" + metadataField.getFieldName() + "]不属于用户域");
        }
    }

    /**
     * 按转换方式校验转换取值：{@code FIXED_VALUE} 要求非空；{@code SCRIPT} 要求非空且语法
     * 合法（{@link TransformScriptValidator#validateSyntax}）；{@code NO_TRANSFORM} 不校验。
     *
     * @param transformType  转换方式
     * @param transformValue 转换取值
     */
    private void assertTransformValueValid(String transformType, String transformValue) {
        if (TransformType.FIXED_VALUE.equals(transformType) && !StringUtils.hasText(transformValue)) {
            throw new BusinessException("转换方式为固定值时，转换取值不能为空");
        }
        if (TransformType.SCRIPT.equals(transformType)) {
            if (!StringUtils.hasText(transformValue)) {
                throw new BusinessException("转换方式为转换脚本时，转换取值不能为空");
            }
            TransformScriptValidator.validateSyntax(transformValue);
        }
    }
}
