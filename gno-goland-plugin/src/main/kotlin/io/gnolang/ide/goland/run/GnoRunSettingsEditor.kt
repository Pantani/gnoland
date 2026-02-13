package io.gnolang.ide.goland.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class GnoRunSettingsEditor(private val project: Project) : SettingsEditor<GnoRunConfiguration>() {
    private val gnoExecutablePathField = TextFieldWithBrowseButton()
    private val argumentsField = RawCommandLineEditor()
    private val workingDirectoryField = TextFieldWithBrowseButton()
    private val gnoRootDirectoryField = TextFieldWithBrowseButton()

    init {
        gnoExecutablePathField.addActionListener {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
                project,
                null
            ) { file ->
                gnoExecutablePathField.text = file.path
            }
        }

        workingDirectoryField.addActionListener {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project,
                null
            ) { file ->
                workingDirectoryField.text = file.path
            }
        }

        gnoRootDirectoryField.addActionListener {
            com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                FileChooserDescriptorFactory.createSingleFolderDescriptor(),
                project,
                null
            ) { file ->
                gnoRootDirectoryField.text = file.path
            }
        }
    }

    override fun resetEditorFrom(s: GnoRunConfiguration) {
        gnoExecutablePathField.text = s.gnoExecutablePath
        argumentsField.text = s.arguments
        workingDirectoryField.text = s.workingDirectory ?: project.basePath.orEmpty()
        gnoRootDirectoryField.text = s.gnoRootDirectory.orEmpty()
    }

    override fun applyEditorTo(s: GnoRunConfiguration) {
        s.gnoExecutablePath = gnoExecutablePathField.text.trim().ifEmpty { "gno" }
        s.arguments = argumentsField.text.trim()

        val wd = workingDirectoryField.text.trim()
        s.workingDirectory = wd.ifEmpty { null }

        val gnoRoot = gnoRootDirectoryField.text.trim()
        s.gnoRootDirectory = gnoRoot.ifEmpty { null }
    }

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Gno executable:", gnoExecutablePathField)
            .addLabeledComponent("Arguments (e.g. `test .`):", argumentsField)
            .addLabeledComponent("Working directory:", workingDirectoryField)
            .addLabeledComponent("GNOROOT (optional):", gnoRootDirectoryField)
            .panel
    }
}
