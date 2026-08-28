package cn.nihility.rbac.approval.service;

import cn.nihility.rbac.approval.dto.ApprovalRequestVO;
import cn.nihility.rbac.approval.dto.ApprovalSubmitRequest;
import cn.nihility.rbac.approval.dto.WriteOperationResultVO;
import cn.nihility.rbac.common.result.PageResult;

/**
 * 主数据变更审批申请业务接口。
 */
public interface ApprovalRequestService {

    /**
     * 提交审批申请。
     *
     * <p>调用方必须在最外层判断审批开关；未启用审批时应直接调用原业务服务。</p>
     *
     * @param request 通用提交请求
     * @return 写操作结果
     */
    WriteOperationResultVO<?> submit(ApprovalSubmitRequest request);

    /**
     * 按业务类型和操作类型提交审批申请。
     *
     * <p>调用方必须在最外层判断审批开关；未启用审批时应直接调用原业务服务。</p>
     *
     * @param bizType      业务对象类型
     * @param operationType 操作类型
     * @param targetId     目标记录 id
     * @param payload      创建或更新 DTO
     * @return 写操作结果
     */
    WriteOperationResultVO<?> submit(String bizType, String operationType, Long targetId, Object payload);

    /**
     * 审批通过。
     *
     * @param id      申请 id
     * @param opinion 审批意见
     */
    void approve(Long id, String opinion);

    /**
     * 审批拒绝。
     *
     * @param id      申请 id
     * @param opinion 拒绝意见
     */
    void reject(Long id, String opinion);

    /**
     * 撤回申请。
     *
     * @param id 申请 id
     */
    void cancel(Long id);

    /**
     * 分页查询当前用户提交的申请。
     *
     * @param bizType       业务对象类型过滤
     * @param operationType 操作类型过滤
     * @param status        状态过滤
     * @param page          页码
     * @param pageSize      每页条数
     * @return 申请分页结果
     */
    PageResult<ApprovalRequestVO> pageMine(
            String bizType,
            String operationType,
            Integer status,
            Integer page,
            Integer pageSize);

    /**
     * 分页查询全部待审批申请。
     *
     * @param bizType       业务对象类型过滤
     * @param operationType 操作类型过滤
     * @param page          页码
     * @param pageSize      每页条数
     * @return 申请分页结果
     */
    PageResult<ApprovalRequestVO> pagePending(
            String bizType,
            String operationType,
            Integer page,
            Integer pageSize);
}
