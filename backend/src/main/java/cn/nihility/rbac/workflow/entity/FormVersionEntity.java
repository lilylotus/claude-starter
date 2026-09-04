package cn.nihility.rbac.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 不可变表单版本快照持久化实体，对应表 {@code tab_wf_form_version}。基于
 * {@code formfield} 模块当前启用的字段定义生成一次性快照，内容摘要不变时复用既有版本行，
 * 不重复插入（production-approval-lifecycle change design.md Decision 5，tasks.md 5.1）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("tab_wf_form_version")
public class FormVersionEntity {

    /** 主键 id。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 表单编码，通常对应 {@code bizType}（ORG/USER/POSITION/APP）。 */
    private String formCode;

    /** 表单版本号，同 {@code formCode} 下自增，从 1 开始。 */
    private Integer formVersion;

    /** 表单字段 schema 快照（JSON），来自动态字段元数据，只读不可变。 */
    private String schemaText;

    /** {@code schemaText} 摘要（SHA-256），供快速比对内容是否变化。 */
    private String schemaDigest;

    /** 创建人。 */
    private String createBy;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 更新人。 */
    private String updateBy;

    /** 更新时间。 */
    private LocalDateTime updateTime;
}
