package cn.nihility.rbac.workflow.dslv2.form;

import cn.nihility.rbac.auth.context.CurrentUserContext;
import cn.nihility.rbac.common.util.JacksonUtils;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.workflow.dslv2.util.DigestUtils;
import cn.nihility.rbac.workflow.entity.FormVersionEntity;
import cn.nihility.rbac.workflow.mapper.FormVersionMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link WorkflowFormVersionService} 实现：从当前启用字段定义生成快照，与最新版本的摘要
 * 一致时复用，不一致（含此前从未生成过）时插入新版本。
 */
@Service
@RequiredArgsConstructor
public class WorkflowFormVersionServiceImpl implements WorkflowFormVersionService {

    /** 表单字段定义业务逻辑接口。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /** 表单版本数据访问接口。 */
    private final FormVersionMapper formVersionMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public FormVersionEntity ensureCurrentVersion(String bizType) {
        List<FormFieldDefinitionVO> definitions = formFieldDefinitionService.listActiveByBizType(bizType);
        String schemaText = JacksonUtils.toJson(definitions.stream().map(FieldSnapshot::from).toList());
        String digest = DigestUtils.sha256(schemaText);

        FormVersionEntity latest = formVersionMapper.selectOne(new LambdaQueryWrapper<FormVersionEntity>()
                .eq(FormVersionEntity::getFormCode, bizType)
                .orderByDesc(FormVersionEntity::getFormVersion)
                .last("LIMIT 1"));
        if (latest != null && Objects.equals(latest.getSchemaDigest(), digest)) {
            return latest;
        }

        LocalDateTime now = LocalDateTime.now();
        Long operatorId = CurrentUserContext.getUserId();
        String operatorText = operatorId == null ? "system" : operatorId.toString();
        FormVersionEntity entity = FormVersionEntity.builder()
                .formCode(bizType)
                .formVersion(latest == null ? 1 : latest.getFormVersion() + 1)
                .schemaText(schemaText)
                .schemaDigest(digest)
                .createBy(operatorText)
                .createTime(now)
                .updateBy(operatorText)
                .updateTime(now)
                .build();
        formVersionMapper.insert(entity);
        return entity;
    }

    /**
     * 表单字段定义的内容快照，只保留决定表单结构/校验规则的字段，剔除
     * {@code createBy}/{@code createTime}/{@code updateBy}/{@code updateTime} 等审计字段，
     * 避免字段定义仅审计信息变化（内容未变）时也被误判为"表单结构发生变化"而生成新版本。
     *
     * @param id            字段定义 id
     * @param fieldCode     字段标识
     * @param fieldName     展示名称
     * @param controlType   控件类型
     * @param dictTypeCode  关联字典类型编码
     * @param isUnique      是否要求唯一
     * @param isRequired    是否必填
     * @param showInCreate  是否在新增表单展示
     * @param showInEdit    是否在编辑表单展示
     * @param editable      是否可编辑
     * @param validateRegex 正则校验规则
     * @param showOrder     显示序号
     * @param status        状态
     * @param locked        是否承重字段
     */
    private record FieldSnapshot(
            Long id,
            String fieldCode,
            String fieldName,
            Integer controlType,
            String dictTypeCode,
            Boolean isUnique,
            Boolean isRequired,
            Boolean showInCreate,
            Boolean showInEdit,
            Boolean editable,
            String validateRegex,
            Integer showOrder,
            Integer status,
            Boolean locked) {

        private static FieldSnapshot from(FormFieldDefinitionVO vo) {
            return new FieldSnapshot(
                    vo.getId(),
                    vo.getFieldCode(),
                    vo.getFieldName(),
                    vo.getControlType(),
                    vo.getDictTypeCode(),
                    vo.getIsUnique(),
                    vo.getIsRequired(),
                    vo.getShowInCreate(),
                    vo.getShowInEdit(),
                    vo.getEditable(),
                    vo.getValidateRegex(),
                    vo.getShowOrder(),
                    vo.getStatus(),
                    vo.getLocked());
        }
    }
}
