package cn.nihility.rbac.appaccess.override.service;

import cn.nihility.rbac.appaccess.override.dto.ManualOverrideQueryRequest;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideUpsertRequest;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideVO;
import cn.nihility.rbac.common.result.PageResult;

/**
 * 人工例外业务逻辑接口：按 {@code userId+appId} upsert 语义的新增/更新、分页查询、删除
 * （spec.md"人工例外的维护"需求）。
 */
public interface ManualOverrideService {

    /**
     * 分页查询人工例外，支持按用户、应用、例外类型过滤。
     *
     * @param request 查询参数
     * @return 分页结果
     */
    PageResult<ManualOverrideVO> page(ManualOverrideQueryRequest request);

    /**
     * 新增或更新人工例外：{@code userId+appId} 组合已存在记录时更新
     * {@code overrideType}/{@code remark}，不新增一行；不存在时新增。
     *
     * @param request 新增/更新请求
     * @return 保存后的人工例外详情
     */
    ManualOverrideVO upsert(ManualOverrideUpsertRequest request);

    /**
     * 删除人工例外，删除后该 {@code userId+appId} 组合不再有人工例外，最终生效权限退回
     * 只看策略授权。
     *
     * @param id 人工例外 id
     */
    void delete(Long id);
}
