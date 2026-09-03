package cn.nihility.rbac.workflow.constant;

/**
 * 流程模型生命周期状态，对应 {@code tab_wf_process_model.status} 与
 * {@code tab_wf_process_definition.status}（后者不使用 {@code DRAFT}）。设计器相关的草稿/
 * 发布/下线能力属于后续批次范围，本次仅随建表预留该常量类。
 */
public final class ProcessModelStatus {

    /** 草稿，可反复编辑，未部署。 */
    public static final String DRAFT = "DRAFT";

    /** 已发布，对应一个不可变的 Flowable 版本。 */
    public static final String PUBLISHED = "PUBLISHED";

    /** 已下线（挂起），不再接受新发起，运行中实例不受影响。 */
    public static final String DISABLED = "DISABLED";

    /**
     * 工具类不允许实例化。
     */
    private ProcessModelStatus() {
    }
}
