package io.gnolang.ide.goland.imports;

import com.goide.GoFileType;
import com.goide.psi.GoFile;
import com.goide.psi.impl.GoPackage;
import com.goide.psi.impl.imports.DefaultGoImportResolver;
import com.goide.psi.impl.imports.GoImportResolver;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.ResolveState;
import com.intellij.util.ThreeState;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.nio.file.Path;

public final class GnoBuiltinImportResolver implements GoImportResolver {
    private final GoImportResolver delegate = new DefaultGoImportResolver();

    @Override
    public ThreeState supportsCanonicalImportPath(Project project, Module module) {
        return ThreeState.YES;
    }

    @Override
    public Collection<GoPackage> resolve(String importPath, Project project, Module module, ResolveState state) {
        GnoBuiltinImportCatalog.BuiltinImport builtinImport = GnoBuiltinImportCatalog.findByImportPath(importPath);
        if (builtinImport == null) {
            Collection<GoPackage> stdlibResolution = resolveFromStdlib(importPath, project, module);
            if (!stdlibResolution.isEmpty()) {
                return stdlibResolution;
            }

            Collection<GoPackage> remoteResolution = resolveFromRemoteCache(importPath, project, module);
            if (!remoteResolution.isEmpty()) {
                return remoteResolution;
            }

            return Collections.emptyList();
        }

        if (module != null) {
            Collection<GoPackage> delegated = delegate.resolve(builtinImport.canonicalImportPath(), project, module, state);
            if (!delegated.isEmpty()) {
                return delegated;
            }
        }

        PsiDirectory directory = GnoBuiltinDirectoryResolver.resolveProjectDirectory(project, builtinImport);
        if (directory != null) {
            GoPackage resolved = GoPackage.in(directory, packageNameFromImportPath(builtinImport.canonicalImportPath()));
            if (resolved != null && resolved.isValid()) {
                return List.of(resolved);
            }

            GoPackage any = packageFromDirectory(directory);
            if (any != null && any.isValid()) {
                return List.of(any);
            }
        }

        Collection<GoPackage> remoteResolution = resolveFromRemoteCache(builtinImport.canonicalImportPath(), project, module);
        if (!remoteResolution.isEmpty()) {
            return remoteResolution;
        }

        directory = GnoBuiltinDirectoryResolver.resolve(project, builtinImport);
        if (directory != null) {
            GoPackage resolved = GoPackage.in(directory, packageNameFromImportPath(builtinImport.canonicalImportPath()));
            if (resolved != null && resolved.isValid()) {
                return List.of(resolved);
            }

            GoPackage any = packageFromDirectory(directory);
            if (any != null && any.isValid()) {
                return List.of(any);
            }
        }

        GoPackage synthetic = createSyntheticPackage(project, packageNameFromImportPath(builtinImport.canonicalImportPath()));
        if (synthetic == null) {
            return Collections.emptyList();
        }

        return List.of(synthetic);
    }

    private Collection<GoPackage> resolveFromStdlib(String importPath, Project project, Module module) {
        // Try resolving via Gno stdlibs (gnovm/stdlibs) when available.
        if (!GnoStdlibSupport.isEnabled(project, module)) {
            return Collections.emptyList();
        }

        Path stdlibRoot = GnoStdlibSupport.findStdlibRoot(project);
        if (stdlibRoot == null) {
            return Collections.emptyList();
        }

        PsiDirectory stdlibDir = GnoStdlibSupport.resolveStdlibDirectory(project, stdlibRoot, importPath);
        if (stdlibDir != null) {
            GoPackage resolved = GoPackage.in(stdlibDir, packageNameFromImportPath(importPath));
            if (resolved != null && resolved.isValid()) {
                return List.of(resolved);
            }

            GoPackage any = packageFromDirectory(stdlibDir);
            if (any != null && any.isValid()) {
                return List.of(any);
            }
        }

        // If stdlibs are present and this looks like a stdlib import, don't fall back to Go's stdlib.
        if (GnoStdlibSupport.shouldStubMissingStdlib(project, module, stdlibRoot, importPath)) {
            GoPackage synthetic = createSyntheticPackage(project, packageNameFromImportPath(importPath));
            if (synthetic != null) {
                return List.of(synthetic);
            }
        }

        return Collections.emptyList();
    }

    private Collection<GoPackage> resolveFromRemoteCache(String importPath, Project project, Module module) {
        if (module != null && !GnoStdlibSupport.isEnabled(project, module)) {
            return Collections.emptyList();
        }

        GnoRemotePackageCacheService remoteCacheService = GnoRemotePackageCacheService.getInstance(project);
        if (remoteCacheService == null || !remoteCacheService.isFetchableImportPath(importPath)) {
            return Collections.emptyList();
        }

        PsiDirectory cachedDirectory = remoteCacheService.resolveCachedDirectory(importPath);
        if (cachedDirectory == null) {
            remoteCacheService.ensurePackageFetched(importPath);
            GoPackage synthetic = createSyntheticPackage(project, packageNameFromImportPath(importPath));
            if (synthetic != null) {
                return List.of(synthetic);
            }
            return Collections.emptyList();
        }

        GoPackage resolved = GoPackage.in(cachedDirectory, packageNameFromImportPath(importPath));
        if (resolved != null && resolved.isValid()) {
            return List.of(resolved);
        }

        GoPackage any = packageFromDirectory(cachedDirectory);
        if (any != null && any.isValid()) {
            return List.of(any);
        }

        return Collections.emptyList();
    }

    private GoPackage createSyntheticPackage(Project project, String packageName) {
        PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(
            "gno_builtin_" + packageName + ".go",
            GoFileType.INSTANCE,
            "package " + packageName + "\n"
        );

        if (!(psiFile instanceof GoFile)) {
            return null;
        }

        return GoPackage.of((GoFile) psiFile);
    }

    private GoPackage packageFromDirectory(PsiDirectory directory) {
        for (PsiFile file : directory.getFiles()) {
            if (file instanceof GoFile) {
                return GoPackage.of((GoFile) file);
            }
        }
        return null;
    }

    private String packageNameFromImportPath(String importPath) {
        int slashIndex = importPath.lastIndexOf('/');
        if (slashIndex == -1 || slashIndex == importPath.length() - 1) {
            return importPath;
        }

        return importPath.substring(slashIndex + 1);
    }
}
