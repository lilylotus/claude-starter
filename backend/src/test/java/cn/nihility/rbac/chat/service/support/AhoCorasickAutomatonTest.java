package cn.nihility.rbac.chat.service.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link AhoCorasickAutomaton} 的多模式串匹配与替换行为测试。 */
class AhoCorasickAutomatonTest {

    /** 命中单个词条时应整体替换为等长星号，且标记 hit = true。 */
    @Test
    void filter_shouldReplaceHitWord() {
        AhoCorasickAutomaton automaton = AhoCorasickAutomaton.build(List.of("赌博", "毒品"));

        AhoCorasickAutomaton.FilterResult result = automaton.filter("请勿参与赌博活动");

        assertThat(result.hit()).isTrue();
        assertThat(result.content()).isEqualTo("请勿参与**活动");
    }

    /** 命中多个不重叠词条时应分别替换。 */
    @Test
    void filter_shouldReplaceMultipleHitWords() {
        AhoCorasickAutomaton automaton = AhoCorasickAutomaton.build(List.of("赌博", "毒品"));

        AhoCorasickAutomaton.FilterResult result = automaton.filter("赌博与毒品都是违法行为");

        assertThat(result.hit()).isTrue();
        assertThat(result.content()).isEqualTo("**与**都是违法行为");
    }

    /** 未命中任何词条时应原样返回，hit = false。 */
    @Test
    void filter_shouldReturnOriginalWhenNoHit() {
        AhoCorasickAutomaton automaton = AhoCorasickAutomaton.build(List.of("赌博", "毒品"));

        AhoCorasickAutomaton.FilterResult result = automaton.filter("今天天气不错");

        assertThat(result.hit()).isFalse();
        assertThat(result.content()).isEqualTo("今天天气不错");
    }

    /** 空词库时不应误判任何文本命中。 */
    @Test
    void filter_shouldNotHitWhenWordListEmpty() {
        AhoCorasickAutomaton automaton = AhoCorasickAutomaton.build(List.of());

        AhoCorasickAutomaton.FilterResult result = automaton.filter("赌博毒品诈骗");

        assertThat(result.hit()).isFalse();
        assertThat(result.content()).isEqualTo("赌博毒品诈骗");
    }
}
