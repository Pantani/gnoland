package io.gnolang.ide.goland.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.execution.ParametersListUtil
import kotlin.io.path.exists
import java.nio.file.Path

class GnoCommandLineState(
    environment: ExecutionEnvironment,
    private val configuration: GnoRunConfiguration,
) : CommandLineState(environment) {

    init {
        consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(environment.project)
    }

    override fun startProcess(): ProcessHandler {
        val exe = configuration.gnoExecutablePath.ifBlank { "gno" }
        val args = ParametersListUtil.parse(configuration.arguments)

        val cmd = com.intellij.execution.configurations.GeneralCommandLine(exe)
            .withParameters(args)
            .withParentEnvironmentType(com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.CONSOLE)

        val workDir = configuration.workingDirectory?.takeIf(StringUtil::isNotEmpty)
            ?: environment.project.basePath
        if (!workDir.isNullOrBlank()) {
            cmd.withWorkDirectory(workDir)
        }

        val gnoRoot = configuration.gnoRootDirectory?.takeIf(StringUtil::isNotEmpty)
        if (!gnoRoot.isNullOrBlank() && Path.of(gnoRoot).exists()) {
            cmd.withEnvironment("GNOROOT", gnoRoot)
        }

        return KillableProcessHandler(cmd).also { ProcessTerminatedListener.attach(it) }
    }
}
