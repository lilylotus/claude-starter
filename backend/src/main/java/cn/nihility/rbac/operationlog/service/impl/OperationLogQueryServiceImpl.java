package cn.nihility.rbac.operationlog.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.operationlog.constant.OperationType;
import cn.nihility.rbac.operationlog.dto.OperationLogDetailVO;
import cn.nihility.rbac.operationlog.dto.OperationLogFieldChangeVO;
import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import cn.nihility.rbac.operationlog.mapper.OperationLogMapper;
import cn.nihility.rbac.operationlog.mapstruct.OperationLogConvert;
import cn.nihility.rbac.operationlog.service.OperationLogQueryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作日志查询业务逻辑实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogQueryServiceImpl implements OperationLogQueryService {

    /** 操作日志数据访问接口。 */
    private final OperationLogMapper operationLogMapper;

    /** 用于把持久化的字段变更详情 JSON 字符串反序列化为结构化列表。 */
    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<OperationLogVO> getPage(OperationLogQueryRequest request) {
        Page<OperationLogEntity> queryPage = new Page<>(request.getPage(), request.getPageSize());
        var resultPage = operationLogMapper.selectOperationLogPage(queryPage, request);

        List<OperationLogVO> records = OperationLogConvert.INSTANCE.toVOList(resultPage.getRecords());
        for (int i = 0; i < records.size(); i++) {
            OperationLogVO vo = records.get(i);
            vo.setOperationTypeLabel(OperationType.label(vo.getOperationType()));
            vo.setChangeDetail(parseChangeDetail(resultPage.getRecords().get(i).getChangeDetail()));
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
        vo.setChangeDetail(parseChangeDetail(entity.getChangeDetail()));
        return vo;
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
            return objectMapper.readValue(changeDetail,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, OperationLogFieldChangeVO.class));
        } catch (Exception e) {
            log.warn("操作日志变更详情反序列化失败：{}", changeDetail, e);
            return List.of();
        }
    }
}
