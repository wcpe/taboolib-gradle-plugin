package io.izzel.taboolib.gradle

import groovy.transform.ToString
import io.izzel.taboolib.gradle.description.Builder
import io.izzel.taboolib.gradle.description.Description
import io.izzel.taboolib.gradle.description.Platforms
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper

import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.CRC32
import java.util.zip.Deflater

@ToString
@DisableCachingByDefault(because = "Relocates and rewrites jar contents with runtime-dependent metadata.")
class TabooLibMainTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    File inJar

    @Input
    Map<String, String> relocations

    @Optional
    @Input
    String classifier

    @Input
    boolean api;

    // 配置缓存兼容：所有原从 TabooLibExtension / Project 读取的值
    // 均在配置期提取为 @Input / @Nested / @Internal 字段，
    // @TaskAction 中不再访问 project.extensions 或 project.hasProperty。

    @Input
    boolean subproject

    @Input
    List<String> exclude

    @Input
    boolean skipVersionFile

    @Input
    boolean skipPlatformFile

    @Input
    Set<String> modules

    @Internal
    Description pluginDescription

    @Input
    boolean envDebug

    @Input
    boolean forceDownloadInDev

    @Input
    String envRepoCentral

    @Input
    String envRepoTabooLib

    @Input
    String envFileLibs

    @Input
    String envFileAssets

    @Input
    boolean enableLegacyDependencyResolver

    @Input
    boolean enableIsolatedClassloader

    @Input
    boolean disableOnSkippedVersion

    @Input
    boolean disableOnUnsupportedVersion

    @Input
    boolean disableWhenPrimitiveLoaderError

    @Input
    boolean skipKotlin

    @Optional
    @Input
    String coroutinesVersion

    @Optional
    @Input
    String taboolibVersion

    @Input
    boolean skipKotlinRelocate

    @Input
    boolean skipTabooLibRelocate

    @Input
    String kotlinVersion

    @Internal
    boolean deleteCode

    // 配置缓存兼容：project.name/group/version 在配置期提取为 @Input 字段
    // 不在执行期访问 Task.project
    @Input
    String projectName

    @Input
    String projectGroup

    @Input
    String projectVersion

    @TaskAction
    def relocate() {
        if (subproject) {
            return
        }
        def totalStart = System.currentTimeMillis()
        // 配置
        def mapping = relocations.collectEntries { [(it.key.replace('.', '/')), it.value.replace('.', '/')] }
        def remapper = new RelocateRemapper(relocations, mapping as Map<String, String>)

        // 文件
        def index = inJar.name.lastIndexOf('.')
        def name = inJar.name.substring(0, index) + (classifier == null ? "" : "-" + classifier) + inJar.name.substring(index)
        def outJar = new File(inJar.getParentFile(), name)
        def tempOut1 = File.createTempFile(name, ".jar")

        // 并行处理 class 文件
        def nThreads = Runtime.getRuntime().availableProcessors()
        def pool = Executors.newFixedThreadPool(nThreads)
        def classCount = 0
        def resourceCount = 0
        try {
            // 收集处理结果（已预压缩），保持顺序
            def results = Collections.synchronizedList(new ArrayList<>())
            def readStart = System.currentTimeMillis()
            new JarFile(inJar).withCloseable { jarFile ->
                def entries = Collections.list(jarFile.entries())
                def futures = new ArrayList<Future>()
                entries.each { JarEntry jarEntry ->
                    def path = jarEntry.name
                    // 忽略用户定义的文件
                    if (exclude.stream().any { String e -> path.startsWith(e) }) {
                        return
                    }
                    // 预读字节，避免线程间共享 JarFile InputStream
                    def bytes = jarFile.getInputStream(jarEntry).withCloseable { it.bytes }
                    if (path.endsWith(".class")) {
                        classCount++
                        futures.add(pool.submit {
                            // 每个线程创建独立的 remapper 和 visitor
                            def threadRemapper = new RelocateRemapper(relocations, mapping as Map<String, String>)
                            def reader = new ClassReader(bytes)
                            def writer = new ClassWriter(0)
                            def visitor = new TabooLibClassVisitor(writer, deleteCode, skipTabooLibRelocate, relocations, projectName, projectVersion, projectGroup, api)
                            def rem = new ClassRemapper(visitor, threadRemapper)
                            threadRemapper.remapper = rem
                            reader.accept(rem, 0)
                            // 并行完成 ASM 重写 + 压缩，写入阶段无需再压缩
                            // 用类名显式限定，避免闭包内被 Gradle 任务动态方法分发拦截
                            def entry = TabooLibMainTask.toDeflatedEntry(threadRemapper.map(path), writer.toByteArray())
                            synchronized (results) {
                                results.add(entry)
                            }
                        })
                    } else {
                        resourceCount++
                        // 非 class 文件也提交到线程池并行压缩
                        futures.add(pool.submit {
                            def entry = TabooLibMainTask.toDeflatedEntry(remapper.map(path), bytes)
                            synchronized (results) {
                                results.add(entry)
                            }
                        })
                    }
                }
                // 等待所有处理完成
                futures.each { it.get() }
            }
            def asmTime = System.currentTimeMillis() - readStart
            // 串行写入 jar（条目已预压缩，写入阶段为纯 IO）
            def writeStart = System.currentTimeMillis()
            // 描述文件
            def extraEntries = new ArrayList<>()
            if (!skipVersionFile) {
                extraEntries.add(TabooLibMainTask.toDeflatedEntry("META-INF/taboolib/env.properties", buildEnv()))
                extraEntries.add(TabooLibMainTask.toDeflatedEntry("META-INF/taboolib/version.properties", buildVersion()))
            }
            // 插件文件
            if (!skipPlatformFile) {
                Platforms.values().each {
                    if (modules.contains(it.module)) {
                        extraEntries.add(TabooLibMainTask.toDeflatedEntry(it.file, it.builder.build(pluginDescription, projectName, projectGroup, projectVersion, skipTabooLibRelocate)))
                    }
                }
            }
            TabooLibMainTask.writeJar(tempOut1, results, extraEntries)
            def writeTime = System.currentTimeMillis() - writeStart
            def totalTime = System.currentTimeMillis() - totalStart
            println("[taboolibMainTask] ${classCount} classes, ${resourceCount} resources | ASM: ${asmTime}ms (${nThreads} threads), Write: ${writeTime}ms, Total: ${totalTime}ms")
        } finally {
            pool.shutdown()
        }
        Files.copy(tempOut1.toPath(), outJar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    /**
     * 将原始字节压缩为 zip DEFLATED 条目，并计算 CRC 与大小。
     * 压缩在并行线程池中完成，写入阶段只需顺序写出已压缩数据。
     */
    private static Map<String, Object> toDeflatedEntry(String name, byte[] data) {
        def deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true)
        try {
            deflater.setInput(data)
            deflater.finish()
            def baos = new ByteArrayOutputStream(Math.max(64, data.length >> 2))
            def buf = new byte[8192]
            while (!deflater.finished()) {
                int n = deflater.deflate(buf)
                if (n > 0) {
                    baos.write(buf, 0, n)
                }
            }
            def crc = new CRC32()
            crc.update(data)
            return [
                    name : name,
                    data : baos.toByteArray(),
                    crc  : crc.getValue(),
                    csize: (long) baos.size(),
                    usize: (long) data.length,
            ]
        } finally {
            deflater.end()
        }
    }

    /**
     * 手写 zip 容器，直接写入已预压缩的 DEFLATED 数据。
     * 相比 JarOutputStream：避免写入阶段串行压缩，并使用缓冲流减少 syscall。
     * 输出为标准 zip（DEFLATED 条目，已知大小，无数据描述符），行为与原实现兼容。
     */
    private static void writeJar(File file, List<Map<String, Object>> results, List<Map<String, Object>> extras) {
        // 去重，保留首次出现的条目（与原实现行为一致）
        def written = new HashSet<String>()
        def entries = new ArrayList<Map<String, Object>>()
        (results + extras).each { e ->
            if (written.add(e.name as String)) {
                entries.add(e)
            }
        }
        // 固定 DOS 时间戳 1980-01-01 00:00:00
        final int dosTime = 0x00210000
        final int gpf = 0x0800 // 文件名使用 UTF-8 编码
        new BufferedOutputStream(new FileOutputStream(file), 1 << 17).withCloseable { out ->
            // 中央目录缓冲
            def central = new ByteArrayOutputStream(1 << 16)
            long pos = 0
            entries.each { e ->
                byte[] nameBytes = (e.name as String).getBytes(StandardCharsets.UTF_8)
                byte[] data = e.data as byte[]
                long crc = e.crc as long
                long csize = e.csize as long
                long usize = e.usize as long
                long localHeaderOffset = pos
                // 本地文件头
                writeInt(out, 0x04034b50)
                writeShort(out, 20)
                writeShort(out, gpf)
                writeShort(out, 8) // DEFLATED
                writeInt(out, dosTime)
                writeInt(out, crc)
                writeInt(out, csize)
                writeInt(out, usize)
                writeShort(out, nameBytes.length)
                writeShort(out, 0) // extra
                out.write(nameBytes)
                out.write(data)
                pos = localHeaderOffset + 30 + nameBytes.length + csize
                // 中央目录记录
                writeInt(central, 0x02014b50)
                writeShort(central, 20)
                writeShort(central, 20)
                writeShort(central, gpf)
                writeShort(central, 8)
                writeInt(central, dosTime)
                writeInt(central, crc)
                writeInt(central, csize)
                writeInt(central, usize)
                writeShort(central, nameBytes.length)
                writeShort(central, 0) // extra
                writeShort(central, 0) // comment
                writeShort(central, 0) // disk number start
                writeShort(central, 0) // internal attrs
                writeInt(central, 0)   // external attrs
                writeInt(central, localHeaderOffset)
                central.write(nameBytes)
            }
            long cdOffset = pos
            byte[] cdBytes = central.toByteArray()
            out.write(cdBytes)
            // 结束中央目录记录
            writeInt(out, 0x06054b50)
            writeShort(out, 0)
            writeShort(out, 0)
            writeShort(out, entries.size())
            writeShort(out, entries.size())
            writeInt(out, cdBytes.length)
            writeInt(out, cdOffset)
            writeShort(out, 0) // comment
        }
    }

    private static void writeShort(OutputStream out, int v) {
        out.write(v & 0xff)
        out.write((v >>> 8) & 0xff)
    }

    private static void writeInt(OutputStream out, long v) {
        out.write((int) (v & 0xff))
        out.write((int) ((v >>> 8) & 0xff))
        out.write((int) ((v >>> 16) & 0xff))
        out.write((int) ((v >>> 24) & 0xff))
    }

    byte[] buildEnv() {
        def modulesSet = new HashSet<String>(modules)
        // 平台实现
        Platforms.values().each { p ->
            if (p.module in modulesSet && p.hasImpl) {
                modulesSet += p.module + "-impl"
            }
        }
        modulesSet.removeIf { i -> i.startsWith("common") }
        def file = Builder.startBukkitFile()
        file.addAll([
                "debug=" + envDebug,
                "force-download-in-dev=" + forceDownloadInDev,
                "repo-central=" + envRepoCentral,
                "repo-taboolib=" + envRepoTabooLib,
                "file-libs=" + envFileLibs,
                "file-assets=" + envFileAssets,
                "enable-legacy-dependency-resolver=" + enableLegacyDependencyResolver,
                "enable-isolated-classloader=" + enableIsolatedClassloader,
                "disable-on-skipped-version=" + disableOnSkippedVersion,
                "disable-on-unsupported-version=" + disableOnUnsupportedVersion,
                "disable-when-primitive-loader-error=" + disableWhenPrimitiveLoaderError,
                "module=" + modulesSet.join(',')
        ])
        return file.join('\n').getBytes(StandardCharsets.UTF_8)
    }

    byte[] buildVersion() {
        def file = Builder.startBukkitFile()
        file.addAll([
                "kotlin=" + (skipKotlin ? "null" : kotlinVersion),
                "kotlin-coroutines=" + coroutinesVersion,
                "taboolib=" + taboolibVersion,
                "skip-kotlin-relocate=" + skipKotlinRelocate,
                "skip-taboolib-relocate=" + skipTabooLibRelocate
        ])
        return file.join('\n').getBytes(StandardCharsets.UTF_8)
    }

    @Override
    String toString() {
        return "TabooLibMainTask{}";
    }
}
