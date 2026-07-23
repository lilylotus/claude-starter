package cn.nihility.rbac.excelimport.service.impl;

import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.excelimport.constant.ImportBizTypes;
import cn.nihility.rbac.excelimport.dto.ImportFailItemVO;
import cn.nihility.rbac.excelimport.dto.ImportFieldConfigVO;
import cn.nihility.rbac.excelimport.dto.ImportResultVO;
import cn.nihility.rbac.excelimport.service.BatchImportService;
import cn.nihility.rbac.excelimport.service.ImportFieldConfigService;
import cn.nihility.rbac.excelimport.service.support.ImportRowExecutor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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

    /** 单次上传允许处理的最大数据行数（不含表头行），超出拒绝并提示分批上传。 */
    private static final int MAX_ROW_COUNT = 1000;

    /** 导入字段配置业务逻辑接口，用于查询启用状态的导入字段配置驱动表头匹配。 */
    private final ImportFieldConfigService importFieldConfigService;

    /** 批量导入单行处理器，每行独立开启新事务处理。 */
    private final ImportRowExecutor importRowExecutor;

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
            if (dataRows.size() > MAX_ROW_COUNT) {
                throw new BusinessException("单次上传行数超过上限 " + MAX_ROW_COUNT + " 行，请分批上传");
            }

            return processDataRows(bizType, configs, columnFieldCodeMap, dataRows, dataFormatter);
        } catch (IOException ex) {
            throw new BusinessException("读取上传的 Excel 文件失败");
        }
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
     * 逐行独立处理数据行，委托 {@link ImportRowExecutor} 在独立事务中执行新增/更新，
     * 捕获单行异常转换为失败明细，汇总成功条数与失败明细列表。
     *
     * @param bizType            业务对象类型
     * @param configs            启用状态的导入字段配置列表
     * @param columnFieldCodeMap 列下标到 {@code fieldCode} 的映射
     * @param dataRows           非空白数据行列表
     * @param dataFormatter      单元格文本格式化工具
     * @return 批量导入结果
     */
    private ImportResultVO processDataRows(String bizType, List<ImportFieldConfigVO> configs,
            Map<Integer, String> columnFieldCodeMap, List<Row> dataRows, DataFormatter dataFormatter) {
        int successCount = 0;
        List<ImportFailItemVO> failList = new ArrayList<>();

        for (Row row : dataRows) {
            int rowNo = row.getRowNum() + 1;
            Map<String, String> rowValues = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> entry : columnFieldCodeMap.entrySet()) {
                Cell cell = row.getCell(entry.getKey());
                String value = cell != null ? dataFormatter.formatCellValue(cell).trim() : "";
                rowValues.put(entry.getValue(), value);
            }

            try {
                importRowExecutor.processRow(bizType, rowValues, configs);
                successCount++;
            } catch (Exception ex) {
                String reason = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "处理失败";
                failList.add(ImportFailItemVO.builder().rowNo(rowNo).reason(reason).build());
            }
        }

        return ImportResultVO.builder().successCount(successCount).failList(failList).build();
    }
}
