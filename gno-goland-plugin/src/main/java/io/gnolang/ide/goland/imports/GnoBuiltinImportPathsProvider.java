package io.gnolang.ide.goland.imports;

import com.goide.completion.GoImportPathsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PairProcessor;
import java.util.Map;

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

        for (Map.Entry<String, PsiDirectory> entry : GnoWorkspacePackageIndex.all(module.getProject()).entrySet()) {
            if (!processor.process(entry.getKey(), entry.getValue())) {
                return;
            }
        }
    }
}
