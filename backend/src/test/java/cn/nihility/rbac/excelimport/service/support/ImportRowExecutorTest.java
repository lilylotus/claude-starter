package cn.nihility.rbac.excelimport.service.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.dto.AppCreateRequest;
import cn.nihility.rbac.app.mapper.AppMapper;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.operationlog.constant.OperationSource;
import cn.nihility.rbac.operationlog.context.OperationSourceContext;
import cn.nihility.rbac.org.constant.OrgStatus;
import cn.nihility.rbac.org.dto.OrgCreateRequest;
import cn.nihility.rbac.org.dto.OrgUpdateRequest;
import cn.nihility.rbac.org.entity.OrgEntity;
import cn.nihility.rbac.org.mapper.OrgMapper;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.user.constant.UserStatus;
import cn.nihility.rbac.user.dto.PositionCreateRequest;
import cn.nihility.rbac.user.dto.PositionUpdateRequest;
import cn.nihility.rbac.user.entity.UserEntity;
import cn.nihility.rbac.user.entity.UserPositionEntity;
import cn.nihility.rbac.user.mapper.UserMapper;
import cn.nihility.rbac.user.mapper.UserPositionMapper;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ImportRowExecutor} 的单元测试，重点覆盖：导入字段配置标记为必填的列缺失时
 * 拒绝该行、按主键列组成的复合键匹配零/一/多条记录时分别走新增/更新/失败流程、
 * 手动触发的 Bean Validation 对未提供必填业务字段的拦截、POSITION 的人员编号/组织
 * 编码两列解析失败时的行级失败提示。业务模块自身的 create/update 校验逻辑（各模块
 * 已有单测覆盖）不在此重复验证，此处仅打桩返回值/验证调用发生。
 */
@ExtendWith(MockitoExtension.class)
class ImportRowExecutorTest {

    /** 被测组件的组织数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgMapper orgMapper;

    /** 被测组件的组织业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgService orgService;

    /** 被测组件的用户数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserMapper userMapper;

    /** 被测组件的用户业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private UserService userService;

    /** 被测组件的应用数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private AppMapper appMapper;

    /** 被测组件的应用业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private AppService appService;

    /** 被测组件的任职数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private UserPositionMapper userPositionMapper;

    /** 被测组件的任职业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private PositionService positionService;

    /** 被测组件实例，Bean Validation 使用真实的默认校验器（不打桩）。 */
    private ImportRowExecutor importRowExecutor;

    /**
     * 每个用例执行前重新构造被测组件，使用真实的 {@link Validator} 以覆盖 DTO 上
     * 声明的 {@code @NotBlank}/{@code @Pattern} 等校验注解的实际生效效果。
     */
    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        importRowExecutor = new ImportRowExecutor(validator, orgMapper, orgService, userMapper, userService,
                appMapper, appService, userPositionMapper, positionService);
    }

    /**
     * 每个用例执行后清理线程绑定的操作来源标记，避免影响后续用例。
     */
    @AfterEach
    void tearDown() {
        OperationSourceContext.clear();
    }

    /**
     * 导入字段配置中标记为必填的列在本行取值为空时，应拒绝该行，不触及任何 Mapper/
     * Service 调用。
     */
    @Test
    void processRow_shouldThrowException_whenRequiredColumnMissing() {
        List<ImportFieldConfigVO> configs = List.of(buildConfig("code", "组织编码", true));
        Map<String, String> rowValues = Map.of();

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织编码");
    }

    /**
     * 组织编码在未删除的组织中不存在匹配记录、上级组织编码列填写字面值 {@code "0"}
     * （表示顶级组织）时，应走新增流程，调用组织模块既有的 create 方法，
     * {@code parentId} 按字面值 {@code "0"} 直接置为 0。
     */
    @Test
    void processRow_shouldCreateOrg_whenNoMatch() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of("code", "ORG001", "name", "测试组织", "__parentCode", "0");

        importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs);

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("ORG001");
        assertThat(captor.getValue().getName()).isEqualTo("测试组织");
        assertThat(captor.getValue().getParentId()).isEqualTo(0L);
    }

    /**
     * 组织编码匹配到一条已存在的未删除组织、上级组织编码列填写字面值 {@code "0"}
     * （表示顶级组织）时，应走更新流程，调用组织模块既有的 update 方法，
     * {@code parentId} 同样按字面值 {@code "0"} 直接置为 0。
     */
    @Test
    void processRow_shouldUpdateOrg_whenOneMatch() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        OrgEntity existing = OrgEntity.builder().id(5L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any())).thenReturn(List.of(existing));
        Map<String, String> rowValues = Map.of(
                "code", "ORG001", "name", "测试组织（更新）", "__parentCode", "0");

        importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs);

        ArgumentCaptor<OrgUpdateRequest> captor = ArgumentCaptor.forClass(OrgUpdateRequest.class);
        verify(orgService).update(eq(5L), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("测试组织（更新）");
        assertThat(captor.getValue().getParentId()).isEqualTo(0L);
    }

    /**
     * 上级组织编码列缺失/为空白字符串时，属于必填列未提供，应在
     * {@code checkRequiredColumns} 阶段即判定该行失败，不进入 {@code processOrg} 的
     * 业务逻辑，不触及 orgService 的 create/update 调用。
     */
    @Test
    void processRow_shouldFailOrg_whenParentCodeMissing() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        Map<String, String> rowValues = Map.of("code", "ORG001", "name", "测试组织", "__parentCode", "  ");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("字段[上级组织编码]不能为空");

        verifyNoInteractions(orgMapper, orgService);
    }

    /**
     * 组织编码匹配到多条已存在的未删除组织时，应判定该行失败，不调用 create/update。
     */
    @Test
    void processRow_shouldFailOrg_whenMultipleMatch() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        OrgEntity match1 = OrgEntity.builder().id(5L).code("ORG001").status(OrgStatus.ENABLED).build();
        OrgEntity match2 = OrgEntity.builder().id(6L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any())).thenReturn(List.of(match1, match2));
        Map<String, String> rowValues = Map.of("code", "ORG001", "name", "测试组织", "__parentCode", "0");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多条");
    }

    /**
     * 组织行数据未提供组织名称（DTO 上 {@code @NotBlank} 校验的业务字段）时，手动
     * 触发的 Bean Validation 应拦截该行，不调用 create。此校验独立于导入字段配置
     * 自身的必填开关，验证既有业务校验规则确实被复用。
     */
    @Test
    void processRow_shouldFailOrg_whenBeanValidationViolated() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of("code", "ORG001", "__parentCode", "0");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织名称不能为空");

        verifyNoInteractions(orgService);
    }

    /**
     * 上级组织编码列提供了合法取值且能匹配到一条已存在的未删除组织时，新增组织应
     * 成功，{@code parentId} 取自匹配结果，而不是 Excel 中并不存在的原始文本。
     */
    @Test
    void processRow_shouldCreateOrg_whenParentCodeMatched() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        OrgEntity parent = OrgEntity.builder().id(9L).code("ROOT").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any()))
                .thenReturn(List.of(parent))
                .thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "code", "ORG001", "name", "测试组织", "__parentCode", "ROOT");

        importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs);

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getParentId()).isEqualTo(9L);
    }

    /**
     * 上级组织编码列提供了取值但在未删除的组织中不存在匹配记录时，应判定该行失败，
     * 明确提示是上级组织编码无法匹配，不触及 orgService 的 create/update 调用。
     */
    @Test
    void processRow_shouldFailOrg_whenParentCodeNotFound() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "code", "ORG001", "name", "测试组织", "__parentCode", "NOT_EXIST");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上级组织编码")
                .hasMessageContaining("无法匹配");

        verifyNoInteractions(orgService);
    }

    /**
     * 负责人编号列取值在未删除的用户中不存在匹配记录时，应判定该行失败，明确提示是
     * 负责人编号无法匹配，不触及组织匹配与应用记录查询（APP 的 {@code ownerId}/
     * {@code orgId} 不在表单字段定义体系内，比照 POSITION 的做法通过
     * {@code __ownerCode}/{@code __orgCode} 两个固定伪字段解析，见 design.md 决策 2）。
     */
    @Test
    void processRow_shouldFailApp_whenOwnerCodeNotFound() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("code", "应用编码", true),
                buildConfig("__ownerCode", "负责人编号", true),
                buildConfig("__orgCode", "组织编码", true));
        when(userMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "code", "APP001", "name", "测试应用", "__ownerCode", "U001", "__orgCode", "ORG001");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.APP, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("负责人编号")
                .hasMessageContaining("无法匹配");

        verifyNoInteractions(appMapper, appService);
    }

    /**
     * 负责人编号匹配成功但组织编码列取值在未删除的组织中不存在匹配记录时，应判定该行
     * 失败，明确提示是组织编码无法匹配。
     */
    @Test
    void processRow_shouldFailApp_whenOrgCodeNotFound() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("code", "应用编码", true),
                buildConfig("__ownerCode", "负责人编号", true),
                buildConfig("__orgCode", "组织编码", true));
        UserEntity owner = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(List.of(owner));
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "code", "APP001", "name", "测试应用", "__ownerCode", "U001", "__orgCode", "ORG001");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.APP, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织编码")
                .hasMessageContaining("无法匹配");

        verifyNoInteractions(appMapper, appService);
    }

    /**
     * 负责人编号、组织编码均解析成功且应用编码未匹配到已有记录时，应走新增流程，
     * {@code ownerId}/{@code orgId} 取自解析结果，而不是 Excel 中并不存在的原始文本。
     */
    @Test
    void processRow_shouldCreateApp_whenNoMatch() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("code", "应用编码", true),
                buildConfig("__ownerCode", "负责人编号", true),
                buildConfig("__orgCode", "组织编码", true));
        UserEntity owner = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(List.of(owner));
        when(orgMapper.selectList(any())).thenReturn(List.of(org));
        when(appMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "code", "APP001", "name", "测试应用", "__ownerCode", "U001", "__orgCode", "ORG001");

        importRowExecutor.processRow(FormFieldBizType.APP, rowValues, configs);

        ArgumentCaptor<AppCreateRequest> captor = ArgumentCaptor.forClass(AppCreateRequest.class);
        verify(appService).create(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo(1L);
        assertThat(captor.getValue().getOrgId()).isEqualTo(2L);
    }

    /**
     * 数值类型属性（{@code showOrder}）在导入字段配置中标记为非必填、Excel 对应单元格
     * 留空时，新增流程应正常成功，落地的 {@code showOrder} 为请求对象自身声明的 Java
     * 默认值 {@code 0}，不再触发该属性上独立于配置之外的硬编码 {@code @NotNull}
     * （design.md 决策 2）。
     */
    @Test
    void processRow_shouldCreateOrg_whenShowOrderBlank_andNotRequired() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("code", "组织编码", true),
                buildConfig("__parentCode", "上级组织编码", true),
                buildConfig("showOrder", "显示序号", false));
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "code", "ORG001", "name", "测试组织", "__parentCode", "0", "showOrder", "");

        importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs);

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getShowOrder()).isEqualTo(0);
    }

    /**
     * 字符串类型属性（{@code remark}）留空时仍然显式设置为空字符串，既有"导入 = 整行
     * 覆盖"的语义不受本次数值类型修复影响（design.md 决策 2 明确不改变字符串字段的
     * 既有行为）。
     */
    @Test
    void processRow_shouldSetRemarkEmpty_whenBlank() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("code", "组织编码", true),
                buildConfig("__parentCode", "上级组织编码", true),
                buildConfig("remark", "备注", false));
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "code", "ORG001", "name", "测试组织", "__parentCode", "0", "remark", "");

        importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs);

        ArgumentCaptor<OrgCreateRequest> captor = ArgumentCaptor.forClass(OrgCreateRequest.class);
        verify(orgService).create(captor.capture());
        assertThat(captor.getValue().getRemark()).isEqualTo("");
    }

    /**
     * 人员编号列取值在未删除的用户中不存在匹配记录时，应判定该行失败，明确提示是
     * 人员编号无法匹配，不触及组织匹配与任职记录查询。
     */
    @Test
    void processRow_shouldFailPosition_whenUserCodeNotFound() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("__userCode", "人员编号", true),
                buildConfig("__orgCode", "组织编码", true));
        when(userMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of("__userCode", "U001", "__orgCode", "ORG001");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.POSITION, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("人员编号")
                .hasMessageContaining("无法匹配");

        verifyNoInteractions(orgMapper, positionService);
    }

    /**
     * 人员编号匹配成功但组织编码列取值在未删除的组织中不存在匹配记录时，应判定该行
     * 失败，明确提示是组织编码无法匹配。
     */
    @Test
    void processRow_shouldFailPosition_whenOrgCodeNotFound() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("__userCode", "人员编号", true),
                buildConfig("__orgCode", "组织编码", true));
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of("__userCode", "U001", "__orgCode", "ORG001");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.POSITION, rowValues, configs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织编码")
                .hasMessageContaining("无法匹配");

        verifyNoInteractions(positionService);
    }

    /**
     * 人员编号、组织编码均匹配成功且复合键未命中已有任职记录时，应走新增流程，
     * {@code userId}/{@code orgId} 取自解析结果，而不是 Excel 中并不存在的原始文本。
     */
    @Test
    void processRow_shouldCreatePosition_whenNoMatch() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("__userCode", "人员编号", true),
                buildConfig("__orgCode", "组织编码", true),
                buildConfig("positionType", "任职类型", true));
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(orgMapper.selectList(any())).thenReturn(List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of(
                "__userCode", "U001", "__orgCode", "ORG001", "positionType", "primary");

        importRowExecutor.processRow(FormFieldBizType.POSITION, rowValues, configs);

        ArgumentCaptor<PositionCreateRequest> captor = ArgumentCaptor.forClass(PositionCreateRequest.class);
        verify(positionService).create(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getOrgId()).isEqualTo(2L);
        assertThat(captor.getValue().getPositionType()).isEqualTo("primary");
    }

    /**
     * 复合键（userId+orgId+positionType）命中一条已有任职记录时，应走更新流程。
     */
    @Test
    void processRow_shouldUpdatePosition_whenOneMatch() {
        List<ImportFieldConfigVO> configs = List.of(
                buildConfig("__userCode", "人员编号", true),
                buildConfig("__orgCode", "组织编码", true),
                buildConfig("positionType", "任职类型", true));
        UserEntity user = UserEntity.builder().id(1L).code("U001").status(UserStatus.ENABLED).build();
        OrgEntity org = OrgEntity.builder().id(2L).code("ORG001").status(OrgStatus.ENABLED).build();
        UserPositionEntity existing = UserPositionEntity.builder().id(9L).userId(1L).orgId(2L)
                .positionType("primary").build();
        when(userMapper.selectList(any())).thenReturn(List.of(user));
        when(orgMapper.selectList(any())).thenReturn(List.of(org));
        when(userPositionMapper.selectList(any())).thenReturn(List.of(existing));
        Map<String, String> rowValues = Map.of(
                "__userCode", "U001", "__orgCode", "ORG001", "positionType", "primary");

        importRowExecutor.processRow(FormFieldBizType.POSITION, rowValues, configs);

        ArgumentCaptor<PositionUpdateRequest> captor = ArgumentCaptor.forClass(PositionUpdateRequest.class);
        verify(positionService).update(eq(9L), captor.capture());
        assertThat(captor.getValue().getOrgId()).isEqualTo(2L);
        assertThat(captor.getValue().getPositionType()).isEqualTo("primary");
    }

    /**
     * 处理期间（真正调用组织模块 create/update 的窗口内）应能观察到线程级操作来源
     * 标记已被置为 {@link OperationSource#IMPORT}，处理成功返回后标记应被清除，
     * 恢复为默认值 {@link OperationSource#MANUAL}（design.md 决策 3）。
     */
    @Test
    void processRow_shouldMarkImportSourceDuringProcessing_andClearAfterSuccess() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        when(orgMapper.selectList(any())).thenReturn(List.of());
        Map<String, String> rowValues = Map.of("code", "ORG001", "name", "测试组织", "__parentCode", "0");

        int[] capturedSourceDuringProcessing = {-1};
        org.mockito.Mockito.doAnswer(invocation -> {
            capturedSourceDuringProcessing[0] = OperationSourceContext.currentOrDefault();
            return null;
        }).when(orgService).create(any());

        assertThat(OperationSourceContext.currentOrDefault()).isEqualTo(OperationSource.MANUAL);

        importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs);

        assertThat(capturedSourceDuringProcessing[0]).isEqualTo(OperationSource.IMPORT);
        assertThat(OperationSourceContext.currentOrDefault()).isEqualTo(OperationSource.MANUAL);
    }

    /**
     * 处理过程中抛出异常（如组织编码匹配到多条已存在记录）时，{@code finally} 块仍应
     * 清除线程级操作来源标记，恢复为默认值，避免线程复用时误继承标记
     * （design.md 决策 3）。
     */
    @Test
    void processRow_shouldClearImportSource_whenProcessingThrows() {
        List<ImportFieldConfigVO> configs = orgConfigs();
        OrgEntity match1 = OrgEntity.builder().id(5L).code("ORG001").status(OrgStatus.ENABLED).build();
        OrgEntity match2 = OrgEntity.builder().id(6L).code("ORG001").status(OrgStatus.ENABLED).build();
        when(orgMapper.selectList(any())).thenReturn(List.of(match1, match2));
        Map<String, String> rowValues = Map.of("code", "ORG001", "name", "测试组织", "__parentCode", "0");

        assertThatThrownBy(() -> importRowExecutor.processRow(FormFieldBizType.ORG, rowValues, configs))
                .isInstanceOf(BusinessException.class);

        assertThat(OperationSourceContext.currentOrDefault()).isEqualTo(OperationSource.MANUAL);
    }

    /**
     * 构造 ORG 场景下的通用导入字段配置列表：组织编码（主键、必填）+ 上级组织编码
     * （伪字段，必填，顶级组织需显式填写字面值 {@code "0"}），供 ORG 相关用例复用，
     * 避免遗漏 {@code __parentCode} 列导致回归判断不准确。
     *
     * @return ORG 场景下的导入字段配置列表
     */
    private List<ImportFieldConfigVO> orgConfigs() {
        return List.of(
                buildConfig("code", "组织编码", true),
                buildConfig("__parentCode", "上级组织编码", true));
    }

    /**
     * 构造一条测试用的导入字段配置视图对象。
     *
     * @param fieldCode  字段标识
     * @param headerName Excel 表头文字
     * @param required   是否必填
     * @return 导入字段配置视图对象
     */
    private ImportFieldConfigVO buildConfig(String fieldCode, String headerName, boolean required) {
        return ImportFieldConfigVO.builder()
                .fieldCode(fieldCode)
                .excelHeaderName(headerName)
                .isPrimaryKey(true)
                .isRequired(required)
                .showOrder(0)
                .build();
    }
}
