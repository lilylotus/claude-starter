package cn.nihility.rbac.fileupload.service.impl;

import cn.nihility.rbac.auth.service.CurrentOperatorService;
import cn.nihility.rbac.common.exception.BusinessException;
import cn.nihility.rbac.fileupload.config.FileUploadProperties;
import cn.nihility.rbac.fileupload.constant.FileUploadStatus;
import cn.nihility.rbac.fileupload.dto.FileDownloadResult;
import cn.nihility.rbac.fileupload.dto.FileUploadVO;
import cn.nihility.rbac.fileupload.entity.FileUploadEntity;
import cn.nihility.rbac.fileupload.exception.FileDisabledException;
import cn.nihility.rbac.fileupload.exception.FileRecordNotFoundException;
import cn.nihility.rbac.fileupload.mapper.FileUploadMapper;
import cn.nihility.rbac.fileupload.mapstruct.FileUploadConvert;
import cn.nihility.rbac.fileupload.service.FileUploadService;
import cn.nihility.rbac.fileupload.service.support.FileUploadRecordWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 通用文件上传/下载业务逻辑实现（add-file-upload change design.md Decision 3/6/7）。
 */
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    /** 原始文件名无法解析出后缀时的兜底文件名，避免落库空字符串。 */
    private static final String DEFAULT_ORIGINAL_FILE_NAME = "unnamed";

    /** 文件保存根目录配置。 */
    private final FileUploadProperties fileUploadProperties;

    /** 文件上传记录数据访问接口。 */
    private final FileUploadMapper fileUploadMapper;

    /** 文件上传记录落库组件，只对数据库插入这一步开启事务，写盘操作在事务之外先完成。 */
    private final FileUploadRecordWriter fileUploadRecordWriter;

    /** 当前登录操作人用户 id 解析服务，用于填充审计字段。 */
    private final CurrentOperatorService currentOperatorService;

    /**
     * {@inheritDoc}
     */
    @Override
    public FileUploadVO upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : DEFAULT_ORIGINAL_FILE_NAME;
        String suffix = extractSuffix(originalFileName);
        String storedFileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        long fileSize = writeToDisk(file, storedFileName);

        String operator = Objects.toString(currentOperatorService.resolveUserId(), null);
        LocalDateTime now = LocalDateTime.now();
        FileUploadEntity entity = FileUploadEntity.builder()
                .originalFileName(originalFileName)
                .storedFileName(storedFileName)
                .fileSize(fileSize)
                .status(FileUploadStatus.NORMAL)
                .createBy(operator)
                .createTime(now)
                .updateBy(operator)
                .updateTime(now)
                .build();
        fileUploadRecordWriter.insert(entity);

        return FileUploadConvert.INSTANCE.toVO(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileDownloadResult download(Long id) {
        FileUploadEntity entity = fileUploadMapper.selectById(id);
        if (entity == null) {
            throw new FileRecordNotFoundException();
        }
        if (!Objects.equals(entity.getStatus(), FileUploadStatus.NORMAL)) {
            throw new FileDisabledException();
        }

        Path target = resolveRootDir().resolve(entity.getStoredFileName());
        try {
            byte[] content = Files.readAllBytes(target);
            String contentType = Files.probeContentType(target);
            return FileDownloadResult.builder()
                    .content(content)
                    .originalFileName(entity.getOriginalFileName())
                    .contentType(contentType)
                    .build();
        } catch (IOException e) {
            // 记录存在但物理文件缺失/不可读属于数据不一致的系统异常，不定义专属业务异常类型，
            // 走 GlobalExceptionHandler 的兜底 Exception 处理分支（design.md Decision 6）。
            throw new IllegalStateException("文件读取失败：" + entity.getStoredFileName(), e);
        }
    }

    /**
     * 取原始文件名的后缀（含 {@code .}），不存在则返回空串。
     *
     * @param originalFileName 原始文件名
     * @return 后缀，如 {@code .xlsx}；不存在后缀时返回空串
     */
    private String extractSuffix(String originalFileName) {
        int dotIndex = originalFileName.lastIndexOf('.');
        return dotIndex >= 0 ? originalFileName.substring(dotIndex) : "";
    }

    /**
     * 把上传文件流式写入磁盘的文件保存根目录下，目录不存在时先创建。
     *
     * @param file           待写入的上传文件
     * @param storedFileName 磁盘保存文件名
     * @return 写入后的文件大小（字节）
     */
    private long writeToDisk(MultipartFile file, String storedFileName) {
        Path rootDir = resolveRootDir();
        Path target = rootDir.resolve(storedFileName);
        try {
            Files.createDirectories(rootDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
            }
            return Files.size(target);
        } catch (IOException e) {
            throw new BusinessException("文件写入磁盘失败：" + e.getMessage());
        }
    }

    /**
     * 解析文件保存根目录路径，相对路径相对于应用当前工作目录解析。
     *
     * @return 文件保存根目录路径
     */
    private Path resolveRootDir() {
        return Paths.get(fileUploadProperties.getDirectory());
    }
}
