//file:noinspection unused
package io.izzel.taboolib.gradle

class Env {

    /** 调试模式 */
    boolean debug = false

    /** 开发模式强制下载 */
    boolean forceDownloadInDev = true

    /** 中央仓库地址 */
    String repoCentral = "https://maven.aliyun.com/repository/central"

    /**
     * TabooLib 仓库地址：**仅**作为运行期自举下载的仓库，会被写进产物
     * `META-INF/taboolib/env.properties` 的 `repo-taboolib`。
     *
     * WCPE 定制版（`X.Y.Z-wcpe.N`）只发布在 WCPE 私有仓库，上游 tabooproject 上是 404，
     * 因此这里必须指向 wcpe，否则产物会带着一个取不到定制工件的地址出厂
     * （Failed to download common-env-*.jar 后插件被禁用）。
     *
     * 写单个地址是安全的：common-env 的 `PrimitiveSettings.buildRepoList()` 会解析
     * `repo-taboolib`（支持 `,`/`;` 分隔多地址）后**无条件追加**上游兜底仓库
     * `https://repo.tabooproject.org/repository/releases`，而 `PrimitiveLoader.load()`
     * 逐个仓库尝试、失败即回退下一个。所以运行期「wcpe 优先、上游兜底」始终成立。
     *
     * 注意：这个值**不能**当作构建期唯一的 Gradle 仓库 —— 见 {@link #repoTabooLibUpstream}。
     */
    String repoTabooLib = "https://repo.wcpe.top/repository/maven-public/"

    /**
     * 构建期 Gradle 的 TabooLib 上游仓库，与 {@link #repoTabooLib} 是两件不同的事。
     *
     * NMS 平台工件（如 `ink.ptms.core:v12004:12004`、`v12110`）只发布在这里：
     * wcpe 私有仓库上它们**只有 POM 没有 jar**（POM 返回 200、`-universal.jar` 返回 404）。
     * 而 Gradle 只要在某个仓库取到了 metadata，就认定 artifact 属于该仓库、
     * **不会回退到下一个仓库**，于是把 wcpe 放在前面会让这类依赖直接解析失败
     * （报错只列出 wcpe 一个地址）。因此上游仓库必须排在 wcpe 之前。
     */
    String repoTabooLibUpstream = "https://repo.tabooproject.org/repository/releases"

    /**
     * WCPE 私有仓库在构建期接管的分组。
     *
     * 只让 wcpe 参与 `io.izzel.taboolib` 的解析，避免它在自己缺失的工件上
     * 「命中 metadata 却不提供 jar」而截断其它分组的回退路径。
     */
    String repoTabooLibGroup = "io.izzel.taboolib"

    /** Library 文件 */
    String fileLibs = "libraries"

    /** Assets 文件 */
    String fileAssets = "assets"

    /** 启用隔离加载器（完全隔离模式）*/
    boolean enableIsolatedClassloader = false

    /** 非开发者模式下是否在被跳过版本上关闭插件 */
    boolean disableOnSkippedVersion = true

    /** 非开发者模式下是否在不支持版本上关闭插件 */
    boolean disableOnUnsupportedVersion = true

    /** 在原始加载器初始化失败时是否强制关闭服务器 */
    boolean disableWhenPrimitiveLoaderError = false

    /** 强制使用旧版依赖处理工具 */
    boolean enableLegacyDependencyResolver = false

    /** 安装模块 */
    Set<String> modules = ["common", "common-env", "common-util", "common-legacy-api", "common-platform-api", "common-reflex"]

    /** 安装模块 */
    def install(Object... name) {
        name.each {
            if (it instanceof String[]) modules.addAll(it)
            else modules += it.toString()
        }
    }
}
