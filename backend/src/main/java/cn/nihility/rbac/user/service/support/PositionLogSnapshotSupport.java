package cn.nihility.rbac.user.service.support;

import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.dto.FormFieldDefinitionVO;
import cn.nihility.rbac.formfield.service.FormFieldDefinitionService;
import cn.nihility.rbac.formfield.support.FormFieldSnapshotSupport;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.user.constant.PositionStatus;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 任职记录操作日志的被操作对象名称快照与字段快照共享组件，供独立任职管理入口
 * （{@code PositionServiceImpl}）与用户管理内嵌任职子表单（{@code UserServiceImpl}，
 * {@code syncPositions}）共用，避免两处各自实现一套相同的快照逻辑。
 */
@Component
@RequiredArgsConstructor
public class PositionLogSnapshotSupport {

    /** 用户数据访问接口，仅用于回填所属用户姓名。 */
    private final UserMapper userMapper;

    /** 组织数据访问接口，仅用于回填所属组织名称。 */
    private final OrgMapper orgMapper;

    /** 表单字段定义业务逻辑接口，用于回查操作日志快照所需的启用字段定义。 */
    private final FormFieldDefinitionService formFieldDefinitionService;

    /**
     * 构造任职记录的操作日志被操作对象名称快照："所属用户姓名-所属组织名称"，
     * 任职记录本身没有独立的名称字段。
     *
     * @param entity 任职记录实体
     * @return 被操作对象名称快照
     */
    public String targetName(UserPositionEntity entity) {
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
    public Map<String, Object> snapshot(UserPositionEntity entity) {
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
