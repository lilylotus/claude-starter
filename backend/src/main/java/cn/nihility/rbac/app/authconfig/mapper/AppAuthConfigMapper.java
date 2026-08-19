package cn.nihility.rbac.app.authconfig.mapper;

import cn.nihility.rbac.app.authconfig.dto.AppProtocolInfo;
import cn.nihility.rbac.app.authconfig.entity.AppAuthConfigEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用单点登录协议配置数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；联表查询（登出
 * 通知回调地址 + 对外接口凭证签名参数）走自定义方法 + {@code AppAuthConfigMapper.xml}
 * （对齐 {@code AppUserinfoFieldMappingMapper} 的既有约定：多表 JOIN 查询写在 MyBatis XML）。
 */
@Mapper
public interface AppAuthConfigMapper extends BaseMapper<AppAuthConfigEntity> {

    /**
     * 查询全部已启用单点登录协议（{@code auth_protocol != NONE}）的应用，联表
     * {@code tab_app_config} 取登出通知回调地址与对外接口凭证签名参数，供
     * {@code SsoLogoutNotifyService} 登出时按 {@code appId} 匹配回调目标（add-sso-single-logout
     * change tasks.md 3.1）。
     *
     * @return 已启用单点登录协议的应用列表
     */
    List<AppProtocolInfo> selectActiveProtocolApps();
}
