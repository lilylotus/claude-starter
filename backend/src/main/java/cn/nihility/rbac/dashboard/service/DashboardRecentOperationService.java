package cn.nihility.rbac.dashboard.service;

import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import java.util.List;

/**
 * 首页概览"当前用户最近操作"业务逻辑接口，只返回当前登录账号自己的最近操作记录，
 * 独立于"操作日志管理"页面的通用分页查询权限点校验。
 */
public interface DashboardRecentOperationService {

    /**
     * 查询当前登录账号自己最近的操作记录（固定最多 4 条，按操作发起时间降序）。
     *
     * @return 当前用户最近操作记录列表
     */
    List<OperationLogVO> getRecentOperations();
}
