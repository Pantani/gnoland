package io.gnolang.ide.goland.imports;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps Gno package import paths to local project directories.
 *
 * <p>Gno packages typically define their canonical import path via either:</p>
 * <ul>
 *   <li>{@code gnomod.toml} (TOML, e.g. {@code module = "gno.land/r/..."})</li>
 *   <li>{@code gno.mod} (legacy go.mod-like syntax, e.g. {@code module gno.land/r/...})</li>
 * </ul>
 */
final class GnoWorkspacePackageIndex {
    private static final Key<CachedValue<Map<String, PsiDirectory>>> CACHE_KEY =
        Key.create("io.gnolang.ide.goland.imports.GnoWorkspacePackageIndex");

    private static final Pattern GNOMOD_TOML_PKG_PATTERN =
        Pattern.compile(
            "(?m)^\\s*(module|pkgpath)\\s*=\\s*(['\\\"])(.*?)\\2\\s*(?:#.*)?$"
        );

    private GnoWorkspacePackageIndex() {
    }

    static PsiDirectory resolve(Project project, String importPath) {
        if (importPath == null || importPath.isEmpty()) {
            return null;
        }

        return all(project).get(importPath);
    }

    static Map<String, PsiDirectory> all(Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(
            project,
            CACHE_KEY,
            () -> CachedValueProvider.Result.create(
                compute(project),
                PsiModificationTracker.MODIFICATION_COUNT
            ),
            false
        );
    }

    private static Map<String, PsiDirectory> compute(Project project) {
        Map<String, PsiDirectory> result = new HashMap<>();
        collectFromGnoModToml(project, result);
        collectFromGnoMod(project, result);
        return result;
    }

    private static void collectFromGnoModToml(Project project, Map<String, PsiDirectory> out) {
        collectByFilename(project, "gnomod.toml", out, true);
    }

    private static void collectFromGnoMod(Project project, Map<String, PsiDirectory> out) {
        collectByFilename(project, "gno.mod", out, false);
    }

    private static void collectByFilename(
        Project project,
        String filename,
        Map<String, PsiDirectory> out,
        boolean toml
    ) {
        Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(
            project,
            filename,
            GlobalSearchScope.projectScope(project)
        );

        PsiManager psiManager = PsiManager.getInstance(project);
        for (VirtualFile vf : files) {
            VirtualFile parent = vf.getParent();
            if (parent == null || !parent.isDirectory()) {
                continue;
            }

            String text;
            try {
                text = VfsUtilCore.loadText(vf);
            } catch (IOException ignored) {
                continue;
            }

            String pkgpath = toml ? parsePkgPathFromGnoModToml(text) : parsePkgPathFromGnoMod(text);
            if (pkgpath == null || pkgpath.isBlank()) {
                continue;
            }

            PsiDirectory dir = psiManager.findDirectory(parent);
            if (dir == null) {
                continue;
            }

            out.putIfAbsent(pkgpath, dir);
        }
    }

    private static String parsePkgPathFromGnoModToml(String text) {
        Matcher m = GNOMOD_TOML_PKG_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }

        return m.group(3).trim();
    }

    private static String parsePkgPathFromGnoMod(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }

            if (!trimmed.startsWith("module")) {
                continue;
            }

            String rest = trimmed.substring("module".length()).trim();
            if (rest.isEmpty()) {
                continue;
            }

            int commentIndex = rest.indexOf("//");
            if (commentIndex >= 0) {
                rest = rest.substring(0, commentIndex).trim();
            }

            return rest;
        }

        return null;
    }
}

