package io.gnolang.ide.goland

internal fun isGnoFileName(fileName: String): Boolean = fileName.endsWith(".gno", ignoreCase = true)
