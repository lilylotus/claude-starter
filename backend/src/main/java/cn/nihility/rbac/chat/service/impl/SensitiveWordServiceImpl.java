package cn.nihility.rbac.chat.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.chat.constant.SensitiveWordStatus;
import cn.nihility.rbac.chat.dto.SensitiveWordCreateRequest;
import cn.nihility.rbac.chat.dto.SensitiveWordVO;
import cn.nihility.rbac.chat.entity.SensitiveWordEntity;
import cn.nihility.rbac.chat.mapper.SensitiveWordMapper;
import cn.nihility.rbac.chat.mapstruct.ChatConvert;
import cn.nihility.rbac.chat.service.SensitiveWordFilterService;
import cn.nihility.rbac.chat.service.SensitiveWordService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 敏感词后台管理业务逻辑实现。
 */
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl implements SensitiveWordService {

    /** 敏感词数据访问接口。 */
    private final SensitiveWordMapper sensitiveWordMapper;

    /** 敏感词过滤服务，写操作后触发内存自动机重建。 */
    private final SensitiveWordFilterService sensitiveWordFilterService;

    /** 当前登录操作人用户 id 解析服务。 */
    private final CurrentOperatorService currentOperatorService;

    /** 审计字段展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<SensitiveWordVO> getPage(String keyword, Integer status, Integer page, Integer pageSize) {
        LambdaQueryWrapper<SensitiveWordEntity> wrapper = new LambdaQueryWrapper<SensitiveWordEntity>()
                .like(StringUtils.hasText(keyword), SensitiveWordEntity::getWord, keyword)
                .eq(status != null, SensitiveWordEntity::getStatus, status)
                .orderByDesc(SensitiveWordEntity::getId);

        Page<SensitiveWordEntity> queryPage = new Page<>(page == null ? 1 : page, pageSize == null ? 10 : pageSize);
        Page<SensitiveWordEntity> resultPage = sensitiveWordMapper.selectPage(queryPage, wrapper);
        List<SensitiveWordVO> records = toVOListWithDisplayNames(resultPage.getRecords());
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public SensitiveWordVO create(SensitiveWordCreateRequest request) {
        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        SensitiveWordEntity entity = ChatConvert.INSTANCE.toSensitiveWordEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(SensitiveWordStatus.ENABLED);
        entity.setCreateBy(operator);
        entity.setCreateTime(now);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(now);
        try {
            sensitiveWordMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("敏感词[" + request.getWord() + "]已存在");
        }

        sensitiveWordFilterService.reload();
        return toVOListWithDisplayNames(List.of(entity)).get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void delete(Long id) {
        getExistingEntity(id);
        sensitiveWordMapper.deleteById(id);
        sensitiveWordFilterService.reload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public SensitiveWordVO enable(Long id) {
        return changeStatus(id, SensitiveWordStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public SensitiveWordVO disable(Long id) {
        return changeStatus(id, SensitiveWordStatus.DISABLED);
    }

    /**
     * 变更敏感词状态（启用/停用）并触发自动机重建。
     *
     * @param id     敏感词 id
     * @param status 目标状态
     * @return 更新后的敏感词详情
     */
    private SensitiveWordVO changeStatus(Long id, int status) {
        SensitiveWordEntity entity = getExistingEntity(id);
        entity.setStatus(status);
        entity.setUpdateBy(Objects.toString(currentOperatorService.resolveUserId(), null));
        entity.setUpdateTime(LocalDateTime.now());
        sensitiveWordMapper.updateById(entity);

        sensitiveWordFilterService.reload();
        return toVOListWithDisplayNames(List.of(entity)).get(0);
    }

    /**
     * 查询一个已存在的敏感词，不存在时抛出业务异常。
     *
     * @param id 敏感词 id
     * @return 敏感词实体
     */
    private SensitiveWordEntity getExistingEntity(Long id) {
        SensitiveWordEntity entity = sensitiveWordMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("敏感词不存在");
        }
        return entity;
    }

    /**
     * 把敏感词实体列表转换为详情视图对象列表，并批量解析 {@code createBy}/{@code updateBy}
     * 审计字段展示名。
     *
     * @param entities 敏感词实体列表
     * @return 详情视图对象列表
     */
    private List<SensitiveWordVO> toVOListWithDisplayNames(List<SensitiveWordEntity> entities) {
        List<SensitiveWordVO> result = ChatConvert.INSTANCE.toSensitiveWordVOList(entities);
        if (result.isEmpty()) {
            return result;
        }

        Set<String> auditUserIdTexts = entities.stream()
                .flatMap(entity -> Stream.of(entity.getCreateBy(), entity.getUpdateBy()))
                .collect(Collectors.toSet());
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(auditUserIdTexts);

        for (int i = 0; i < entities.size(); i++) {
            SensitiveWordVO vo = result.get(i);
            vo.setCreateBy(resolveDisplayName(entities.get(i).getCreateBy(), displayNames));
            vo.setUpdateBy(resolveDisplayName(entities.get(i).getUpdateBy(), displayNames));
        }
        return result;
    }

    /**
     * 把审计字段原始存储的用户 id 文本解析为人可读展示名，查不到时兜底为"未知用户"。
     *
     * @param userIdText   审计字段原始存储的用户 id 文本
     * @param displayNames 批量解析得到的用户 id 文本到展示名的映射
     * @return 人可读展示名
     */
    private String resolveDisplayName(String userIdText, Map<String, String> displayNames) {
        if (!StringUtils.hasText(userIdText)) {
            return "";
        }
        return displayNames.getOrDefault(userIdText, "未知用户");
    }
}
