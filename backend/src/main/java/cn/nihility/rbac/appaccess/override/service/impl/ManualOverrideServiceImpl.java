package cn.nihility.rbac.appaccess.override.service.impl;

import cn.nihility.rbac.appaccess.override.constant.OverrideType;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideQueryRequest;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideUpsertRequest;
import cn.nihility.rbac.appaccess.override.dto.ManualOverrideVO;
import cn.nihility.rbac.appaccess.override.entity.ManualOverrideEntity;
import cn.nihility.rbac.appaccess.override.mapper.ManualOverrideMapper;
import cn.nihility.rbac.appaccess.override.service.ManualOverrideService;
import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 人工例外业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class ManualOverrideServiceImpl implements ManualOverrideService {

    /** 人工例外数据访问接口。 */
    private final ManualOverrideMapper manualOverrideMapper;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<ManualOverrideVO> page(ManualOverrideQueryRequest request) {
        Page<ManualOverrideVO> queryPage = new Page<>(request.getPage(), request.getPageSize());
        var resultPage = manualOverrideMapper.selectOverridePage(queryPage, request.getUserId(), request.getAppId(),
                request.getOverrideType());
        return PageResult.of(resultPage.getRecords(), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ManualOverrideVO upsert(ManualOverrideUpsertRequest request) {
        if (!OverrideType.ALL_TYPES.contains(request.getOverrideType())) {
            throw new BusinessException("非法的例外类型：" + request.getOverrideType());
        }

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        ManualOverrideEntity existing = manualOverrideMapper.selectOne(new LambdaQueryWrapper<ManualOverrideEntity>()
                .eq(ManualOverrideEntity::getUserId, request.getUserId())
                .eq(ManualOverrideEntity::getAppId, request.getAppId()));

        Long id;
        if (existing != null) {
            existing.setOverrideType(request.getOverrideType());
            existing.setRemark(request.getRemark());
            existing.setUpdateBy(operator);
            existing.setUpdateTime(now);
            manualOverrideMapper.updateById(existing);
            id = existing.getId();
        } else {
            ManualOverrideEntity entity = ManualOverrideEntity.builder()
                    .userId(request.getUserId())
                    .appId(request.getAppId())
                    .overrideType(request.getOverrideType())
                    .remark(request.getRemark())
                    .createBy(operator)
                    .createTime(now)
                    .updateBy(operator)
                    .updateTime(now)
                    .build();
            manualOverrideMapper.insert(entity);
            id = entity.getId();
        }

        ManualOverrideVO vo = manualOverrideMapper.selectVOById(id);
        if (vo == null) {
            throw new BusinessException("用户或应用不存在");
        }
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        ManualOverrideEntity entity = manualOverrideMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("人工例外不存在");
        }
        manualOverrideMapper.deleteById(id);
    }
}
