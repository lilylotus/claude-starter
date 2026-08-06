package cn.nihility.rbac.user.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserDisplayServiceImpl} 的单元测试，覆盖空集合、非数字脏值、部分 id 不存在、
 * 正常批量四种场景（audit-fields-store-user-id design.md Decision 3）。
 */
@ExtendWith(MockitoExtension.class)
class UserDisplayServiceImplTest {

    /** 被测服务的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /**
     * 空集合输入时直接返回空 {@code Map}，不触发任何查询。
     */
    @Test
    void resolveDisplayNames_shouldReturnEmptyMap_whenInputEmpty() {
        UserDisplayServiceImpl service = new UserDisplayServiceImpl(userMapper);

        assertThat(service.resolveDisplayNames(List.of())).isEmpty();
    }

    /**
     * 非数字的脏值应被忽略，不出现在返回结果里。
     */
    @Test
    void resolveDisplayNames_shouldIgnoreNonNumericValues() {
        UserDisplayServiceImpl service = new UserDisplayServiceImpl(userMapper);

        Map<String, String> result = service.resolveDisplayNames(List.of("not-a-number", "", "  "));

        assertThat(result).isEmpty();
    }

    /**
     * 部分 id 在 {@code tab_user} 里查不到时，只有查得到的那部分出现在返回结果里。
     */
    @Test
    void resolveDisplayNames_shouldOmitMissingIds() {
        UserEntity user = UserEntity.builder().id(1L).name("张三").code("ZS0001").build();
        when(userMapper.selectByIds(any())).thenReturn(List.of(user));

        UserDisplayServiceImpl service = new UserDisplayServiceImpl(userMapper);

        Map<String, String> result = service.resolveDisplayNames(Set.of("1", "999"));

        assertThat(result).containsExactly(Map.entry("1", "张三（ZS0001）"));
    }

    /**
     * 正常批量场景下，返回结果按 {@code 姓名（账号编码）} 格式拼接，key 与输入文本一致。
     */
    @Test
    void resolveDisplayNames_shouldReturnFormattedDisplayNames() {
        UserEntity user1 = UserEntity.builder().id(1L).name("张三").code("ZS0001").build();
        UserEntity user2 = UserEntity.builder().id(2L).name("李四").code("LS0002").build();
        when(userMapper.selectByIds(any())).thenReturn(List.of(user1, user2));

        UserDisplayServiceImpl service = new UserDisplayServiceImpl(userMapper);

        Map<String, String> result = service.resolveDisplayNames(Set.of("1", "2"));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(
                Map.of("1", "张三（ZS0001）", "2", "李四（LS0002）"));
    }
}
