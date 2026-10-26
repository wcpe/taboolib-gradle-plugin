package io.izzel.taboolib.gradle

import java.util.concurrent.Callable

/**
 * 默认输入即 jar 自身输出；显式外部输入和分类产物另外交给 Gradle 跟踪。
 *
 * 求值前同样先同步扩展配置（见 [TabooLibRelocationSource]），
 * 避免在用户脚本执行前就按默认值算出「不需要额外文件」的错误结论。
 */
class TabooLibRelocationFiles implements Callable<List<File>>, Serializable {

    private final TabooLibRelocation configuration

    private final TabooLibRelocationSource source

    private final File archiveFile

    private final boolean inputs

    TabooLibRelocationFiles(TabooLibRelocation configuration, TabooLibRelocationSource source,
                            File archiveFile, boolean inputs) {
        this.configuration = configuration
        this.source = source
        this.archiveFile = archiveFile
        this.inputs = inputs
    }

    @Override
    List<File> call() {
        source.applyTo(configuration)
        if (configuration.subproject || !configuration.enabled || configuration.excluded) {
            return []
        }
        File file = inputs ? configuration.inJar : configuration.outputJar
        return file == archiveFile ? [] : [file]
    }
}
