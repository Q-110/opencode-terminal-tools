# Opencode Short File Links

IntelliJ IDEA 插件，将终端/控制台输出中的文件引用（如 `FileName.java:932` 或 `src/main/java/FileName.java:10-20`）转换为可点击链接，点击后自动跳转至对应文件的指定行。

## 功能特性

- **智能匹配**：唯一匹配直接跳转；多个同名文件优先使用路径缓存与评分系统自动选择最佳文件
- **多文件选择**：最高得分并列或无法自动消歧时弹出对话框手动选择
- **行范围支持**：支持 `FileName.java:10-20` 和 `src/main/java/FileName.java:10-20` 格式，点击后选中指定行范围
- **路径支持**：支持短文件名、相对路径、Windows 绝对路径、Unix 绝对路径
- **支持 17 种文件类型**：java, kt, kts, js, jsx, ts, tsx, vue, xml, html, css, scss, yml, yaml, properties, sql, md
- **零侵入**：纯 IDEA 端实现，不修改 opencode，不影响终端流程

## 支持格式

```text
HomeController.java
HomeController.java:22
HomeController.java:22-30
src/main/java/com/example/HomeController.java:22
./src/main/java/com/example/HomeController.java:22-30
../other/src/main/java/com/example/HomeController.java:22
F:\webSpace\project\src\main\java\com\example\HomeController.java:22
/home/user/project/src/main/java/com/example/HomeController.java:22
```

路径匹配面向常见无空格控制台输出，暂不支持带空格路径。

## 使用方式

1. 在 IDEA 中打开本项目
2. 按需修改 `gradle.properties` 中的 `localIdePath`
3. 执行 Gradle 任务 `runIde` 启动沙盒 IDE，或执行 `buildPlugin` 生成插件包后手动安装
4. 在沙盒 IDE 中打开目标项目，终端运行 opencode
5. 输出中出现 `FileName.ext:line` 或路径引用时，点击即可跳转

## 技术栈

| 项目 | 版本 |
|------|------|
| Kotlin | 2.2.21 |
| JVM | 17 |
| IntelliJ Platform Plugin | 2.12.0 |
| 最低支持 IDE | 2025.3+ |

## 插件信息

| 项目 | 值 |
|------|----|
| Plugin ID | `io.github.q110.opencodelinks` |
| Group | `io.github.q110` |
| Vendor | `Q-110` |

## 注意事项

当前基于 `ConsoleFilterProvider` 实现。若你的 IDEA 版本或终端引擎未将该过滤器应用到普通 Terminal Tool Window，需额外实现 Reworked Terminal 专用适配。
