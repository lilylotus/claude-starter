package cn.nihility.rbac.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 条件分支单条条件项的结构化数据，不做任何可读文案拼接——字段展示名/字典值翻译交给前端
 * 复用已加载的业务对象表单渲染元数据完成（add-approval-remark-and-process-flowchart change
 * design.md Decision 9）。
 */
@Getter
@Setter
@Builder
@Schema(description = "条件分支单条条件项")
public class ConditionItemVO {

    /** 字段所属业务对象类型：{@code ORG}/{@code USER}/{@code POSITION}/{@code APP}。DSL v1
     *  取自条件本身携带的 {@code fieldBizType}；DSL v2 条件项无此字段，按该流程绑定的业务
     *  对象类型（{@code ProcessInstanceEntity.businessType}）兜底。 */
    private String fieldBizType;

    /** 参与比较的字段标识（fieldCode），前端按此到对应业务对象的渲染元数据里查展示名。 */
    private String field;

    /** 归一化后的比较符，统一取值 {@code EQ}/{@code NE}/{@code GT}/{@code GE}/{@code LT}/
     *  {@code LE}/{@code IN}/{@code IS_NULL}（DSL v1 的 {@code GTE}/{@code LTE} 归一化为
     *  {@code GE}/{@code LE}）。 */
    private String operator;

    /** 原始比较值，不做任何格式化，前端按字段的渲染元数据（字典/布尔/其余）翻译成可读文案。 */
    private Object value;
}
