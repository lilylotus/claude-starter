package cn.nihility.rbac.app.authconfig.mapstruct;

import cn.nihility.rbac.app.authconfig.dto.AppAuthConfigVO;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

/**
 * 应用单点登录协议配置实体与视图对象之间的 MapStruct 转换器，不接入 Spring 容器，
 * 通过 {@link #INSTANCE} 静态创建单例调用。{@code servicePatterns}（JSON 文本 -&gt; List）
 * 与 6 个只读协议接口地址由 {@code AppAuthConfigServiceImpl} 在 {@link #toVO} 之后手动
 * 回填，本转换器显式忽略这些字段（同 {@code AppConfigConvert} 对 {@code notifyParams}
 * 的既有处理方式）。
 */
@Mapper
public interface AppAuthConfigConvert {

    /** 静态单例，避免注册为 Spring bean。 */
    AppAuthConfigConvert INSTANCE = Mappers.getMapper(AppAuthConfigConvert.class);

    /**
     * 实体转视图对象，只映射 {@code authProtocol}。
     *
     * @param entity 应用单点登录协议配置实体
     * @return 应用单点登录协议配置视图对象
     */
    @Mapping(target = "servicePatterns", ignore = true)
    @Mapping(target = "loginMethods", ignore = true)
    @Mapping(target = "casLoginUrl", ignore = true)
    @Mapping(target = "casServiceValidateUrl", ignore = true)
    @Mapping(target = "casLogoutUrl", ignore = true)
    @Mapping(target = "oauthAuthorizeUrl", ignore = true)
    @Mapping(target = "oauthTokenUrl", ignore = true)
    @Mapping(target = "oauthUserInfoUrl", ignore = true)
    AppAuthConfigVO toVO(AppAuthConfigEntity entity);
}
