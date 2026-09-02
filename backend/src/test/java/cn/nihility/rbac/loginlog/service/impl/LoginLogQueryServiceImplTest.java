package cn.nihility.rbac.loginlog.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.loginlog.constant.LoginResult;
import cn.nihility.rbac.loginlog.dto.LoginLogQueryRequest;
import cn.nihility.rbac.loginlog.dto.LoginLogVO;
import cn.nihility.rbac.loginlog.entity.LoginLogEntity;
import cn.nihility.rbac.loginlog.mapper.LoginLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LoginLogQueryServiceImpl} 的单元测试，重点覆盖筛选条件按需拼接
 * （全部参数为空/按 loginAccount/按 loginResult 精确匹配）、按登录发起时间降序排列、
 * {@code loginResultLabel} 中文文案填充等分支逻辑。脱离 Spring 容器的纯单元测试里调用
 * {@code getSqlSegment()} 做条件断言（正常启动时该方法用于生成实际 SQL 片段，这里仅
 * 借助其副作用验证 wrapper 内部条件是否符合预期。手动初始化的 {@code TableInfo}
 * 必须使用与生产环境一致的驼峰转下划线配置，避免污染同一测试 JVM 的全局缓存。
 */
@ExtendWith(MockitoExtension.class)
class LoginLogQueryServiceImplTest {

    /** 被测服务的登录日志数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private LoginLogMapper loginLogMapper;

    /** 被测服务实例。 */
    private LoginLogQueryServiceImpl loginLogQueryService;

    /**
     * MyBatis-Plus 的 {@code LambdaQueryWrapper} 需要先有实体的 {@code TableInfo} 缓存才能在
     * 脱离 Spring 容器的纯单元测试里调用 {@code getSqlSegment()} 做条件断言（正常启动时该
     * 缓存由 Spring Boot 扫描 Mapper 时自动建立），这里手动触发一次初始化，仅供本测试类使用。
     */
    @BeforeAll
    static void primeLambdaColumnCache() {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "loginLogQueryServiceImplTest");
        assistant.setCurrentNamespace(LoginLogEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, LoginLogEntity.class);
    }

    /**
     * 每个用例执行前重新构造被测服务。
     */
    @BeforeEach
    void setUp() {
        loginLogQueryService = new LoginLogQueryServiceImpl(loginLogMapper);
    }

    /**
     * 全部筛选参数为空时，应返回 mapper 查询到的全部记录，且查询条件里不包含
     * loginAccount/loginResult/createTime 相关限制，仅保留按登录发起时间降序排序。
     */
    @Test
    void getPage_shouldReturnAllRecords_whenAllFiltersEmpty() {
        LoginLogEntity entity = buildEntity(1L, "admin", LoginResult.SUCCESS);
        Page<LoginLogEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(entity));
        when(loginLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        LoginLogQueryRequest request = new LoginLogQueryRequest();
        request.setPage(1);
        request.setPageSize(10);

        var pageResult = loginLogQueryService.getPage(request);

        assertThat(pageResult.getTotal()).isEqualTo(1L);
        assertThat(pageResult.getRecords()).hasSize(1);
        LoginLogVO vo = pageResult.getRecords().get(0);
        assertThat(vo.getLoginAccount()).isEqualTo("admin");
        assertThat(vo.getLoginResultLabel()).isEqualTo("成功");

        ArgumentCaptor<LambdaQueryWrapper<LoginLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(loginLogMapper).selectPage(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).doesNotContain("login_account =").doesNotContain("login_result =");
        assertThat(sqlSegment).contains("ORDER BY create_time DESC");
    }

    /**
     * 按 loginAccount 精确匹配筛选时，查询条件应包含 loginAccount 等值限制，不包含
     * loginResult 限制。
     */
    @Test
    void getPage_shouldFilterByLoginAccount_whenProvided() {
        when(loginLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        LoginLogQueryRequest request = new LoginLogQueryRequest();
        request.setLoginAccount("admin");
        request.setPage(1);
        request.setPageSize(10);

        loginLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<LoginLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(loginLogMapper).selectPage(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("login_account =");
        assertThat(sqlSegment).doesNotContain("login_result =");
    }

    /**
     * 按 loginResult 精确匹配筛选时，查询条件应包含 loginResult 等值限制，不包含
     * loginAccount 限制。
     */
    @Test
    void getPage_shouldFilterByLoginResult_whenProvided() {
        when(loginLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10, 0L));

        LoginLogQueryRequest request = new LoginLogQueryRequest();
        request.setLoginResult(LoginResult.FAILED);
        request.setPage(1);
        request.setPageSize(10);

        loginLogQueryService.getPage(request);

        ArgumentCaptor<LambdaQueryWrapper<LoginLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(loginLogMapper).selectPage(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("login_result =");
        assertThat(sqlSegment).doesNotContain("login_account =");
    }

    /**
     * 按 loginMethod 精确匹配筛选时，查询条件应包含 loginMethod 等值限制，不包含
     * loginAccount/loginResult 限制；返回行的 {@code loginMethodLabel} 应正确回填中文文案。
     */
    @Test
    void getPage_shouldFilterByLoginMethod_whenProvided() {
        LoginLogEntity entity = buildEntity(1L, "admin", LoginResult.SUCCESS);
        entity.setLoginMethod(cn.nihility.rbac.loginlog.constant.LoginMethod.SMS);
        Page<LoginLogEntity> resultPage = new Page<>(1, 10, 1L);
        resultPage.setRecords(List.of(entity));
        when(loginLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(resultPage);

        LoginLogQueryRequest request = new LoginLogQueryRequest();
        request.setLoginMethod(cn.nihility.rbac.loginlog.constant.LoginMethod.SMS);
        request.setPage(1);
        request.setPageSize(10);

        var pageResult = loginLogQueryService.getPage(request);

        assertThat(pageResult.getRecords().get(0).getLoginMethodLabel()).isEqualTo("短信验证码");

        ArgumentCaptor<LambdaQueryWrapper<LoginLogEntity>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(loginLogMapper).selectPage(any(Page.class), captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment();
        assertThat(sqlSegment).contains("login_method =");
        assertThat(sqlSegment).doesNotContain("login_account =").doesNotContain("login_result =");
    }

    /**
     * 构造一个测试用的登录日志实体。
     *
     * @param id           主键 id
     * @param loginAccount 登录账号
     * @param loginResult  登录结果
     * @return 登录日志实体
     */
    private LoginLogEntity buildEntity(long id, String loginAccount, int loginResult) {
        return LoginLogEntity.builder()
                .id(id)
                .loginAccount(loginAccount)
                .loginResult(loginResult)
                .createBy(loginAccount)
                .createTime(LocalDateTime.now())
                .updateBy(loginAccount)
                .updateTime(LocalDateTime.now())
                .build();
    }
}
