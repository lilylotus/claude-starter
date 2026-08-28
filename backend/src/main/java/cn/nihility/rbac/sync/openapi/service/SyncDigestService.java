package cn.nihility.rbac.sync.openapi.service;

import cn.nihility.rbac.sync.openapi.dto.SyncDigestVO;

/** 对账摘要业务逻辑接口。 */
public interface SyncDigestService {

    /**
     * 计算调用方当前可见范围内某数据类型的记录数与内容摘要。
     *
     * @param entityType 数据类型：ORG/USER/POSITION/APP/ROLE/DICT
     * @return 摘要响应
     */
    SyncDigestVO digest(String entityType);
}
