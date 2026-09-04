package cn.nihility.rbac.workflow.dslv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.workflow.constant.ExecutionMode;
import cn.nihility.rbac.workflow.designer.dto.ProcessModelVO;
import cn.nihility.rbac.workflow.designer.dto.PublishResultVO;
import cn.nihility.rbac.workflow.designer.service.WorkflowProcessModelService;
import cn.nihility.rbac.workflow.dslv2.constant.AssigneeTypeV2;
import cn.nihility.rbac.workflow.dslv2.constant.EmptyPolicy;
import cn.nihility.rbac.workflow.dslv2.dto.ActionsConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.ApprovalNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.AssigneeConfigDsl;
import cn.nihility.rbac.workflow.dslv2.dto.EdgeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.EndNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.ProcessModelDslV2;
import cn.nihility.rbac.workflow.dslv2.dto.StartNodeDslV2;
import cn.nihility.rbac.workflow.dslv2.review.WorkflowReleaseReviewService;
import cn.nihility.rbac.workflow.dto.ApproveCommand;
import cn.nihility.rbac.workflow.dto.StartProcessCommand;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.engine.WorkflowService;
import cn.nihility.rbac.workflow.entity.ApprovalTaskEntity;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ApprovalTaskMapper;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * DSL v2 端到端发布集成测试：通过 {@link WorkflowProcessModelService} 真实的
 * 创建模型 → 保存草稿（schemaVersion=2）→ 发布 路径（而不是直接调用
 * {@code WorkflowModelCompilerV2}），验证 {@code tasks.md} 3.7/4.4 提到的"发布产物持久化
 * 接入实际 publish 流程"确已闭环：`tab_wf_process_definition` 的 schema_version/
 * model_digest/xml_snapshot/xml_digest/node_mapping_json/rule_snapshot_json 均被真实写入，
 * 且发布产物可以被 {@link WorkflowService} 正常启动、审批（production-approval-lifecycle
 * change 第 4 节）。发布前须先完成"编辑者提交审核 + 另一位审核者批准"，否则
 * {@code publishV2} 直接拒绝（tasks.md 4.3"本轮补齐"：{@code requireApprovedForCurrentRevision}
 * 接入 {@code publish()} 作为强制前置门禁）。
 */
@SpringBootTest
@Transactional
class WorkflowProcessModelServiceV2PublishIntegrationTest {

    @Autowired
    private WorkflowProcessModelService workflowProcessModelService;
    @Autowired
    private WorkflowReleaseReviewService workflowReleaseReviewService;
    @Autowired
    private WorkflowService workflowService;
    @Autowired
    private ProcessDefinitionMapper processDefinitionMapper;
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;
    @Autowired
    private ApprovalTaskMapper approvalTaskMapper;

    private static final AtomicInteger PROCESS_CODE_SEQ = new AtomicInteger();

    @Test
    void publish_shouldReject_whenV2DraftNotApproved() {
        String processCode = "TEST_V2_PUBLISH_GATE_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelVO model = workflowProcessModelService.createModel(processCode, "v2 发布门禁测试", 1L);
        workflowProcessModelService.saveDraft(model.getId(), JacksonUtils.toJson(minimalDsl(processCode)), null);

        assertThatThrownBy(() -> workflowProcessModelService.publish(model.getId(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未通过发布审核");
    }

    @Test
    void publish_shouldPersistV2ArtifactsAndBeRunnable() {
        String processCode = "TEST_V2_PUBLISH_" + PROCESS_CODE_SEQ.incrementAndGet();
        ProcessModelVO model = workflowProcessModelService.createModel(processCode, "v2 发布测试", 1L);

        ProcessModelDslV2 dsl = ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 发布测试")
                .nodes(List.of(startNode(), approvalNode(), endNode()))
                .edges(List.of(
                        EdgeDslV2.builder().id("e1").source("start").target("approve").build(),
                        EdgeDslV2.builder().id("e2").source("approve").target("end").build()))
                .build();
        workflowProcessModelService.saveDraft(model.getId(), JacksonUtils.toJson(dsl), null);

        Long reviewId = workflowReleaseReviewService.submitForReview(model.getId(), 1L);
        workflowReleaseReviewService.decide(reviewId, 2L, true, "同意发布");

        PublishResultVO published = workflowProcessModelService.publish(model.getId(), 1L);

        ProcessDefinitionEntity definition = processDefinitionMapper.selectById(published.getProcessDefinitionId());
        assertThat(definition.getSchemaVersion()).isEqualTo(2);
        assertThat(definition.getModelDigest()).isNotBlank();
        assertThat(definition.getXmlSnapshot()).contains("<?xml").contains(processCode);
        assertThat(definition.getXmlDigest()).isNotBlank();
        assertThat(definition.getNodeMappingJson()).contains("approve");
        assertThat(definition.getRuleSnapshotJson()).contains("USER");

        WorkflowInstanceResult started = workflowService.start(new StartProcessCommand(
                processCode, "TEST", 1L, "v2 发布运行测试", 959999L, null, null, null,
                published.getProcessDefinitionId(), null, null, ExecutionMode.LEGACY_SYNC));
        List<ApprovalTaskEntity> tasks = approvalTaskMapper.selectList(new LambdaQueryWrapper<ApprovalTaskEntity>()
                .eq(ApprovalTaskEntity::getProcessInstanceId, started.processInstanceId())
                .eq(ApprovalTaskEntity::getNodeId, "approve"));
        assertThat(tasks).hasSize(1);

        workflowService.approve(new ApproveCommand(tasks.get(0).getId(), 959001L, "同意", null));
        ProcessInstanceEntity finished = processInstanceMapper.selectById(started.processInstanceId());
        assertThat(finished.getStatus()).isEqualTo("APPROVED");
    }

    /** 结构最简的合法 v2 草稿，仅用于验证发布门禁，不关心实际能否驱动引擎运行。 */
    private ProcessModelDslV2 minimalDsl(String processCode) {
        return ProcessModelDslV2.builder()
                .schemaVersion(2)
                .processCode(processCode)
                .processName("v2 发布门禁测试")
                .nodes(List.of(startNode(), approvalNode(), endNode()))
                .edges(List.of(
                        EdgeDslV2.builder().id("e1").source("start").target("approve").build(),
                        EdgeDslV2.builder().id("e2").source("approve").target("end").build()))
                .build();
    }

    private StartNodeDslV2 startNode() {
        StartNodeDslV2 node = new StartNodeDslV2();
        node.setId("start");
        node.setType("START");
        return node;
    }

    private ApprovalNodeDslV2 approvalNode() {
        ApprovalNodeDslV2 node = new ApprovalNodeDslV2();
        node.setId("approve");
        node.setType("APPROVAL");
        node.setName("审批");
        AssigneeConfigDsl assignee = new AssigneeConfigDsl();
        assignee.setType(AssigneeTypeV2.USER);
        assignee.setValue("959001");
        node.setAssignee(assignee);
        node.setEmptyPolicy(EmptyPolicy.BLOCK);
        node.setActions(new ActionsConfigDsl());
        return node;
    }

    private EndNodeDslV2 endNode() {
        EndNodeDslV2 node = new EndNodeDslV2();
        node.setId("end");
        node.setType("END");
        node.setOutcome("APPROVED");
        return node;
    }
}
