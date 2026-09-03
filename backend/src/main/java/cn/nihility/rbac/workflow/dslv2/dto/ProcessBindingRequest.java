package cn.nihility.rbac.workflow.dslv2.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 新建/切换业务绑定的请求体（design.md Decision 4/12）。 */
@Getter
@Setter
public class ProcessBindingRequest {

    /** 业务对象类型：ORG/USER/POSITION/APP。 */
    @NotBlank
    private String bizType;

    /** 操作类型：CREATE/UPDATE/ENABLE/DISABLE/DELETE。 */
    @NotBlank
    private String operationType;

    /** 绑定范围类型：ORG/GLOBAL。 */
    @NotBlank
    private String scopeType;

    /** 范围内组织 id，{@code scopeType=GLOBAL} 时可不传（服务端固定为 0）。 */
    private Long scopeId;

    /** 绑定的流程定义 id，显式版本。 */
    @NotNull
    private Long definitionId;

    /** 执行模式：LEGACY_SYNC/RELIABLE_ASYNC，默认 LEGACY_SYNC。 */
    private String executionMode;

    /** 切换绑定时的乐观锁期望修订号，新建时可为空。 */
    private Long expectedRevision;
}
