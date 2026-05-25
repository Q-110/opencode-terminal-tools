# OpenCode Terminal Tools

OpenCode Terminal Tools 是一个 JetBrains IDE 插件，为终端、控制台和 Commit 面板提供五类增强功能：**文件跳转**、**点击复制**、**控制台错误发送**、**OpenCode 桥接** 和 **中文提交信息生成**。

---

## 功能详解

### 📄 文件跳转

将终端输出中的文件引用自动识别为可点击超链接，点击后跳转到 IDE 编辑器中的对应文件位置，支持行号和行范围选中。

**支持的文件引用格式：**

```
# 仅文件名
ExampleController.java

# 文件名 + 行号
ExampleController.java:22

# 文件名 + 行范围
ExampleController.java:22-30

# 相对路径
src/main/java/com/example/ExampleController.java:22

# 相对路径（带 ./ 或 ../）
./src/main/java/com/example/ExampleController.java:22-30
../module/src/main/java/com/example/ExampleController.java:22

# Windows 绝对路径
C:\Projects\demo\src\main\java\com\example\ExampleController.java:22

# Unix 绝对路径
/projects/demo/src/main/java/com/example/ExampleController.java:22

# @路径引用（用于 OpenCode 桥接补全状态）
@src/main/java/com/example/ExampleController.java:10
```

**路径解析规则：**

- 优先以路径后缀匹配项目文件
- 支持的文件扩展名：`java`, `kt`, `kts`, `js`, `jsx`, `ts`, `tsx`, `vue`, `xml`, `html`, `css`, `scss`, `yml`, `yaml`, `properties`, `sql`, `md`
- @路径引用优先级更高，用于匹配 OpenCode 补全状态的精确路径

**同名文件智能选择：**

当多个文件匹配同一引用时，插件根据路径特征打分排序：

| 路径特征 | 分值 |
|---------|------|
| 包含 `src/main/` | +80 |
| 包含 `src/test/` | -40 |
| 包含 `build/` / `target/` / `out/` | -80 |
| 包含 `templates/` | +30 |
| 包含 `static/` | +20 |

首选文件自动跳转；若仍有多个候选，弹出选择对话框供用户手动选取。

---

### 📋 点击复制

终端输出中的结构化文本片段会被识别为可点击链接，点击后自动复制到系统剪贴板，并在链接上方显示 **"已复制"** 提示（持续 700ms）。

**支持的复制模式（按优先级排序）：**

| 模式 | 示例 |
|------|------|
| 双花括号 `{{...}}` | `{{value}}` |
| 双方括号 `[[...]]` | `[[value]]` |
| 函数调用 | `funcName(arg)` |
| URL | `https://example.com/path` |
| 点号链 | `com.example.service.UserService` |
| 引号字符串 | `"value"`、`'value'` |
| 标识符 | `myVariable`、`CONSTANT_NAME` |
| 数字 | `42`、`3.14`、`0xFF` |

点击复制链接不会占用文件跳转链接的区间，两者互不干扰。

---

### ⚠️ 控制台错误发送

Run/Debug Console 中的 Java/JVM 异常首行会显示一个 OpenCode 图标，点击后自动将当前可见异常段发送到正在运行的 OpenCode。

**发送范围：**

- 从异常首行开始，例如 `Caused by: java.net.ConnectException: Connection timed out: connect`
- 包含后续连续的 `at ...(...)` 调用栈行
- 不包含 `... N common frames omitted`、`<N folded frames>`、`Disconnected from ...` 或 `Process finished ...`

**发送格式：**

```text
控制台错误：
-------
<异常首行和连续 stack frames>
-------
```

如果一段 stack trace 中有多个 `Caused by:`，每个异常段会独立显示图标，点击不同图标只发送对应异常段。

错误图标只在鼠标命中图标时接管光标和提示文本，避免在控制台空白区域反复重置悬浮状态造成抖动。

可在 **Settings → Tools → OpenCode Terminal Tools** 中通过 **Enable error-to-OpenCode console icons** 开关启用或关闭。

---

### 🔗 OpenCode 桥接

将 IDE 编辑器中的选区、文件路径或控制台错误发送到正在运行的 [OpenCode](https://opencode.ai) TUI 输入区，支持 @路径补全状态的自动结束。

#### 架构概览

```
IDE 编辑器选区
      │
      ▼
写入桥接文件 (%TEMP%\opencode-idea-bridge\)
      │
      ▼
触发 editor_open 快捷键 → OpenCode 读取临时编辑文件
      │
      ▼
桥接脚本合并选区内容 → OpenCode TUI 输入区
```

桥接使用文件系统作为通信媒介，`EDITOR` 环境变量指向桥接脚本 `opencode-editor.cmd`/`.ps1`。

#### 快速开始（推荐）

1. 点击 IDE 工具栏的 **Start OpenCode Terminal** 按钮
2. 插件自动创建新终端标签页、写入桥接脚本、设置 `EDITOR` 环境变量后启动 `opencode`
3. 在编辑器中选中代码，按 **`Ctrl+Alt+,`** 发送到 OpenCode

> 一键启动会自动处理 `EDITOR` 环境变量配置，无需手动设置。已存在的终端的标签页名依次为 `OpenCode`、`OpenCode (2)`、`OpenCode (3)`...

#### 手动配置

如果已手动启动 OpenCode，需先配置 `EDITOR` 环境变量再启动：

**PowerShell：**

```powershell
$env:EDITOR="$env:TEMP\opencode-idea-bridge\opencode-editor.cmd"
```

**CMD：**

```cmd
set EDITOR=%TEMP%\opencode-idea-bridge\opencode-editor.cmd
```

> ⚠️ 环境变量必须在启动 `opencode` **之前**设置，已运行的 OpenCode 不会读取之后设置的环境变量。

**手动启动后的操作：**

1. 在 OpenCode 所在的终端标签页右键 → **Mark as OpenCode Terminal**
2. 在编辑器中选中代码，按 **`Ctrl+Alt+,`** 发送到 OpenCode

#### 发送选区到 OpenCode

- **快捷键：** `Ctrl+Alt+,`
- **从菜单触发：** 编辑器右键 → **Send Selection to OpenCode**
- **数据格式：**

```text
src/main/java/A.java:10-20
-------
<selected code>
-------
```

插件会：写入选区文件 → 切换到终端标签页 → 聚焦输入框 → 触发 `editor_open` 快捷键。

#### 发送文件/文件夹路径到 OpenCode

- **从项目视图触发：** 右键文件/文件夹 → **Send File/Folder Path to OpenCode**
- **从编辑器标签页触发：** 右键标签页 → **Send File Path to OpenCode**
- 以 `@displayPath` 格式发送，自动结束 OpenCode 的 @路径补全状态

#### 发送控制台错误到 OpenCode

- **从控制台触发：** 点击异常首行中的 OpenCode 图标
- **数据格式：**

```text
控制台错误：
-------
<当前异常段>
-------
```

插件会：识别异常段 → 写入桥接文件 → 切换到 OpenCode 终端 → 触发 `editor_open` 快捷键。

#### 自定义 editor_open 快捷键

插件默认按 `ctrl+x e`（OpenCode 默认快捷键）触发外部编辑器。如果修改了 OpenCode 的 `editor_open` 快捷键，需在 **Settings → Tools → OpenCode Terminal Tools** 中同步修改。

在设置页的 `OpenCode editor_open shortcut` 字段填入新的快捷键，例如：

```text
f4
```

如需使用旧版 `/editor` 流程，填写：

```text
/editor
```

#### 桥接脚本说明

插件自动生成以下桥接文件到 `%TEMP%\opencode-idea-bridge\`：

| 文件 | 说明 |
|------|------|
| `latest-selection.md` | 选区内荣 payload 文件 |
| `opencode-editor.ps1` | PowerShell 桥接脚本 |
| `opencode-editor.cmd` | CMD 桥接脚本 |

> `EDITOR` 必须指向桥接脚本，不要直接指向 `code`、`notepad` 或其他真实编辑器。桥接脚本会读取 `latest-selection.md`，将选区内容合并进 OpenCode 传入的临时编辑文件后退出。

如果希望合并选区后仍然打开真实外部编辑器，可额外设置：

```powershell
$env:OPENCODE_IDEA_REAL_EDITOR="code --wait"
```

---

### 📝 生成中文提交信息

Commit 面板工具栏会显示 **Generate Commit Message** 动作，点击后使用 OpenCode 根据当前 Commit 面板中已勾选的文件生成中文提交文案。

**生成规则：**

- 只分析 Commit 面板中已勾选的 tracked 文件和 unversioned 文件
- tracked 文件会要求 OpenCode 通过 `git diff --no-color --no-ext-diff -- <已勾选路径>` 查看详情
- unversioned 文件只允许读取已勾选文件清单中的文件
- 生成结果使用中文 `- ` 分条，精简覆盖关键改动
- 如果当前提交文案已有内容，会先询问是否替换
- 生成结束后会自动删除本次生成创建的 OpenCode session，避免残留临时会话

**模型配置：**

可在 **Settings → Tools → OpenCode Terminal Tools** 的 `Commit message model` 字段配置 OpenCode 的 `provider/model` 格式模型，例如：

```text
openai/gpt-4.1
```

留空时使用 OpenCode 默认模型。

---

## ⌨️ 快捷键一览

| 动作 | 默认快捷键 | 触发方式 |
|------|-----------|---------|
| Send Selection to OpenCode | `Ctrl+Alt+,` | 编辑器内快捷键 / 右键菜单 |
| Send File Path to OpenCode | — | 项目视图 / 编辑器标签页右键菜单 |
| Send Console Error to OpenCode | — | 控制台异常行图标 |
| Generate Commit Message | — | Commit 面板工具栏 |
| Start OpenCode Terminal | — | 工具栏按钮（Debugger.Console 图标） |
| Mark as OpenCode Terminal | — | 终端标签页右键菜单 |

## 🏗️ 项目架构

```
src/main/kotlin/io/github/q110/opencodeterminaltools/
│
├── bridge/              # OpenCode 桥接模块
│   ├── OpenCodeBridgeService.kt          # 核心服务（项目级 Service）
│   ├── FrontendTerminalHelper.kt         # 新版终端 API (2025.3+)
│   ├── LegacyReworkedTerminalHelper.kt   # 旧版 Reworked 终端 (2025.1-2025.2)
│   ├── SendSelectionToOpenCodeAction.kt  # 发送选区 Action
│   ├── SendPathToOpenCodeAction.kt       # 发送路径 Action
│   ├── MarkOpenCodeTerminalAction.kt     # 标记终端 Action
│   ├── StartOpenCodeTerminalAction.kt    # 启动 OpenCode 终端 Action
│   ├── GenerateCommitMessageAction.kt    # 生成中文提交文案 Action
│   └── OpenCodeTerminalToolsMenuRegistrar.kt  # 菜单注册器
│
├── filter/              # 终端输出过滤模块
│   ├── OpenCodeTerminalToolsFilter.kt     # 核心 Filter（四阶段解析）
│   ├── OpenCodeTerminalToolsFilterProvider.kt
│   ├── FilterPatterns.kt                  # 正则表达式定义
│   └── PathUtils.kt                       # 路径工具函数
│
├── jump/                # 文件/文件夹跳转模块
│   ├── FileReferenceHyperlinkInfo.kt      # 文件跳转 Hyperlink
│   ├── FolderReferenceHyperlinkInfo.kt    # 文件夹跳转 Hyperlink
│   └── FileChoiceDialog.kt               # 同名文件选择弹窗
│
├── copy/                # 点击复制模块
│   └── CopyTextHyperlinkInfo.kt           # 点击复制 Hyperlink
│
├── console/             # 控制台错误发送模块
│   ├── ConsoleErrorBlockParser.kt         # Java/JVM 异常段解析
│   └── OpenCodeConsoleErrorInlayService.kt # 控制台行内 OpenCode 图标
│
└── settings/            # 设置模块
    ├── OpenCodeTerminalToolsSettings.kt     # 配置持久化
    └── OpenCodeTerminalToolsConfigurable.kt # 设置页面 UI
```

**终端兼容层设计：**

- **Frontend** (IDE 2025.3+)：使用官方 `TerminalToolWindowTabsManager` API，最稳定
- **Legacy Reworked** (IDE 2025.1~2025.2)：通过反射调用 `TerminalToolWindowManager.createNewTab()`
- **Classic** (回退)：使用 `ShellTerminalWidget` + TTY Connector 操作

插件无主入口类，通过 `plugin.xml` 中 `postStartupActivity` 注册初始化，Service 通过 `@Service` 注解懒加载。

---

## 🔧 构建与运行

### 环境要求

- IntelliJ IDEA Ultimate 2025.3（默认）或通过 `-P` 参数切换 IDE 类型和版本
- JDK 17
- Gradle（通过项目自带的 `gradlew`/`gradlew.bat`）

### 常用命令

```powershell
# 默认（IntelliJ IDEA Ultimate 2025.3）运行 IDE 实例
.\gradlew.bat runIde

# 指定版本和 IDE 类型
.\gradlew.bat buildPlugin -PplatformVersion=2025.3 -PplatformType=IU

# 打包插件
.\gradlew.bat buildPlugin
```

### IDE 类型参数

| 参数值 | IDE |
|--------|-----|
| `IU` | IntelliJ IDEA Ultimate |
| `IC` | IntelliJ IDEA Community |
| `PY` | PyCharm |
| `WS` | WebStorm |
| `GO` | GoLand |
| `PS` | PhpStorm |
| `RM` | RubyMine |
| `RD` | Rider |
| `CL` | CLion |
| `DG` | DataGrip |

打包产物生成在 `build/distributions/` 目录。

---

## 📊 技术栈

| 项目 | 版本 |
|------|------|
| Kotlin | 2.2.21 |
| JVM | 17 |
| IntelliJ Platform Plugin | 2.12.0 |
| 最低支持 IDE | 2025.3+ |

---

## ℹ️ 插件信息

| 项目 | 值 |
|------|-----|
| 插件 ID | `io.github.q110.opencodeterminaltools` |
| 当前版本 | `1.10.2` |
| Group | `io.github.q110` |
| Vendor | `zibo` |
| 许可证 | MIT |

---

## 许可证

本项目基于 MIT License 开源。详见 [LICENSE](./LICENSE) 文件。
