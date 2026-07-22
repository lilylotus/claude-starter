package cn.nihility.rbac.operationlog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link OperationLogRecorderImpl} 的单元测试，重点覆盖新增/编辑/启停用/删除四种场景
 * 的字段级 diff 逻辑，以及 {@code operate_ip}、User-Agent 相关字段在有/无请求上下文、
 * 请求头存在/缺失/非法时的解析行为。
 */
@ExtendWith(MockitoExtension.class)
class OperationLogRecorderImplTest {

    /** 被测服务的操作日志数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OperationLogMapper operationLogMapper;

    /** 被测服务实例，序列化组件使用 {@link cn.nihility.rbac.common.util.JacksonUtils}，不做打桩。 */
    private OperationLogRecorderImpl operationLogRecorder;

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        operationLogRecorder = new OperationLogRecorderImpl(operationLogMapper);
    }

    /**
     * 每个用例执行后清理线程绑定的请求上下文，避免影响后续用例。
     */
    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 新增操作时，before 快照视为全 null，变更详情中每个字段的旧值为空、新值为快照中对应的值；
     * {@link cn.nihility.rbac.common.util.JacksonUtils} 序列化时排除 {@code null} 字段，
     * 持久化的 JSON 中 {@code oldValue} 键本身被省略，而不是写成字面量 {@code "oldValue":null}。
     */
    @Test
    void recordCreate_shouldTreatBeforeAsAllNull() {
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("角色名称", "测试角色");
        after.put("角色编码", "role001");

        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, 10L, "测试角色", after);

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperationType()).isEqualTo(OperationType.CREATE);
        assertThat(captured.getModule()).isEqualTo(OperationLogResourceType.module(OperationLogResourceType.ROLE));
        assertThat(captured.getResourceName())
                .isEqualTo(OperationLogResourceType.resourceName(OperationLogResourceType.ROLE));
        assertThat(captured.getChangeDetail()).doesNotContain("\"oldValue\"").contains("\"newValue\":\"测试角色\"");
    }

    /**
     * 编辑操作时，仅记录发生变化的字段，值相同的字段不出现在变更详情中。
     */
    @Test
    void recordUpdate_shouldOnlyIncludeChangedFields() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("角色名称", "旧名称");
        before.put("角色编码", "role001");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("角色名称", "新名称");
        after.put("角色编码", "role001");

        operationLogRecorder.recordUpdate(OperationLogResourceType.ROLE, 10L, "新名称", before, after);

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperationType()).isEqualTo(OperationType.UPDATE);
        assertThat(captured.getChangeDetail()).contains("角色名称").doesNotContain("角色编码");
    }

    /**
     * 启用/停用操作时，若快照仅状态字段不同，变更详情中应仅包含状态字段。
     */
    @Test
    void recordStatusChange_shouldOnlyIncludeStatusField() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("角色名称", "测试角色");
        before.put("状态", "停用");

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("角色名称", "测试角色");
        after.put("状态", "启用");

        operationLogRecorder.recordStatusChange(OperationLogResourceType.ROLE, 10L, "测试角色", true, before, after);

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperationType()).isEqualTo(OperationType.ENABLE);
        assertThat(captured.getChangeDetail()).contains("状态").doesNotContain("角色名称");
    }

    /**
     * 停用场景下应写入停用对应的操作类型码值。
     */
    @Test
    void recordStatusChange_shouldUseDisableOperationType_whenEnableFalse() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("状态", "启用");
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("状态", "停用");

        operationLogRecorder.recordStatusChange(OperationLogResourceType.ROLE, 10L, "测试角色", false, before, after);

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperationType()).isEqualTo(OperationType.DISABLE);
    }

    /**
     * 删除操作时，after 快照视为全 null，变更详情中每个字段的新值为空、旧值为快照中对应的值；
     * {@link cn.nihility.rbac.common.util.JacksonUtils} 序列化时排除 {@code null} 字段，
     * 持久化的 JSON 中 {@code newValue} 键本身被省略，而不是写成字面量 {@code "newValue":null}。
     */
    @Test
    void recordDelete_shouldTreatAfterAsAllNull() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("角色名称", "测试角色");

        operationLogRecorder.recordDelete(OperationLogResourceType.ROLE, 10L, "测试角色", before);

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperationType()).isEqualTo(OperationType.DELETE);
        assertThat(captured.getChangeDetail()).contains("\"oldValue\":\"测试角色\"").doesNotContain("\"newValue\"");
    }

    /**
     * 当前线程没有绑定 HTTP 请求上下文（如单元测试直接调用）时，操作 IP 与 User-Agent
     * 相关字段均应为空，不抛出异常。
     */
    @Test
    void record_shouldLeaveIpAndUserAgentFieldsNull_whenNoRequestContext() {
        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, 10L, "测试角色", Map.of("角色名称", "测试角色"));

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperateIp()).isNull();
        assertThat(captured.getOperateUserAgent()).isNull();
        assertThat(captured.getOperateOs()).isNull();
        assertThat(captured.getOperateBrowser()).isNull();
        assertThat(captured.getOperateTerminal()).isNull();
    }

    /**
     * 存在请求上下文且携带 {@code X-Forwarded-For} 请求头时，应优先取其第一个 IP。
     */
    @Test
    void record_shouldPreferForwardedForHeader_whenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, 10L, "测试角色", Map.of("角色名称", "测试角色"));

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperateIp()).isEqualTo("10.0.0.1");
    }

    /**
     * 存在请求上下文但没有 {@code X-Forwarded-For} 请求头时，应回退取 {@code remoteAddr}。
     */
    @Test
    void record_shouldFallbackToRemoteAddr_whenForwardedForAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, 10L, "测试角色", Map.of("角色名称", "测试角色"));

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperateIp()).isEqualTo("192.168.1.1");
    }

    /**
     * 存在合法的 {@code User-Agent} 请求头时，应正确解析出操作终端类型/操作系统/操作浏览器，
     * 并原样保留原始请求头到 {@code operateUserAgent}。
     */
    @Test
    void record_shouldParseUserAgent_whenHeaderPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/120.0.0.0 Safari/537.36";
        request.addHeader("User-Agent", userAgent);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, 10L, "测试角色", Map.of("角色名称", "测试角色"));

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperateUserAgent()).isEqualTo(userAgent);
        assertThat(captured.getOperateBrowser()).isEqualTo("Chrome 120");
        assertThat(captured.getOperateOs()).isEqualTo("Windows 10");
        assertThat(captured.getOperateTerminal()).isEqualTo("Computer");
    }

    /**
     * 无法识别的 {@code User-Agent} 请求头（非法/未知客户端）时，操作终端类型/操作系统/
     * 操作浏览器应为空，但原始请求头仍然原样保留。
     */
    @Test
    void record_shouldLeaveParsedFieldsNull_whenUserAgentUnrecognizable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String userAgent = "SomeCustomBot/1.0";
        request.addHeader("User-Agent", userAgent);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, 10L, "测试角色", Map.of("角色名称", "测试角色"));

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperateUserAgent()).isEqualTo(userAgent);
        assertThat(captured.getOperateBrowser()).isNull();
        assertThat(captured.getOperateOs()).isNull();
        assertThat(captured.getOperateTerminal()).isEqualTo("Computer");
    }

    /**
     * {@code User-Agent} 请求头缺失时，操作终端类型/操作系统/操作浏览器/原始 User-Agent
     * 均应为空，不影响操作 IP 等其余字段的正常写入。
     */
    @Test
    void record_shouldLeaveUserAgentFieldsNull_whenHeaderAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        operationLogRecorder.recordCreate(OperationLogResourceType.ROLE, 10L, "测试角色", Map.of("角色名称", "测试角色"));

        OperationLogEntity captured = captureInsertedEntity();
        assertThat(captured.getOperateIp()).isEqualTo("192.168.1.1");
        assertThat(captured.getOperateUserAgent()).isNull();
        assertThat(captured.getOperateOs()).isNull();
        assertThat(captured.getOperateBrowser()).isNull();
        assertThat(captured.getOperateTerminal()).isNull();
    }

    /**
     * 捕获本次调用写入 {@link OperationLogMapper#insert} 的操作日志实体。
     *
     * @return 被写入的操作日志实体
     */
    private OperationLogEntity captureInsertedEntity() {
        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(operationLogMapper).insert(captor.capture());
        return captor.getValue();
    }
}
