package io.gnolang.ide.goland.imports;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class GnoBuiltinDirectoryResolver {
    private GnoBuiltinDirectoryResolver() {
    }

    static PsiDirectory resolve(Project project, GnoBuiltinImportCatalog.BuiltinImport builtinImport) {
        PsiDirectory projectDirectory = findInProject(project, builtinImport);
        if (projectDirectory != null) {
            return projectDirectory;
        }

        return ensureStubDirectory(project, packageNameFromImportPath(builtinImport.canonicalImportPath()));
    }

    private static PsiDirectory findInProject(Project project, GnoBuiltinImportCatalog.BuiltinImport builtinImport) {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isEmpty()) {
            return null;
        }

        for (String relativePath : builtinImport.projectRelativeDirs()) {
            Path directoryPath = Path.of(basePath).resolve(relativePath);
            VirtualFile virtualDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directoryPath);
            if (virtualDirectory == null || !virtualDirectory.isDirectory()) {
                continue;
            }

            PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualDirectory);
            if (psiDirectory != null) {
                return psiDirectory;
            }
        }

        return null;
    }

    private static PsiDirectory ensureStubDirectory(Project project, String packageName) {
        try {
            Path stubDirectoryPath = Path.of(
                PathManager.getSystemPath(),
                "gno-goland-plugin",
                "stubs",
                project.getLocationHash(),
                packageName
            );

            Files.createDirectories(stubDirectoryPath);
            Path stubFilePath = stubDirectoryPath.resolve(packageName + ".go");
            writeStubFileIfNeeded(stubFilePath, packageName);

            VirtualFile virtualDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(stubDirectoryPath);
            if (virtualDirectory == null || !virtualDirectory.isDirectory()) {
                return null;
            }

            return PsiManager.getInstance(project).findDirectory(virtualDirectory);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void writeStubFileIfNeeded(Path stubFilePath, String packageName) throws IOException {
        String stubContent = "package " + packageName + "\n";
        if (Files.exists(stubFilePath)) {
            return;
        }

        Files.writeString(
            stubFilePath,
            stubContent,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        );
    }

    private static String packageNameFromImportPath(String importPath) {
        int slashIndex = importPath.lastIndexOf('/');
        if (slashIndex == -1 || slashIndex == importPath.length() - 1) {
            return importPath;
        }

        return importPath.substring(slashIndex + 1);
    }
}
