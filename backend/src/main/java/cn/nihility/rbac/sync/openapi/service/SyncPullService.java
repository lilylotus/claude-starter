package cn.nihility.rbac.sync.openapi.service;

import cn.nihility.rbac.sync.openapi.dto.SyncPullRecordVO;
import java.util.List;

/**
 * 对外拉取业务逻辑接口：按数据类型+id 批量拉取、按序列号游标批量拉取
 * （app-sync-notify-pull-api change design.md Decision 9）。调用方应用由
 * {@code OpenApiSignInterceptor} 校验通过后标记在
 * {@code cn.nihility.rbac.sync.openapi.OpenApiCallerContext} 中，本接口内部直接读取，
 * 不作为方法参数显式传入。
 */
public interface SyncPullService {

    /**
     * 按数据类型 + 一批变更对象 id 拉取每个 id 最新一条变更记录。
     *
     * @param dataType 数据类型
     * @param bizIds   变更对象 id 列表
     * @return 变更记录列表；数据类型不在当前调用方应用允许同步的范围内时返回空列表
     */
    List<SyncPullRecordVO> pullByBizIds(String dataType, List<Long> bizIds);

    /**
     * 按起始序列号游标批量拉取变更记录。
     *
     * @param dataType     数据类型，可为空（缺省返回调用方应用当前允许同步的全部数据域）
     * @param fromSequence 起始序列号，只返回大于该值的记录
     * @param limit        单次最多返回条数，可为空（缺省取对应数据域的分页大小配置）
     * @return 变更记录列表，按序列号升序排列
     */
    List<SyncPullRecordVO> pullBySequence(String dataType, Long fromSequence, Integer limit);
}
