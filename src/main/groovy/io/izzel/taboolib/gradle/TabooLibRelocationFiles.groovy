package io.izzel.taboolib.gradle

import java.util.concurrent.Callable

/** 默认输入即 jar 自身输出；显式外部输入和分类产物另外交给 Gradle 跟踪。 */
class TabooLibRelocationFiles implements Callable<List<File>>, Serializable {

    private final TabooLibRelocation configuration
    private final File archiveFile
    private final boolean inputs

    TabooLibRelocationFiles(TabooLibRelocation configuration, File archiveFile, boolean inputs) {
        this.configuration = configuration
        this.archiveFile = archiveFile
        this.inputs = inputs
    }

    @Override
    List<File> call() {
        if (configuration.subproject || !configuration.enabled || configuration.excluded) {
            return []
        }
        File file = inputs ? configuration.inJar : configuration.outputJar
        return file == archiveFile ? [] : [file]
    }
}
