package cn.nihility.rbac.ssoprotocollog.service.impl;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.ssoprotocollog.constant.SsoProtocolLogResult;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogQueryRequest;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import cn.nihility.rbac.ssoprotocollog.mapper.SsoProtocolLogMapper;
import cn.nihility.rbac.ssoprotocollog.mapstruct.SsoProtocolLogConvert;
import cn.nihility.rbac.ssoprotocollog.service.SsoProtocolLogQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        LambdaQueryWrapper<SsoProtocolLogEntity> wrapper = new LambdaQueryWrapper<SsoProtocolLogEntity>()
                .eq(request.getAppRefId() != null, SsoProtocolLogEntity::getAppRefId, request.getAppRefId())
                .eq(StringUtils.hasText(request.getProtocol()), SsoProtocolLogEntity::getProtocol, request.getProtocol())
                .eq(StringUtils.hasText(request.getEventType()), SsoProtocolLogEntity::getEventType, request.getEventType())
                .eq(request.getResult() != null, SsoProtocolLogEntity::getResult, request.getResult())
                .eq(StringUtils.hasText(request.getSessionId()), SsoProtocolLogEntity::getSessionId,
                        request.getSessionId())
                .ge(request.getStartTime() != null, SsoProtocolLogEntity::getCreateTime, request.getStartTime())
                .le(request.getEndTime() != null, SsoProtocolLogEntity::getCreateTime, request.getEndTime())
                .orderByDesc(SsoProtocolLogEntity::getCreateTime);

        Page<SsoProtocolLogEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        Page<SsoProtocolLogEntity> resultPage = ssoProtocolLogMapper.selectPage(queryPage, wrapper);

        List<SsoProtocolLogVO> records = SsoProtocolLogConvert.INSTANCE.toVOList(resultPage.getRecords());
        for (SsoProtocolLogVO vo : records) {
            vo.setResultLabel(SsoProtocolLogResult.label(vo.getResult()));
        }
        return PageResult.of(records, resultPage);
    }
}
