package cn.nihility.rbac.app.support;

import cn.nihility.rbac.app.constant.AppStatus;
import cn.nihility.rbac.app.entity.AppEntity;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.auth.service.OrgScopeService;
import cn.nihility.rbac.common.exception.BusinessException;
import java.util.Objects;

/**
 * 应用管辖组织范围校验的共享工具类，从 {@code AppConfigServiceImpl#getExistingAppInScope}
 * 上提而来，供 {@code AppConfigServiceImpl}/{@code AppSyncConfigServiceImpl} 共用，避免
 * 复制同一段校验逻辑（app-sync-field-mapping change tasks.md 5.2）。无状态，不持有任何依赖，
 * 调用方按需传入自己已注入的 {@link AppMapper}/{@link OrgScopeService}。
 */
public final class AppScopeGuard {

    /**
     * 工具类不允许实例化。
     */
    private AppScopeGuard() {
    }

    /**
     * 查询一个未被逻辑删除、且所属组织落在当前登录用户管辖组织范围内的应用实体。越权与
     * 不存在复用同一条"应用不存在"错误文案，不额外暴露越权探测信号（org-scope-write-guard
     * change design.md Decision 3 的既有模式）。
     *
     * @param appMapper       应用数据访问接口
     * @param orgScopeService 管辖组织范围解析业务逻辑接口
     * @param appRefId        应用 id
     * @return 应用实体
     */
    public static AppEntity getExistingAppInScope(AppMapper appMapper, OrgScopeService orgScopeService,
            Long appRefId) {
        AppEntity appEntity = appMapper.selectById(appRefId);
        if (appEntity == null || Objects.equals(appEntity.getStatus(), AppStatus.DELETED)) {
            throw new BusinessException("应用不存在");
        }
        if (!orgScopeService.isOrgIdAllowed(CurrentUserContext.getUserId(), appEntity.getOrgId())) {
            throw new BusinessException("应用不存在");
        }
        return appEntity;
    }
}
