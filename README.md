# Console Links

IntelliJ IDEA 插件，将终端/控制台输出中的文件引用（如 `FileName.java:932` 或 `src/main/java/FileName.java:10-20`）转换为可点击链接，点击后自动跳转至对应文件的指定行。

## 功能特性

- **智能匹配**：唯一匹配直接跳转；多个同名文件优先使用路径缓存与评分系统自动选择最佳文件
- **多文件选择**：最高得分并列或无法自动消歧时弹出对话框手动选择
- **行范围支持**：支持 `FileName.java:10-20` 和 `src/main/java/FileName.java:10-20` 格式，点击后选中指定行范围
- **路径支持**：支持短文件名、相对路径、Windows 绝对路径、Unix 绝对路径
- **点击复制**：支持点击复制终端/控制台输出中的文本片段，例如结构化代码片段、普通英文标识符和普通数字
- **支持 17 种文件类型**：java, kt, kts, js, jsx, ts, tsx, vue, xml, html, css, scss, yml, yaml, properties, sql, md
- **零侵入**：纯 IDEA 端实现，不修改命令行工具，不影响终端流程

## 支持格式

```text
ExampleController.java
ExampleController.java:22
ExampleController.java:22-30
src/main/java/com/example/ExampleController.java:22
./src/main/java/com/example/ExampleController.java:22-30
../module/src/main/java/com/example/ExampleController.java:22
C:\Projects\demo\src\main\java\com\example\ExampleController.java:22
/projects/demo/src/main/java/com/example/ExampleController.java:22
```

路径匹配面向常见无空格控制台输出，暂不支持带空格路径。

## 点击复制

除文件跳转链接外，插件会将终端/控制台输出中的文本片段转换为可点击复制链接。文件跳转优先，如果文本同时符合文件引用格式，点击仍执行跳转。
复制链接不添加额外视觉样式，尽量保持终端原本的颜色和显示效果。

支持复制的常见格式：

```text
Plan
DeepSeek
120
loadData()
showPanel(true)
self.items[self.currentIndex]
loadData/loadDataV2
/api/items?name=
sample.geojson
[[204,147,73] ... [55,130,204]]
["alpha","beta","gamma"]
null
NaN
```

该能力基于文本规则识别常见结构化输出片段，不读取终端颜色属性，因此不保证所有 ANSI 彩色文本都能被识别。

## 使用方式

1. 在 IDEA 中打开本项目
2. 按需修改 `gradle.properties` 中的 `localIdePath`
3. 执行 Gradle 任务 `runIde` 启动沙盒 IDE，或执行 `buildPlugin` 生成插件包后手动安装
4. 在沙盒 IDE 中打开目标项目，并在终端/控制台中运行需要观察输出的命令
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
| Plugin ID | `io.github.q110.consolelinks` |
| Group | `io.github.q110` |
| Vendor | `zibo` |

## 注意事项

当前基于 `ConsoleFilterProvider` 实现。若你的 IDEA 版本或终端引擎未将该过滤器应用到普通 Terminal Tool Window，需额外实现 Reworked Terminal 专用适配。
