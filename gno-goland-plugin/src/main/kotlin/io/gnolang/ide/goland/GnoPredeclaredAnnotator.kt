package io.gnolang.ide.goland

import com.goide.highlighting.GoSyntaxHighlightingColors
import com.goide.psi.GoTypeReferenceExpression
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

class GnoPredeclaredAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile ?: return
        if (!isGnoFileName(file.name)) {
            return
        }

        val typeRef = element as? GoTypeReferenceExpression ?: return
        if (typeRef.qualifier != null) {
            return
        }

        val identifier = typeRef.identifier ?: return
        if (identifier.text !in GNO_PREDECLARED_TYPES) {
            return
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(identifier)
            .textAttributes(GoSyntaxHighlightingColors.BUILTIN_TYPE_REFERENCE)
            .create()
    }

    private companion object {
        private val GNO_PREDECLARED_TYPES = setOf(
            "realm",
            "address",
            "cross",
        )
    }
}

