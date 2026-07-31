package cn.nihility.rbac.loginlog.service.impl;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.loginlog.constant.LoginResult;
import cn.nihility.rbac.loginlog.dto.LoginLogQueryRequest;
import cn.nihility.rbac.loginlog.dto.LoginLogVO;
import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import cn.nihility.rbac.loginlog.mapper.LoginLogMapper;
import cn.nihility.rbac.loginlog.mapstruct.LoginLogConvert;
import cn.nihility.rbac.loginlog.service.LoginLogQueryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 登录日志查询业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class LoginLogQueryServiceImpl implements LoginLogQueryService {

    /** 登录日志数据访问接口。 */
    private final LoginLogMapper loginLogMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<LoginLogVO> getPage(LoginLogQueryRequest request) {
        LambdaQueryWrapper<LoginLogEntity> wrapper = new LambdaQueryWrapper<LoginLogEntity>()
                .eq(StringUtils.hasText(request.getLoginAccount()),
                        LoginLogEntity::getLoginAccount, request.getLoginAccount())
                .eq(request.getLoginResult() != null, LoginLogEntity::getLoginResult, request.getLoginResult())
                .ge(request.getStartTime() != null, LoginLogEntity::getCreateTime, request.getStartTime())
                .le(request.getEndTime() != null, LoginLogEntity::getCreateTime, request.getEndTime())
                .orderByDesc(LoginLogEntity::getCreateTime);

        Page<LoginLogEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        Page<LoginLogEntity> resultPage = loginLogMapper.selectPage(queryPage, wrapper);

        List<LoginLogVO> records = LoginLogConvert.INSTANCE.toVOList(resultPage.getRecords());
        for (LoginLogVO vo : records) {
            vo.setLoginResultLabel(LoginResult.label(vo.getLoginResult()));
        }
        return PageResult.of(records, resultPage);
    }
}
