package cn.nihility.rbac.ssoprotocollog.service;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogQueryRequest;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;

/**
 * SSO 协议调用记录查询业务逻辑接口，只读，不提供新增/编辑/删除能力——写入只通过
 * {@link SsoProtocolLogRecorder} 内部完成。
 */
public interface SsoProtocolLogQueryService {

    /**
     * 按可选条件动态分页查询 SSO 协议调用记录，按调用发生时间降序排列。
     *
     * @param request 分页 + 筛选参数
     * @return SSO 协议调用记录的分页结果
     */
    PageResult<SsoProtocolLogVO> getPage(SsoProtocolLogQueryRequest request);
}
