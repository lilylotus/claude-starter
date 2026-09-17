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

    /**
     * 查询单条申请详情，供"审批历史"等已处理记录场景复用"我的申请"/"待我审批"的详情弹窗。
     *
     * <p>查看权限口径与流程实例详情接口（{@code GET /api/v1/workflow/process-instances/
     * {processInstanceId}}）一致：{@code processInstanceId} 非空时，申请人本人、该流程实例
     * 审批轨迹中出现过的操作人/转办来源人、或当前任一开放任务的指定处理人/候选人三者之一即可
     * 查看；{@code processInstanceId} 为空（未走 Flowable 的简单审批场景）时，退化为申请提交人
     * 本人或该申请记录的审批人本人可查看。均不满足时拒绝访问。</p>
     *
     * @param id       申请 id
     * @param viewerId 当前查看者用户 id
     * @return 申请详情
     */
    ApprovalRequestVO getDetail(Long id, Long viewerId);
}
