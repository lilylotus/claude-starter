package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.common.PageResult;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
import cn.nihility.rbac.operationlog.constant.OperationLogResourceType;
import cn.nihility.rbac.operationlog.service.OperationLogRecorder;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.mapstruct.PositionConvert;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.support.PositionDynamicFieldSupport;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 任职管理业务逻辑实现，复用用户管理模块既有的 {@code tab_user_position} 表/实体/Mapper，
 * 以组织为导航维度提供独立的查询与维护能力，不影响用户管理内嵌任职子表单的既有行为。
 */
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    /** 当前项目尚未接入登录鉴权，创建人/更新人暂时固定为该值。 */
    private static final String DEFAULT_OPERATOR = "admin";

    /** 用户任职记录数据访问接口。 */
    private final UserPositionMapper userPositionMapper;

    /** 用户数据访问接口，仅用于回填所属用户姓名。 */
    private final UserMapper userMapper;

    /** 组织数据访问接口，仅用于回填所属组织名称。 */
    private final OrgMapper orgMapper;

    /** 操作日志记录组件。 */
    private final OperationLogRecorder operationLogRecorder;

    /** 表单字段定义业务逻辑接口，用于回查操作日志快照所需的启用字段定义。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /**
     * {@code bizType=POSITION} 动态字段（必填/正则/唯一性）校验的共享组件，与用户管理
     * 内嵌任职子表单（{@code UserServiceImpl}）共用同一份校验逻辑。
     */
    private final PositionDynamicFieldSupport positionDynamicFieldSupport;

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<PositionVO> getPage(Long orgId, Integer page, Integer pageSize) {
        if (orgId == null) {
            throw new BusinessException("所属组织不能为空");
        }

        IPage<PositionVO> resultPage = userPositionMapper.selectPositionPage(
                new Page<>(page, pageSize), orgId, PositionStatus.DELETED);
        return PageResult.of(resultPage.getRecords(), resultPage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO getById(Long id) {
        PositionVO vo = userPositionMapper.selectPositionDetail(id, PositionStatus.DELETED);
        if (vo == null) {
            throw new BusinessException("任职记录不存在");
        }
        return vo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO create(PositionCreateRequest request) {
        positionDynamicFieldSupport.validate(request, true, null);

        UserPositionEntity entity = PositionConvert.INSTANCE.toEntity(request);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(PositionStatus.ENABLED);
        entity.setCreateBy(DEFAULT_OPERATOR);
        entity.setCreateTime(now);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(now);
        userPositionMapper.insert(entity);

        operationLogRecorder.recordCreate(OperationLogResourceType.POSITION, entity.getId(),
                targetName(entity), toLogSnapshot(entity));

        return getById(entity.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO update(Long id, PositionUpdateRequest request) {
        UserPositionEntity entity = getExistingEntity(id);
        positionDynamicFieldSupport.validate(request, false, id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        PositionConvert.INSTANCE.updateEntity(request, entity);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);

        operationLogRecorder.recordUpdate(OperationLogResourceType.POSITION, id, targetName(entity),
                beforeSnapshot, toLogSnapshot(entity));

        return getById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO enable(Long id) {
        return changeStatus(id, PositionStatus.ENABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PositionVO disable(Long id) {
        return changeStatus(id, PositionStatus.DISABLED);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        UserPositionEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(PositionStatus.DELETED);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);

        operationLogRecorder.recordDelete(OperationLogResourceType.POSITION, id, targetName(entity), beforeSnapshot);
    }

    /**
     * 变更任职记录状态（启用/停用）并返回更新后的详情。
     *
     * @param id     任职记录 id
     * @param status 目标状态
     * @return 更新后的任职记录详情
     */
    private PositionVO changeStatus(Long id, int status) {
        UserPositionEntity entity = getExistingEntity(id);
        Map<String, Object> beforeSnapshot = toLogSnapshot(entity);

        entity.setStatus(status);
        entity.setUpdateBy(DEFAULT_OPERATOR);
        entity.setUpdateTime(LocalDateTime.now());
        userPositionMapper.updateById(entity);

        operationLogRecorder.recordStatusChange(OperationLogResourceType.POSITION, id, targetName(entity),
                status == PositionStatus.ENABLED, beforeSnapshot, toLogSnapshot(entity));
        return getById(id);
    }

    /**
     * 查询一个未被逻辑删除的任职记录，不存在时抛出业务异常。
     *
     * @param id 任职记录 id
     * @return 任职记录实体
     */
    private UserPositionEntity getExistingEntity(Long id) {
        UserPositionEntity entity = userPositionMapper.selectById(id);
        if (entity == null || Objects.equals(entity.getStatus(), PositionStatus.DELETED)) {
            throw new BusinessException("任职记录不存在");
        }
        return entity;
    }

    /**
     * 构造任职记录的操作日志被操作对象名称快照："所属用户姓名-所属组织名称"，
     * 任职记录本身没有独立的名称字段。
     *
     * @param entity 任职记录实体
     * @return 被操作对象名称快照
     */
    private String targetName(UserPositionEntity entity) {
        UserEntity user = entity.getUserId() != null ? userMapper.selectById(entity.getUserId()) : null;
        OrgEntity org = entity.getOrgId() != null ? orgMapper.selectById(entity.getOrgId()) : null;
        String userName = user != null ? user.getName() : "未知用户";
        String orgName = org != null ? org.getName() : "未知组织";
        return userName + "-" + orgName;
    }

    /**
     * 构造任职记录实体的操作日志字段快照，key 为中文字段名，value 为人类可读的格式化值；
     * 所属用户姓名、所属组织名称需分别按 {@code userId}/{@code orgId} 回查一次；末尾追加
     * 当前启用的 {@code ext1}..{@code ext10} 扩展字段（key 使用字段定义的展示名）。
     *
     * @param entity 任职记录实体
     * @return 操作日志字段快照
     */
    private Map<String, Object> toLogSnapshot(UserPositionEntity entity) {
        UserEntity user = entity.getUserId() != null ? userMapper.selectById(entity.getUserId()) : null;
        OrgEntity org = entity.getOrgId() != null ? orgMapper.selectById(entity.getOrgId()) : null;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("所属用户", user != null ? user.getName() : null);
        snapshot.put("所属组织", org != null ? org.getName() : null);
        snapshot.put("任职类型", entity.getPositionType());
        snapshot.put("任职地址", entity.getPositionAddress());
        snapshot.put("任职电话", entity.getPositionPhone());
        snapshot.put("显示序号", entity.getShowOrder());
        snapshot.put("备注", entity.getRemark());
        snapshot.put("状态", statusLabel(entity.getStatus()));

        List<FormFieldDefinitionVO> definitions =
                formFieldDefinitionService.listActiveByBizType(FormFieldBizType.POSITION);
        FormFieldSnapshotSupport.appendExtFieldSnapshot(snapshot, definitions, extValues(entity));
        return snapshot;
    }

    /**
     * 把任职记录实体的 {@code ext1}..{@code ext10} 逐一收集为列名到当前值的映射，
     * 供 {@link FormFieldSnapshotSupport#appendExtFieldSnapshot} 使用。
     *
     * @param entity 任职记录实体
     * @return {@code ext1}..{@code ext10} 列名到当前值的映射
     */
    private Map<String, String> extValues(UserPositionEntity entity) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ext1", entity.getExt1());
        values.put("ext2", entity.getExt2());
        values.put("ext3", entity.getExt3());
        values.put("ext4", entity.getExt4());
        values.put("ext5", entity.getExt5());
        values.put("ext6", entity.getExt6());
        values.put("ext7", entity.getExt7());
        values.put("ext8", entity.getExt8());
        values.put("ext9", entity.getExt9());
        values.put("ext10", entity.getExt10());
        return values;
    }

    /**
     * 把任职记录状态码值转换为中文文案，供操作日志快照使用。
     *
     * @param status 状态码值
     * @return 中文文案
     */
    private String statusLabel(Integer status) {
        if (Objects.equals(status, PositionStatus.ENABLED)) {
            return "启用";
        }
        if (Objects.equals(status, PositionStatus.DISABLED)) {
            return "停用";
        }
        return "已删除";
    }
}
