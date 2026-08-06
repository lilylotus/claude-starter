package cn.nihility.rbac.dashboard.service.impl;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.dashboard.service.DashboardRecentOperationService;
import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.service.OperationLogQueryService;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 首页概览"当前用户最近操作"业务逻辑实现。按 {@code CurrentUserContext} 标记的当前登录
 * 用户 id 查出其登录账号编码（{@code tab_user.code}），再复用已有的
 * {@link OperationLogQueryService#getPage(OperationLogQueryRequest)} 按 {@code createBy}
 * 精确过滤，固定只取最近 4 条（dashboard-real-data change design.md Decision "新增
 * 当前用户最近操作接口"）——不新增可配置的条数参数，首页只有这一个固定诉求。
 */
@Service
@RequiredArgsConstructor
public class DashboardRecentOperationServiceImpl implements DashboardRecentOperationService {

    /** 首页最近操作时间线固定展示的最大条数。 */
    private static final int RECENT_OPERATION_PAGE_SIZE = 4;

    /** 用户数据访问接口，用于把当前用户 id 换算为登录账号编码。 */
    private final UserMapper userMapper;

    /** 操作日志查询业务逻辑接口，复用其分页查询能力。 */
    private final OperationLogQueryService operationLogQueryService;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<OperationLogVO> getRecentOperations() {
        Long userId = CurrentUserContext.getUserId();
        UserEntity user = userMapper.selectById(userId);

        OperationLogQueryRequest request = new OperationLogQueryRequest();
        request.setCreateBy(user == null ? null : user.getCode());
        request.setPage(1);
        request.setPageSize(RECENT_OPERATION_PAGE_SIZE);

        PageResult<OperationLogVO> pageResult = operationLogQueryService.getPage(request);
        return pageResult.getRecords();
    }
}
