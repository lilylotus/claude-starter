package cn.nihility.rbac.user.service.impl;

import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.service.UserDisplayService;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * {@link UserDisplayService} 的默认实现：过滤掉空白/非数字的输入后，批量查
 * {@code tab_user} 拼出 {@code 姓名（账号编码）} 形式的展示名。
 */
@Service
@RequiredArgsConstructor
public class UserDisplayServiceImpl implements UserDisplayService {

    /** 用户数据访问接口。 */
    private final UserMapper userMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> resolveDisplayNames(Collection<String> userIdTexts) {
        if (userIdTexts == null || userIdTexts.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> parsedByText = new HashMap<>();
        for (String text : userIdTexts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            try {
                parsedByText.put(text, Long.valueOf(text.trim()));
            } catch (NumberFormatException ignored) {
                // 非数字内容（历史脏数据）直接忽略，调用方按"未知用户"展示
            }
        }
        if (parsedByText.isEmpty()) {
            return Map.of();
        }

        List<UserEntity> users = userMapper.selectByIds(parsedByText.values());
        Map<Long, String> displayNameById = new HashMap<>();
        for (UserEntity user : users) {
            displayNameById.put(user.getId(), user.getName() + "（" + user.getCode() + "）");
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Long> entry : parsedByText.entrySet()) {
            String displayName = displayNameById.get(entry.getValue());
            if (displayName != null) {
                result.put(entry.getKey(), displayName);
            }
        }
        return result;
    }
}
