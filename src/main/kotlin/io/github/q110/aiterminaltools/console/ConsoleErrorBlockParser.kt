package io.github.q110.aiterminaltools.console

import com.intellij.openapi.editor.Document

internal data class ConsoleErrorBlock(
    val startLine: Int,
    val startOffset: Int,
    val iconOffset: Int,
    val endOffset: Int
)

internal object ConsoleErrorBlockParser {
    private val exceptionStartPattern = Regex(
        """^\s*(?:Exception in thread\s+"[^"]+"\s+)?(?:Caused by:\s+)?(?:[\w$]+(?:\.[\w$]+)*(?:Exception|Error|Throwable|Failure)|[\w$]+(?:Exception|Error|Throwable|Failure))(?:[:\s].*)?$"""
    )
    private val stackFramePattern = Regex("""^\s+at\s+.+\(.+\)\s*$""")

    fun parse(document: Document): List<ConsoleErrorBlock> {
        val blocks = mutableListOf<ConsoleErrorBlock>()
        var currentStartLine: Int? = null
        var currentStartOffset = 0
        var currentIconOffset = 0
        var currentEndOffset = 0

        for (lineNumber in 0 until document.lineCount) {
            val lineStart = document.getLineStartOffset(lineNumber)
            val lineEnd = document.getLineEndOffset(lineNumber)
            val line = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
            val inBlock = currentStartLine != null

            if (!inBlock) {
                if (isExceptionStart(line)) {
                    currentStartLine = lineNumber
                    currentStartOffset = lineStart
                    currentIconOffset = iconOffset(line, lineStart, lineEnd)
                    currentEndOffset = lineEnd
                }
                continue
            }

            when {
                stackFramePattern.matches(line) -> {
                    currentEndOffset = lineEnd
                }
                else -> {
                    finishBlock(blocks, currentStartLine, currentStartOffset, currentIconOffset, currentEndOffset)
                    currentStartLine = null
                    if (isExceptionStart(line)) {
                        currentStartLine = lineNumber
                        currentStartOffset = lineStart
                        currentIconOffset = iconOffset(line, lineStart, lineEnd)
                        currentEndOffset = lineEnd
                    }
                }
            }
        }

        finishBlock(blocks, currentStartLine, currentStartOffset, currentIconOffset, currentEndOffset)
        return blocks
    }

    private fun finishBlock(
        blocks: MutableList<ConsoleErrorBlock>,
        startLine: Int?,
        startOffset: Int,
        iconOffset: Int,
        endOffset: Int
    ) {
        if (startLine == null || endOffset <= startOffset) return
        blocks += ConsoleErrorBlock(startLine, startOffset, iconOffset.coerceIn(startOffset, endOffset), endOffset)
    }

    private fun isExceptionStart(line: String): Boolean {
        return exceptionStartPattern.matches(line.trimEnd())
    }

    private fun iconOffset(line: String, lineStart: Int, lineEnd: Int): Int {
        val searchStart = causedByPrefixPattern.find(line)?.range?.last?.plus(1) ?: 0
        val messageSeparatorIndex = line.indexOf(':', startIndex = searchStart)
        return if (messageSeparatorIndex >= 0) {
            lineStart + messageSeparatorIndex
        } else {
            lineEnd
        }
    }

    private val causedByPrefixPattern = Regex("""^\s*Caused by:""")
}
