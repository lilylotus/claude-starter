package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PluginJarScanner} 单元测试：验证扫描顺序与目录缺失场景（plugin-jar-upgrade change
 * tasks.md 2.1）。
 */
class PluginJarScannerTest {

    /** JUnit 管理的临时目录。 */
    @TempDir
    private Path tempDir;

    /**
     * 目录下存在多个 {@code .jar} 文件、非 jar 文件与子目录时，只返回 {@code .jar} 文件，
     * 按文件名字典序排列。
     */
    @Test
    void scan_shouldReturnJarFilesInFileNameOrder_andIgnoreOthers() throws IOException {
        Files.writeString(tempDir.resolve("c-plugin.jar"), "c");
        Files.writeString(tempDir.resolve("a-plugin.jar"), "a");
        Files.writeString(tempDir.resolve("b-plugin.jar"), "b");
        Files.writeString(tempDir.resolve("readme.txt"), "ignored");
        Files.createDirectory(tempDir.resolve("nested-dir"));

        List<Path> result = PluginJarScanner.scan(tempDir);

        assertThat(result).extracting(path -> path.getFileName().toString())
                .containsExactly("a-plugin.jar", "b-plugin.jar", "c-plugin.jar");
    }

    /**
     * 插件目录不存在时返回空列表，不抛出异常。
     */
    @Test
    void scan_shouldReturnEmptyList_whenDirectoryMissing() {
        Path missingDir = tempDir.resolve("does-not-exist");
        assertThat(PluginJarScanner.scan(missingDir)).isEmpty();
    }

    /**
     * 目录为空时返回空列表。
     */
    @Test
    void scan_shouldReturnEmptyList_whenDirectoryEmpty() {
        assertThat(PluginJarScanner.scan(tempDir)).isEmpty();
    }
}
