# Opencode Short File Links

这个 IntelliJ IDEA 插件用于把终端或控制台输出中的短文件引用变成可点击链接。

目标格式：

```text
RealtimeDemandPlanServiceImpl.java:932
```

点击后，插件会在当前项目索引中查找 `RealtimeDemandPlanServiceImpl.java`，并跳转到第 `932` 行。

## 行为

- 唯一匹配：直接打开文件并跳转到行号。
- 多个同名文件：弹出选择框，选择后跳转。
- 未找到文件：不添加链接。
- 不修改 opencode，不改变终端输入输出流程。

## 使用

1. 在 IDEA 中打开本目录。
2. 使用 Gradle 的 `runIde` 启动沙盒 IDE，或使用 `buildPlugin` 生成插件包后安装。
3. 在沙盒 IDE 中打开你的项目，并在终端中运行 opencode。
4. 当输出包含 `FileName.ext:line` 时，按 IDEA 的链接交互方式点击。

## 说明

当前实现优先使用 `ConsoleFilterProvider`。如果你的 IDEA 版本或终端引擎没有把该过滤器应用到普通 Terminal Tool Window，需要再实现 Reworked Terminal 专用适配。
