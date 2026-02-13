package io.gnolang.ide.goland.imports;

import com.goide.completion.GoImportPathsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PairProcessor;
import java.nio.file.Path;

public final class GnoBuiltinImportPathsProvider implements GoImportPathsProvider {
    @Override
    public void processImportPaths(
        Module module,
        GlobalSearchScope scope,
        boolean withTests,
        boolean vendoringEnabled,
        PairProcessor<? super String, ? super PsiDirectory> processor
    ) {
        for (GnoBuiltinImportCatalog.BuiltinImport builtinImport : GnoBuiltinImportCatalog.all()) {
            PsiDirectory directory = GnoBuiltinDirectoryResolver.resolve(module.getProject(), builtinImport);
            if (directory == null) {
                continue;
            }

            if (!processor.process(builtinImport.displayImportPath(), directory)) {
                return;
            }
        }

        // If Gno stdlibs are available, also offer them for import completion (e.g. `chain`).
        if (!GnoStdlibSupport.isEnabled(module.getProject(), module)) {
            return;
        }

        Path stdlibRoot = GnoStdlibSupport.findStdlibRoot(module.getProject());
        if (stdlibRoot == null) {
            return;
        }

        for (String importPath : GnoStdlibSupport.listAvailableStdlibImportPaths(stdlibRoot)) {
            PsiDirectory directory = GnoStdlibSupport.resolveStdlibDirectory(module.getProject(), stdlibRoot, importPath);
            if (directory == null) {
                continue;
            }

            if (!processor.process(importPath, directory)) {
                return;
            }
        }
    }
}
