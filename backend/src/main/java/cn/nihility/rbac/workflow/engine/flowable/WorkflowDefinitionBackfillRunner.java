package cn.nihility.rbac.workflow.engine.flowable;

import cn.nihility.rbac.workflow.constant.WorkflowConstants;
import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import cn.nihility.rbac.workflow.mapper.ProcessDefinitionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用启动完成、Flowable 自动部署 {@code processes/*.bpmn20.xml} 之后，把默认主数据审批
 * 流程实际部署产生的 Flowable 流程定义 id 回填到 Flyway 预置的 {@code tab_wf_process_definition}
 * 种子行（workflow-approval-engine change design.md Decision 8/Migration Plan 2）。始终把
 * 该行的 {@code flowableDefinitionId} 同步为当前部署的最新版本 id（而不是仅在为空时才回填），
 * 因为默认流程当前只有这一行手写维护的记录，不经由设计器发布产生新版本行，本地调试阶段
 * 重复修改 BPMN 内容重启应用时 Flowable 会自动生成新的内部版本，理应让这一行始终指向最新
 * 部署的版本，语义上仍满足"版本不可变"约束（约束针对的是设计器发布产生的独立版本行）。
 */
@Slf4j
@Component
@Order
@RequiredArgsConstructor
public class WorkflowDefinitionBackfillRunner implements ApplicationRunner {

    /** Flowable 流程仓库服务。 */
    private final RepositoryService repositoryService;

    /** 流程定义数据访问接口。 */
    private final ProcessDefinitionMapper processDefinitionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill(WorkflowConstants.MASTER_DATA_APPROVAL_PROCESS_KEY);
        } catch (RuntimeException ex) {
            log.error("回填默认审批流程 flowableDefinitionId 失败，不影响应用启动", ex);
        }
    }

    /**
     * 按流程定义 key 查询最新部署版本，回填对应的 {@code tab_wf_process_definition} 行。
     */
    private void backfill(String flowableDefinitionKey) {
        ProcessDefinition latest = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(flowableDefinitionKey)
                .latestVersion()
                .singleResult();
        if (latest == null) {
            log.warn("未查询到流程定义 key={} 的已部署版本，跳过回填", flowableDefinitionKey);
            return;
        }
        List<ProcessDefinitionEntity> rows = processDefinitionMapper.selectList(
                new LambdaQueryWrapper<ProcessDefinitionEntity>()
                        .eq(ProcessDefinitionEntity::getFlowableDefinitionKey, flowableDefinitionKey));
        for (ProcessDefinitionEntity row : rows) {
            if (!latest.getId().equals(row.getFlowableDefinitionId())) {
                row.setFlowableDefinitionId(latest.getId());
                row.setUpdateTime(LocalDateTime.now());
                processDefinitionMapper.updateById(row);
                log.info("已回填 tab_wf_process_definition#{} 的 flowableDefinitionId={}", row.getId(), latest.getId());
            }
        }
    }
}
