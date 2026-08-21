package cn.nihility.rbac.ssoprotocollog.mapstruct;

import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * SSO 协议调用记录实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，通过
 * {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface SsoProtocolLogConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    SsoProtocolLogConvert INSTANCE = Mappers.getMapper(SsoProtocolLogConvert.class);

    /**
     * 实体转列表行视图对象，{@code resultLabel}（调用结果中文文案）由服务层另行赋值，
     * 不参与本次转换。
     *
     * @param entity SSO 协议调用记录实体
     * @return 列表行视图对象
     */
    @Mapping(target = "resultLabel", ignore = true)
    SsoProtocolLogVO toVO(SsoProtocolLogEntity entity);

    /**
     * 实体列表批量转列表行视图对象列表。
     *
     * @param entities SSO 协议调用记录实体列表
     * @return 列表行视图对象列表
     */
    List<SsoProtocolLogVO> toVOList(List<SsoProtocolLogEntity> entities);
}
