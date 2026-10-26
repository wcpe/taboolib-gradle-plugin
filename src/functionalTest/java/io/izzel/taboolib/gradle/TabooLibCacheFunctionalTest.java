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
        requireRepositoryOrder(root, args[0]);
        requireEarlyJarRealization(root, args[0]);
        requireSubprojectLeanPath(root, args[0]);
        requireConfigureOnDemand(root, args[0]);
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

    /**
     * 固定构建期仓库的两个不变量，防止再次把上游仓库从列表里挤掉。
     *
     * 背景：wcpe 私有仓库上 NMS 平台工件（ink.ptms.core:*）只有 POM、没有 jar。
     * Gradle 在某个仓库取到 metadata 后就认定 artifact 归属该仓库、不会回退，
     * 所以 wcpe 一旦排在前面，这类依赖会直接解析失败且报错只列出 wcpe 一个地址。
     * 这里不联网，只断言插件注册的仓库顺序与内容过滤。
     */
    private static void requireRepositoryOrder(Path parent, String base) throws Exception {
        Path root = parent.resolve("repo-order-fixture");
        Files.createDirectories(root);
        write(root, "settings.gradle", "rootProject.name = 'repo-order-fixture'\n");
        write(root, "build.gradle", "plugins { id 'java-library'; id 'io.izzel.taboolib' }\n"
                + "group = 'fixture'; version = '1.0'\n"
                + "taboolib { version { taboolib = null; coroutines = null } }\n"
                + "afterEvaluate { configurations.compileOnly.dependencies.clear() }\n"
                + "tasks.register('dumpRepos') { doLast {\n"
                + "    def out = new StringBuilder()\n"
                + "    repositories.each { r ->\n"
                + "        if (!(r instanceof MavenArtifactRepository)) { return }\n"
                + "        def groups = []\n"
                + "        try {\n"
                + "            def filter = r.properties['contentFilter']\n"
                + "            filter.includeMatchers.each { m -> groups << m.properties['group'] }\n"
                + "        } catch (Throwable ignored) {\n"
                + "            // 无内容过滤时 contentFilter 为 NullAction，视为不限定分组\n"
                + "        }\n"
                + "        out.append('REPO ').append(r.url).append(' GROUPS=').append(groups).append('\\n')\n"
                + "    }\n"
                + "    println out.toString()\n"
                + "} }\n");
        BuildResult result = GradleRunner.create().withProjectDir(root.toFile())
                .withPluginClasspath().withArguments("dumpRepos", "--offline", "--console=plain").build();
        String output = result.getOutput();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line.startsWith("REPO ")) {
                lines.add(line.trim());
            }
        }
        require(lines.size() >= 2, "插件未注册足够的 Maven 仓库，实际：" + lines, result);
        String upstream = "https://repo.tabooproject.org/repository/releases";
        int upstreamIndex = -1;
        int wcpeIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains(upstream)) {
                upstreamIndex = i;
            }
            if (line.contains("https://repo.wcpe.top/repository/maven-public")) {
                wcpeIndex = i;
            }
        }
        require(upstreamIndex >= 0, "构建期缺失上游仓库（NMS 工件只在上游发布）：" + lines, result);
        require(wcpeIndex >= 0, "构建期缺失 wcpe 仓库（定制版模块只在 wcpe 发布）：" + lines, result);
        require(upstreamIndex < wcpeIndex,
                "上游仓库必须排在 wcpe 之前，否则 NMS 依赖会在 wcpe 的 POM 命中处直接失败：" + lines, result);
        require(lines.get(wcpeIndex).contains("io.izzel.taboolib"),
                "wcpe 仓库必须限定到 io.izzel.taboolib 分组，避免截断其它分组的回退：" + lines, result);
        require(!lines.get(upstreamIndex).contains("io.izzel.taboolib"),
                "上游仓库不应被分组过滤，否则上游工件不可达：" + lines, result);
    }

    /**
     * 验证 `jar` 在 `taboolib { }` 之前被提前实例化时仍然读到最终配置。
     *
     * 背景：插件去掉了 afterEvaluate，改为延迟取值。如果实现退化成「在 jar.configure 里
     * 直接快照扩展」，那么用户脚本里 `tasks.jar { manifest { ... } }` 这种常见写法会
     * 让快照发生在 `taboolib { }` 之前 —— 产物缺 plugin.yml、类也没被重定位，
     * 而且不报错。这个用例专门钉死该行为。
     */
    private static void requireEarlyJarRealization(Path parent, String base) throws Exception {
        Path root = parent.resolve("early-jar-fixture");
        Files.createDirectories(root);
        write(root, "settings.gradle", "rootProject.name = 'early-jar-fixture'\ninclude 'library'\n");
        write(root, "build.gradle", "plugins { id 'java-library'; id 'io.izzel.taboolib' }\n"
                + "group = 'fixture'; version = '1.0'\n"
                // 关键：在 taboolib { } 之前提前实例化 jar
                + "tasks.jar { manifest { attributes('X-Early': 'yes') } }\n"
                + "taboolib {\n"
                + "    relocate 'fixture.before', 'fixture.after'\n"
                + "    env { install('platform-bukkit') }\n"
                + "    version { taboolib = null; coroutines = null; skipKotlinRelocate = true; skipTabooLibRelocate = true }\n"
                + "    description { desc('提前实例化仍生效') }\n"
                + "}\n"
                + "dependencies { taboo project(':library') }\n"
                + "afterEvaluate { configurations.compileOnly.dependencies.clear() }\n"
                + "tasks.named('jar') { dependsOn(':library:jar') }\n");
        write(root, "library/build.gradle", "plugins { id 'java-library' }\n");
        write(root, "library/src/main/resources/library.txt", "依赖内容\n");
        write(root, "src/main/java/fixture/before/Example.java",
                "package fixture.before; public class Example { public static String value() { return \"提前实例化\"; } }\n");

        run(root, "jar");
        Path jar = root.resolve("build/libs/early-jar-fixture-1.0.jar");
        // 期望：重定位与元数据都生效
        requireEntry(jar, "plugin.yml", "提前实例化仍生效");
        try (JarFile file = new JarFile(jar.toFile())) {
            if (file.getJarEntry("fixture/after/Example.class") == null) {
                throw new AssertionError("jar 提前实例化时扩展配置未被读到：类没有重定位");
            }
        }
    }

    /**
     * 验证 `subproject = true` 的精简路径。
     *
     * 这类模块会被内联进主插件，自己既不产出插件 jar 也不做重定位，
     * 因此必须：不重定位、不写 plugin.yml / 版本文件 / 平台文件、不把 taboolibMainTask 拉进任务图。
     */
    private static void requireSubprojectLeanPath(Path parent, String base) throws Exception {
        Path root = parent.resolve("subproject-fixture");
        Files.createDirectories(root);
        write(root, "settings.gradle", "rootProject.name = 'subproject-fixture'\n");
        write(root, "build.gradle", "plugins { id 'java-library'; id 'io.izzel.taboolib' }\n"
                + "group = 'fixture'; version = '1.0'\n"
                + "taboolib {\n"
                + "    subproject = true\n"
                + "    relocate 'fixture.before', 'fixture.after'\n"
                + "    env { install('platform-bukkit') }\n"
                + "    version { taboolib = null; coroutines = null; skipKotlinRelocate = true; skipTabooLibRelocate = true }\n"
                + "    description { desc('精简路径') }\n"
                + "}\n"
                + "afterEvaluate { configurations.compileOnly.dependencies.clear() }\n");
        write(root, "src/main/java/fixture/before/Example.java",
                "package fixture.before; public class Example { public static String value() { return \"精简\"; } }\n");

        run(root, "assemble");
        Path jar = root.resolve("build/libs/subproject-fixture-1.0.jar");
        try (JarFile file = new JarFile(jar.toFile())) {
            if (file.getJarEntry("plugin.yml") != null) {
                throw new AssertionError("subproject 模块不应生成 plugin.yml");
            }
            if (file.getJarEntry("META-INF/taboolib/env.properties") != null) {
                throw new AssertionError("subproject 模块不应生成版本 / 环境描述文件");
            }
            if (file.getJarEntry("fixture/after/Example.class") != null) {
                throw new AssertionError("subproject 模块不应重定位");
            }
            if (file.getJarEntry("fixture/before/Example.class") == null) {
                throw new AssertionError("subproject 模块应保留原始类");
            }
        }
        // 精简路径下 taboolibMainTask 不应出现在任务图里
        BuildResult dryRun = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
                .withArguments("assemble", "--dry-run", "--offline", "--console=plain").build();
        require(!dryRun.getOutput().contains("taboolibMainTask"),
                "subproject 模块的任务图不应包含 taboolibMainTask", dryRun);
    }

    /**
     * 验证按需配置（CoD）下不再抛 "cannot be executed in the current context"。
     *
     * 插件此前用 afterEvaluate，在 CoD 下对已求值的工程注册监听会直接抛该异常。
     * 这里用一个多模块工程、只为其中一个模块请求任务，强制走「按需配置」路径。
     */
    private static void requireConfigureOnDemand(Path parent, String base) throws Exception {
        Path root = parent.resolve("cod-fixture");
        Files.createDirectories(root);
        write(root, "settings.gradle", "rootProject.name = 'cod-fixture'\ninclude 'other'\n");
        write(root, "gradle.properties", "org.gradle.configureondemand=true\norg.gradle.jvmargs=-Xmx512m\n");
        write(root, "build.gradle",
                "plugins { id 'java-library'; id 'io.izzel.taboolib' }\n"
                        + "group = 'fixture'; version = '1.0'\n"
                        + "taboolib {\n"
                        + "    env { install('platform-bukkit') }\n"
                        + "    version { taboolib = null; coroutines = null; skipKotlinRelocate = true; skipTabooLibRelocate = true }\n"
                        + "    description { desc('按需配置') }\n"
                        + "}\n"
                        + "afterEvaluate { configurations.compileOnly.dependencies.clear() }\n");
        // other 也应用插件：CoD 下它不被请求，插件仍不得对它注册求值钩子
        write(root, "other/build.gradle", "plugins { id 'java-library'; id 'io.izzel.taboolib' }\n"
                + "group = 'fixture'; version = '1.0'\n"
                + "taboolib { version { taboolib = null; coroutines = null; skipKotlinRelocate = true; skipTabooLibRelocate = true } }\n"
                + "afterEvaluate { configurations.compileOnly.dependencies.clear() }\n");

        BuildResult result = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
                .withArguments("jar", "--offline", "--console=plain").build();
        require(!result.getOutput().contains("cannot be executed in the current context"),
                "按需配置下不应出现 afterEvaluate 上下文异常", result);
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
