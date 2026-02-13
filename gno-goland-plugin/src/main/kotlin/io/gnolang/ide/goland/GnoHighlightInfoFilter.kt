package io.gnolang.ide.goland

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile

class GnoHighlightInfoFilter : HighlightInfoFilter {
    override fun accept(highlightInfo: HighlightInfo, file: PsiFile?): Boolean {
        if (file == null || !isGnoFileName(file.name)) {
            return true
        }

        val severity = highlightInfo.severity
        if (severity == HighlightSeverity.WARNING || severity == HighlightSeverity.WEAK_WARNING) {
            return false
        }

        if (severity != HighlightSeverity.ERROR) {
            return true
        }

        return !isKnownResolveFalsePositive(highlightInfo)
    }

    private fun isKnownResolveFalsePositive(highlightInfo: HighlightInfo): Boolean {
        val message = highlightInfo.description?.lowercase() ?: return false
        return RESOLVE_ERROR_SNIPPETS.any(message::contains)
    }

    private companion object {
        private val RESOLVE_ERROR_SNIPPETS = listOf(
            "cannot resolve",
            "unresolved reference",
            "cannot find package",
            "unknown package",
        )
    }
}
