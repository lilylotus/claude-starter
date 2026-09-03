package cn.nihility.rbac.workflow.mapper;

import cn.nihility.rbac.workflow.entity.ProcessModelEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程模型数据访问接口，单表 CRUD 直接复用 {@link BaseMapper}。
 */
@Mapper
public interface ProcessModelMapper extends BaseMapper<ProcessModelEntity> {
}
