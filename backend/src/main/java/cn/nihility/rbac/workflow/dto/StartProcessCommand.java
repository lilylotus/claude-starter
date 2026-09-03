package cn.nihility.rbac.workflow.dto;

import java.util.Map;

/**
 * 启动流程命令对象，{@link cn.nihility.rbac.workflow.engine.WorkflowService#start} 的入参。
 * 是内部服务层参数，不是 HTTP 请求体，不加 {@code jakarta.validation} 注解，校验在
 * Controller 的请求 DTO 上完成。
 *
 * @param processCode    业务侧流程编码，关联 {@code tab_wf_process_model.process_code}
 * @param businessType   业务对象类型，如 {@code ORG}/{@code USER}/{@code POSITION}/{@code APP}
 * @param businessId     业务对象 id
 * @param title          流程标题，供列表展示
 * @param applicantId    发起人用户 id
 * @param applicantOrgId 发起人所属组织 id，可为空
 * @param variables      附加的流程变量（如条件分支判断字段），可为空
 * @param idempotencyKey 幂等键，取自 {@code X-Request-Id} 请求头，可为空
 */
public record StartProcessCommand(
        String processCode,
        String businessType,
        Long businessId,
        String title,
        Long applicantId,
        Long applicantOrgId,
        Map<String, Object> variables,
        String idempotencyKey) {
}
