//file:noinspection unused
package io.izzel.taboolib.gradle

import io.izzel.taboolib.gradle.description.Description
import org.gradle.api.Action
import org.gradle.api.Project

class TabooLibExtension {

    /** 所属项目（用于 Fabric 模式在配置阶段装配依赖，须先于 Loom 的 afterEvaluate） */
    final transient Project project

    TabooLibExtension(Project project) {
        this.project = project
    }

    /**
     * 是否为子模块（不进行重定向，不产生描述文件）
     */
    boolean subproject = false

    /** 描述文件 */
    Description des = new Description()

    /** 环境文件 */
    Env env = new Env()

    /** 版本文件 */
    Version version = new Version()

    /** Fabric 配置（非空即启用 Fabric 模式） */
    Fabric fabric = null

    /** 排除文件 */
    List<String> exclude = []

    /** 重定向 */
    Map<String, String> relocation = new LinkedHashMap<>()

    /** 根包名 */
    String rootPackage = null

    /** 分类 */
    String classifier = null

    /** 排除文件 */
    def exclude(String match) {
        exclude += match
    }

    /** 重定向 */
    def relocate(String pre, String post) {
        relocation[pre] = post
    }

    /** 描述文件构造器 */
    def description(Action<? super Description> action) {
        action.execute(des)
    }

    /** 环境文件构造器 */
    def env(Action<? super Env> action) {
        action.execute(env)
    }

    /** 版本文件构造器 */
    def version(Action<? super Version> action) {
        action.execute(version)
    }

    /** Fabric 配置构造器（调用即进入 Fabric 模式；在配置阶段立即装配 Loom 依赖与元数据，先于 Loom 的 afterEvaluate） */
    def fabric(Action<? super Fabric> action) {
        if (fabric == null) {
            fabric = new Fabric()
        }
        action.execute(fabric)
        TabooLibPlugin.setupFabric(project, this)
    }
}