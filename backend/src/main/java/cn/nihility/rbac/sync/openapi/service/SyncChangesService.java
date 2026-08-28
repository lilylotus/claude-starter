package cn.nihility.rbac.sync.openapi.service;

import cn.nihility.rbac.sync.openapi.dto.SyncChangesPageVO;
import cn.nihility.rbac.sync.openapi.dto.SyncChangesRequest;

/** 增量游标拉取变更指针业务逻辑接口。 */
public interface SyncChangesService {

    /**
     * 增量游标拉取调用方当前可见范围内的变更指针。
     *
     * @param request 请求参数
     * @return 响应整体视图对象
     */
    SyncChangesPageVO changes(SyncChangesRequest request);
}
