package cn.nihility.rbac.identity.upstream.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.nihility.rbac.app.sync.constant.TransformType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingRow;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@link UpstreamFieldMappingTransformer} 的单元测试，覆盖 {@code NO_TRANSFORM}/
 * {@code FIXED_VALUE}/{@code SCRIPT} 三种转换方式（tasks.md 10.1）。
 */
class UpstreamFieldMappingTransformerTest {

    /** 被测组件实例，无外部依赖，直接 new。 */
    private static final UpstreamFieldMappingTransformer TRANSFORMER = new UpstreamFieldMappingTransformer();

    /**
     * GraalVM 引擎首次加载（类加载 + 解释器初始化，在没有 JVMCI/JIT 的"fallback runtime"
     * 环境下尤其明显）实测耗时可达数百毫秒，远超脚本执行本身 200ms 的超时保护窗口，
     * 导致最先执行的几条涉及 {@code SCRIPT} 的用例被误判为"执行超时"；实测同一 JVM 内
     * 反复执行几次后耗时会降到 20ms 以内（HotSpot 分层编译预热）。这里在所有用例执行前
     * 先跑几次脚本转换预热，把首次加载/解释执行的开销从计时窗口内移出，避免用例结果
     * 受 JVM/GraalVM 冷启动速度影响而不稳定（不改变生产代码的 200ms 超时值本身——那是
     * 已有生产组件 {@code sync.transform.FieldMappingTransformer} 沿用的既定设计取值，
     * 只是本仓库此前没有任何单测真正执行过这条 GraalVM 路径，冷启动耗时问题此前未被
     * 观测到）。
     */
    @BeforeAll
    static void warmUpGraalVm() {
        UpstreamFieldMappingRow warmUpMapping = UpstreamFieldMappingRow.builder()
                .upstreamFieldCode("warmUp")
                .fieldCode("warmUp")
                .transformType(TransformType.SCRIPT)
                .transformValue("value")
                .build();
        for (int i = 0; i < 8; i++) {
            TRANSFORMER.transform(List.of(warmUpMapping), Map.of("warmUp", "warmUp"));
        }
    }

    /**
     * {@code NO_TRANSFORM} 时，目标字段应原样取上游字段编码对应的原始值。
     */
    @Test
    void transform_shouldReturnRawValue_whenNoTransform() {
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .upstreamFieldCode("orgCode")
                .fieldCode("code")
                .transformType(TransformType.NO_TRANSFORM)
                .build();
        Map<String, Object> rawRow = Map.of("orgCode", "ORG001");

        Map<String, Object> result = TRANSFORMER.transform(List.of(mapping), rawRow);

        assertThat(result).containsEntry("code", "ORG001");
    }

    /**
     * {@code FIXED_VALUE} 时，目标字段应固定取 {@code transformValue}，忽略上游原始值。
     */
    @Test
    void transform_shouldReturnFixedValue_whenFixedValue() {
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .upstreamFieldCode("orgCode")
                .fieldCode("remark")
                .transformType(TransformType.FIXED_VALUE)
                .transformValue("固定备注")
                .build();
        Map<String, Object> rawRow = Map.of("orgCode", "ORG001");

        Map<String, Object> result = TRANSFORMER.transform(List.of(mapping), rawRow);

        assertThat(result).containsEntry("remark", "固定备注");
    }

    /**
     * {@code SCRIPT} 时，目标字段应取脚本最后一个表达式的求值结果，{@code value}
     * 绑定为上游原始值。
     */
    @Test
    void transform_shouldReturnScriptResult_whenScript() {
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .upstreamFieldCode("orgCode")
                .fieldCode("code")
                .transformType(TransformType.SCRIPT)
                .transformValue("value + '_SUFFIX'")
                .build();
        Map<String, Object> rawRow = Map.of("orgCode", "ORG001");

        Map<String, Object> result = TRANSFORMER.transform(List.of(mapping), rawRow);

        assertThat(result).containsEntry("code", "ORG001_SUFFIX");
    }

    /**
     * {@code SCRIPT} 脚本执行异常时，应捕获异常并跳过该字段（结果为 {@code null}），
     * 不向上抛出异常影响其余字段转换。
     */
    @Test
    void transform_shouldReturnNull_whenScriptThrows() {
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .upstreamFieldCode("orgCode")
                .fieldCode("code")
                .transformType(TransformType.SCRIPT)
                .transformValue("value.nonExistentMethod()")
                .build();
        Map<String, Object> rawRow = Map.of("orgCode", "ORG001");

        Map<String, Object> result = TRANSFORMER.transform(List.of(mapping), rawRow);

        assertThat(result).containsEntry("code", null);
    }

    /**
     * 上游原始行中不存在对应上游字段编码的取值时，源值按 {@code null} 处理，不抛异常。
     */
    @Test
    void transform_shouldUseNullSourceValue_whenRawRowMissingKey() {
        UpstreamFieldMappingRow mapping = UpstreamFieldMappingRow.builder()
                .upstreamFieldCode("missingCode")
                .fieldCode("code")
                .transformType(TransformType.NO_TRANSFORM)
                .build();
        Map<String, Object> rawRow = Map.of("orgCode", "ORG001");

        Map<String, Object> result = TRANSFORMER.transform(List.of(mapping), rawRow);

        assertThat(result).containsEntry("code", null);
    }
}
