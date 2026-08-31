package cn.nihility.rbac.chat.service.impl;

import cn.nihility.rbac.chat.constant.SensitiveWordStatus;
import cn.nihility.rbac.chat.entity.SensitiveWordEntity;
import cn.nihility.rbac.chat.mapper.SensitiveWordMapper;
import cn.nihility.rbac.chat.service.SensitiveWordFilterService;
import cn.nihility.rbac.chat.service.support.AhoCorasickAutomaton;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

/**
 * 敏感词过滤业务逻辑实现：{@link InitializingBean#afterPropertiesSet()} 在应用启动阶段
 * 首次加载词库；{@code automaton} 用 {@code volatile} 修饰，{@link #reload()} 重建后整体
 * 替换引用，读侧 {@link #filter} 无需加锁即可看到最新自动机（写少读多场景）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordFilterServiceImpl implements SensitiveWordFilterService, InitializingBean {

    /** 敏感词数据访问接口。 */
    private final SensitiveWordMapper sensitiveWordMapper;

    /** 当前生效的敏感词过滤自动机，启动前先给一个空自动机兜底，避免空指针。 */
    private volatile AhoCorasickAutomaton automaton = AhoCorasickAutomaton.build(List.of());

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet() {
        reload();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AhoCorasickAutomaton.FilterResult filter(String text) {
        return automaton.filter(text);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void reload() {
        List<SensitiveWordEntity> words = sensitiveWordMapper.selectList(new LambdaQueryWrapper<SensitiveWordEntity>()
                .eq(SensitiveWordEntity::getStatus, SensitiveWordStatus.ENABLED));
        List<String> wordTexts = words.stream().map(SensitiveWordEntity::getWord).toList();
        this.automaton = AhoCorasickAutomaton.build(wordTexts);
        log.info("敏感词过滤自动机已重建，当前启用词条数：{}", wordTexts.size());
    }
}
