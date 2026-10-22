# 更新日志

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
