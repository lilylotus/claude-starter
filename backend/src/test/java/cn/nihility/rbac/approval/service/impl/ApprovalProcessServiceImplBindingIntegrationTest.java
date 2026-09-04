package cn.nihility.rbac.approval.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import cn.nihility.rbac.workflow.entity.ProcessBindingEntity;
import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ProcessBindingMapper;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 真实数据库集成测试：验证 {@code V13__seed_process_binding_global_fallback.sql} 种子迁移
 * 落地后，ORG/USER/POSITION/APP 四个业务模块经
 * {@link ApprovalProcessServiceImpl#start} 接入 {@code ProcessBindingResolutionService} 后
 * 仍能正常发起主数据变更审批（production-approval-lifecycle change tasks.md 4.5"兼容性种子
 * 迁移"）——这是本轮接入绑定解析后最容易回归的场景：接入前四个模块共用硬编码
 * {@code MASTER_DATA_APPROVAL_PROCESS_CODE}，接入后若种子迁移缺失或有误，会导致全部四个
 * 模块提交审批时统一报"未配置绑定"而无法使用。
 */
@SpringBootTest
@Transactional
class ApprovalProcessServiceImplBindingIntegrationTest {

    /** 主数据审批流程接口，内部委托通用审批引擎并经业务绑定解析出实际启动的流程定义。 */
    @Autowired
    private ApprovalProcessService approvalProcessService;

    /** 流程实例数据访问接口，验证绑定 id/修订号已正确落库。 */
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;

    /** 业务绑定数据访问接口，验证命中的确实是种子迁移插入的全局兜底绑定。 */
    @Autowired
    private ProcessBindingMapper processBindingMapper;

    /** 测试用业务对象 id 自增序号，避免多个参数化用例之间互相干扰。 */
    private static final AtomicLong BUSINESS_ID_SEQ = new AtomicLong(990000L);

    /**
     * 四个业务模块使用 {@code CREATE} 操作类型（种子迁移覆盖的五种操作类型之一）均应命中
     * 种子迁移插入的 {@code scope_type=GLOBAL} 兜底绑定并成功发起流程。
     */
    @ParameterizedTest(name = "bizType={0}")
    @ValueSource(strings = {"ORG", "USER", "POSITION", "APP"})
    void start_shouldSucceed_viaGlobalFallbackBindingSeededByMigration(String bizType) {
        Long businessId = BUSINESS_ID_SEQ.incrementAndGet();

        WorkflowInstanceResult result = approvalProcessService.start(businessId, bizType, "CREATE", 1L, null);

        assertThat(result.processInstanceId()).isNotNull();
        assertThat(result.flowableProcessInstanceId()).isNotBlank();

        ProcessInstanceEntity instance = processInstanceMapper.selectById(result.processInstanceId());
        assertThat(instance.getBindingId()).isNotNull();
        assertThat(instance.getBindingRevision()).isNotNull();

        ProcessBindingEntity binding = processBindingMapper.selectById(instance.getBindingId());
        assertThat(binding.getBizType()).isEqualTo(bizType);
        assertThat(binding.getOperationType()).isEqualTo("CREATE");
        assertThat(binding.getScopeType()).isEqualTo("GLOBAL");
        assertThat(binding.getScopeId()).isEqualTo(0L);

        approvalProcessService.withdraw(result.processInstanceId(), 1L);
    }
}
