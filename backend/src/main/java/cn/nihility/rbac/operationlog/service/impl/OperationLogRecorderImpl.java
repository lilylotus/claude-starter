package cn.nihility.rbac.operationlog.service.impl;

import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.dto.OperationLogFieldChangeVO;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.operationlog.util.UserAgentParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link OperationLogRecorder} 的默认实现：对 before/after 快照做逐字段 diff、
 * 通过 {@link RequestContextHolder} 获取当前 HTTP 请求解析操作 IP 与 User-Agent
 * 相关信息，最终写入 {@code tab_operation_log}。当前项目尚未接入登录鉴权，操作人
 * 暂时固定为 {@link #DEFAULT_OPERATOR}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogRecorderImpl implements OperationLogRecorder {

    /** 当前项目尚未接入登录鉴权，操作人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 反向代理场景下客户端真实 IP 所在的请求头。 */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    /** 携带浏览器/操作系统/设备类型信息的请求头。 */
    private static final String USER_AGENT_HEADER = "User-Agent";

    /** 操作日志数据访问接口。 */
    private final OperationLogMapper operationLogMapper;

    /** 用于把字段变更详情序列化为 JSON 字符串。 */
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordCreate(String resourceType, Long targetId, String targetName, Map<String, Object> afterSnapshot) {
        record(OperationType.CREATE, resourceType, targetId, targetName, null, afterSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordUpdate(String resourceType, Long targetId, String targetName,
            Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot) {
        record(OperationType.UPDATE, resourceType, targetId, targetName, beforeSnapshot, afterSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordStatusChange(String resourceType, Long targetId, String targetName, boolean enable,
            Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot) {
        int operationType = enable ? OperationType.ENABLE : OperationType.DISABLE;
        record(operationType, resourceType, targetId, targetName, beforeSnapshot, afterSnapshot);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordDelete(String resourceType, Long targetId, String targetName, Map<String, Object> beforeSnapshot) {
        record(OperationType.DELETE, resourceType, targetId, targetName, beforeSnapshot, null);
    }

    /**
     * 统一的落库入口：对 before/after 快照做逐字段 diff、解析请求上下文信息、写入日志。
     *
     * @param operationType   操作类型
     * @param resourceType    资源类型编码
     * @param targetId        被操作对象主键 id
     * @param targetName      被操作对象名称快照
     * @param beforeSnapshot  变更前的字段快照，可为 {@code null}
     * @param afterSnapshot   变更后的字段快照，可为 {@code null}
     */
    private void record(int operationType, String resourceType, Long targetId, String targetName,
            Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot) {
        List<OperationLogFieldChangeVO> changes = diff(beforeSnapshot, afterSnapshot);

        HttpServletRequest request = currentRequest();
        LocalDateTime now = LocalDateTime.now();
        OperationLogEntity entity = OperationLogEntity.builder()
                .module(OperationLogResourceType.module(resourceType))
                .resourceType(resourceType)
                .resourceName(OperationLogResourceType.resourceName(resourceType))
                .operationType(operationType)
                .targetId(targetId)
                .targetName(targetName)
                .changeDetail(toJson(changes))
                .operateIp(resolveIp(request))
                .createBy(DEFAULT_OPERATOR)
                .createTime(now)
                .updateBy(DEFAULT_OPERATOR)
                .updateTime(now)
                .build();
        fillUserAgentInfo(entity, request);

        operationLogMapper.insert(entity);
    }

    /**
     * 对 before/after 两份快照做逐字段 diff：以两者 key 的并集遍历，值相等的字段跳过，
     * 不相等的才写入结果；{@code beforeSnapshot} 为 {@code null} 时视为全 null（新增场景），
     * {@code afterSnapshot} 为 {@code null} 时视为全 null（删除场景）。
     *
     * @param beforeSnapshot 变更前的字段快照，可为 {@code null}
     * @param afterSnapshot  变更后的字段快照，可为 {@code null}
     * @return 发生变化的字段列表，保持 key 出现的先后顺序
     */
    private List<OperationLogFieldChangeVO> diff(Map<String, Object> beforeSnapshot, Map<String, Object> afterSnapshot) {
        Map<String, Object> before = beforeSnapshot != null ? beforeSnapshot : Map.of();
        Map<String, Object> after = afterSnapshot != null ? afterSnapshot : Map.of();

        Set<String> fields = new LinkedHashSet<>();
        fields.addAll(before.keySet());
        fields.addAll(after.keySet());

        return fields.stream()
                .filter(field -> !Objects.equals(before.get(field), after.get(field)))
                .map(field -> OperationLogFieldChangeVO.builder()
                        .field(field)
                        .oldValue(toDisplayValue(before.get(field)))
                        .newValue(toDisplayValue(after.get(field)))
                        .build())
                .toList();
    }

    /**
     * 把快照值转换为用于展示/存储的字符串。
     *
     * @param value 原始快照值
     * @return 字符串表示，{@code null} 原样返回 {@code null}
     */
    private String toDisplayValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 把字段变更列表序列化为 JSON 字符串。
     *
     * @param changes 字段变更列表
     * @return JSON 字符串
     */
    private String toJson(List<OperationLogFieldChangeVO> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (Exception e) {
            log.warn("操作日志变更详情序列化失败，将写入空数组", e);
            return "[]";
        }
    }

    /**
     * 获取当前线程绑定的 HTTP 请求，非 HTTP 上下文（如单元测试）中调用时返回 {@code null}。
     *
     * @return 当前 HTTP 请求，取不到时为 {@code null}
     */
    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    /**
     * 解析操作发起 IP：优先取 {@code X-Forwarded-For} 请求头的第一个 IP（兼容经反向代理/
     * 网关的场景），否则取 {@code request.getRemoteAddr()}；请求不可用时返回 {@code null}。
     *
     * @param request 当前 HTTP 请求，可为 {@code null}
     * @return 操作发起 IP，取不到时为 {@code null}
     */
    private String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 读取 {@code User-Agent} 请求头并用 {@link UserAgentParser} 手写正则解析出操作终端
     * 类型/操作系统/操作浏览器，原样存入 {@code operateUserAgent}；请求不可用、请求头缺失
     * 或解析异常时相关字段均保持为 {@code null}，不影响日志主体写入。
     *
     * @param entity  待写入的操作日志实体
     * @param request 当前 HTTP 请求，可为 {@code null}
     */
    private void fillUserAgentInfo(OperationLogEntity entity, HttpServletRequest request) {
        if (request == null) {
            return;
        }
        String userAgentHeader = request.getHeader(USER_AGENT_HEADER);
        if (!StringUtils.hasText(userAgentHeader)) {
            return;
        }
        entity.setOperateUserAgent(userAgentHeader);

        try {
            entity.setOperateBrowser(UserAgentParser.parseBrowser(userAgentHeader));
            entity.setOperateOs(UserAgentParser.parseOs(userAgentHeader));
            entity.setOperateTerminal(UserAgentParser.parseTerminal(userAgentHeader));
        } catch (Exception e) {
            log.warn("解析 User-Agent 请求头失败：{}", userAgentHeader, e);
        }
    }
}
