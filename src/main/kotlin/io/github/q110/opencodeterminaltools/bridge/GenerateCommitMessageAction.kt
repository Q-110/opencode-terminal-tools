package io.github.q110.opencodeterminaltools.bridge

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.commit.CommitMessageUi
import com.intellij.vcs.commit.CommitWorkflowUi
import io.github.q110.opencodeterminaltools.settings.OpenCodeTerminalToolsSettings
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.UUID

class GenerateCommitMessageAction : AnAction(AllIcons.Debugger.Console) {
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(event: AnActionEvent) {
        val workflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        val hasIncludedFiles = workflowUi != null &&
            (workflowUi.getIncludedChanges().isNotEmpty() || workflowUi.getIncludedUnversionedFiles().isNotEmpty())
        event.presentation.isEnabled = event.project != null && hasIncludedFiles
        event.presentation.isVisible = event.project != null && workflowUi != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val workflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        if (workflowUi == null) {
            OpenCodeBridgeService.notify(project, "没有找到 Commit 面板上下文。", NotificationType.WARNING)
            return
        }

        val includedChanges = workflowUi.getIncludedChanges()
        val includedUnversionedFiles = workflowUi.getIncludedUnversionedFiles()
        if (includedChanges.isEmpty() && includedUnversionedFiles.isEmpty()) {
            OpenCodeBridgeService.notify(project, "请先勾选要提交的文件。", NotificationType.WARNING)
            return
        }

        val commitMessageUi = workflowUi.commitMessageUi
        val currentMessage = commitMessageUi.text.trim()
        if (currentMessage.isNotEmpty() && !confirmReplaceCommitMessage(project)) {
            return
        }

        commitMessageUi.startLoading()
        GenerateCommitMessageTask(project, workflowUi, commitMessageUi, includedChanges, includedUnversionedFiles).queue()
    }

    private fun confirmReplaceCommitMessage(project: Project): Boolean {
        return Messages.showYesNoDialog(
            project,
            "当前提交文案已有内容，是否用生成结果替换？",
            "生成提交文案",
            "替换",
            "取消",
            Messages.getQuestionIcon()
        ) == Messages.YES
    }

    private class GenerateCommitMessageTask(
        project: Project,
        private val workflowUi: CommitWorkflowUi,
        private val commitMessageUi: CommitMessageUi,
        private val includedChanges: List<Change>,
        private val includedUnversionedFiles: List<FilePath>
    ) : Task.Backgroundable(project, "Generating OpenCode Commit Message", true) {
        override fun run(indicator: ProgressIndicator) {
            try {
                indicator.text = "Collecting selected changes"
                val changeSummary = CommitChangeSummary(project, includedChanges, includedUnversionedFiles).build()
                indicator.checkCanceled()

                indicator.text = "Running opencode"
                val generatedMessage = OpenCodeCommitMessageGenerator(project, changeSummary).generate(indicator)
                indicator.checkCanceled()

                invokeOnEdt {
                    commitMessageUi.setText(generatedMessage)
                    commitMessageUi.focus()
                    OpenCodeBridgeService.notify(project, "已生成提交文案。", NotificationType.INFORMATION)
                }
            } catch (exception: CommitMessageGenerationException) {
                invokeOnEdt {
                    OpenCodeBridgeService.notify(project, exception.message ?: "生成提交文案失败。", NotificationType.WARNING)
                }
            } catch (exception: Throwable) {
                invokeOnEdt {
                    OpenCodeBridgeService.notify(project, "生成提交文案失败：${exception.message}", NotificationType.WARNING)
                }
            } finally {
                invokeOnEdt {
                    commitMessageUi.stopLoading()
                }
            }
        }

        private fun invokeOnEdt(action: () -> Unit) {
            ApplicationManager.getApplication().invokeLater(
                {
                    if (!project.isDisposed && !Disposer.isDisposed(workflowUi)) {
                        action()
                    }
                },
                ModalityState.any()
            )
        }
    }

    private class CommitChangeSummary(
        private val project: Project,
        private val includedChanges: List<Change>,
        private val includedUnversionedFiles: List<FilePath>
    ) {
        fun build(): String {
            val changedFiles = includedChanges.mapNotNull { change -> change.toIncludedFile(project) }
            val unversionedFiles = includedUnversionedFiles.map { filePath -> filePath.toIncludedFile(project, "新增文件") }
            val allFiles = changedFiles + unversionedFiles
            if (allFiles.isEmpty()) {
                throw CommitMessageGenerationException("没有可用于生成提交文案的文件。")
            }

            val fileList = allFiles.joinToString(separator = "\n") { "- ${it.status}: ${it.relativePath}" }
            return truncateInput(
                """
                已勾选文件：
                $fileList

                只分析上面列出的文件。不要分析未勾选文件。
                """.trimIndent()
            )
        }
    }

    private class OpenCodeCommitMessageGenerator(
        private val project: Project,
        private val changeSummary: String
    ) {
        fun generate(indicator: ProgressIndicator): String {
            val basePath = project.basePath
                ?: throw CommitMessageGenerationException("项目没有可用的工作目录。")
            val settings = OpenCodeTerminalToolsSettings.getInstance().getState()
            val prompt = buildPrompt(changeSummary)
            val configHome = createOpenCodeConfigHome()
            val basePathPath = Path.of(basePath).toAbsolutePath().normalize()
            val sessionTitle = "Generate commit message ${UUID.randomUUID()}"
            try {
                val command = mutableListOf(
                    opencodeCommand(),
                    "run",
                    "--pure",
                    "--agent",
                    COMMIT_MESSAGE_AGENT,
                    "--dir",
                    basePath,
                    "--title",
                    sessionTitle
                )
                val model = settings.commitMessageModel.trim()
                if (model.isNotEmpty()) {
                    command += listOf("-m", model)
                }
                command += prompt

                val result = runProcess(
                    command,
                    basePathPath,
                    OPENCODE_TIMEOUT_SECONDS,
                    indicator,
                    "opencode 生成提交文案超时，请尝试减少勾选文件或配置更快的 Commit message model。",
                    environment = mapOf("XDG_CONFIG_HOME" to configHome.toString())
                )
                if (result.exitCode != 0) {
                    throw CommitMessageGenerationException("opencode 执行失败：${result.output.take(ERROR_OUTPUT_LIMIT)}")
                }

                val message = cleanupOutput(result.output)
                if (message.isBlank()) {
                    throw CommitMessageGenerationException("opencode 没有返回可用的提交文案。")
                }
                return message
            } finally {
                deleteSessionQuietly(sessionTitle, basePathPath)
                configHome.toFile().deleteRecursively()
            }
        }

        private fun buildPrompt(summary: String): String {
            return """
                请根据 Commit 面板已勾选文件生成提交文案。

                你可以在当前项目目录中查看变更，但必须遵守：
                - 只分析“已勾选文件”清单中的文件。
                - tracked 文件如需详情，只执行 git diff --no-color --no-ext-diff -- <已勾选路径>。
                - 新增未跟踪文件如需详情，只读取清单中的文件。
                - 不要修改任何文件，不要执行与生成提交文案无关的命令。

                - 只输出提交文案，不要解释，不要分析过程，不要 Markdown 代码块。
                - 使用中文。
                - 使用 "- " 分条。
                - 精简但完整，覆盖关键改动。
                - 不要编造变更中不存在的内容。

                变更内容：
                $summary
            """.trimIndent()
        }

        private fun cleanupOutput(output: String): String {
            val lines = output
                .replace(ANSI_PATTERN, "")
                .lineSequence()
                .map { it.trimEnd() }
                .filterNot { it.trim().equals("```", ignoreCase = true) }
                .filterNot { it.trimStart().startsWith("> ") }
                .toList()
            val trailingBulletLines = lines
                .asReversed()
                .dropWhile { it.isBlank() }
                .takeWhile { it.isBlank() || it.trimStart().startsWith("- ") }
                .asReversed()
                .filter { it.trimStart().startsWith("- ") }
            return (trailingBulletLines.ifEmpty { lines })
                .joinToString("\n")
                .trim()
        }

        private fun deleteSessionQuietly(sessionTitle: String, basePath: Path) {
            try {
                val listResult = runProcess(
                    listOf(opencodeCommand(), "session", "list", "--format", "json", "--max-count", "20"),
                    basePath,
                    OPENCODE_SESSION_CLEANUP_TIMEOUT_SECONDS
                )
                if (listResult.exitCode != 0) return

                val sessionId = parseOpenCodeSessions(listResult.output)
                    .firstOrNull { session ->
                        session.title == sessionTitle && pathsEqual(session.directory, basePath)
                    }
                    ?.id
                    ?: return

                runProcess(
                    listOf(opencodeCommand(), "session", "delete", sessionId),
                    basePath,
                    OPENCODE_SESSION_CLEANUP_TIMEOUT_SECONDS
                )
            } catch (_: Throwable) {
            }
        }
    }

    private data class IncludedFile(
        val gitRoot: Path,
        val absolutePath: Path,
        val relativePath: String,
        val status: String
    )

    private data class ProcessResult(val exitCode: Int, val output: String)

    private data class OpenCodeSession(val id: String, val title: String, val directory: String)

    private class CommitMessageGenerationException(message: String) : RuntimeException(message)

    companion object {
        private const val COMMIT_MESSAGE_AGENT = "commit-message"
        private const val MAX_INPUT_CHARS = 4000
        private const val ERROR_OUTPUT_LIMIT = 600
        private const val OPENCODE_TIMEOUT_SECONDS = 120L
        private const val OPENCODE_SESSION_CLEANUP_TIMEOUT_SECONDS = 15L
        private const val PROCESS_POLL_INTERVAL_MS = 200L
        private val ANSI_PATTERN = Regex("\\u001B\\[[;?0-9]*[ -/]*[@-~]")

        private fun Change.toIncludedFile(project: Project): IncludedFile? {
            val filePath = afterRevision?.file ?: beforeRevision?.file ?: return null
            val status = when {
                beforeRevision == null -> "新增"
                afterRevision == null -> "删除"
                isMoved || isRenamed -> "重命名/移动"
                else -> "修改"
            }
            return filePath.toIncludedFile(project, status)
        }

        private fun FilePath.toIncludedFile(project: Project, status: String): IncludedFile {
            val absolutePath = ioFile.toPath().toAbsolutePath().normalize()
            val gitRoot = findGitRoot(absolutePath, project)
            val relativePath = try {
                gitRoot.relativize(absolutePath).toString().replace(File.separatorChar, '/')
            } catch (_: IllegalArgumentException) {
                absolutePath.toString().replace(File.separatorChar, '/')
            }
            return IncludedFile(gitRoot, absolutePath, relativePath, status)
        }

        private fun findGitRoot(path: Path, project: Project): Path {
            val start = if (Files.isDirectory(path)) path else path.parent
            var current = start
            while (current != null) {
                if (Files.exists(current.resolve(".git"))) {
                    return current
                }
                current = current.parent
            }
            return Path.of(project.basePath ?: ".").toAbsolutePath().normalize()
        }

        private fun runProcess(
            command: List<String>,
            workingDirectory: Path,
            timeoutSeconds: Long,
            indicator: ProgressIndicator? = null,
            timeoutMessage: String? = null,
            environment: Map<String, String> = emptyMap()
        ): ProcessResult {
            val process = try {
                ProcessBuilder(command).apply {
                    environment().putAll(environment)
                }
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start()
            } catch (exception: Throwable) {
                throw CommitMessageGenerationException("无法启动命令 ${command.firstOrNull().orEmpty()}：${exception.message}")
            }
            try {
                process.outputStream.close()
            } catch (_: Throwable) {
            }

            val output = StringBuilder()
            val readerThread = Thread {
                process.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        output.appendLine(line)
                    }
                }
            }
            readerThread.isDaemon = true
            readerThread.start()

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (true) {
                if (indicator?.isCanceled == true) {
                    destroyProcessTree(process)
                    throw CommitMessageGenerationException("已取消生成提交文案。")
                }
                if (process.waitFor(PROCESS_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)) {
                    break
                }
                if (System.nanoTime() >= deadline) {
                    destroyProcessTree(process)
                    throw CommitMessageGenerationException(timeoutMessage ?: "命令执行超时：${command.firstOrNull().orEmpty()}")
                }
            }
            readerThread.join(TimeUnit.SECONDS.toMillis(2))
            return ProcessResult(process.exitValue(), output.toString())
        }

        private fun destroyProcessTree(process: Process) {
            process.descendants().forEach { descendant ->
                try {
                    descendant.destroyForcibly()
                } catch (_: Throwable) {
                }
            }
            process.destroyForcibly()
        }

        private fun truncateInput(input: String): String {
            if (input.length <= MAX_INPUT_CHARS) return input
            return input.take(MAX_INPUT_CHARS) + "\n\n[文件清单过长，后续内容已截断]"
        }

        private fun parseOpenCodeSessions(json: String): List<OpenCodeSession> {
            return Regex("""\{[^{}]*\}""")
                .findAll(json)
                .mapNotNull { match ->
                    val item = match.value
                    val id = extractJsonStringField(item, "id") ?: return@mapNotNull null
                    val title = extractJsonStringField(item, "title") ?: return@mapNotNull null
                    val directory = extractJsonStringField(item, "directory") ?: return@mapNotNull null
                    OpenCodeSession(id, title, directory)
                }
                .toList()
        }

        private fun extractJsonStringField(jsonObject: String, field: String): String? {
            val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
            return pattern.find(jsonObject)?.groupValues?.getOrNull(1)?.let(::unescapeJsonString)
        }

        private fun unescapeJsonString(value: String): String {
            val result = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (char != '\\' || index + 1 >= value.length) {
                    result.append(char)
                    index++
                    continue
                }

                when (val escaped = value[index + 1]) {
                    '"', '\\', '/' -> result.append(escaped)
                    'b' -> result.append('\b')
                    'f' -> result.append('\u000C')
                    'n' -> result.append('\n')
                    'r' -> result.append('\r')
                    't' -> result.append('\t')
                    'u' -> {
                        val hexStart = index + 2
                        val hexEnd = hexStart + 4
                        if (hexEnd <= value.length) {
                            val codePoint = value.substring(hexStart, hexEnd).toIntOrNull(16)
                            if (codePoint != null) {
                                result.append(codePoint.toChar())
                                index += 6
                                continue
                            }
                        }
                        result.append("\\u")
                    }
                    else -> result.append(escaped)
                }
                index += 2
            }
            return result.toString()
        }

        private fun pathsEqual(directory: String, basePath: Path): Boolean {
            return try {
                val sessionPath = Path.of(directory).toAbsolutePath().normalize().toString()
                val expectedPath = basePath.toAbsolutePath().normalize().toString()
                sessionPath.equals(expectedPath, ignoreCase = SystemInfo.isWindows)
            } catch (_: Throwable) {
                directory.equals(basePath.toString(), ignoreCase = SystemInfo.isWindows)
            }
        }

        private fun createOpenCodeConfigHome(): Path {
            val configHome = Files.createTempDirectory("opencode-commit-message-config")
            val configDir = configHome.resolve("opencode")
            Files.createDirectories(configDir)
            Files.writeString(
                configDir.resolve("opencode.json"),
                commitMessageAgentConfig(),
                StandardCharsets.UTF_8
            )
            return configHome
        }

        private fun commitMessageAgentConfig(): String {
            return """
                {
                  "agent": {
                    "$COMMIT_MESSAGE_AGENT": {
                      "description": "Commit message generator",
                      "mode": "primary",
                      "prompt": "Generate concise Chinese git commit message bullets for the listed checked files. You may use bash only to inspect git status, git diff, or listed untracked files. Never modify files. The final answer must contain only bullet lines.",
                      "tools": {
                        "invalid": false,
                        "skill": false,
                        "question": false,
                        "bash": true,
                        "read": false,
                        "glob": false,
                        "grep": false,
                        "edit": false,
                        "write": false,
                        "task": false,
                        "webfetch": false,
                        "websearch": false,
                        "todowrite": false
                      }
                    }
                  }
                }
            """.trimIndent()
        }

        private fun opencodeCommand(): String {
            return if (SystemInfo.isWindows) "opencode.cmd" else "opencode"
        }
    }
}
