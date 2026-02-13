package io.gnolang.ide.goland.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons

class GnoRunConfigurationType : ConfigurationTypeBase(
    ID,
    "Gno",
    "Run Gno commands using the gno CLI",
    AllIcons.RunConfigurations.Application,
) {
    init {
        addFactory(GnoRunConfigurationFactory(this))
    }

    companion object {
        const val ID: String = "io.gnolang.ide.goland.runConfiguration"
    }
}

