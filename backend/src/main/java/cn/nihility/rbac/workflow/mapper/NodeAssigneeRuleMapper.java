package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.NodeAssigneeRuleEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 节点审批人规则数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface NodeAssigneeRuleMapper extends BaseMapper<NodeAssigneeRuleEntity> {
}
