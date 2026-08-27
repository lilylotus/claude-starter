package cn.nihility.rbac.plugin.testsupport;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * 测试专用插件 jar 构造工具：用 JDK 自带 {@link javax.tools.JavaCompiler} 在测试运行期实时
 * 编译示例插件源码并打包为 jar，避免依赖 Gradle 额外 sourceSet（不改动 build.gradle）。
 * <p>
 * 编译所需 classpath 不依赖 {@code java.class.path} 系统属性字符串解析（Gradle 在 Windows
 * 上命令行过长时会退化为 pathing jar/参数文件，届时该属性可能不反映真实的展开路径列表），
 * 改为直接取"标记类"（{@code classpathMarkers}）各自的 {@link CodeSource} 位置，
 * 这与启动 JVM 的具体命令行形式无关，更为稳健。
 */
public final class PluginJarTestSupport {

    /**
     * 工具类不允许实例化。
     */
    private PluginJarTestSupport() {
    }

    /**
     * 编译给定源码并打包为插件 jar。
     *
     * @param targetJar         目标 jar 路径（父目录不存在会自动创建）
     * @param sources           类全限定名 -&gt; 源码文本
     * @param pluginProperties  {@code META-INF/plugin.properties} 内容，{@code null} 表示不写入该文件
     * @param classpathMarkers  编译所需 classpath 的标记类（取各自 {@link CodeSource} 位置）
     * @return 构建完成的 jar 路径
     * @throws IOException 编译或打包过程失败时抛出
     */
    public static Path buildJar(Path targetJar, Map<String, String> sources, Properties pluginProperties,
            Class<?>... classpathMarkers) throws IOException {
        Path classesDir = Files.createTempDirectory("plugin-jar-classes");
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("当前运行环境不提供系统 Java 编译器（javax.tools.ToolProvider.getSystemJavaCompiler 返回 null）");
        }

        List<JavaFileObject> compilationUnits = new ArrayList<>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            compilationUnits.add(new StringSource(entry.getKey(), entry.getValue()));
        }

        List<String> options = new ArrayList<>(List.of("-d", classesDir.toString(), "-proc:none"));
        String classpath = resolveClasspath(classpathMarkers);
        if (!classpath.isBlank()) {
            options.add("-classpath");
            options.add(classpath);
        }

        StringWriter diagnostics = new StringWriter();
        boolean success;
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            JavaCompiler.CompilationTask task =
                    compiler.getTask(diagnostics, fileManager, null, options, null, compilationUnits);
            success = task.call();
        }
        if (!success) {
            throw new IllegalStateException("测试插件 fixture 编译失败：\n" + diagnostics);
        }

        Files.createDirectories(targetJar.getParent());
        try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(targetJar))) {
            writeCompiledClasses(jarOut, classesDir);
            if (pluginProperties != null) {
                writePluginProperties(jarOut, pluginProperties);
            }
        }
        return targetJar;
    }

    /**
     * 在指定路径写入一个非法 jar 内容（用于模拟"插件 jar 损坏"场景）。
     *
     * @param targetJar 目标路径
     * @return 写入完成的路径
     * @throws IOException 写入失败时抛出
     */
    public static Path writeCorruptJar(Path targetJar) throws IOException {
        Files.createDirectories(targetJar.getParent());
        Files.writeString(targetJar, "this is not a valid jar file content", StandardCharsets.UTF_8);
        return targetJar;
    }

    /**
     * 把编译产物目录内的全部 class 文件写入 jar。
     *
     * @param jarOut     jar 输出流
     * @param classesDir 编译产物目录
     * @throws IOException 写入失败时抛出
     */
    private static void writeCompiledClasses(JarOutputStream jarOut, Path classesDir) throws IOException {
        try (Stream<Path> stream = Files.walk(classesDir)) {
            List<Path> files = stream.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String entryName = classesDir.relativize(file).toString().replace(File.separatorChar, '/');
                jarOut.putNextEntry(new JarEntry(entryName));
                Files.copy(file, jarOut);
                jarOut.closeEntry();
            }
        }
    }

    /**
     * 写入 {@code META-INF/plugin.properties} 条目。
     *
     * @param jarOut     jar 输出流
     * @param properties 属性内容
     * @throws IOException 写入失败时抛出
     */
    private static void writePluginProperties(JarOutputStream jarOut, Properties properties) throws IOException {
        StringWriter writer = new StringWriter();
        properties.store(writer, null);
        jarOut.putNextEntry(new JarEntry("META-INF/plugin.properties"));
        jarOut.write(writer.toString().getBytes(StandardCharsets.UTF_8));
        jarOut.closeEntry();
    }

    /**
     * 解析编译所需 classpath：取每个标记类的 {@link CodeSource} 位置并去重拼接。
     *
     * @param markers 标记类
     * @return classpath 字符串
     */
    private static String resolveClasspath(Class<?>... markers) {
        LinkedHashSet<String> entries = new LinkedHashSet<>();
        for (Class<?> marker : markers) {
            CodeSource codeSource = marker.getProtectionDomain().getCodeSource();
            if (codeSource == null) {
                continue;
            }
            try {
                entries.add(Path.of(codeSource.getLocation().toURI()).toString());
            } catch (URISyntaxException ex) {
                throw new IllegalStateException("无法解析类 [" + marker.getName() + "] 的 CodeSource 位置", ex);
            }
        }
        return String.join(File.pathSeparator, entries);
    }

    /**
     * 内存字符串源码文件对象。
     */
    private static final class StringSource extends SimpleJavaFileObject {

        /** 源码文本。 */
        private final String code;

        /**
         * 构造内存源码对象。
         *
         * @param className 类全限定名
         * @param code      源码文本
         */
        StringSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
