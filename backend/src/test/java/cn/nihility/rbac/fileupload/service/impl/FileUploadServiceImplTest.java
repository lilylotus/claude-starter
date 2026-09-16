package cn.nihility.rbac.fileupload.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import cn.nihility.rbac.fileupload.service.support.FileUploadRecordWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * {@link FileUploadServiceImpl} 的单元测试，重点覆盖上传时的空文件校验、保存文件名与
 * 原始文件名解耦、下载时的记录不存在/状态失效两种业务异常分支
 * （add-file-upload change specs/file-upload/spec.md）。
 */
@ExtendWith(MockitoExtension.class)
class FileUploadServiceImplTest {

    /** 落盘目录，测试用临时目录，避免污染仓库工作目录。 */
    @TempDir
    private Path tempDir;

    /** 被测服务的文件上传记录数据访问依赖，使用 Mockito 打桩。 */
    @Mock
    private FileUploadMapper fileUploadMapper;

    /** 被测服务的文件上传记录落库依赖，使用 Mockito 打桩。 */
    @Mock
    private FileUploadRecordWriter fileUploadRecordWriter;

    /** 被测服务的当前登录操作人用户 id 解析依赖，使用 Mockito 打桩。 */
    @Mock
    private CurrentOperatorService currentOperatorService;

    /** 被测服务实例。 */
    private FileUploadServiceImpl fileUploadService;

    /**
     * 每个用例执行前重新构造被测服务，落盘根目录指向 JUnit 临时目录。
     */
    @BeforeEach
    void setUp() {
        FileUploadProperties properties = new FileUploadProperties();
        properties.setDirectory(tempDir.toString());
        fileUploadService = new FileUploadServiceImpl(properties, fileUploadMapper, fileUploadRecordWriter,
                currentOperatorService);
        lenient().when(currentOperatorService.resolveUserId()).thenReturn(1L);
    }

    /**
     * 上传合法文件时，磁盘保存文件名应与原始文件名不同（随机字符串+原始后缀），
     * 且落库记录应为"正常"状态。
     */
    @Test
    void upload_shouldWriteFileAndInsertNormalRecord_whenFileProvided() {
        MockMultipartFile file = new MockMultipartFile("file", "报表.xlsx", "application/octet-stream",
                "hello".getBytes(StandardCharsets.UTF_8));

        FileUploadVO vo = fileUploadService.upload(file);

        ArgumentCaptor<FileUploadEntity> captor = ArgumentCaptor.forClass(FileUploadEntity.class);
        verify(fileUploadRecordWriter).insert(captor.capture());
        FileUploadEntity entity = captor.getValue();

        assertThat(entity.getOriginalFileName()).isEqualTo("报表.xlsx");
        assertThat(entity.getStoredFileName()).isNotEqualTo("报表.xlsx").endsWith(".xlsx");
        assertThat(entity.getStatus()).isEqualTo(FileUploadStatus.NORMAL);
        assertThat(entity.getFileSize()).isEqualTo(5L);
        assertThat(entity.getCreateBy()).isEqualTo("1");
        assertThat(entity.getUpdateBy()).isEqualTo("1");
        assertThat(vo.getOriginalFileName()).isEqualTo("报表.xlsx");
        assertThat(vo.getStatus()).isEqualTo(FileUploadStatus.NORMAL);

        Path savedFile = tempDir.resolve(entity.getStoredFileName());
        assertThat(Files.exists(savedFile)).isTrue();
    }

    /**
     * 上传空文件时应拒绝，不产生磁盘文件或数据库落库调用。
     */
    @Test
    void upload_shouldThrowBusinessException_whenFileEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> fileUploadService.upload(emptyFile))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 下载状态为"正常"的记录时，应返回磁盘文件的真实内容与原始文件名。
     */
    @Test
    void download_shouldReturnContent_whenRecordStatusNormal() throws Exception {
        String storedFileName = "abc123.txt";
        Path storedFile = tempDir.resolve(storedFileName);
        Files.writeString(storedFile, "file-content", StandardCharsets.UTF_8);

        FileUploadEntity entity = FileUploadEntity.builder()
                .id(1L)
                .originalFileName("原始文件.txt")
                .storedFileName(storedFileName)
                .fileSize(12L)
                .status(FileUploadStatus.NORMAL)
                .build();
        when(fileUploadMapper.selectById(1L)).thenReturn(entity);

        FileDownloadResult result = fileUploadService.download(1L);

        assertThat(new String(result.getContent(), StandardCharsets.UTF_8)).isEqualTo("file-content");
        assertThat(result.getOriginalFileName()).isEqualTo("原始文件.txt");
    }

    /**
     * 下载不存在的记录 id 时应抛出 {@link FileRecordNotFoundException}。
     */
    @Test
    void download_shouldThrowFileRecordNotFoundException_whenRecordMissing() {
        when(fileUploadMapper.selectById(any(Long.class))).thenReturn(null);

        assertThatThrownBy(() -> fileUploadService.download(999L))
                .isInstanceOf(FileRecordNotFoundException.class);
    }

    /**
     * 下载状态为"失效"的记录时应抛出 {@link FileDisabledException}，不读取磁盘文件。
     */
    @Test
    void download_shouldThrowFileDisabledException_whenRecordStatusDisabled() {
        FileUploadEntity entity = FileUploadEntity.builder()
                .id(2L)
                .originalFileName("已失效.txt")
                .storedFileName("disabled.txt")
                .status(FileUploadStatus.DISABLED)
                .build();
        when(fileUploadMapper.selectById(2L)).thenReturn(entity);

        assertThatThrownBy(() -> fileUploadService.download(2L))
                .isInstanceOf(FileDisabledException.class);
    }
}
