package cn.nihility.rbac.loginlog.mapstruct;

import cn.nihility.rbac.loginlog.dto.LoginLogVO;
import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 登录日志实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface LoginLogConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    LoginLogConvert INSTANCE = Mappers.getMapper(LoginLogConvert.class);

    /**
     * 实体转列表行视图对象，{@code loginResultLabel}（登录结果中文文案）由服务层
     * 另行赋值，不参与本次转换。
     *
     * @param entity 登录日志实体
     * @return 列表行视图对象
     */
    @Mapping(target = "loginResultLabel", ignore = true)
    LoginLogVO toVO(LoginLogEntity entity);

    /**
     * 实体列表批量转列表行视图对象列表。
     *
     * @param entities 登录日志实体列表
     * @return 列表行视图对象列表
     */
    List<LoginLogVO> toVOList(List<LoginLogEntity> entities);
}
