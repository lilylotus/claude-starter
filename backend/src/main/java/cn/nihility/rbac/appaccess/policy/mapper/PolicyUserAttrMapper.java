package cn.nihility.rbac.appaccess.policy.mapper;

import cn.nihility.rbac.appaccess.policy.dto.PolicyUserAttrRow;
import cn.nihility.rbac.appaccess.policy.entity.PolicyUserAttrEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 策略用户属性条件数据访问接口。单表 CRUD（整体替换用的批量插入/按 {@code policyId}
 * 删除）直接复用 {@link BaseMapper}；按策略 id 查询需要关联 {@code tab_metadata_field}
 * 回填字段名称/编码，SQL 写在
 * {@code resources/mybatis/mapper/PolicyUserAttrMapper.xml} 里，用单条 JOIN 完成。
 */
@Mapper
public interface PolicyUserAttrMapper extends BaseMapper<PolicyUserAttrEntity> {

    /**
     * 按策略 id 查询其全部用户属性条件，关联回填元数据字段名称/编码。
     *
     * @param policyId 策略 id
     * @return 用户属性条件联表查询行列表
     */
    List<PolicyUserAttrRow> selectByPolicyId(@Param("policyId") Long policyId);
}
