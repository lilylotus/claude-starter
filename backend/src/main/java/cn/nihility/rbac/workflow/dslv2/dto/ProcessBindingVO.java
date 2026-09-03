package cn.nihility.rbac.workflow.dslv2.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 业务绑定返回对象。 */
@Getter
@Builder
public class ProcessBindingVO {

    private final Long id;
    private final String bizType;
    private final String operationType;
    private final String scopeType;
    private final Long scopeId;
    private final Long definitionId;
    private final String executionMode;
    private final Long revision;
    private final Boolean enabled;
    private final String createBy;
    private final LocalDateTime createTime;
    private final String updateBy;
    private final LocalDateTime updateTime;
}
