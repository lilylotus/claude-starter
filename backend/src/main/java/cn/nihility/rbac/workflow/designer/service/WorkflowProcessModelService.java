package cn.nihility.rbac.workflow.designer.service;

import cn.nihility.rbac.workflow.designer.dto.ProcessDefinitionVersionVO;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.dto.PublishResultVO;
import java.util.List;

/**
 * 流程模型草稿/发布/下线/启用/版本历史生命周期业务逻辑接口（workflow-approval-engine change
 * design.md Decision 11）。
 */
public interface WorkflowProcessModelService {

    /** 查询全部流程模型，按更新时间倒序。 */
    List<ProcessModelVO> listModels();

    /** 查询一条流程模型详情。 */
    ProcessModelVO getModel(Long modelId);

    /** 创建尚未部署的流程模型草稿。 */
    ProcessModelVO createModel(String processCode, String processName, Long operatorId);

    /** 复制现有模型草稿到新的流程编码。 */
    ProcessModelVO copyModel(Long sourceModelId, String processCode, String processName, Long operatorId);

    /**
     * 保存流程模型草稿：仅更新 {@code tab_wf_process_model.model_json}，不触碰 Flowable，
     * {@code status} 不变，不影响当前已发布、正在运行的版本。{@code draft_revision} 每次
     * 保存自增一，供多人协作时的乐观锁冲突检测（production-approval-lifecycle change
     * design.md Decision 2/4）。
     *
     * @param modelId          流程模型 id
     * @param modelJson        草稿 Workflow JSON DSL 文本
     * @param expectedRevision 期望的当前修订号，非空时与数据库当前值不一致则拒绝保存并抛出
     *                         携带服务器最新修订号的冲突异常；为空时不做冲突检测（兼容尚未
     *                         传递该参数的历史调用方）
     */
    void saveDraft(Long modelId, String modelJson, Long expectedRevision);

    /**
     * 发布流程模型：编译当前草稿 DSL 为 BPMN 并部署，生成一个新的不可变版本记录，此前已发布
     * 的版本记录与其关联的节点审批人规则保持不变。
     *
     * @param modelId    流程模型 id
     * @param operatorId 操作人用户 id
     * @return 发布结果
     */
    PublishResultVO publish(Long modelId, Long operatorId);

    /**
     * 下线（禁用）流程模型当前生效版本：挂起对应的 Flowable 流程定义，拒绝新发起，不影响
     * 已经在运行中的流程实例，不删除任何历史版本数据。
     *
     * @param modelId 流程模型 id
     */
    void disable(Long modelId);

    /**
     * 重新启用流程模型当前生效版本：激活对应的 Flowable 流程定义，不支持跳过版本直接激活
     * 更早的历史版本。
     *
     * @param modelId 流程模型 id
     */
    void enable(Long modelId);

    /**
     * 查询流程模型的版本历史列表（按版本号倒序）。
     *
     * @param modelId 流程模型 id
     * @return 版本历史列表
     */
    List<ProcessDefinitionVersionVO> listVersions(Long modelId);
}
