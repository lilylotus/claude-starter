package cn.nihility.rbac.sync.openapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 对账摘要接口响应（app-sync-changelog-pull change design.md Decision 10）：返回调用方
 * 当前可见范围内该数据类型的记录数与内容摘要，并携带当前变更流水表最大 {@code changeSeq}
 * （水位号），供"全量 pull + digest 拿到的水位号切入增量 /changes"这套衔接协议使用。
 */
@Getter
@Builder
@Schema(description = "对账摘要接口响应")
public class SyncDigestVO {

    /** 数据类型：ORG/USER/POSITION/APP/ROLE/DICT。 */
    @Schema(description = "数据类型：ORG/USER/POSITION/APP/ROLE/DICT")
    private final String entityType;

    /** 摘要算法名，固定为 {@code SHA-256}。 */
    @Schema(description = "摘要算法名，固定为 SHA-256")
    private final String algorithm;

    /**
     * 摘要规则版本号，标记记录排序/字段映射/canonical JSON 编码规则的版本，未来规则调整时
     * 升级该值，客户端据此判断新旧摘要是否可比较。
     */
    @Schema(description = "摘要规则版本号，未来规则调整时升级，新旧版本摘要不可直接比较")
    private final String digestVersion;

    /** 参与摘要计算的记录条数。 */
    @Schema(description = "参与摘要计算的记录条数")
    private final long recordCount;

    /** 摘要值，{@code algorithm} 计算结果的十六进制小写字符串。 */
    @Schema(description = "摘要值，algorithm 计算结果的十六进制小写字符串")
    private final String digestValue;

    /**
     * 当前变更流水表的最大 {@code changeSeq}，十进制字符串；表为空（尚未产生任何变更）时
     * 返回 {@code "0"}，与增量拉取 {@code sinceSeq} 默认值语义一致，可直接作为
     * {@code /changes} 的起始游标。
     */
    @Schema(description = "当前变更流水表最大 changeSeq，十进制字符串；表为空时为 \"0\"，可直接作为 /changes 的起始游标")
    private final String currentMaxSeq;

    /** 应用级同步配置纪元，十进制字符串，语义同 {@link SyncChangesPageVO#getConfigEpoch()}。 */
    @Schema(description = "应用级同步配置纪元，十进制字符串")
    private final String configEpoch;
}
