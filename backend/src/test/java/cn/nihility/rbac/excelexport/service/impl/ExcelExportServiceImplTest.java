package cn.nihility.rbac.excelexport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.nihility.rbac.app.dto.AppVO;
import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelexport.constant.ExportLimits;
import cn.nihility.rbac.excelexport.support.ExportDictLabelSupport;
import cn.nihility.rbac.formfield.constant.FormFieldControlType;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.org.dto.OrgVO;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.user.dto.PositionVO;
import cn.nihility.rbac.user.dto.UserVO;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link ExcelExportServiceImpl} 的单元测试，重点覆盖四个 {@code bizType} 各自的列顺序
 * （字段定义驱动列 + 固定关联展示列的相对位置）、字典标签换算、超限拒绝生成，以及生成的
 * {@code .xlsx} 文件能被 Apache POI 正常读回、行列数与预期一致（tasks.md 3.7/3.8）。
 */
@ExtendWith(MockitoExtension.class)
class ExcelExportServiceImplTest {

    /** 被测服务的表单字段定义数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 被测服务的元数据字段数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private MetadataFieldMapper metadataFieldMapper;

    /** 被测服务的组织业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private OrgService orgService;

    /** 被测服务的用户业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private UserService userService;

    /** 被测服务的任职业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private PositionService positionService;

    /** 被测服务的应用业务逻辑依赖，使用 Mockito 打桩。 */
    @Mock
    private AppService appService;

    /** 被测服务的字典标签换算依赖，使用 Mockito 打桩。 */
    @Mock
    private ExportDictLabelSupport exportDictLabelSupport;

    /** 被测服务实例。 */
    private ExcelExportServiceImpl excelExportService;

    /**
     * 每个用例执行前重新构造被测服务；字典标签换算默认桩为原样返回入参的原始值，
     * 不涉及字典列的用例无需额外打桩。
     */
    @BeforeEach
    void setUp() {
        excelExportService = new ExcelExportServiceImpl(formFieldDefinitionMapper, metadataFieldMapper, orgService,
                userService, positionService, appService, exportDictLabelSupport);
        lenient().when(exportDictLabelSupport.resolveDisplayText(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
    }

    /**
     * 业务对象类型非法时，应拒绝生成并抛出业务异常。
     */
    @Test
    void export_shouldThrowException_whenBizTypeInvalid() {
        assertThatThrownBy(() -> excelExportService.export("INVALID")).isInstanceOf(BusinessException.class);
    }

    /**
     * 待导出记录数超过 5 万行时，应拒绝生成文件（design.md Decision 1）。
     */
    @SuppressWarnings("unchecked")
    @Test
    void export_shouldRejectOversizedRows_whenExceedsLimit() {
        List<UserVO> hugeList = mock(List.class);
        when(hugeList.size()).thenReturn(ExportLimits.MAX_ROW_COUNT + 1);
        when(userService.listAllForExport()).thenReturn(hugeList);

        assertThatThrownBy(() -> excelExportService.export("USER"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待导出数据量过大");
    }

    /**
     * 任职导出应在字段定义驱动列之前固定包含"姓名""组织"两列，并且生成的文件能被
     * Apache POI 正常读回，行列数与预期一致。
     */
    @Test
    void export_shouldPrependFixedColumns_forPosition() throws Exception {
        FormFieldDefinitionEntity definition = buildDefinition(1L, "POSITION", 10L, "任职类型",
                FormFieldControlType.TEXT, null, 1);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(definition));
        when(metadataFieldMapper.selectByIds(any()))
                .thenReturn(List.of(buildMetadata(10L, "position_type")));
        PositionVO record = PositionVO.builder()
                .id(1L)
                .userName("张三")
                .orgName("研发部")
                .positionType("primary")
                .build();
        when(positionService.listAllForExport()).thenReturn(List.of(record));

        byte[] content = excelExportService.export("POSITION");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("姓名");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("组织");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("任职类型");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("张三");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("研发部");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("primary");
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
        }
    }

    /**
     * 应用导出应在字段定义驱动列之后固定包含"负责人""所属组织"两列。
     */
    @Test
    void export_shouldAppendFixedColumns_forApp() throws Exception {
        FormFieldDefinitionEntity definition = buildDefinition(2L, "APP", 20L, "应用名称",
                FormFieldControlType.TEXT, null, 1);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(definition));
        when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(buildMetadata(20L, "name")));
        AppVO record = AppVO.builder()
                .id(1L)
                .name("测试应用")
                .ownerName("李四")
                .orgName("研发部")
                .build();
        when(appService.listAllForExport()).thenReturn(List.of(record));

        byte[] content = excelExportService.export("APP");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("应用名称");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("负责人");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("所属组织");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("测试应用");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("李四");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("研发部");
        }
    }

    /**
     * 组织、用户导出不应携带任何固定关联展示列，列集合仅由字段定义驱动。
     */
    @Test
    void export_shouldNotAppendFixedColumns_forOrg() throws Exception {
        FormFieldDefinitionEntity definition = buildDefinition(3L, "ORG", 30L, "组织名称",
                FormFieldControlType.TEXT, null, 1);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(definition));
        when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(buildMetadata(30L, "name")));
        OrgVO record = OrgVO.builder().id(1L).name("总公司").build();
        when(orgService.listAllForExport()).thenReturn(List.of(record));

        byte[] content = excelExportService.export("ORG");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat((int) header.getLastCellNum()).isEqualTo(1);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("组织名称");
        }
    }

    /**
     * 数据库列名带下划线（如 {@code id_card}）时，应转换为驼峰形式的 Java Bean 属性名
     * 再反射取值，与前端 {@code toCamelCase} 转换约定保持一致（design.md Decision 5）。
     */
    @Test
    void export_shouldConvertUnderscoreColumnNameToCamelCase() throws Exception {
        FormFieldDefinitionEntity definition = buildDefinition(4L, "USER", 40L, "身份证号",
                FormFieldControlType.TEXT, null, 1);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(definition));
        when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(buildMetadata(40L, "id_card")));
        UserVO record = UserVO.builder().id(1L).idCard("110000200001011234").build();
        when(userService.listAllForExport()).thenReturn(List.of(record));

        byte[] content = excelExportService.export("USER");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("110000200001011234");
        }
    }

    /**
     * 字典/多选字典下拉列应通过 {@link ExportDictLabelSupport} 换算为展示标签，而不是
     * 原始存储编码。
     */
    @Test
    void export_shouldResolveDictLabel_forDictColumn() throws Exception {
        FormFieldDefinitionEntity definition = buildDefinition(5L, "USER", 50L, "性别",
                FormFieldControlType.DICT, "gender", 1);
        when(formFieldDefinitionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(definition));
        when(metadataFieldMapper.selectByIds(any())).thenReturn(List.of(buildMetadata(50L, "gender")));
        UserVO record = UserVO.builder().id(1L).gender("M").build();
        when(userService.listAllForExport()).thenReturn(List.of(record));
        when(exportDictLabelSupport.resolveDisplayText(eq(FormFieldControlType.DICT), eq("gender"), eq("M")))
                .thenReturn("男");

        byte[] content = excelExportService.export("USER");

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("男");
        }
    }

    /**
     * 构造一条测试用的表单字段定义实体。
     *
     * @param id              主键 id
     * @param bizType         业务对象类型
     * @param metadataFieldId 绑定的元数据字段 id
     * @param fieldName       展示名称
     * @param controlType     控件类型
     * @param dictTypeCode    关联的字典类型编码
     * @param showOrder       显示序号
     * @return 表单字段定义实体
     */
    private FormFieldDefinitionEntity buildDefinition(long id, String bizType, long metadataFieldId,
            String fieldName, int controlType, String dictTypeCode, int showOrder) {
        return FormFieldDefinitionEntity.builder()
                .id(id)
                .bizType(bizType)
                .metadataFieldId(metadataFieldId)
                .fieldName(fieldName)
                .controlType(controlType)
                .dictTypeCode(dictTypeCode)
                .showOrder(showOrder)
                .showInExport(true)
                .status(FormFieldStatus.ENABLED)
                .build();
    }

    /**
     * 构造一条测试用的元数据字段实体，仅填充列名解析所需字段。
     *
     * @param id         主键 id
     * @param columnName 数据库列名
     * @return 元数据字段实体
     */
    private MetadataFieldEntity buildMetadata(long id, String columnName) {
        return MetadataFieldEntity.builder().id(id).columnName(columnName).build();
    }
}
