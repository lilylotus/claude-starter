package cn.nihility.rbac.userrole.mapper;

import cn.nihility.rbac.userrole.entity.UserRoleRuleGrantEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户角色规则计算结果数据访问接口。按 {@code ruleId} 查询既有记录、按 {@code (ruleId,
 * userId)} 删除均直接复用 {@link BaseMapper}；{@link #insertBatch} 与
 * {@link #selectUserIdsByRoleId} 需要单条 SQL 完成批量插入/去重查询，SQL 写在
 * {@code resources/mybatis/mapper/UserRoleRuleGrantMapper.xml} 里。
 */
@Mapper
public interface UserRoleRuleGrantMapper extends BaseMapper<UserRoleRuleGrantEntity> {

    /**
     * 使用单条多值插入语句批量保存规则计算结果，调用方需保证传入的 {@code (ruleId,
     * userId)} 组合均未在库中存在，避免触发唯一约束冲突导致整条批量语句失败。
     *
     * @param entities 待保存的规则计算结果实体列表，不能为空
     * @return 插入的记录数
     */
    int insertBatch(@Param("entities") List<UserRoleRuleGrantEntity> entities);

    /**
     * 按角色 id 查询当前持有该角色的全部用户 id（去重），供
     * {@code AdminService#previewBatchPromoteByRole}/{@code batchPromoteByRole} 复用作为
     * "持有该角色"的判定数据来源（add-user-role-batch-assignment change design.md
     * Decision 5：一个用户的同一角色可能被多条规则各自产生一行 grant 记录，需要去重）。
     *
     * @param roleId 角色 id
     * @return 去重后的用户 id 列表
     */
    List<Long> selectUserIdsByRoleId(@Param("roleId") Long roleId);
}
