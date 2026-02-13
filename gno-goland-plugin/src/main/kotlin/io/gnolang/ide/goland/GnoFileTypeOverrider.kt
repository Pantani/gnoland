package io.gnolang.ide.goland

import com.goide.GoFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.fileTypes.impl.FileTypeOverrider
import com.intellij.openapi.vfs.VirtualFile

class GnoFileTypeOverrider : FileTypeOverrider {
    override fun getOverriddenFileType(file: VirtualFile): FileType? {
        val fileName = file.name
        if (!isGnoFileName(file.name)) {
            if (!fileName.equals("gno.mod", ignoreCase = true)) {
                return null
            }

            // Reuse GoLand's native go.mod support for gno.mod highlighting/editing.
            val goModFileType = FileTypeManager.getInstance().getFileTypeByFileName("go.mod")
            if (goModFileType !== UnknownFileType.INSTANCE) {
                return goModFileType
            }

            return null
        }

        return GoFileType.INSTANCE
    }
}
