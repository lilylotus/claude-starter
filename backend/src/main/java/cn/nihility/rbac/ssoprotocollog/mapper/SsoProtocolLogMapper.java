package cn.nihility.rbac.ssoprotocollog.mapper;

import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogQueryRequest;
import cn.nihility.rbac.ssoprotocollog.dto.SsoProtocolLogVO;
import cn.nihility.rbac.ssoprotocollog.entity.SsoProtocolLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SSO 协议调用记录数据访问接口。单表 CRUD 直接复用 {@link BaseMapper}；分页查询需要关联
 * {@code tab_user}/{@code tab_app_access_policy} 取用户姓名与拒绝策略名称展示字段，SQL 写在
 * {@code resources/mybatis/mapper/SsoProtocolLogMapper.xml} 里。
 */
@Mapper
public interface SsoProtocolLogMapper extends BaseMapper<SsoProtocolLogEntity> {

    /**
     * 按可选条件动态分页查询 SSO 协议调用记录，关联查出用户姓名、拒绝策略名称，按调用发生
     * 时间降序排列。
     *
     * @param page  分页参数
     * @param query 筛选参数，各字段均可选
     * @return 分页结果
     */
    IPage<SsoProtocolLogVO> selectSsoProtocolLogPage(IPage<?> page, @Param("query") SsoProtocolLogQueryRequest query);
}
