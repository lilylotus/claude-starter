package cn.nihility.rbac.loginlog.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.loginlog.dto.LoginLogQueryRequest;
import cn.nihility.rbac.loginlog.dto.LoginLogVO;

/**
 * 登录日志查询业务逻辑接口，只读，不提供新增/编辑/删除能力——写入只通过
 * {@link LoginLogRecorder} 内部完成。
 */
public interface LoginLogQueryService {

    /**
     * 按可选条件动态分页查询登录日志，按登录发起时间降序排列。
     *
     * @param request 分页 + 筛选参数
     * @return 登录日志的分页结果
     */
    PageResult<LoginLogVO> getPage(LoginLogQueryRequest request);
}
