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

        // Gno has a few predeclared identifiers that Go doesn't know about. Instead of forcing users
        // to maintain stubs in each project, just suppress the IDE error diagnostics for them.
        if (GNO_PREDECLARED_IDENTIFIERS.any { id -> message.contains(id) } &&
            GNO_PREDECLARED_ERROR_SNIPPETS.any(message::contains)
        ) {
            return true
        }

        return RESOLVE_ERROR_SNIPPETS.any(message::contains)
    }

    private companion object {
        private val RESOLVE_ERROR_SNIPPETS = listOf(
            "cannot resolve",
            "unresolved reference",
            "cannot find package",
            "unknown package",
        )

        private val GNO_PREDECLARED_IDENTIFIERS = listOf(
            "realm",
            "address",
            "cross",
        )

        private val GNO_PREDECLARED_ERROR_SNIPPETS = listOf(
            "unresolved type",
            "undefined",
        )
    }
}
