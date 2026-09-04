package cn.nihility.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.approval.service.ApprovalProcessService;
import cn.nihility.rbac.plugin.config.PluginProperties;
import cn.nihility.rbac.workflow.dto.WorkflowInstanceResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RbacApplicationTests {

	@Test
	void contextLoads() {
	}

	/** 插件相关配置 Bean，验证 {@code rbac.plugin.*} 前缀能正常绑定加载（plugin-jar-upgrade
	 * change tasks.md 1.2）。 */
	@Autowired
	private PluginProperties pluginProperties;

    /** 用于验证预置审批 BPMN 可在已初始化的 Flowable schema 上启动。 */
    @Autowired
    private ApprovalProcessService approvalProcessService;

	/**
	 * 验证 {@code rbac.plugin} 配置项能正常绑定：目录默认值、覆盖黑名单默认包含认证过滤器、
	 * 全局异常处理器、全局响应包装器三个安全关键类全限定名。
	 */
	@Test
	void pluginProperties_shouldBindDefaultsCorrectly() {
		assertThat(pluginProperties.getDirectory()).isEqualTo("plugins");
		assertThat(pluginProperties.getOverride().getDenyList()).contains(
				"cn.nihility.rbac.auth.filter.IdentityAuthFilter",
				"cn.nihility.rbac.common.exception.GlobalExceptionHandler",
				"cn.nihility.rbac.common.advice.GlobalResponseAdvice");
	}

    /**
     * 验证审批 BPMN 已部署，且可启动并撤回一个最小流程实例（两级默认审批流程，
     * workflow-approval-engine change design.md Decision 8）。
     */
    @Test
    void approvalProcess_shouldStartAndTerminate() {
        WorkflowInstanceResult result = approvalProcessService.start(10001L, "ORG", "CREATE", 1L, null);

        assertThat(result.processInstanceId()).isNotNull();
        assertThat(result.flowableProcessInstanceId()).isNotBlank();
        approvalProcessService.withdraw(result.processInstanceId(), 1L);
    }

}
