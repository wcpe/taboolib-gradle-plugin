package io.izzel.taboolib.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import java.util.jar.JarFile;

/** 通过真实插件与依赖子工程验证缓存，不依赖测试框架或远端业务依赖。 */
public final class TabooLibCacheFunctionalTest {

    public static void main(String[] args) throws Exception {
        Path root = new File(args[0]).toPath().resolve(UUID.randomUUID().toString());
        Files.createDirectories(root);
        write(root, "settings.gradle", "rootProject.name = 'cache-fixture'\ninclude 'library'\n"
                + "buildCache { local { directory = file('local-build-cache') } }\n");
        write(root, "gradle.properties", "org.gradle.jvmargs=-Xmx512m\norg.gradle.workers.max=2\n");
        write(root, "build.gradle", buildScript("第一版描述", ""));
        write(root, "library/build.gradle", "plugins { id 'java-library' }\n");
        write(root, "library/src/main/resources/library.txt", "依赖初始内容\n");
        write(root, "src/main/java/fixture/before/Example.java",
                "package fixture.before; public class Example { public static String value() { return \"正常重定位\"; } }\n");
        for (int i = 0; i < 100; i++) {
            write(root, "src/main/resources/data/entry-" + i + ".txt", "资源 " + i + "\n");
        }

        BuildResult first = run(root, "assemble");
        require(first.getOutput().contains("Configuration cache entry stored"), "首次构建未保存配置缓存", first);
        Path jar = root.resolve("build/libs/cache-fixture-1.0.jar");
        requireEntry(jar, "plugin.yml", "第一版描述");
        requireEntry(jar, "library.txt", "依赖初始内容");
        requireRelocatedClass(jar);
        byte[] initial = Files.readAllBytes(jar);

        BuildResult second = run(root, "assemble");
        require(second.getOutput().contains("Reusing configuration cache"), "二次构建未复用配置缓存", second);
        requireOutcome(second, ":jar", TaskOutcome.UP_TO_DATE);
        require(Arrays.equals(initial, Files.readAllBytes(jar)), "无改动构建改变了最终 JAR", second);

        BuildResult forced = run(root, "assemble", "--rerun-tasks");
        require(Arrays.equals(initial, Files.readAllBytes(jar)), "相同输入重复构建的 JAR 字节不稳定", forced);
        run(root, "clean");
        BuildResult restored = run(root, "assemble");
        requireOutcome(restored, ":jar", TaskOutcome.FROM_CACHE);
        requireEntry(jar, "plugin.yml", "第一版描述");

        write(root, "library/src/main/resources/library.txt", "依赖更新内容\n");
        BuildResult changedDependency = run(root, "assemble");
        require(changedDependency.getOutput().contains("Reusing configuration cache"), "依赖产物变化污染了配置缓存", changedDependency);
        requireOutcome(changedDependency, ":jar", TaskOutcome.SUCCESS);
        requireEntry(jar, "library.txt", "依赖更新内容");
        requireOutcome(run(root, "assemble"), ":jar", TaskOutcome.UP_TO_DATE);

        write(root, "build.gradle", buildScript("第二版描述", ""));
        BuildResult changedDescription = run(root, "assemble");
        requireOutcome(changedDescription, ":jar", TaskOutcome.SUCCESS);
        requireEntry(jar, "plugin.yml", "第二版描述");
        requireOutcome(run(root, "taboolibMainTask"), ":jar", TaskOutcome.UP_TO_DATE);

        write(root, "build.gradle", buildScript("分类产物", "classifier = 'relocated'"));
        run(root, "assemble");
        Path classifiedJar = root.resolve("build/libs/cache-fixture-1.0-relocated.jar");
        requireEntry(classifiedJar, "plugin.yml", "分类产物");
        requireRelocatedClass(classifiedJar);

        write(root, "src/main/resources/broken.class", "损坏的字节码");
        BuildResult malformed = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
                .withArguments("assemble", "--configuration-cache", "--configuration-cache-problems=fail", "--offline").buildAndFail();
        requireOutcome(malformed, ":jar", TaskOutcome.FAILED);
        Files.delete(root.resolve("src/main/resources/broken.class"));
        run(root, "assemble");
        requireRelocatedClass(classifiedJar);
        requireOutcome(run(root, "assemble"), ":jar", TaskOutcome.UP_TO_DATE);
        Files.delete(classifiedJar);
        BuildResult missingOutput = run(root, "assemble");
        require(missingOutput.task(":jar").getOutcome() != TaskOutcome.UP_TO_DATE, "删除分类产物后任务未恢复输出", missingOutput);
        requireEntry(classifiedJar, "plugin.yml", "分类产物");

        write(root, "build.gradle", buildScript("禁用验证", "")
                + "afterEvaluate { tasks.named('taboolibMainTask') { enabled = false } }\n");
        run(root, "jar");
        requireRawJar(jar);
        requireOutcome(run(root, "jar"), ":jar", TaskOutcome.UP_TO_DATE);

        write(root, "build.gradle", buildScript("排除验证", ""));
        run(root, "jar", "-x", "taboolibMainTask");
        requireRawJar(jar);
        requireOutcome(run(root, "jar", "-x", "taboolibMainTask"), ":jar", TaskOutcome.UP_TO_DATE);
        run(root, "jar", "-x", ":taboolibMainTask");
        requireRawJar(jar);
        run(root, "jar", "-x", "taboolibM");
        requireRawJar(jar);
        run(root, "jar");
        requireEntry(jar, "plugin.yml", "排除验证");
        requireRelocatedClass(jar);
        System.out.println("TabooLib 缓存功能回归通过，证据目录：" + root);
    }

    private static String buildScript(String description, String classifier) {
        return "plugins { id 'java-library'; id 'io.izzel.taboolib' }\n"
                + "group = 'fixture'; version = '1.0'\n"
                + "taboolib {\n"
                + "    " + classifier + "\n"
                + "    relocate 'fixture.before', 'fixture.after'\n"
                + "    env { install('platform-bukkit') }\n"
                + "    version { taboolib = null; coroutines = null; skipKotlinRelocate = true; skipTabooLibRelocate = true }\n"
                + "    description { desc('" + description + "') }\n"
                + "}\n"
                + "dependencies { taboo project(':library') }\n"
                + "afterEvaluate { configurations.compileOnly.dependencies.clear() }\n"
                + "tasks.named('jar') { dependsOn(':library:jar') }\n";
    }

    private static BuildResult run(Path root, String... tasks) {
        String[] arguments = Arrays.copyOf(tasks, tasks.length + 6);
        arguments[tasks.length] = "--configuration-cache";
        arguments[tasks.length + 1] = "--configuration-cache-problems=fail";
        arguments[tasks.length + 2] = "--build-cache";
        arguments[tasks.length + 3] = "--stacktrace";
        arguments[tasks.length + 4] = "--console=plain";
        arguments[tasks.length + 5] = "--offline";
        BuildResult result = GradleRunner.create().withProjectDir(root.toFile())
                .withPluginClasspath().withArguments(arguments).build();
        try {
            Files.write(root.resolve("run-" + System.nanoTime() + ".log"), result.getOutput().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new AssertionError("无法保存回归日志", error);
        }
        return result;
    }

    private static void requireOutcome(BuildResult result, String task, TaskOutcome expected) {
        require(result.task(task) != null && result.task(task).getOutcome() == expected,
                task + " 应为 " + expected + "，实际为 " + (result.task(task) == null ? "未运行" : result.task(task).getOutcome()), result);
    }

    private static void require(boolean condition, String message, BuildResult result) {
        if (!condition) {
            throw new AssertionError(message + "\n" + result.getOutput());
        }
    }

    private static void requireEntry(Path path, String name, String expected) throws Exception {
        try (JarFile file = new JarFile(path.toFile())) {
            if (file.getJarEntry(name) == null) {
                throw new AssertionError("JAR 缺少条目：" + name);
            }
            byte[] content;
            try (java.io.InputStream input = file.getInputStream(file.getJarEntry(name));
                 java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    output.write(buffer, 0, size);
                }
                content = output.toByteArray();
            }
            if (!new String(content, StandardCharsets.UTF_8).contains(expected)) {
                throw new AssertionError("JAR 条目内容不匹配：" + name);
            }
        }
    }

    private static void requireRelocatedClass(Path jar) throws Exception {
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
                new java.net.URL[]{jar.toUri().toURL()}, null)) {
            Object value = loader.loadClass("fixture.after.Example").getMethod("value").invoke(null);
            if (!"正常重定位".equals(value)) {
                throw new AssertionError("重定位后类的真实调用结果不正确");
            }
        }
    }

    private static void requireRawJar(Path jar) throws Exception {
        try (JarFile file = new JarFile(jar.toFile())) {
            if (file.getJarEntry("plugin.yml") != null || file.getJarEntry("fixture/after/Example.class") != null
                    || file.getJarEntry("fixture/before/Example.class") == null) {
                throw new AssertionError("禁用或排除 taboolibMainTask 后必须保留原始 jar");
            }
        }
    }

    private static void write(Path root, String name, String content) throws Exception {
        Path path = root.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
