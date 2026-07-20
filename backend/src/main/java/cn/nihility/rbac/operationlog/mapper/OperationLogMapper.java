package cn.nihility.rbac.operationlog.mapper;

import cn.nihility.rbac.operationlog.dto.OperationLogQueryRequest;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 操作日志数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；分页查询支持按模块、
 * 资源类型、操作类型、操作人、操作时间范围等多个可选条件动态筛选，SQL 写在
 * {@code resources/mybatis/mapper/OperationLogMapper.xml} 里。
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogEntity> {

    /**
     * 按可选条件动态分页查询操作日志，按操作发起时间降序排列。
     *
     * @param page  分页参数
     * @param query 筛选参数，各字段均可选
     * @return 分页结果
     */
    IPage<OperationLogEntity> selectOperationLogPage(IPage<?> page, @Param("query") OperationLogQueryRequest query);
}
