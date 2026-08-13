package cn.nihility.rbac.identity.upstream.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.identity.upstream.constant.UpstreamDataType;
import cn.nihility.rbac.identity.upstream.dto.UpstreamFieldMappingSaveRequest;
import cn.nihility.rbac.identity.upstream.entity.UpstreamSourceEntity;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamFieldMappingMapper;
import cn.nihility.rbac.identity.upstream.mapper.UpstreamSourceMapper;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UpstreamFieldMappingServiceImpl#replace} 的单元测试，覆盖
 * upstream-field-mapping-primary-key change 新增的"非空列表未标记主键被拒绝保存"
 * 校验规则（tasks.md 6.1）。
 */
@ExtendWith(MockitoExtension.class)
class UpstreamFieldMappingServiceImplTest {

    /** 被测组件的上游字段映射数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamFieldMappingMapper upstreamFieldMappingMapper;

    /** 被测组件的上游数据源数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UpstreamSourceMapper upstreamSourceMapper;

    /** 被测组件的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测组件的当前登录操作人解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测组件实例。 */
    private UpstreamFieldMappingServiceImpl upstreamFieldMappingService;

    /**
     * 每个用例执行前重新构造被测组件，并打桩数据源存在性校验（多数用例都会走到该
     * 校验之后才触发主键标识校验）。
     */
    @BeforeEach
    void setUp() {
        upstreamFieldMappingService = new UpstreamFieldMappingServiceImpl(upstreamFieldMappingMapper,
                upstreamSourceMapper, metadataFieldMapper, currentOperatorService);
        when(upstreamSourceMapper.selectById(any())).thenReturn(UpstreamSourceEntity.builder().id(1L).build());
    }

    /**
     * 提交的字段映射列表非空，但所有记录的"主键标识"均为 {@code false}（含未设置，即
     * {@code null}）时，应拒绝保存并给出明确提示。
     */
    @Test
    void replace_shouldReject_whenNonEmptyListHasNoPrimaryKey() {
        UpstreamFieldMappingSaveRequest request = new UpstreamFieldMappingSaveRequest();
        request.setUpstreamFieldName("组织编码");
        request.setUpstreamFieldCode("orgCode");
        request.setMetadataFieldId(100L);
        request.setIsPrimaryKey(false);
        request.setTransformType("NO_TRANSFORM");

        assertThatThrownBy(() -> upstreamFieldMappingService.replace(1L, UpstreamDataType.ORG, List.of(request)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("主键标识");
    }

    /**
     * 提交的字段映射列表为空时，不校验主键标识（不会抛出"至少标记一个主键"的异常），
     * 直接走到删除既有映射行这一步（这里通过不抛出主键相关异常间接验证）。
     */
    @Test
    void replace_shouldNotRejectForPrimaryKey_whenListEmpty() {
        assertThatThrownBy(() -> upstreamFieldMappingService.replace(1L, UpstreamDataType.ORG, List.of()))
                .isNull();
    }
}
