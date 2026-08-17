package cn.nihility.rbac.app.service.impl;

import cn.nihility.rbac.app.authconfig.service.AppAuthConfigService;
import cn.nihility.rbac.app.config.AppSecretProperties;
import cn.nihility.rbac.app.constant.SignAlgorithm;
import cn.nihility.rbac.app.constant.SyncMode;
import cn.nihility.rbac.app.dto.AppConfigVO;
import cn.nihility.rbac.app.dto.SecretKeyVO;
import cn.nihility.rbac.app.dto.SignAlgorithmUpdateRequest;
import cn.nihility.rbac.app.dto.SyncConfigUpdateRequest;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppConfigMapper;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.mapstruct.AppConfigConvert;
import cn.nihility.rbac.app.service.AppConfigService;
import cn.nihility.rbac.app.support.AppCredentialGenerator;
import cn.nihility.rbac.app.support.AppScopeGuard;
import cn.nihility.rbac.app.sync.service.AppSyncConfigService;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.common.util.Sm4JdkUtils;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 应用对外接口配置业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class AppConfigServiceImpl implements AppConfigService {

    /** 应用对外接口配置数据访问接口。 */
    private final AppConfigMapper appConfigMapper;

    /**
     * 应用数据访问接口，仅用于只读查询应用实体（获取 {@code orgId}/{@code name}、校验存在性），
     * 不注入 {@code AppService}，避免引入不必要的服务间依赖（design.md Decision 6）。
     */
    private final AppMapper appMapper;

    /** 管辖组织范围解析业务逻辑接口，写操作按应用 {@code orgId} 复用管辖范围校验。 */
    private final OrgScopeService orgScopeService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 应用对外接口凭证相关配置，提供 SM4 加解密主密钥。 */
    private final AppSecretProperties appSecretProperties;

    /**
     * 应用同步配置业务逻辑接口，新建应用时在同一事务内生成 5 行默认数据域配置
     * （app-sync-field-mapping change tasks.md 5.3）。
     */
    private final AppSyncConfigService appSyncConfigService;

    /**
     * 应用单点登录协议配置业务逻辑接口，新建应用时在同一事务内生成默认认证配置
     * （app-auth-protocol-config change design.md Decision 2）。
     */
    private final AppAuthConfigService appAuthConfigService;

    /**
     * {@inheritDoc}
     */
    @Override
    public void createDefaultConfig(Long appRefId, String operator) {
        String plainSecretKey = AppCredentialGenerator.generateSecretKey();
        LocalDateTime now = LocalDateTime.now();
        AppConfigEntity entity = AppConfigEntity.builder()
                .appRefId(appRefId)
                .appId(AppCredentialGenerator.generateOpenAppId())
                .accessKey(AppCredentialGenerator.generateAccessKey())
                .secretKey(Sm4JdkUtils.encrypt(plainSecretKey, appSecretProperties.getSm4Key()))
                .signAlgorithm(SignAlgorithm.SHA256)
                .needSign(false)
                .syncMode(SyncMode.PULL)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        appConfigMapper.insert(entity);
        appSyncConfigService.createDefaultDomainConfigs(appRefId, operator);
        appAuthConfigService.createDefaultConfig(appRefId, operator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppConfigVO getByAppId(Long appRefId) {
        AppConfigEntity entity = findByAppRefId(appRefId);
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppConfigVO updateSignAlgorithm(Long appRefId, SignAlgorithmUpdateRequest request) {
        AppEntity appEntity = getExistingAppInScope(appRefId);
        AppConfigEntity entity = findByAppRefId(appRefId);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setSignAlgorithm(request.getSignAlgorithm());
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appConfigMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                beforeSnapshot, toLogSnapshot(entity));
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 同步方式为 {@link SyncMode#NOTIFY} 时，校验通知回调接口地址非空且是合法的 http/https
     * URL；为 {@link SyncMode#PULL} 时不做该项校验，即使请求里携带了通知相关字段也照常存储，
     * 不强制清空（管理员来回切换同步方式时，之前填过的通知配置不会丢失）。
     */
    @Override
    public AppConfigVO updateSyncConfig(Long appRefId, SyncConfigUpdateRequest request) {
        AppEntity appEntity = getExistingAppInScope(appRefId);
        if (SyncMode.NOTIFY.equals(request.getSyncMode())) {
            assertValidNotifyUrl(request.getNotifyUrl());
        }
        AppConfigEntity entity = findByAppRefId(appRefId);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setSyncMode(request.getSyncMode());
        entity.setNotifyUrl(request.getNotifyUrl());
        entity.setNotifyParams(JacksonUtils.toJson(
                request.getNotifyParams() != null ? request.getNotifyParams() : Map.of()));
        entity.setNeedSign(request.getNeedSign());
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appConfigMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                beforeSnapshot, toLogSnapshot(entity));
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 操作日志仅记录"执行了一次重置"这一事实，不记录新旧 SecretKey 的明文或密文——操作日志
     * 的历史记录对同组织的其他管理员可见，写入密钥material 等于把秘密广播出去
     * （design.md Decision 8）。
     */
    @Override
    public SecretKeyVO resetSecretKey(Long appRefId) {
        AppEntity appEntity = getExistingAppInScope(appRefId);
        AppConfigEntity entity = findByAppRefId(appRefId);

        String plainSecretKey = AppCredentialGenerator.generateSecretKey();
        entity.setSecretKey(Sm4JdkUtils.encrypt(plainSecretKey, appSecretProperties.getSm4Key()));
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        appConfigMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.APP, appRefId, appEntity.getName(),
                Map.of(), Map.of("操作", "重置 SecretKey"));
        return SecretKeyVO.builder().secretKey(plainSecretKey).build();
    }

    /**
     * 查询一个未被逻辑删除、且所属组织落在当前登录用户管辖组织范围内的应用实体，供三个写操作
     * 复用。校验逻辑本身上提为共享工具类 {@link AppScopeGuard}（app-sync-field-mapping change
     * tasks.md 5.2），本方法只是薄薄的转发，避免每处调用点都要重复传入
     * {@code appMapper}/{@code orgScopeService} 两个依赖。
     *
     * @param appRefId 应用 id
     * @return 应用实体
     */
    private AppEntity getExistingAppInScope(Long appRefId) {
        return AppScopeGuard.getExistingAppInScope(appMapper, orgScopeService, appRefId);
    }

    /**
     * 实体转视图对象，并把 {@code notifyParams} 原始 JSON 文本解析为 {@code Map<String, String>}
     * 回填（{@link AppConfigConvert} 显式忽略了这个字段的映射，见其 Javadoc）。
     *
     * @param entity 应用对外接口配置实体
     * @return 应用对外接口配置视图对象
     */
    private AppConfigVO toVO(AppConfigEntity entity) {
        AppConfigVO vo = AppConfigConvert.INSTANCE.toVO(entity);
        vo.setNotifyParams(StringUtils.hasText(entity.getNotifyParams())
                ? JacksonUtils.toObj(entity.getNotifyParams(), JacksonUtils.MAP_STRING_TYPE_REFERENCE)
                : Map.of());
        return vo;
    }

    /**
     * 校验通知回调接口地址非空且是合法的 http/https URL，同步方式为 {@link SyncMode#NOTIFY}
     * 时调用。
     *
     * @param notifyUrl 通知回调接口地址
     */
    private void assertValidNotifyUrl(String notifyUrl) {
        if (!StringUtils.hasText(notifyUrl)) {
            throw new BusinessException("同步方式为通知时，接口地址不能为空");
        }
        try {
            URI uri = new URI(notifyUrl);
            String scheme = uri.getScheme();
            boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            if (!validScheme || !StringUtils.hasText(uri.getHost())) {
                throw new BusinessException("接口地址格式不正确，必须是 http/https 开头的合法 URL");
            }
        } catch (URISyntaxException e) {
            throw new BusinessException("接口地址格式不正确，必须是 http/https 开头的合法 URL");
        }
    }

    /**
     * 按内部外键 {@code appRefId} 查询对外接口配置，理论上因一对一不变式必然存在，仍做
     * 防御性判空。
     *
     * @param appRefId 应用 id（{@code tab_app.id}）
     * @return 应用对外接口配置实体
     */
    private AppConfigEntity findByAppRefId(Long appRefId) {
        AppConfigEntity entity = appConfigMapper.selectOne(
                new LambdaQueryWrapper<AppConfigEntity>().eq(AppConfigEntity::getAppRefId, appRefId));
        if (entity == null) {
            throw new BusinessException("应用不存在");
        }
        return entity;
    }

    /**
     * 构造对外接口配置的操作日志字段快照，key 为中文字段名。刻意不包含 {@code secretKey}
     * （无论明文还是密文），签名算法/同步方式的修改复用本快照即可完整覆盖两个写接口涉及的字段。
     * 数据域启用开关/分页大小/字段映射改由 {@code AppSyncConfigServiceImpl} 单独记录操作日志
     * （app-sync-field-mapping change tasks.md 5.3），不再出现在本快照中。
     *
     * @param entity 应用对外接口配置实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(AppConfigEntity entity) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("AppId", entity.getAppId());
        snapshot.put("AccessKey", entity.getAccessKey());
        snapshot.put("签名算法", entity.getSignAlgorithm());
        snapshot.put("是否需要签名验签校验", Boolean.TRUE.equals(entity.getNeedSign()) ? "是" : "否");
        snapshot.put("同步方式", entity.getSyncMode());
        snapshot.put("通知回调接口地址", entity.getNotifyUrl());
        snapshot.put("通知自定义参数", entity.getNotifyParams());
        return snapshot;
    }
}
