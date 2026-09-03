package cn.nihility.rbac.workflow.dslv2.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 审批节点超时配置（design.md Decision 3/11）：使用非中断边界定时器触发提醒，节点操作期限
 * 存储为 UTC 自然时间，前端按 Asia/Shanghai 显示；不支持工作日历换算。
 */
@Getter
@Setter
public class TimeoutConfigDsl {

    /** 超时时长，ISO-8601 duration 文本，如 {@code PT48H}。 */
    private String duration;

    /** 超时后的动作，首轮仅支持 {@code REMIND}（催办提醒），默认不启用自动批准/自动拒绝。 */
    private String action;

    /** 最大提醒次数，达到上限后不再重复提醒。 */
    private Integer maxReminders;
}
