package io.gnolang.ide.goland.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.util.execution.ParametersListUtil

class GnoRunConfigurationProducer : LazyRunConfigurationProducer<GnoRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return ConfigurationTypeUtil.findConfigurationType(GnoRunConfigurationType::class.java).configurationFactories.first()
    }
    override fun setupConfigurationFromContext(
        configuration: GnoRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val psiFile = context.psiLocation?.containingFile ?: return false
        val vFile = psiFile.virtualFile ?: return false
        val name = vFile.name
        if (!name.endsWith(".gno", ignoreCase = true)) {
            return false
        }

        val parentPath = vFile.parent?.path
        val filePath = vFile.path
        if (isTestFileName(name)) {
            configuration.name = "Gno test: ${parentPath ?: name}"
            configuration.arguments = if (parentPath != null) "test ${ParametersListUtil.escape(parentPath)}" else "test ."
            configuration.workingDirectory = parentPath
            return true
        }

        configuration.name = "Gno run: $name"
        configuration.arguments = "run ${ParametersListUtil.escape(filePath)}"
        configuration.workingDirectory = parentPath
        return true
    }

    override fun isConfigurationFromContext(
        configuration: GnoRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val psiFile = context.psiLocation?.containingFile ?: return false
        val vFile = psiFile.virtualFile ?: return false
        val name = vFile.name
        if (!name.endsWith(".gno", ignoreCase = true)) {
            return false
        }

        val expectedTokens = if (isTestFileName(name)) {
            val parentPath = vFile.parent?.path ?: "."
            listOf("test", parentPath)
        } else {
            listOf("run", vFile.path)
        }

        val actualTokens = ParametersListUtil.parse(configuration.arguments)
        return actualTokens == expectedTokens
    }

    private fun isTestFileName(fileName: String): Boolean {
        return fileName.endsWith("_test.gno", ignoreCase = true) ||
            fileName.endsWith("_filetest.gno", ignoreCase = true)
    }
}
