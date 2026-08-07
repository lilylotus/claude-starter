package cn.nihility.rbac.app.mapstruct;

import cn.nihility.rbac.app.dto.AppConfigVO;
import cn.nihility.rbac.app.entity.AppConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 应用对外接口配置实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。
 */
@Mapper
public interface AppConfigConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppConfigConvert INSTANCE = Mappers.getMapper(AppConfigConvert.class);

    /**
     * 实体转视图对象：实体的 {@code appId}（映射自 {@code open_app_id} 列）按同名属性
     * 直接复制到视图对象的 {@code appId}；实体的内部外键字段 {@code appRefId} 与敏感字段
     * {@code secretKey} 在视图对象上均无对应属性，MapStruct 不会生成任何转换代码，天然不会
     * 泄露。{@code notifyParams} 在实体上是原始 JSON 文本、在视图对象上是 {@code Map<String,
     * String>}，类型不同 MapStruct 无法直接映射，显式忽略，调用方（{@code AppConfigServiceImpl}）
     * 转换后自行解析 JSON 并回填。
     *
     * @param entity 应用对外接口配置实体
     * @return 应用对外接口配置视图对象
     */
    @Mapping(target = "notifyParams", ignore = true)
    AppConfigVO toVO(AppConfigEntity entity);
}
