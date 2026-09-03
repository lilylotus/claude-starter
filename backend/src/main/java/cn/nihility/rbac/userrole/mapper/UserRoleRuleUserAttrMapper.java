package cn.nihility.rbac.userrole.mapper;

import cn.nihility.rbac.userrole.dto.UserRoleRuleUserAttrRow;
import cn.nihility.rbac.userrole.entity.UserRoleRuleUserAttrEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户角色规则用户属性条件数据访问接口。单表 CRUD（整体替换用的批量插入/按 {@code ruleId}
 * 删除）直接复用 {@link BaseMapper}；按规则 id 查询需要关联 {@code tab_metadata_field}
 * 回填字段名称/编码，SQL 写在
 * {@code resources/mybatis/mapper/UserRoleRuleUserAttrMapper.xml} 里，风格对齐
 * {@code cn.nihility.rbac.appaccess.policy.mapper.PolicyUserAttrMapper}。
 */
@Mapper
public interface UserRoleRuleUserAttrMapper extends BaseMapper<UserRoleRuleUserAttrEntity> {

    /**
     * 按规则 id 查询其全部用户属性条件，关联回填元数据字段名称/编码。
     *
     * @param ruleId 规则 id
     * @return 用户属性条件联表查询行列表
     */
    List<UserRoleRuleUserAttrRow> selectByRuleId(@Param("ruleId") Long ruleId);
}
