package cn.nihility.rbac.userrole.mapper;

import cn.nihility.rbac.userrole.dto.UserRoleRuleOrgScopeVO;
import cn.nihility.rbac.userrole.entity.UserRoleRuleOrgScopeEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户角色规则组织范围条件数据访问接口。单表 CRUD（整体替换用的批量插入/按 {@code ruleId}
 * 删除）直接复用 {@link BaseMapper}；按规则 id 查询需要关联 {@code tab_org} 回填组织名称，
 * SQL 写在 {@code resources/mybatis/mapper/UserRoleRuleOrgScopeMapper.xml} 里，风格对齐
 * {@code cn.nihility.rbac.appaccess.policy.mapper.PolicyOrgScopeMapper}。
 */
@Mapper
public interface UserRoleRuleOrgScopeMapper extends BaseMapper<UserRoleRuleOrgScopeEntity> {

    /**
     * 按规则 id 查询其全部组织范围条件，关联回填组织名称；组织若已被逻辑删除则不返回
     * （INNER JOIN 语义，脏关联数据没有展示价值）。
     *
     * @param ruleId 规则 id
     * @return 组织范围条件视图对象列表
     */
    List<UserRoleRuleOrgScopeVO> selectByRuleId(@Param("ruleId") Long ruleId);
}
