package cn.nihility.rbac.excelimport.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.constant.ImportBizTypes;
import cn.nihility.rbac.excelimport.constant.ImportLimits;
import cn.nihility.rbac.excelimport.constant.OrgPseudoFieldCode;
import cn.nihility.rbac.excelimport.dto.ImportFailItemVO;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.dto.ImportResultVO;
import cn.nihility.rbac.excelimport.service.BatchImportService;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import cn.nihility.rbac.excelimport.service.support.DictImportColumnSupport;
import cn.nihility.rbac.excelimport.service.support.ImportRowExecutor;
import cn.nihility.rbac.formfield.constant.FormFieldBizType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 批量导入业务逻辑实现，基于 Apache POI 解析上传文件，逐行独立事务提交
 * （委托 {@link ImportRowExecutor}），汇总成功条数与失败明细。
 */
@Service
@RequiredArgsConstructor
public class BatchImportServiceImpl implements BatchImportService {

    /** 标注版错误文件名里的时间戳格式：年月日时分，12 位数字。 */
    private static final DateTimeFormatter ERROR_FILE_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    /** 标注版错误文件表头最后一列固定文案。 */
    private static final String ERROR_REASON_HEADER = "错误原因";

    /** 导入字段配置业务逻辑接口，用于查询启用状态的导入字段配置驱动表头匹配。 */
    private final ImportFieldConfigService importFieldConfigService;

    /** 批量导入单行处理器，每行独立开启新事务处理。 */
    private final ImportRowExecutor importRowExecutor;

    /** 字典/文本格式列支持组件，用于判定标注版错误文件里哪些列应设置为 Excel 文本格式。 */
    private final DictImportColumnSupport dictImportColumnSupport;

    /**
     * {@inheritDoc}
     */
    @Override
    public ImportResultVO importExcel(String bizType, MultipartFile file) {
        if (!ImportBizTypes.isValid(bizType)) {
            throw new BusinessException("不支持的业务对象类型：" + bizType);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传待导入的 Excel 文件");
        }

        List<ImportFieldConfigVO> configs = importFieldConfigService.listActiveByBizType(bizType);
        if (configs.isEmpty()) {
            throw new BusinessException("尚未配置该业务对象类型的导入字段，无法执行批量导入");
        }

        DataFormatter dataFormatter = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet != null ? sheet.getRow(sheet.getFirstRowNum()) : null;
            if (headerRow == null) {
                throw new BusinessException("上传的 Excel 文件缺少表头");
            }

            Map<Integer, String> columnFieldCodeMap = resolveColumnMapping(headerRow, configs, dataFormatter);
            validateRequiredHeaders(configs, columnFieldCodeMap);

            List<Row> dataRows = collectDataRows(sheet, headerRow.getRowNum(), dataFormatter);
            if (dataRows.size() > ImportLimits.MAX_ROW_COUNT) {
                throw new BusinessException("单次上传行数超过上限 " + ImportLimits.MAX_ROW_COUNT + " 行，请分批上传");
            }

            ProcessOutcome outcome = processDataRows(bizType, configs, columnFieldCodeMap, dataRows, dataFormatter);
            ImportResultVO result = outcome.result();
            if (!outcome.failedRows().isEmpty()) {
                ErrorFile errorFile = buildErrorFile(bizType, configs, outcome.failedRows());
                result.setErrorFileBase64(errorFile.base64());
                result.setErrorFileName(errorFile.fileName());
            }
            return result;
        } catch (IOException ex) {
            throw new BusinessException("读取上传的 Excel 文件失败");
        }
    }

    /**
     * 用失败行的解析结果重新拼装一份全新的标注版错误 {@code .xlsx}（不复用/修改原始上传的
     * {@code workbook}），仅包含失败的行：表头按 {@code configs}（{@code showOrder} 升序）
     * 逐列取 {@code excelHeaderName}，末尾追加"错误原因"列；数据行按 {@code configs} 列顺序
     * 从每个失败行的 {@code rowValues} 取值填入，最后一列写失败原因并标红；成功的行不出现在
     * 这份文件里。数据列（错误原因列除外）复用
     * {@link DictImportColumnSupport#resolveTextFormatFieldCodes(List)} 判定结果设置文本
     * 格式，避免手机号/身份证号等取值被 Excel 误判成数值（design.md 决策 3）。
     *
     * @param bizType    业务对象类型，用于拼装文件名
     * @param configs    启用状态的导入字段配置列表，已按 {@code showOrder} 升序排列
     * @param failedRows 失败行的解析结果与失败原因列表，非空
     * @return 错误文件的 Base64 内容与建议文件名
     */
    private ErrorFile buildErrorFile(String bizType, List<ImportFieldConfigVO> configs, List<FailedRow> failedRows) {
        Set<String> textFormatFieldCodes = dictImportColumnSupport.resolveTextFormatFieldCodes(configs);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("失败明细");
            CellStyle errorStyle = buildErrorReasonStyle(workbook);
            CellStyle textStyle = buildTextCellStyle(workbook);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < configs.size(); i++) {
                headerRow.createCell(i).setCellValue(configs.get(i).getExcelHeaderName());
            }
            int errorColumnIndex = configs.size();
            headerRow.createCell(errorColumnIndex).setCellValue(ERROR_REASON_HEADER);

            for (int r = 0; r < failedRows.size(); r++) {
                FailedRow failedRow = failedRows.get(r);
                Row dataRow = sheet.createRow(r + 1);
                for (int i = 0; i < configs.size(); i++) {
                    String fieldCode = configs.get(i).getFieldCode();
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(failedRow.parsedRow().rowValues().getOrDefault(fieldCode, ""));
                    if (textFormatFieldCodes.contains(fieldCode)) {
                        cell.setCellStyle(textStyle);
                    }
                }
                Cell reasonCell = dataRow.createCell(errorColumnIndex);
                reasonCell.setCellValue(failedRow.reason());
                reasonCell.setCellStyle(errorStyle);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            String base64 = Base64.getEncoder().encodeToString(out.toByteArray());
            String fileName = ImportBizTypes.labelOf(bizType) + "失败明细-"
                    + LocalDateTime.now().format(ERROR_FILE_TIMESTAMP_FORMATTER) + ".xlsx";
            return new ErrorFile(base64, fileName);
        } catch (IOException ex) {
            throw new BusinessException("生成失败明细标注文件失败");
        }
    }

    /**
     * 构造错误原因单元格样式：字体颜色标红，不加粗。
     *
     * @param workbook 工作簿
     * @return 错误原因单元格样式
     */
    private CellStyle buildErrorReasonStyle(Workbook workbook) {
        Font errorFont = workbook.createFont();
        errorFont.setColor(IndexedColors.RED.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(errorFont);
        return style;
    }

    /**
     * 构造文本格式单元格样式：数据格式设为 {@code @}（Excel 文本格式），用于避免手机号、
     * 身份证号等纯数字外观的字符串被 Excel 自动识别为数值，写法与
     * {@code ImportTemplateServiceImpl#buildTextCellStyle} 一致。
     *
     * @param workbook 工作簿
     * @return 文本格式单元格样式
     */
    private CellStyle buildTextCellStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("@"));
        return style;
    }

    /**
     * 按表头文字匹配启用状态的导入字段配置，得到列下标到 {@code fieldCode} 的映射；
     * 表头文字未匹配任何配置的列被忽略。
     *
     * @param headerRow     表头行
     * @param configs       启用状态的导入字段配置列表
     * @param dataFormatter 单元格文本格式化工具
     * @return 列下标 -> {@code fieldCode} 的映射
     */
    private Map<Integer, String> resolveColumnMapping(Row headerRow, List<ImportFieldConfigVO> configs,
            DataFormatter dataFormatter) {
        Map<String, String> headerNameToFieldCode = new LinkedHashMap<>();
        for (ImportFieldConfigVO config : configs) {
            headerNameToFieldCode.putIfAbsent(config.getExcelHeaderName(), config.getFieldCode());
        }

        Map<Integer, String> columnFieldCodeMap = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            String headerText = dataFormatter.formatCellValue(cell).trim();
            String fieldCode = headerNameToFieldCode.get(headerText);
            if (fieldCode != null) {
                columnFieldCodeMap.put(cell.getColumnIndex(), fieldCode);
            }
        }
        return columnFieldCodeMap;
    }

    /**
     * 校验启用状态的导入字段配置中标记为必填的表头是否均已在上传文件的表头行中出现，
     * 缺失时拒绝整个导入请求并提示缺失的表头名称。
     *
     * @param configs           启用状态的导入字段配置列表
     * @param columnFieldCodeMap 表头解析得到的列下标到 {@code fieldCode} 的映射
     */
    private void validateRequiredHeaders(List<ImportFieldConfigVO> configs, Map<Integer, String> columnFieldCodeMap) {
        Set<String> matchedFieldCodes = new HashSet<>(columnFieldCodeMap.values());
        List<String> missingHeaders = configs.stream()
                .filter(config -> Boolean.TRUE.equals(config.getIsRequired()))
                .filter(config -> !matchedFieldCodes.contains(config.getFieldCode()))
                .map(ImportFieldConfigVO::getExcelHeaderName)
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new BusinessException("上传的模板缺少必填表头：" + String.join("、", missingHeaders));
        }
    }

    /**
     * 收集表头行之后的全部非空白数据行（跳过完全空白的行，不计入行数上限与处理结果）。
     *
     * @param sheet          工作表，可为 {@code null}
     * @param headerRowNum   表头行下标
     * @param dataFormatter  单元格文本格式化工具
     * @return 非空白数据行列表
     */
    private List<Row> collectDataRows(Sheet sheet, int headerRowNum, DataFormatter dataFormatter) {
        List<Row> dataRows = new ArrayList<>();
        if (sheet == null) {
            return dataRows;
        }
        for (int rowNum = headerRowNum + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null || isRowBlank(row, dataFormatter)) {
                continue;
            }
            dataRows.add(row);
        }
        return dataRows;
    }

    /**
     * 判断一行是否所有单元格均为空白文本。
     *
     * @param row           数据行
     * @param dataFormatter 单元格文本格式化工具
     * @return 是否全部空白
     */
    private boolean isRowBlank(Row row, DataFormatter dataFormatter) {
        for (Cell cell : row) {
            if (StringUtils.hasText(dataFormatter.formatCellValue(cell))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 先完整解析全部数据行，再决定处理顺序：ORG 场景下按"上级组织编码"列在本批次内
     * 构造的父子依赖关系做拓扑排序（design.md 决策 2），确保一个组织所在行在其上级
     * 组织所在行（若也在同一文件中）之后处理；其余业务对象类型维持原始文件行序不变。
     * 排序（或原始顺序）确定后，依次委托 {@link ImportRowExecutor} 在独立事务中执行
     * 新增/更新，捕获单行异常转换为失败明细，汇总成功条数与失败明细列表。
     *
     * @param bizType            业务对象类型
     * @param configs            启用状态的导入字段配置列表
     * @param columnFieldCodeMap 列下标到 {@code fieldCode} 的映射
     * @param dataRows           非空白数据行列表
     * @param dataFormatter      单元格文本格式化工具
     * @return 批量导入结果与每条失败行的原始解析数据（供生成标注版错误文件使用）
     */
    private ProcessOutcome processDataRows(String bizType, List<ImportFieldConfigVO> configs,
            Map<Integer, String> columnFieldCodeMap, List<Row> dataRows, DataFormatter dataFormatter) {
        List<ParsedRow> parsedRows = parseDataRows(columnFieldCodeMap, dataRows, dataFormatter);

        int successCount = 0;
        List<ImportFailItemVO> failList = new ArrayList<>();
        List<FailedRow> failedRows = new ArrayList<>();

        List<ParsedRow> orderedRows = FormFieldBizType.ORG.equals(bizType)
                ? sortOrgRowsByParentDependency(parsedRows, failList, failedRows)
                : parsedRows;

        for (ParsedRow parsedRow : orderedRows) {
            // importRowExecutor.processRow() 会把字典列的取值从 label 原地改写成 code
            // （见 ImportRowExecutor.resolveDictColumns()），失败时若直接把 parsedRow 存进
            // failedRows，标注版错误文件里字典列会显示 code 而不是用户原本填写的 label，
            // 导致修正后重新上传会在字典列上再次判定失败；这里先克隆一份原始取值用于错误
            // 文件展示，不受 processRow() 内部原地修改的影响。
            Map<String, String> originalValues = new LinkedHashMap<>(parsedRow.rowValues());
            try {
                importRowExecutor.processRow(bizType, parsedRow.rowValues(), configs);
                successCount++;
            } catch (Exception ex) {
                String reason = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "处理失败";
                failList.add(ImportFailItemVO.builder().rowNo(parsedRow.rowNo()).reason(reason).build());
                failedRows.add(new FailedRow(new ParsedRow(parsedRow.rowNo(), originalValues), reason));
            }
        }

        ImportResultVO result = ImportResultVO.builder().successCount(successCount).failList(failList).build();
        return new ProcessOutcome(result, failedRows);
    }

    /**
     * 把每个数据行按 {@code columnFieldCodeMap} 读取单元格文本，组装为
     * {@code fieldCode -> 单元格文本} 的映射，连同其 Excel 行号一起收集成列表，
     * 不在本方法内调用 {@link ImportRowExecutor}（解析与执行两步拆分，design.md 决策 2）。
     *
     * @param columnFieldCodeMap 列下标到 {@code fieldCode} 的映射
     * @param dataRows           非空白数据行列表
     * @param dataFormatter      单元格文本格式化工具
     * @return 按原始文件行序排列的解析结果列表
     */
    private List<ParsedRow> parseDataRows(Map<Integer, String> columnFieldCodeMap, List<Row> dataRows,
            DataFormatter dataFormatter) {
        List<ParsedRow> parsedRows = new ArrayList<>(dataRows.size());
        for (Row row : dataRows) {
            int rowNo = row.getRowNum() + 1;
            Map<String, String> rowValues = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> entry : columnFieldCodeMap.entrySet()) {
                Cell cell = row.getCell(entry.getKey());
                String value = cell != null ? dataFormatter.formatCellValue(cell).trim() : "";
                rowValues.put(entry.getValue(), value);
            }
            parsedRows.add(new ParsedRow(rowNo, rowValues));
        }
        return parsedRows;
    }

    /**
     * 仅在 {@code bizType=ORG} 时调用：以每行的组织编码（{@code code}）为节点、以
     * "某行的上级组织编码（{@link OrgPseudoFieldCode#PARENT_CODE}）等于批次内另一行的
     * {@code code}"为父→子有向边，用 Kahn 算法做拓扑排序，使父组织所在行排在子组织
     * 所在行之前；上级组织编码为字面量 {@code "0"}，或其取值在批次内的 {@code code}
     * 集合中找不到匹配（引用的是数据库里已存在的组织，或是本次会在
     * {@code processOrg} 里查库判定失败的无效编码），都不构成批次内部依赖。若干行
     * 因互相引用形成环，无法参与拓扑排序，直接组装为失败明细加入 {@code failList}，
     * 不出现在返回结果中（design.md 决策 2）。
     *
     * @param rows       解析阶段得到的按原始文件行序排列的数据行列表
     * @param failList   失败明细汇总列表，成环的行会被加入其中
     * @param failedRows 失败行原始解析数据汇总列表，成环的行会被加入其中，供生成标注版错误文件使用
     * @return 按拓扑序（父在前、子在后）排列的数据行列表，不含成环的行
     */
    private List<ParsedRow> sortOrgRowsByParentDependency(List<ParsedRow> rows, List<ImportFailItemVO> failList,
            List<FailedRow> failedRows) {
        Set<String> codesInBatch = new HashSet<>();
        for (ParsedRow row : rows) {
            String code = row.rowValues().get("code");
            if (StringUtils.hasText(code)) {
                codesInBatch.add(code);
            }
        }

        // 父 code -> 依赖该父组织的子行列表；每行的入度即其"父组织在批次内待处理"的边数（0 或 1）。
        Map<String, List<ParsedRow>> childrenByParentCode = new HashMap<>();
        Map<ParsedRow, Integer> inDegree = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            inDegree.put(row, 0);
        }
        for (ParsedRow row : rows) {
            String parentCode = row.rowValues().get(OrgPseudoFieldCode.PARENT_CODE);
            if (!"0".equals(parentCode) && StringUtils.hasText(parentCode) && codesInBatch.contains(parentCode)) {
                childrenByParentCode.computeIfAbsent(parentCode, key -> new ArrayList<>()).add(row);
                inDegree.merge(row, 1, Integer::sum);
            }
        }

        Deque<ParsedRow> queue = new ArrayDeque<>();
        for (ParsedRow row : rows) {
            if (inDegree.get(row) == 0) {
                queue.add(row);
            }
        }

        List<ParsedRow> sorted = new ArrayList<>(rows.size());
        while (!queue.isEmpty()) {
            ParsedRow current = queue.poll();
            sorted.add(current);
            String code = current.rowValues().get("code");
            List<ParsedRow> children = childrenByParentCode.get(code);
            if (children == null) {
                continue;
            }
            for (ParsedRow child : children) {
                int remaining = inDegree.merge(child, -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(child);
                }
            }
        }

        if (sorted.size() < rows.size()) {
            Set<ParsedRow> sortedSet = new HashSet<>(sorted);
            for (ParsedRow row : rows) {
                if (!sortedSet.contains(row)) {
                    String reason = "上级组织编码与文件内其他行形成循环引用，无法确定导入顺序";
                    failList.add(ImportFailItemVO.builder().rowNo(row.rowNo()).reason(reason).build());
                    failedRows.add(new FailedRow(row, reason));
                }
            }
        }

        return sorted;
    }

    /**
     * 一行已解析完成的数据：Excel 行号（从 1 开始，含表头行）与
     * {@code fieldCode -> 单元格文本} 的映射，供解析阶段与执行阶段之间传递
     * （design.md 决策 2）。
     *
     * @param rowNo     Excel 行号（从 1 开始，含表头行）
     * @param rowValues 本行数据，key 为 {@code fieldCode}，value 为单元格文本
     */
    private record ParsedRow(int rowNo, Map<String, String> rowValues) {
    }

    /**
     * 一条处理失败的数据行：原始解析结果与失败原因，仅在本类内部使用，用于生成标注版
     * 错误文件；不放进 {@link ImportResultVO}/{@link ImportFailItemVO}，避免对外暴露的
     * API 响应结构承载整行原始数据（design.md 决策 3）。
     *
     * @param parsedRow 该行的原始解析结果
     * @param reason    失败原因
     */
    private record FailedRow(ParsedRow parsedRow, String reason) {
    }

    /**
     * {@link #processDataRows} 的内部返回结果：既包含对外暴露的批量导入结果，又包含仅供
     * 生成标注版错误文件使用的失败行原始解析数据。
     *
     * @param result     批量导入结果
     * @param failedRows 失败行原始解析数据与失败原因列表
     */
    private record ProcessOutcome(ImportResultVO result, List<FailedRow> failedRows) {
    }

    /**
     * 标注版错误文件的生成结果：Base64 编码内容与建议文件名。
     *
     * @param base64   Base64 编码后的 {@code .xlsx} 文件内容
     * @param fileName 建议文件名
     */
    private record ErrorFile(String base64, String fileName) {
    }
}
