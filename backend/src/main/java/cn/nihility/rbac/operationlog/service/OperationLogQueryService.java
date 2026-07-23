package cn.nihility.rbac.operationlog.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.operationlog.dto.OperationLogDetailVO;
import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;

/**
 * 操作日志查询业务逻辑接口，只读，不提供新增/编辑/删除能力——写入只通过
 * {@link OperationLogRecorder} 内部完成。
 */
public interface OperationLogQueryService {

    /**
     * 按可选条件动态分页查询操作日志，按操作发起时间降序排列。
     *
     * @param request 分页 + 筛选参数
     * @return 操作日志的分页结果
     */
    PageResult<OperationLogVO> getPage(OperationLogQueryRequest request);

    /**
     * 查询操作日志详情，含结构化的字段变更列表。
     *
     * @param id 操作日志 id
     * @return 操作日志详情
     */
    OperationLogDetailVO getById(Long id);
}
