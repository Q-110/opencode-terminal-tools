# AI Terminal Tools

AI Terminal Tools 是一个 JetBrains IDE 插件，为终端、控制台和 Commit 面板提供五类增强功能：文件跳转、点击复制、AI 终端发送、[OpenCode](https://opencode.ai/) / [Claude Code](https://docs.anthropic.com/en/docs/claude-code/overview) 启动，以及提交信息生成。

## 功能详解

### 文件跳转

将终端输出中的文件引用自动识别为可点击超链接，点击后跳转到 IDE 编辑器中的对应文件位置，支持行号和行范围选中。

支持的文件引用格式：

```text
ExampleController.java
ExampleController.java:22
ExampleController.java:22-30
src/main/java/com/example/ExampleController.java:22
./src/main/java/com/example/ExampleController.java:22-30
../module/src/main/java/com/example/ExampleController.java:22
C:\Projects\demo\src\main\java\com\example\ExampleController.java:22
/projects/demo/src/main/java/com/example/ExampleController.java:22
@src/main/java/com/example/ExampleController.java:10
```

路径解析规则：

- 优先以路径后缀匹配项目文件。
- 支持扩展名：`java`, `kt`, `kts`, `js`, `jsx`, `ts`, `tsx`, `vue`, `xml`, `html`, `css`, `scss`, `yml`, `yaml`, `properties`, `sql`, `md`。
- `@路径` 引用优先级更高，用于匹配 AI 终端中的路径引用。

当多个文件匹配同一引用时，插件会根据路径特征打分排序；若仍有多个候选，弹出选择对话框供用户手动选取。

### 点击复制

终端输出中的结构化文本片段会被识别为可点击链接，点击后自动复制到系统剪贴板，并在链接上方显示“已复制”提示。

支持的复制模式包括 `{{...}}`、`[[...]]`、函数调用、URL、点号链、引号字符串、标识符和数字。点击复制链接不会占用文件跳转链接的区间，两者互不干扰。

### 控制台错误发送

Run/Debug Console 中的 Java/JVM 异常首行会显示发送图标，点击后自动将当前可见异常段发送到当前激活的 Terminal。

发送范围：

- 从异常首行开始，例如 `Caused by: java.net.ConnectException: Connection timed out: connect`。
- 包含后续连续的 `at ...(...)` 调用栈行。
- 不包含 `... N common frames omitted`、`<N folded frames>`、`Disconnected from ...` 或 `Process finished ...`。

发送格式：

```text
控制台错误：
-------
<异常首行和连续 stack frames>
-------
```

如果一段 stack trace 中有多个 `Caused by:`，每个异常段会独立显示图标，点击不同图标只发送对应异常段。

可在 Settings → Tools → AI Terminal Tools 中通过“启用控制台错误发送图标”开关启用或关闭。
可在 Settings → Tools → AI Terminal Tools 中通过"启用拖拽文件/文件夹到 AI 终端"开关启用或关闭拖拽功能，默认开启。开启后拖拽文件/文件夹到任意终端均发送为 @路径；关闭后仅插件启动的 OpenCode / Claude Code 终端生效。

### AI 终端发送

将 IDE 编辑器中的选区、文件路径或控制台错误发送到当前激活的 Terminal 输入区。目标终端可以是 OpenCode，也可以是 Claude Code。

发送流程：

```text
IDE 编辑器选区 / 文件路径 / 控制台错误
      |
      v
定位当前 DataContext 或当前激活的 Terminal
      |
      v
写入当前终端输入区
      |
      v
OpenCode 或 Claude Code 接收内容
```

多行选区和控制台错误使用 bracketed paste 写入，文件路径使用普通输入并自动结束 `@路径` 补全状态。找不到可写终端时，会提示“请先启动并激活 OpenCode 或 Claude Code 终端。”

快速开始：

1. 点击 IDE 工具栏的“启动 OpenCode”或“启动 Claude Code”。
2. 插件自动创建新终端标签页并分别运行 `opencode` 或 `claude`。
3. 激活目标终端标签页。
4. 在编辑器中选中代码，按 `Ctrl+Alt+,` 发送到当前激活终端。

手动启动时，只需要让目标终端标签页保持激活，再执行发送动作即可。

发送选区：

- 快捷键：`Ctrl+Alt+,`
- 菜单：编辑器右键 → 发送选区到 AI Terminal
- 格式：

```text
src/main/java/A.java:10-20
-------
<selected code>
-------
```

发送文件或文件夹路径：

- 项目视图右键：发送文件/文件夹路径到 AI Terminal
- 编辑器标签页右键：发送文件路径到 AI Terminal
- 终端拖拽：将项目视图中的文件或文件夹拖到当前激活终端
- 发送格式为 `@displayPath`
- 一次拖拽多个文件或文件夹时，会发送为同一行 `@路径 @路径`，末尾保留空格以结束补全。
- 插件会记录通过"启动 OpenCode"或"启动 Claude Code"创建的 AI 终端，只有记录的 AI 终端才会被拖拽接管。
- 关闭"启用拖拽文件/文件夹到 AI 终端"设置后，仅插件启动的终端保留拖拽接管。


发送控制台错误：

- 控制台触发：点击异常首行中的发送图标
- 格式：

```text
控制台错误：
-------
<当前异常段>
-------
```

### 生成中文提交信息

Commit 面板工具栏会显示“生成提交信息”动作，点击后仍使用 OpenCode 根据当前 Commit 面板中已勾选的文件生成简要提交信息。

可在 Settings → Tools → AI Terminal Tools 的“提交信息模型”字段配置 OpenCode 的 `provider/model` 格式模型，例如：

```text
openai/gpt-4.1
```

留空时使用 OpenCode 默认模型（可在 [`%USERPROFILE%\.config\opencode\opencode.json`](https://opencode.ai/docs/models/) 中配置）。

## 快捷键

| 动作 | 默认快捷键 | 触发方式 |
|------|-----------|---------|
| 发送选区到 AI Terminal | `Ctrl+Alt+,` | 编辑器内快捷键 / 右键菜单 |
| 发送文件路径到 AI Terminal | 无 | 项目视图 / 编辑器标签页右键菜单 / 拖拽到当前激活终端 |
| 发送控制台错误到 AI Terminal | 无 | 控制台异常行图标 |
| 生成提交信息 | 无 | Commit 面板工具栏 |
| 启动 OpenCode | 无 | 工具栏按钮 |
| 启动 Claude Code | 无 | 工具栏按钮 |

## 项目架构

```text
src/main/kotlin/io/github/q110/aiterminaltools/
|
├── bridge/
│   ├── AiTerminalBridgeService.kt
│   ├── FrontendTerminalHelper.kt
│   ├── LegacyReworkedTerminalHelper.kt
│   ├── SendSelectionToAiTerminalAction.kt
│   ├── SendPathToAiTerminalAction.kt
│   ├── StartOpenCodeAction.kt
│   ├── StartClaudeCodeAction.kt
│   ├── GenerateCommitMessageAction.kt
│   ├── AiTerminalDropService.kt
│   └── AiTerminalToolsMenuRegistrar.kt
|
├── filter/
│   ├── AiTerminalToolsFilter.kt
│   ├── AiTerminalToolsFilterProvider.kt
│   ├── FilterPatterns.kt
│   └── PathUtils.kt
|
├── jump/
│   ├── FileReferenceHyperlinkInfo.kt
│   ├── FolderReferenceHyperlinkInfo.kt
│   └── FileChoiceDialog.kt
|
├── copy/
│   └── CopyTextHyperlinkInfo.kt
|
├── console/
│   ├── ConsoleErrorBlockParser.kt
│   └── AiConsoleErrorInlayService.kt
|
└── settings/
    ├── AiTerminalToolsSettings.kt
    └── AiTerminalToolsConfigurable.kt
```

终端兼容层：

- Frontend：IDE 2025.3+ 使用 `TerminalToolWindowTabsManager`。
- Legacy Reworked：IDE 2025.1 到 2025.2 通过反射调用旧 Reworked Terminal API。
- Classic：回退到 `ShellTerminalWidget` 和 TTY Connector。

## 构建与运行

环境要求：

- [IntelliJ IDEA Ultimate 2025.3](https://www.jetbrains.com/idea/download/)，或通过 `-P` 参数切换 IDE 类型和版本。
- [JDK 17](https://www.jetbrains.com/help/idea/sdk.html)。
- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)。

常用命令：

```powershell
.\gradlew.bat runIde
.\gradlew.bat buildPlugin -PplatformVersion=2025.3 -PplatformType=IU
.\gradlew.bat buildPlugin
```

## 插件信息

| 项目 | 值 |
|------|-----|
| 插件 ID | `io.github.q110.aiterminaltools` |
| 当前版本 | `0.0.1` |
| Group | `io.github.q110` |
| Vendor | `zibo` |
| 许可证 | [MIT License](https://opensource.org/license/mit/) |

## 许可证

本项目基于 MIT License 开源。详见 [LICENSE](./LICENSE) 文件。
