package cn.nihility.rbac.operationlog.service.impl;

import cn.nihility.rbac.common.result.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.operationlog.constant.OperationSource;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.dto.OperationLogDetailVO;
import cn.nihility.rbac.operationlog.dto.OperationLogFieldChangeVO;
import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
import cn.nihility.rbac.operationlog.mapstruct.OperationLogConvert;
import cn.nihility.rbac.operationlog.service.OperationLogQueryService;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志查询业务逻辑实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogQueryServiceImpl implements OperationLogQueryService {

    /** 操作日志数据访问接口。 */
    private final OperationLogMapper operationLogMapper;

    /** 用户数据访问接口，用于按账号编码/姓名解析 {@code createBy} 查询条件对应的候选用户 id。 */
    private final UserMapper userMapper;

    /** 审计字段展示名批量解析服务。 */
    private final UserDisplayService userDisplayService;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<OperationLogVO> getPage(OperationLogQueryRequest request) {
        if (StringUtils.hasText(request.getCreateBy())) {
            List<UserEntity> candidates = userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getCode, request.getCreateBy())
                    .or()
                    .eq(UserEntity::getName, request.getCreateBy()));
            if (candidates.isEmpty()) {
                return new PageResult<>(List.of(), 0L, request.getPage(), request.getPageSize());
            }
            request.setCreateByUserIds(candidates.stream().map(u -> String.valueOf(u.getId())).toList());
        }

        Page<OperationLogEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        var resultPage = operationLogMapper.selectOperationLogPage(queryPage, request);

        List<OperationLogVO> records = OperationLogConvert.INSTANCE.toVOList(resultPage.getRecords());
        Map<String, String> displayNames = resolveDisplayNames(resultPage.getRecords());
        for (int i = 0; i < records.size(); i++) {
            OperationLogVO vo = records.get(i);
            OperationLogEntity entity = resultPage.getRecords().get(i);
            vo.setOperationTypeLabel(OperationType.label(vo.getOperationType()));
            vo.setOperateSourceLabel(OperationSource.label(vo.getOperateSource()));
            vo.setChangeDetail(parseChangeDetail(entity.getChangeDetail()));
            vo.setCreateBy(resolveDisplayName(entity.getCreateBy(), displayNames));
        }
        return PageResult.of(records, resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OperationLogDetailVO getById(Long id) {
        OperationLogEntity entity = operationLogMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException("操作日志不存在");
        }

        OperationLogDetailVO vo = OperationLogConvert.INSTANCE.toDetailVO(entity);
        vo.setOperationTypeLabel(OperationType.label(entity.getOperationType()));
        vo.setOperateSourceLabel(OperationSource.label(entity.getOperateSource()));
        vo.setChangeDetail(parseChangeDetail(entity.getChangeDetail()));
        Map<String, String> displayNames = userDisplayService.resolveDisplayNames(Set.of(entity.getCreateBy()));
        vo.setCreateBy(resolveDisplayName(entity.getCreateBy(), displayNames));
        return vo;
    }

    /**
     * 批量收集本页操作日志实体的 {@code createBy}，调用 {@link UserDisplayService} 解析展示名。
     *
     * @param entities 本页操作日志实体列表
     * @return 输入用户 id 文本 -> 展示名的映射
     */
    private Map<String, String> resolveDisplayNames(List<OperationLogEntity> entities) {
        Set<String> userIdTexts = new LinkedHashSet<>();
        for (OperationLogEntity entity : entities) {
            userIdTexts.add(entity.getCreateBy());
        }
        return userDisplayService.resolveDisplayNames(userIdTexts);
    }

    /**
     * 把审计字段原始值（用户 id 文本）解析为展示名，查不到时回退为"未知用户"。
     *
     * @param userIdText   用户 id 文本
     * @param displayNames 已批量解析好的展示名映射
     * @return 展示名；输入为空白时返回空字符串
     */
    private String resolveDisplayName(String userIdText, Map<String, String> displayNames) {
        if (!StringUtils.hasText(userIdText)) {
            return "";
        }
        return displayNames.getOrDefault(userIdText, "未知用户");
    }

    /**
     * 把持久化的字段变更详情 JSON 字符串反序列化为结构化列表，反序列化失败时返回空列表，
     * 不影响日志其余字段的正常返回。
     *
     * @param changeDetail 字段变更详情 JSON 字符串
     * @return 结构化的字段变更列表
     */
    private List<OperationLogFieldChangeVO> parseChangeDetail(String changeDetail) {
        if (changeDetail == null || changeDetail.isBlank()) {
            return List.of();
        }
        try {
            return JacksonUtils.toObj(changeDetail, new TypeReference<List<OperationLogFieldChangeVO>>() {
            });
        } catch (Exception e) {
            log.warn("操作日志变更详情反序列化失败：{}", changeDetail, e);
            return List.of();
        }
    }
}
