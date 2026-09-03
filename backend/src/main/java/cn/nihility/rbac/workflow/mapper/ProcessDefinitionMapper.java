package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.ProcessDefinitionEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程定义（不可变发布版本快照）数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface ProcessDefinitionMapper extends BaseMapper<ProcessDefinitionEntity> {
}
