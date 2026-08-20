package cn.nihility.rbac.appaccess.policy.mapper;

import cn.nihility.rbac.appaccess.policy.dto.PolicyTargetAppVO;
import cn.nihility.rbac.appaccess.policy.entity.PolicyTargetAppEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 策略目标应用数据访问接口。单表 CRUD（整体替换用的批量插入/按 {@code policyId}
 * 删除）直接复用 {@link BaseMapper}；按策略 id 查询需要关联 {@code tab_app} 回填应用
 * 名称，SQL 写在 {@code resources/mybatis/mapper/PolicyTargetAppMapper.xml} 里，用单条
 * JOIN 完成。
 */
@Mapper
public interface PolicyTargetAppMapper extends BaseMapper<PolicyTargetAppEntity> {

    /**
     * 按策略 id 查询其全部目标应用，关联回填应用名称；应用若已被逻辑删除则不返回
     * （INNER JOIN 语义）。
     *
     * @param policyId 策略 id
     * @return 目标应用视图对象列表
     */
    List<PolicyTargetAppVO> selectByPolicyId(@Param("policyId") Long policyId);
}
