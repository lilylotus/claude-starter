package cn.nihility.rbac.excelimport.service;

import cn.nihility.rbac.excelimport.dto.ImportResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * Excel 批量导入业务逻辑接口。
 */
public interface BatchImportService {

    /**
     * 按业务对象类型批量导入 Excel 文件：解析首行表头得到列到 {@code fieldCode} 的
     * 映射，校验启用状态的导入字段配置中标记为必填的表头是否齐全（缺失则拒绝整个
     * 请求），校验数据行数未超过单次上传上限；表头齐全时对每一数据行独立处理（按
     * 主键列匹配已有记录，命中零条新增、一条更新、多条判定失败；必填列为空、业务
     * 校验不通过等单行异常均不影响其余行的处理），最终返回本次导入的成功条数与
     * 失败明细。
     *
     * @param bizType 业务对象类型：ORG/USER/POSITION/APP
     * @param file    上传的 {@code .xlsx} 文件
     * @return 批量导入结果
     */
    ImportResultVO importExcel(String bizType, MultipartFile file);
}
