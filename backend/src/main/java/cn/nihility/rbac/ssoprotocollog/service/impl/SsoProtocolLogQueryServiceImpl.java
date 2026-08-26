package cn.nihility.rbac.ssoprotocollog.service.impl;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogQueryRequest;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import cn.nihility.rbac.ssoprotocollog.service.SsoProtocolLogQueryService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * SSO 协议调用记录查询业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class SsoProtocolLogQueryServiceImpl implements SsoProtocolLogQueryService {

    /** SSO 协议调用记录数据访问接口。 */
    private final SsoProtocolLogMapper ssoProtocolLogMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<SsoProtocolLogVO> getPage(SsoProtocolLogQueryRequest request) {
        Page<SsoProtocolLogVO> queryPage = new Page<>(request.getPage(), request.getPageSize());
        IPage<SsoProtocolLogVO> resultPage = ssoProtocolLogMapper.selectSsoProtocolLogPage(queryPage, request);

        for (SsoProtocolLogVO vo : resultPage.getRecords()) {
            vo.setResultLabel(SsoProtocolLogResult.label(vo.getResult()));
        }
        return PageResult.of(resultPage.getRecords(), resultPage);
    }
}
