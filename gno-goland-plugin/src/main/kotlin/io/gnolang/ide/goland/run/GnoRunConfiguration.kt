package io.gnolang.ide.goland.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element

class GnoRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : RunConfigurationBase<Any>(project, factory, name) {
    var gnoExecutablePath: String = "gno"
    var arguments: String = "test ."
    var workingDirectory: String? = null
    var gnoRootDirectory: String? = null

    override fun getConfigurationEditor() = GnoRunSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return GnoCommandLineState(environment, this)
    }

    override fun checkConfiguration() {
        if (gnoExecutablePath.isBlank()) {
            throw RuntimeConfigurationError("Gno executable must not be empty")
        }
        if (arguments.isBlank()) {
            throw RuntimeConfigurationError("Arguments must not be empty (example: test .)")
        }
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "gnoExecutablePath", gnoExecutablePath)
        JDOMExternalizerUtil.writeField(element, "arguments", arguments)
        JDOMExternalizerUtil.writeField(element, "workingDirectory", workingDirectory ?: "")
        JDOMExternalizerUtil.writeField(element, "gnoRootDirectory", gnoRootDirectory ?: "")
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        gnoExecutablePath = JDOMExternalizerUtil.readField(element, "gnoExecutablePath") ?: "gno"
        arguments = JDOMExternalizerUtil.readField(element, "arguments") ?: "test ."
        workingDirectory = JDOMExternalizerUtil.readField(element, "workingDirectory")?.trim().takeUnless { it.isNullOrEmpty() }
        gnoRootDirectory = JDOMExternalizerUtil.readField(element, "gnoRootDirectory")?.trim().takeUnless { it.isNullOrEmpty() }
    }
}
