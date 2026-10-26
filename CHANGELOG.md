# 更新日志

## 未发布

- 移除 `afterEvaluate` / `gradle.projectsEvaluated`，插件改为纯惰性求值，兼容 Gradle 按需配置（`org.gradle.configureondemand=true`）。
  此前在 CoD 下对已求值工程注册监听会抛 `cannot be executed in the current context`；`projectsEvaluated` 则属配置缓存禁止项。
- 依赖注入改用 `Provider` + `Configuration.dependencies.addAllLater`：TabooLib 模块、协程依赖均在依赖集合被观察时求值，
  求值时机仍然晚于用户脚本里的 `taboolib { }`，但不再依赖「配置期末尾」这个隐式钩子。
- 新增 `TabooLibRelocationSource` 承载所有扩展取值的 `Provider`，重定位输入 / 输出 / 末尾动作在取值前统一同步。
  修复了一个**此前就存在、容易被误判为生效**的坑：`jar` 若在脚本里被提前实例化（`tasks.jar { manifest { ... } }`），
  旧实现在 `jar.configure` 里读扩展会拿到默认值，产物缺 `plugin.yml`、类也不重定位，且不报错。
  现在取值点推迟到任务输入快照 / 动作执行时，提前实例化也能拿到最终配置。
- `subproject = true` 的模块走精简路径：不再挂重定位动作与重定位输入输出、不再生成 `plugin.yml` /
  版本文件 / 平台文件、`taboolibMainTask` 不进入任务图。`subproject = false` 的产物与 `wcpe.3` **逐字节一致**（已实测 sha256 相同）。
- `taboolibMainTask` 与 `taboolibRefreshDependencies` / `taboolibBuildApi` 改为惰性注册：未被请求时不实例化。
  刷新任务改为独立任务类型（原先的 `doLast` 闭包会捕获脚本对象，配置缓存会判失败）。
- 重定位配置对象改由插件提前创建、任务创建时 `adoptRelocationConfiguration` 接收同一实例，
  保证「任务属性」与「jar 实际使用的配置」仍是同一份数据（`-x` 排除、`enabled = false` 语义不变）。
- `functionalTest` 新增四组回归：提前实例化 `jar` 仍生效、`subproject` 精简路径、按需配置无上下文异常、构建期仓库顺序（见下条 wcpe.3）。

## 2.0.39-wcpe.3 - 2026-10-23

- 修复 `2.0.39-wcpe.2` 引入的构建期依赖解析失败：NMS 平台工件（`ink.ptms.core:v12004`、`v12110` 等）无法解析。
  上一版把构建期仓库整个替换成 wcpe，而上游 `repo.tabooproject.org` 上的 NMS jar 在 wcpe 上缺失
  （`v12004-12004.pom` 返回 200，但 `-universal.jar` / `-mapped.jar` 均返回 404）。
  Gradle 在某个仓库取到 metadata 后即认定 artifact 归属该仓库、**不会回退到下一个仓库**，
  因此这类依赖直接失败，且报错只列出 wcpe 一个地址。
- 构建期仓库改为「上游在前 + wcpe 在后」：
  `Env` 新增 `repoTabooLibUpstream`（上游）与 `repoTabooLibGroup`（`io.izzel.taboolib`），
  wcpe 仓库通过 `content { includeGroup(...) }` 只接管 TabooLib 定制模块分组；
  上游仓库不加分组过滤，保证 NMS 等工件可达。
- 澄清 `Env.repoTabooLib` 的职责：它**仅**是运行期自举地址（写入产物 `env.properties` 的 `repo-taboolib`），
  不再是构建期唯一仓库来源。运行期安全性来自 common-env 的 `PrimitiveSettings.buildRepoList()` ——
  它会解析该值（支持 `,`/`;` 分隔多地址）后无条件追加上游兜底仓库，
  并由 `PrimitiveLoader.load()` 逐仓库尝试、失败即回退。故产物中仍写单个 wcpe 地址。
- `functionalTest` 新增仓库配置回归：断言上游仓库存在且排在 wcpe 之前、wcpe 限定到
  `io.izzel.taboolib` 分组、上游仓库不被分组过滤。已反向验证该断言能捕获上一版缺陷。

## 2.0.39-wcpe.2 - 2026-10-22

- 依赖下载源默认改为 `https://repo.wcpe.top/repository/maven-public/`。
  原先 `Env.repoTabooLib` 默认指向上游 `repo.tabooproject.org`，而该地址上没有
  WCPE 定制版工件（`X.Y.Z-wcpe.N`），取不到就返回 404。
  这个默认值会被写进产物 `META-INF/taboolib/env.properties` 的 `repo-taboolib`，
  且该文件**优先于**运行期默认值（common-env 的 `PrimitiveSettings` 默认已是 wcpe 私有仓库），
  于是所有用本插件构建的插件都带着一个取不到定制依赖的地址出厂 ——
  表现为启动时 `Failed to download common-env-*.jar`，随后插件被禁用。
- `TabooLibPlugin` 添加的解析仓库改为引用 `Env.repoTabooLib` 而非硬编码字面量：
  同一个地址既用于构建期解析、又被写进产物，两处必须同源，否则改一处仍会漏。

## 2.0.38-wcpe.3 - 2026-09-13

- 修复重定位任务在 `jar` 完成后覆写产物，导致下一轮构建重复打包的问题；由 `jar` 单独拥有最终产物及其缓存。
- 将平台描述、重定位规则顺序、删除规则及启停状态纳入缓存输入，固定 ZIP 条目顺序，清理重定位临时文件。
- 保留 `jar.archiveFile`、发行文件名及 `taboolibMainTask` 的原有属性和入口；禁用任务或通过 `-x` 排除任务（包括缩写）时仍产出原始 JAR。
- 增加真实消费工程回归，覆盖配置缓存、增量构建、构建缓存恢复、依赖与描述更新、可加载字节码、分类产物和错误恢复。
- 增加 Gradle 9.4.0、9.5.0 的 main/PR CI，正式 tag 发布前复用相同验证，明确 GitHub Release 的目标仓库并验证 tag 已存在。
