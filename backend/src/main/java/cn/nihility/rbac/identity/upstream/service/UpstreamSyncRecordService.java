package cn.nihility.rbac.identity.upstream.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordDetailVO;
import cn.nihility.rbac.identity.upstream.dto.UpstreamSyncRecordVO;

/**
 * 上游数据同步执行记录业务逻辑接口：负责按数据源分页查询同步执行记录列表、按记录 id
 * 分页查询行明细列表（spec.md Requirement：同步执行记录）。
 */
public interface UpstreamSyncRecordService {

    /**
     * 按数据源分页查询其全部数据域的同步执行记录，按时间倒序返回。
     *
     * @param sourceId 上游数据源 id
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 同步执行记录分页结果
     */
    PageResult<UpstreamSyncRecordVO> listBySource(Long sourceId, Integer page, Integer pageSize);

    /**
     * 按执行记录 id 分页查询其行明细列表，同时校验该记录属于指定数据源，避免越权查看
     * 其他数据源的明细。
     *
     * @param sourceId 上游数据源 id
     * @param recordId 同步执行记录 id
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @return 行明细分页结果
     */
    PageResult<UpstreamSyncRecordDetailVO> listDetailsByRecord(Long sourceId, Long recordId, Integer page,
            Integer pageSize);
}
