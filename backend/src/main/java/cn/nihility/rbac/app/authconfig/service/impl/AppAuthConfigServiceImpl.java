package cn.nihility.rbac.app.authconfig.service.impl;

import cn.nihility.rbac.app.authconfig.constant.AuthProtocol;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigUpdateRequest;
import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import cn.nihility.rbac.app.authconfig.mapper.AppAuthConfigMapper;
import cn.nihility.rbac.app.authconfig.mapstruct.AppAuthConfigConvert;
import cn.nihility.rbac.app.authconfig.service.AppAuthConfigService;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.support.AppScopeGuard;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 应用单点登录协议配置业务逻辑实现（app-auth-protocol-config change design.md）。
 */
@Service
@RequiredArgsConstructor
public class AppAuthConfigServiceImpl implements AppAuthConfigService {

    /** 应用单点登录协议配置数据访问接口。 */
    private final AppAuthConfigMapper appAuthConfigMapper;

    /** 应用对外接口配置数据访问接口，仅用于查询该应用的 AppId 以计算协议接口地址。 */
    private final AppConfigMapper appConfigMapper;

    /** 应用数据访问接口，仅用于只读查询应用实体（校验存在性、管辖组织范围）。 */
    private final AppMapper appMapper;

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
                .casServicePatterns(JacksonUtils.toJson(List.of()))
                .oauth2RedirectUriPatterns(JacksonUtils.toJson(List.of()))
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
     * 协议类型与两个匹配列表的关联校验、去重规则见 {@link #assertAndNormalize}。
     */
    @Override
    public AppAuthConfigVO updateConfig(Long appRefId, AppAuthConfigUpdateRequest request) {
        AppEntity appEntity = AppScopeGuard.getExistingAppInScope(appMapper, orgScopeService, appRefId);
        AppAuthConfigEntity entity = findOrCreateByAppRefId(appRefId);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        List<String> casServicePatterns = normalize(request.getCasServicePatterns());
        List<String> oauth2RedirectUriPatterns = normalize(request.getOauth2RedirectUriPatterns());
        assertProtocolPatternsValid(request.getAuthProtocol(), casServicePatterns, oauth2RedirectUriPatterns);

        entity.setAuthProtocol(request.getAuthProtocol());
        entity.setCasServicePatterns(JacksonUtils.toJson(
                AuthProtocol.CAS.equals(request.getAuthProtocol()) ? casServicePatterns : List.of()));
        entity.setOauth2RedirectUriPatterns(JacksonUtils.toJson(
                AuthProtocol.OAUTH2.equals(request.getAuthProtocol()) ? oauth2RedirectUriPatterns : List.of()));
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appAuthConfigMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                beforeSnapshot, toLogSnapshot(entity));
        return toVO(appRefId, entity);
    }

    /**
     * 按协议类型校验匹配列表：CAS/OAUTH2 要求各自对应的匹配列表非空，NONE 不校验（两个列表
     * 保存时统一清空，见调用方）。
     *
     * @param authProtocol              协议类型
     * @param casServicePatterns        去重去空白后的 CAS 匹配列表
     * @param oauth2RedirectUriPatterns 去重去空白后的 OAuth2 匹配列表
     */
    private void assertProtocolPatternsValid(String authProtocol, List<String> casServicePatterns,
            List<String> oauth2RedirectUriPatterns) {
        if (AuthProtocol.CAS.equals(authProtocol) && casServicePatterns.isEmpty()) {
            throw new BusinessException("协议类型为 CAS 时，service 匹配列表至少需要一条规则");
        }
        if (AuthProtocol.OAUTH2.equals(authProtocol) && oauth2RedirectUriPatterns.isEmpty()) {
            throw new BusinessException("协议类型为 OAuth2.0 时，redirect_uri 匹配列表至少需要一条规则");
        }
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
     * 实体转视图对象：MapStruct 映射 {@code authProtocol}，两个匹配列表由 JSON 文本解析
     * 回填，6 个只读协议接口地址按该应用的 AppId 实时计算回填。
     *
     * @param appRefId 应用 id
     * @param entity   应用单点登录协议配置实体
     * @return 应用单点登录协议配置视图对象
     */
    private AppAuthConfigVO toVO(Long appRefId, AppAuthConfigEntity entity) {
        AppAuthConfigVO vo = AppAuthConfigConvert.INSTANCE.toVO(entity);
        vo.setCasServicePatterns(parsePatterns(entity.getCasServicePatterns()));
        vo.setOauth2RedirectUriPatterns(parsePatterns(entity.getOauth2RedirectUriPatterns()));

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
        snapshot.put("CAS service 匹配列表", entity.getCasServicePatterns());
        snapshot.put("OAuth2 redirect_uri 匹配列表", entity.getOauth2RedirectUriPatterns());
        return snapshot;
    }
}
