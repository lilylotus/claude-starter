package cn.nihility.rbac.workflow.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.nihility.rbac.workflow.entity.ProcessInstanceEntity;
import cn.nihility.rbac.workflow.mapper.ProcessInstanceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 验证 Flowable 引擎与自有 {@code tab_wf_*} 业务表确实共用同一个 {@link DataSource}/
 * {@link PlatformTransactionManager} 物理事务（production-approval-lifecycle change tasks.md
 * 6.1），而不是"两边操作同一个数据库、各自事务边界独立、恰好相安无事"这种表面相似。
 * <p>
 * 本类刻意不使用 {@link org.springframework.transaction.annotation.Transactional}
 * （区别于 {@link AbstractWorkflowEngineIntegrationTest} 的测试专用回滚事务）：如果测试方法
 * 本身被包在一个外层测试事务里，被测代码内部再开启的 {@code PROPAGATION_REQUIRED} 事务只会
 * 加入同一个尚未提交的外层事务，异常发生时 Spring 只会把该外层事务标记为 rollback-only，并
 * 不会立即对数据库执行真正的 ROLLBACK；此时在同一个连接上再查询，看到的是"本事务内的脏读"，
 * 并不能证明数据已经被真实回滚。本类每个测试方法各自都是最外层、真正会提交或回滚的物理事务，
 * 断言时看到的才是数据库落盘后的真实状态。
 */
@SpringBootTest
class EngineBusinessSharedTransactionIntegrationTest {

    /** Flowable 流程仓库服务，用于测试动态部署/清理 BPMN 资源。 */
    @Autowired
    private RepositoryService repositoryService;

    /** Flowable 运行时服务，用于测试直接发起真实流程实例、查询其运行时状态。 */
    @Autowired
    private RuntimeService runtimeService;

    /** 流程实例数据访问接口，用于测试构造业务表写入。 */
    @Autowired
    private ProcessInstanceMapper processInstanceMapper;

    /** 全局唯一的 Spring 事务管理器 Bean——与 Flowable 引擎配置实际持有的是同一个实例。 */
    @Autowired
    private PlatformTransactionManager transactionManager;

    /** 全局唯一的 DataSource Bean——与 Flowable 引擎配置实际持有的是同一个实例。 */
    @Autowired
    private DataSource dataSource;

    /** Flowable Spring 集成的引擎配置 Bean，供本类直接读取其持有的 DataSource/事务管理器。 */
    @Autowired
    private SpringProcessEngineConfiguration processEngineConfiguration;

    /** 本方法动态部署的测试 BPMN 部署 id，测试结束后清理，避免在共享开发库残留数据。 */
    private String deploymentId;

    /** 本次部署对应的 Flowable 流程定义 id。 */
    private String flowableDefinitionId;

    /**
     * 部署一份最小可用的测试流程（仅需要能被 {@code startProcessInstanceById} 真实启动到第一个
     * 用户任务，不需要走完整个流程），部署本身是一次真实提交，与被测事务无关。
     */
    @BeforeEach
    void deployTestProcess() {
        Deployment deployment = repositoryService.createDeployment()
                .name("engine-business-shared-transaction-test")
                .addClasspathResource("processes/test-transfer-delegate-return.bpmn20.xml")
                .deploy();
        deploymentId = deployment.getId();
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .singleResult();
        flowableDefinitionId = definition.getId();
    }

    /**
     * 清理本方法部署的测试流程定义，避免在共享开发库中残留（本类没有测试事务自动回滚兜底）。
     */
    @AfterEach
    void cleanupDeployment() {
        if (deploymentId != null) {
            repositoryService.deleteDeployment(deploymentId, true);
        }
    }

    /**
     * 把"读代码得出的结论"落成可执行断言：反编译核实
     * {@code org.flowable.spring.boot.ProcessEngineAutoConfiguration#springProcessEngineConfiguration}
     * 方法签名直接以 Spring 注入的 {@link DataSource}/{@link PlatformTransactionManager} 构造
     * {@link SpringProcessEngineConfiguration}；本项目未新增任何自定义 DataSource/
     * TransactionManager Bean（{@code application.yml} 只声明了一份
     * {@code spring.datasource}，未见任何 {@code @Configuration} 类定义第二个 DataSource 或
     * TransactionManager Bean），因此 Flowable 引擎持有的 DataSource/事务管理器与业务层
     * {@code @Transactional} 使用的必然是同一个单例 Bean，而不仅仅是"连到同一个数据库"。
     * <p>
     * 实测发现 {@link SpringProcessEngineConfiguration#getDataSource()} 返回的并非
     * {@code @Autowired DataSource} 注入到本测试的那个原始 {@code HikariDataSource} 实例本身，
     * 而是 Flowable 自动配置额外包了一层
     * {@link org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy}（Spring
     * 生态里让直接持有 {@code DataSource} 引用、绕过 {@code CommandContext} 拿连接的代码也能
     * 参与到当前线程绑定事务的标准做法）；这层包装仍然通过
     * {@link org.springframework.jdbc.datasource.DelegatingDataSource#getTargetDataSource()}
     * 精确指向同一个目标单例 Bean，断言解包后的目标而非外层代理对象引用相等，同样能证明
     * "同一份物理连接池/DataSource"，而不是另建了一份连接配置。
     */
    @Test
    void flowableEngine_shouldShareSameDataSourceAndTransactionManagerBean_withBusinessLayer() {
        DataSource flowableDataSource = processEngineConfiguration.getDataSource();
        DataSource unwrapped = flowableDataSource instanceof DelegatingDataSource delegating
                ? delegating.getTargetDataSource()
                : flowableDataSource;
        assertThat(unwrapped).isSameAs(dataSource);
        assertThat(processEngineConfiguration.getTransactionManager()).isSameAs(transactionManager);
    }

    /**
     * 核心场景：在同一个真实物理事务内，先执行一次真实 Flowable 引擎运行时调用
     * （{@code runtimeService.startProcessInstanceById}，会同步创建到第一个用户任务），再执行
     * 业务表写入，且业务表写入因违反真实唯一约束
     * （{@code uk_tab_wf_process_instance_flowable_id}，不是人为 throw）而失败；断言事务整体
     * 回滚后：Flowable 端刚创建的流程实例查不到了，业务表两次尝试插入（含"第一次本已成功"的
     * 那一条）也都不存在——证明引擎运行时变更与业务写入确实共享同一个物理事务，一荣俱荣、
     * 一损俱损，而不是"看起来在同一个库"。
     */
    @Test
    void engineRuntimeCallAndBusinessWrite_shouldRollBackTogether_whenBusinessWriteFailsAfterEngineCall() {
        String duplicateFlowableInstanceId = "IT-SHARED-TX-" + UUID.randomUUID();
        AtomicReference<String> startedProcessInstanceId = new AtomicReference<>();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            // 1) 真实 Flowable 引擎运行时调用：启动流程实例。
            ProcessInstance instance = runtimeService.startProcessInstanceById(flowableDefinitionId);
            startedProcessInstanceId.set(instance.getId());

            // 2) 业务表写入：第一次插入成功。
            processInstanceMapper.insert(newInstanceEntity(duplicateFlowableInstanceId));

            // 3) 业务表写入：第二次插入违反唯一约束，产生真实 SQL 异常，模拟"引擎调用之后
            //    业务写入失败"。
            processInstanceMapper.insert(newInstanceEntity(duplicateFlowableInstanceId));
        })).isInstanceOf(DataIntegrityViolationException.class);

        // 断言一：Flowable 端的运行时变更被回滚——刚启动的流程实例查不到了。
        assertThat(startedProcessInstanceId.get()).isNotNull();
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(startedProcessInstanceId.get())
                .count()).isZero();

        // 断言二：业务表写入也被回滚——两次尝试插入的记录都不存在。
        List<ProcessInstanceEntity> persisted = processInstanceMapper.selectList(
                new LambdaQueryWrapper<ProcessInstanceEntity>()
                        .eq(ProcessInstanceEntity::getFlowableInstanceId, duplicateFlowableInstanceId));
        assertThat(persisted).isEmpty();
    }

    /**
     * 构造一条最小合法的流程实例业务行，仅用于触发唯一约束冲突；{@code processDefinitionId}
     * 使用不存在的占位值（本项目 {@code tab_wf_*} 表未建 FOREIGN KEY 约束，纯应用层校验，
     * 不影响本测试对唯一约束的验证）。
     */
    private ProcessInstanceEntity newInstanceEntity(String flowableInstanceId) {
        LocalDateTime now = LocalDateTime.now();
        return ProcessInstanceEntity.builder()
                .flowableInstanceId(flowableInstanceId)
                .processDefinitionId(-1L)
                .businessType("TEST")
                .applicantId(1L)
                .status("RUNNING")
                .startedTime(now)
                .createBy("test")
                .createTime(now)
                .updateBy("test")
                .updateTime(now)
                .build();
    }
}
