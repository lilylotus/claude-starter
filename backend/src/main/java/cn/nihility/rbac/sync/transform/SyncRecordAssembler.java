package cn.nihility.rbac.sync.transform;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把一条业务表查询结果行（{@link SyncBizPageRow}）组装为对外输出的记录 Map，供
 * {@code SyncPullServiceImpl}（分页拉取 {@code records}）与对账摘要接口共用同一份"字段映射
 * 后的完整输出记录"组装逻辑，避免两处各自实现一套容易产生分歧（app-sync-changelog-pull
 * change design.md Decision 10——摘要接口"对每条记录做字段映射后的完整输出记录"必须与
 * {@code /pull} 返回的记录形状一致，才能让"全量 pull + 对账摘要"的组合校验有意义）。
 */
@Component
@RequiredArgsConstructor
public class SyncRecordAssembler {

    /** 字段映射转换执行器。 */
    private final FieldMappingTransformer fieldMappingTransformer;

    /**
     * 组装一条对外记录：先放入按该应用该数据域字段映射配置转换后的业务字段，再用
     * {@code bizId}/{@code bizCode}/{@code bizStatus}/{@code updateTime}/{@code version}
     * 五个通用固定键覆盖式写入，最后按领域特定固定键的值是否非空条件写入
     * {@code userCode}/{@code orgCode}（仅 POSITION）/{@code dictTypeCode}（仅 DICT）
     * （app-sync-drop-changelog change design.md Decision 1/2 修订版；version 固定键为
     * app-sync-changelog-pull change design.md Decision 5/11 新增，DICT 数据域没有版本号，
     * 恒为 {@code null}，十进制字符串序列化时按 {@code null} 原样保留，不转换为 "null" 文本）。
     *
     * @param appRefId 应用 id
     * @param dataType 数据类型
     * @param row      业务表查询结果行
     * @return 合并后的记录字段 Map
     */
    public Map<String, Object> assemble(Long appRefId, String dataType, SyncBizPageRow row) {
        Map<String, Object> data = fieldMappingTransformer.transform(appRefId, dataType, row.getData());
        Map<String, Object> record = new LinkedHashMap<>(data);
        record.put("bizId", row.getId());
        record.put("bizCode", row.getCode());
        record.put("bizStatus", row.getStatus());
        record.put("updateTime", row.getUpdateTime());
        record.put("version", row.getVersion() != null ? String.valueOf(row.getVersion()) : null);
        if (row.getUserCode() != null) {
            record.put("userCode", row.getUserCode());
        }
        if (row.getOrgCode() != null) {
            record.put("orgCode", row.getOrgCode());
        }
        if (row.getDictTypeCode() != null) {
            record.put("dictTypeCode", row.getDictTypeCode());
        }
        return record;
    }
}
