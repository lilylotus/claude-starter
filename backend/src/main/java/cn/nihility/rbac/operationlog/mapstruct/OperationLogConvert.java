package cn.nihility.rbac.operationlog.mapstruct;

import cn.nihility.rbac.operationlog.dto.OperationLogDetailVO;
import cn.nihility.rbac.operationlog.dto.OperationLogVO;
import cn.nihility.rbac.operationlog.entity.OperationLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 操作日志实体与各类视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。{@code operationTypeLabel}/
 * {@code operateSourceLabel} 分别由 {@code OperationType.label(int)}/
 * {@code OperationSource.label(int)} 计算，{@code changeDetail} 由持久化的 JSON
 * 字符串反序列化得到，均不在本转换器内处理，转换后由调用方另行赋值；
 * {@code operateSource} 字段名在 entity/VO 间完全一致，按同名字段默认映射。
 */
@Mapper
public interface OperationLogConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    OperationLogConvert INSTANCE = Mappers.getMapper(OperationLogConvert.class);

    /**
     * 实体转列表行视图对象，{@code operationTypeLabel}/{@code operateSourceLabel}/
     * {@code changeDetail} 由调用方另行赋值。
     *
     * @param entity 操作日志实体
     * @return 列表行视图对象
     */
    @Mapping(target = "operationTypeLabel", ignore = true)
    @Mapping(target = "operateSourceLabel", ignore = true)
    @Mapping(target = "changeDetail", ignore = true)
    OperationLogVO toVO(OperationLogEntity entity);

    /**
     * 实体列表批量转列表行视图对象列表。
     *
     * @param entities 操作日志实体列表
     * @return 列表行视图对象列表
     */
    List<OperationLogVO> toVOList(List<OperationLogEntity> entities);

    /**
     * 实体转详情视图对象，{@code operationTypeLabel}/{@code operateSourceLabel}/
     * {@code changeDetail} 由调用方另行赋值。
     *
     * @param entity 操作日志实体
     * @return 详情视图对象
     */
    @Mapping(target = "operationTypeLabel", ignore = true)
    @Mapping(target = "operateSourceLabel", ignore = true)
    @Mapping(target = "changeDetail", ignore = true)
    OperationLogDetailVO toDetailVO(OperationLogEntity entity);
}
