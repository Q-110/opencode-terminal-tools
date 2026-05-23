# OpenCode Terminal Tools

OpenCode Terminal Tools 是一个 JetBrains IDE 终端/控制台增强插件，提供文件跳转、点击复制、OpenCode 桥接三大功能。

## 功能

- 文件跳转：支持 `FileName.java:22`、`src/main/java/A.java:10-20` 等格式，点击后跳到对应文件和行号。
- 行范围：支持 `10-20` 这类范围，跳转后选中对应行。
- 路径解析：支持文件名、相对路径、Windows 绝对路径、Unix 绝对路径。
- 点击复制：对常见结构化文本片段生成复制链接。
- 设置页：`Settings -> Tools -> OpenCode Terminal Tools` 可开关文件跳转链接和点击复制链接。
- OpenCode 桥接：把 IDE 编辑器当前选区发送到正在运行的 OpenCode TUI 输入区。

## 支持的文件引用格式

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

路径匹配主要面向常见无空格控制台输出，暂不保证带空格路径都能识别。

## OpenCode 选区桥接

插件可以把 IDE 编辑器中选中的代码写入桥接文件，并自动触发 OpenCode 的 `editor_open`，让 OpenCode 通过 `EDITOR` 脚本把选区内容合并回 TUI 输入区。

### 使用步骤

1. 在 IDE 终端中启动 OpenCode。
2. 启动 OpenCode 前配置 `EDITOR` 环境变量。
3. 在 OpenCode 所在 Terminal 标签页执行 `Mark as OpenCode Terminal`。
4. 在编辑器中选中代码。
5. 执行 `Send Selection to OpenCode`。
6. 插件会激活 Terminal、聚焦输入组件，并触发配置的 `editor_open`。

### Windows EDITOR 配置

PowerShell：

```powershell
$env:EDITOR="$env:TEMP\opencode-idea-bridge\opencode-editor.cmd"
```

cmd：

```cmd
set EDITOR=%TEMP%\opencode-idea-bridge\opencode-editor.cmd
```

这些环境变量必须在启动 `opencode` 之前设置。已经运行中的 OpenCode 不会读取后来才设置的环境变量。

### OpenCode editor_open 快捷键

插件默认按 OpenCode 的默认 `editor_open` 快捷键 `ctrl+x e` 触发外部编辑器，这样 OpenCode 输入框已有内容会先进入临时编辑文件，再由桥接脚本和选区内容换行拼接。

如果你在 OpenCode 的 `tui.json` 中把 `editor_open` 改成了其他快捷键，需要在 `Settings -> Tools -> OpenCode Terminal Tools` 中同步修改 `OpenCode editor_open 快捷键`，例如：

```text
f4
```

如需使用旧流程，可以填写：

```text
/editor
```

插件会自动生成：

```text
%TEMP%\opencode-idea-bridge\latest-selection.md
%TEMP%\opencode-idea-bridge\opencode-editor.ps1
%TEMP%\opencode-idea-bridge\opencode-editor.cmd
```

选区 payload 格式固定为：

```text
src/main/java/A.java:10-20
-------
<selected code>
-------
```

`EDITOR` 必须指向桥接脚本，不要直接指向 `code`、`notepad` 或其他真实编辑器。桥接脚本会读取 `latest-selection.md`，把选区内容合并进 OpenCode 传入的临时编辑文件，然后退出。

如果希望合并选区后仍然打开真实外部编辑器，可以额外设置：

```powershell
$env:OPENCODE_IDEA_REAL_EDITOR="code --wait"
```

或：

```powershell
$env:OPENCODE_IDEA_REAL_EDITOR="notepad"
```

## 构建与运行

1. 默认使用 IntelliJ IDEA Ultimate 2025.3 构建。可通过 `-P` 参数切换 IDE 类型和版本：

```powershell
# 默认（IntelliJ IDEA Ultimate 2025.3）
.\gradlew.bat runIde

# 指定版本和 IDE 类型
.\gradlew.bat buildPlugin -PplatformVersion=2025.3 -PplatformType=IU
```

支持的 IDE 类型：

| 参数值 | IDE |
|--------|-----|
| `IU` / `IC` | IntelliJ IDEA Ultimate / Community |
| `PY` | PyCharm |
| `WS` | WebStorm |
| `GO` | GoLand |
| `PS` | PhpStorm |
| `RM` | RubyMine |
| `RD` | Rider |
| `CL` | CLion |
| `DG` | DataGrip |

2. 打包插件：

```powershell
.\gradlew.bat buildPlugin
```

插件包会生成在：

```text
build/distributions/
```

## 技术栈

| 项目 | 版本 |
| --- | --- |
| Kotlin | 2.2.21 |
| JVM | 17 |
| IntelliJ Platform Plugin | 2.12.0 |
| 最低支持 IDE | 2024.2+ |

## 插件信息

| 项目 | 值 |
| --- | --- |
| Plugin ID | `io.github.q110.opencodeterminaltools` |
| Group | `io.github.q110` |
| Vendor | `zibo` |
