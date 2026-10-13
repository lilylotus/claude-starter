package cn.nihility.rbac.abac.mapper;

import cn.nihility.rbac.abac.dto.AbacPolicyUserAttrRow;
import cn.nihility.rbac.abac.entity.AbacPolicyUserAttrEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ABAC 策略用户属性条件数据访问接口。单表 CRUD（整体替换用的批量插入/按
 * {@code policyId} 删除、运行时判定按一组 {@code policyId} 批量查询）直接复用
 * {@link BaseMapper}；管理端详情展示需要关联 {@code tab_metadata_field} 回填字段名称/
 * 编码，SQL 写在 {@code resources/mybatis/mapper/AbacPolicyUserAttrMapper.xml} 里，用
 * 单条 JOIN 完成。
 */
@Mapper
public interface AbacPolicyUserAttrMapper extends BaseMapper<AbacPolicyUserAttrEntity> {

    /**
     * 按策略 id 查询其全部用户属性条件，关联回填元数据字段名称/编码，供管理端详情展示
     * 使用。
     *
     * @param policyId 策略 id
     * @return 用户属性条件联表查询行列表
     */
    List<AbacPolicyUserAttrRow> selectByPolicyId(@Param("policyId") Long policyId);
}
