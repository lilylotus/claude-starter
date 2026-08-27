package cn.nihility.rbac.plugin.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PluginMetadataReader} 单元测试：覆盖 {@code META-INF/plugin.properties} 存在/
 * 缺失两种情况（plugin-jar-upgrade change tasks.md 1.3）。
 */
class PluginMetadataReaderTest {

    /** JUnit 管理的临时目录。 */
    @TempDir
    private Path tempDir;

    /**
     * {@code plugin.properties} 存在且字段齐全时，应正确解析全部字段。
     */
    @Test
    void read_shouldParseAllFields_whenPropertiesFilePresent() throws IOException {
        Path jarPath = tempDir.resolve("with-properties.jar");
        writeJarWithProperties(jarPath, "name=demo-plugin\nversion=1.2.3\npriority=5\n");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            PluginMetadata metadata = PluginMetadataReader.read(jarFile, jarPath);
            assertThat(metadata.name()).isEqualTo("demo-plugin");
            assertThat(metadata.version()).isEqualTo("1.2.3");
            assertThat(metadata.priority()).isEqualTo(5);
        }
    }

    /**
     * {@code plugin.properties} 整体缺失时，用来源文件名兜底名称、缺省版本号、缺省优先级。
     */
    @Test
    void read_shouldFallback_whenPropertiesFileMissing() throws IOException {
        Path jarPath = tempDir.resolve("no-properties.jar");
        writeEmptyJar(jarPath);

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            PluginMetadata metadata = PluginMetadataReader.read(jarFile, jarPath);
            assertThat(metadata.name()).isEqualTo("no-properties");
            assertThat(metadata.version()).isEqualTo(PluginMetadata.UNKNOWN_VERSION);
            assertThat(metadata.priority()).isEqualTo(PluginMetadata.DEFAULT_PRIORITY);
        }
    }

    /**
     * {@code priority} 属性值非法（非数字）时，回退为缺省优先级，不抛出异常。
     */
    @Test
    void read_shouldFallbackPriority_whenPriorityIsNotNumeric() throws IOException {
        Path jarPath = tempDir.resolve("bad-priority.jar");
        writeJarWithProperties(jarPath, "name=bad-plugin\npriority=not-a-number\n");

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            PluginMetadata metadata = PluginMetadataReader.read(jarFile, jarPath);
            assertThat(metadata.name()).isEqualTo("bad-plugin");
            assertThat(metadata.priority()).isEqualTo(PluginMetadata.DEFAULT_PRIORITY);
        }
    }

    /**
     * 写入一个仅含 {@code META-INF/plugin.properties} 条目的最小 jar。
     *
     * @param jarPath    目标路径
     * @param properties 属性文件文本内容
     */
    private void writeJarWithProperties(Path jarPath, String properties) throws IOException {
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jarOut.putNextEntry(new JarEntry("META-INF/plugin.properties"));
            jarOut.write(properties.getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
        }
    }

    /**
     * 写入一个不含任何条目的最小合法 jar。
     *
     * @param jarPath 目标路径
     */
    private void writeEmptyJar(Path jarPath) throws IOException {
        try (OutputStream out = Files.newOutputStream(jarPath); JarOutputStream jarOut = new JarOutputStream(out)) {
            jarOut.putNextEntry(new JarEntry("placeholder.txt"));
            jarOut.write("placeholder".getBytes(StandardCharsets.UTF_8));
            jarOut.closeEntry();
        }
    }
}
