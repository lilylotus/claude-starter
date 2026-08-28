package cn.nihility.rbac.excelexport.service.impl;

import cn.nihility.rbac.app.service.AppService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelexport.constant.ExportLimits;
import cn.nihility.rbac.excelexport.service.ExcelExportService;
import cn.nihility.rbac.excelexport.support.ExportColumn;
import cn.nihility.rbac.excelexport.support.ExportDictLabelSupport;
import cn.nihility.rbac.excelimport.constant.ImportBizTypes;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import cn.nihility.rbac.formfield.constant.FormFieldStatus;
import cn.nihility.rbac.formfield.entity.FormFieldDefinitionEntity;
import cn.nihility.rbac.formfield.mapper.FormFieldDefinitionMapper;
import cn.nihility.rbac.metadata.entity.MetadataFieldEntity;
import cn.nihility.rbac.metadata.mapper.MetadataFieldMapper;
import cn.nihility.rbac.org.service.OrgService;
import cn.nihility.rbac.user.service.PositionService;
import cn.nihility.rbac.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

/**
 * Excel 导出业务逻辑实现，基于 Apache POI 的 {@link SXSSFWorkbook} 流式写入生成
 * {@code .xlsx} 文件（design.md Decision 7）。导出列由字段定义驱动列 +
 * 任职/应用固定关联展示列拼接而成（design.md Decision 4），列取值统一复用各业务
 * 模块已有的列表 VO，按 {@code columnName} 反射读取（design.md Decision 5）。
 */
@Service
@RequiredArgsConstructor
public class ExcelExportServiceImpl implements ExcelExportService {

    /** 导出工作表名称。 */
    private static final String SHEET_NAME = "导出数据";

    /** POI 流式写入时内存中保留的最大行数，超出后刷盘，控制内存占用。 */
    private static final int SXSSF_WINDOW_SIZE = 200;

    /** 表单字段定义数据访问接口，直接跨模块注入查询导出列的驱动数据。 */
    private final FormFieldDefinitionMapper formFieldDefinitionMapper;

    /** 元数据字段数据访问接口，直接跨模块注入，用于解析字段定义绑定的数据库列名。 */
    private final MetadataFieldMapper metadataFieldMapper;

    /** 组织业务逻辑接口，用于查询组织导出数据。 */
    private final OrgService orgService;

    /** 用户业务逻辑接口，用于查询用户导出数据。 */
    private final UserService userService;

    /** 任职业务逻辑接口，用于查询任职导出数据。 */
    private final PositionService positionService;

    /** 应用业务逻辑接口，用于查询应用导出数据。 */
    private final AppService appService;

    /** 字典/多选字典列展示标签换算支持组件。 */
    private final ExportDictLabelSupport exportDictLabelSupport;

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] export(String bizType) {
        if (!ImportBizTypes.isValid(bizType)) {
            throw new BusinessException("不支持的业务对象类型：" + bizType);
        }

        List<?> rows = resolveRows(bizType);
        if (rows.size() > ExportLimits.MAX_ROW_COUNT) {
            throw new BusinessException("待导出数据量过大（超过 5 万行），请缩小管辖范围后再试");
        }

        List<ExportColumn> columns = buildColumns(bizType);
        return writeWorkbook(columns, rows);
    }

    /**
     * 按业务对象类型分发到各自业务模块的导出用查询方法。
     *
     * @param bizType 业务对象类型
     * @return 待导出的行数据列表
     */
    private List<?> resolveRows(String bizType) {
        return switch (bizType) {
            case FormFieldBizType.ORG -> orgService.listAllForExport();
            case FormFieldBizType.USER -> userService.listAllForExport();
            case FormFieldBizType.POSITION -> positionService.listAllForExport();
            case FormFieldBizType.APP -> appService.listAllForExport();
            default -> throw new BusinessException("不支持的业务对象类型：" + bizType);
        };
    }

    /**
     * 构造导出列集合：字段定义驱动列（状态启用且 {@code showInExport=true}，按
     * {@code showOrder} 升序）+ 任职/应用固定关联展示列（design.md Decision 4）。
     *
     * @param bizType 业务对象类型
     * @return 导出列定义列表，顺序即导出表头顺序
     */
    private List<ExportColumn> buildColumns(String bizType) {
        List<FormFieldDefinitionEntity> definitions = formFieldDefinitionMapper.selectList(
                new LambdaQueryWrapper<FormFieldDefinitionEntity>()
                        .eq(FormFieldDefinitionEntity::getBizType, bizType)
                        .eq(FormFieldDefinitionEntity::getStatus, FormFieldStatus.ENABLED)
                        .eq(FormFieldDefinitionEntity::getShowInExport, true)
                        .orderByAsc(FormFieldDefinitionEntity::getShowOrder)
                        .orderByAsc(FormFieldDefinitionEntity::getId));
        Map<Long, MetadataFieldEntity> metadataMap = fetchMetadataMap(definitions);

        List<ExportColumn> columns = new ArrayList<>();
        if (FormFieldBizType.POSITION.equals(bizType)) {
            columns.add(ExportColumn.fixed("姓名", "userName"));
            columns.add(ExportColumn.fixed("组织", "orgName"));
        }
        for (FormFieldDefinitionEntity definition : definitions) {
            MetadataFieldEntity metadata = metadataMap.get(definition.getMetadataFieldId());
            if (metadata == null) {
                continue;
            }
            String propertyName = toCamelCase(metadata.getColumnName());
            columns.add(ExportColumn.dynamic(definition.getFieldName(), propertyName,
                    definition.getControlType(), definition.getDictTypeCode()));
        }
        if (FormFieldBizType.APP.equals(bizType)) {
            columns.add(ExportColumn.fixed("负责人", "ownerName"));
            columns.add(ExportColumn.fixed("所属组织", "orgName"));
        }
        return columns;
    }

    /**
     * 按绑定的元数据字段 id 批量查询元数据字段实体，key 为元数据字段 id，避免逐条查询。
     *
     * @param definitions 字段定义驱动列的实体列表
     * @return 元数据字段 id -&gt; 元数据字段实体
     */
    private Map<Long, MetadataFieldEntity> fetchMetadataMap(List<FormFieldDefinitionEntity> definitions) {
        List<Long> ids = definitions.stream()
                .map(FormFieldDefinitionEntity::getMetadataFieldId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<MetadataFieldEntity> metadataList = metadataFieldMapper.selectByIds(ids);
        return metadataList.stream()
                .collect(Collectors.toMap(MetadataFieldEntity::getId, entity -> entity, (left, right) -> left));
    }

    /**
     * 把元数据字段登记的数据库列名（下划线分隔，如 {@code show_order}/{@code id_card}）
     * 转换为 Java Bean 属性名（驼峰形式，如 {@code showOrder}/{@code idCard}），与前端
     * {@code useDynamicFormFields.ts} 的 {@code toCamelCase} 转换约定保持一致
     * （design.md Decision 5）：组织/用户/任职/应用四个模块 VO 的 JSON 字段名是驼峰，
     * 与数据库列名不同，不能直接拿数据库列名当 Bean 属性路径反射取值。
     *
     * @param columnName 数据库列名
     * @return 转换后的 Java Bean 属性名
     */
    private String toCamelCase(String columnName) {
        StringBuilder result = new StringBuilder(columnName.length());
        for (int i = 0; i < columnName.length(); i++) {
            char current = columnName.charAt(i);
            if (current == '_' && i + 1 < columnName.length()) {
                result.append(Character.toUpperCase(columnName.charAt(++i)));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    /**
     * 用 {@link SXSSFWorkbook} 流式写入生成 {@code .xlsx} 字节内容：表头加粗，
     * 数据行只写只读展示文本，不设置数据校验/下拉（design.md Decision 7）。
     *
     * @param columns 导出列定义列表
     * @param rows    待导出的行数据列表
     * @return {@code .xlsx} 文件字节内容
     */
    private byte[] writeWorkbook(List<ExportColumn> columns, List<?> rows) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_WINDOW_SIZE)) {
            Sheet sheet = workbook.createSheet(SHEET_NAME);
            CellStyle headerStyle = buildHeaderStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i).header());
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (Object rowData : rows) {
                BeanWrapper wrapper = new BeanWrapperImpl(rowData);
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < columns.size(); i++) {
                    ExportColumn column = columns.get(i);
                    String rawText = toText(wrapper.getPropertyValue(column.propertyName()));
                    String displayText = column.dictTypeCode() != null
                            ? exportDictLabelSupport.resolveDisplayText(column.controlType(), column.dictTypeCode(),
                                    rawText)
                            : rawText;
                    row.createCell(i).setCellValue(displayText);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("生成导出文件失败");
        }
    }

    /**
     * 构造表头单元格样式：字体加粗。
     *
     * @param workbook 工作簿
     * @return 表头单元格样式
     */
    private CellStyle buildHeaderStyle(SXSSFWorkbook workbook) {
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(headerFont);
        return style;
    }

    /**
     * 把反射取到的原始属性值转换为导出展示文本，{@code null} 转换为空字符串。
     *
     * @param rawValue 反射取到的原始属性值
     * @return 展示文本
     */
    private String toText(Object rawValue) {
        return rawValue == null ? "" : Objects.toString(rawValue);
    }
}
