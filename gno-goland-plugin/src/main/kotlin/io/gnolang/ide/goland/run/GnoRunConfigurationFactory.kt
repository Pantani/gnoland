package io.gnolang.ide.goland.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

class GnoRunConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return GnoRunConfiguration(project, this, "Gno")
    }

    override fun getId(): String = "GnoRunConfigurationFactory"
}

