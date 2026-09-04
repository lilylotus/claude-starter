package cn.nihility.rbac.workflow.dslv2.form;

import cn.nihility.rbac.workflow.entity.FormVersionEntity;

/**
 * 表单版本业务逻辑接口：基于 {@code formfield} 模块当前启用的字段定义生成不可变表单版本快照
 * （production-approval-lifecycle change design.md Decision 5，tasks.md 5.1）。
 */
public interface WorkflowFormVersionService {

    /**
     * 确保指定业务对象类型存在与其当前字段定义内容一致的表单版本：内容摘要与最新版本相同
     * 时直接复用该版本，不重复插入；内容有变化（或此前从未生成过）时生成新版本
     * （{@code formVersion} 在同一 {@code formCode} 下自增）。
     *
     * @param bizType 业务对象类型（ORG/USER/POSITION/APP），作为 {@code formCode}
     * @return 当前生效的表单版本
     */
    FormVersionEntity ensureCurrentVersion(String bizType);
}
