package cn.nihility.rbac.identity.upstream.service;

import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordVO;
import java.util.List;

/**
 * 上游数据同步执行记录业务逻辑接口：负责按数据源查询同步执行记录列表、写入新记录
 * （spec.md Requirement：同步执行记录）。
 */
public interface UpstreamSyncRecordService {

    /**
     * 按数据源查询其全部数据域的同步执行记录，按时间倒序返回。
     *
     * @param sourceId 上游数据源 id
     * @return 同步执行记录视图对象列表
     */
    List<UpstreamSyncRecordVO> listBySource(Long sourceId);
}
