package io.gnolang.ide.goland.imports;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

final class GnoStdlibSupport {
    private static final Key<Boolean> IS_GNO_MODULE = Key.create("gno-goland-plugin.isGnoModule");
    private static final Key<Path> STDLIB_ROOT = Key.create("gno-goland-plugin.stdlibRoot");

    private static final ConcurrentHashMap<Path, List<String>> IMPORT_PATHS_CACHE = new ConcurrentHashMap<>();

    private GnoStdlibSupport() {
    }

    static boolean isEnabled(Project project, Module module) {
        if (module == null) {
            return false;
        }

        Boolean cached = module.getUserData(IS_GNO_MODULE);
        if (Boolean.TRUE.equals(cached)) {
            return true;
        }

        // Fast-path: opening the Gno repo (or a nested folder within it). Note: do not treat
        // GNOROOT presence as a Gno-project indicator, as it may be set globally for unrelated
        // Go projects.
        if (findStdlibRootFromProject(project) != null) {
            module.putUserData(IS_GNO_MODULE, Boolean.TRUE);
            return true;
        }

        // Avoid index reads during indexing.
        if (DumbService.isDumb(project)) {
            return false;
        }

        try {
            GlobalSearchScope scope = GlobalSearchScope.moduleScope(module);
            boolean looksLikeGno =
                !FilenameIndex.getVirtualFilesByName("gnomod.toml", scope).isEmpty()
                    || !FilenameIndex.getVirtualFilesByName("gno.mod", scope).isEmpty()
                    || hasGnoFiles(project, scope);
            if (looksLikeGno) {
                module.putUserData(IS_GNO_MODULE, Boolean.TRUE);
                return true;
            }
        } catch (IndexNotReadyException ignored) {
            return false;
        }

        return false;
    }

    private static boolean hasGnoFiles(Project project, GlobalSearchScope scope) {
        try {
            // More thorough check: process all filenames to find any .gno file
            boolean[] found = {false};
            FilenameIndex.processAllFileNames(
                filename -> {
                    if (filename.endsWith(".gno")) {
                        if (!FilenameIndex.getVirtualFilesByName(filename, scope).isEmpty()) {
                            found[0] = true;
                            return false; // Stop processing
                        }
                    }
                    return true; // Continue processing
                },
                scope,
                null
            );
            return found[0];
        } catch (Exception e) {
            return false;
        }
    }

    static Path findStdlibRoot(Project project) {
        Path cached = project.getUserData(STDLIB_ROOT);
        if (cached != null && Files.isDirectory(cached)) {
            return cached;
        }

        Path fromProject = findStdlibRootFromProject(project);
        if (fromProject != null) {
            project.putUserData(STDLIB_ROOT, fromProject);
            return fromProject;
        }

        Path fromEnv = findStdlibRootFromEnv();
        if (fromEnv != null) {
            project.putUserData(STDLIB_ROOT, fromEnv);
            return fromEnv;
        }

        return null;
    }

    private static Path findStdlibRootFromProject(Project project) {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            return null;
        }

        for (Path p = Path.of(basePath.trim()); p != null; p = p.getParent()) {
            Path candidate = p.resolve("gnovm").resolve("stdlibs");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static Path findStdlibRootFromEnv() {
        String gnoRoot = System.getenv("GNOROOT");
        if (gnoRoot == null || gnoRoot.isBlank()) {
            return null;
        }

        Path stdlibRoot = Path.of(gnoRoot.trim()).resolve("gnovm").resolve("stdlibs");
        if (Files.isDirectory(stdlibRoot)) {
            return stdlibRoot;
        }

        return null;
    }

    static PsiDirectory resolveStdlibDirectory(Project project, Path stdlibRoot, String importPath) {
        if (stdlibRoot == null || importPath == null || importPath.isBlank()) {
            return null;
        }

        if (!isStdlibLikeImportPath(importPath)) {
            return null;
        }

        Path directoryPath = stdlibRoot.resolve(importPath);
        VirtualFile virtualDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directoryPath);
        if (virtualDirectory == null || !virtualDirectory.isDirectory()) {
            return null;
        }

        return PsiManager.getInstance(project).findDirectory(virtualDirectory);
    }

    static boolean shouldStubMissingStdlib(Project project, Module module, Path stdlibRoot, String importPath) {
        if (!isEnabled(project, module)) {
            return false;
        }
        if (stdlibRoot == null) {
            return false;
        }
        if (importPath == null || importPath.isBlank()) {
            return false;
        }

        // Only block "dotless" imports (stdlib-like). Anything with a dot is likely a user/module import.
        if (!isStdlibLikeImportPath(importPath)) {
            return false;
        }

        // If the package exists in Gno stdlibs, let it resolve normally.
        return !Files.isDirectory(stdlibRoot.resolve(importPath));
    }

    static boolean isStdlibLikeImportPath(String importPath) {
        return importPath != null && !importPath.isBlank() && !importPath.contains(".");
    }

    static List<String> listAvailableStdlibImportPaths(Path stdlibRoot) {
        if (stdlibRoot == null || !Files.isDirectory(stdlibRoot)) {
            return Collections.emptyList();
        }

        return IMPORT_PATHS_CACHE.computeIfAbsent(stdlibRoot, GnoStdlibSupport::scanStdlibImportPaths);
    }

    private static List<String> scanStdlibImportPaths(Path stdlibRoot) {
        List<String> importPaths = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(stdlibRoot)) {
            paths
                .filter(Files::isDirectory)
                .map(dir -> stdlibRoot.relativize(dir))
                .filter(rel -> !rel.toString().isBlank())
                .map(rel -> rel.toString().replace('\\', '/'))
                .filter(GnoStdlibSupport::isImportableStdlibPathForCompletion)
                .forEach(importPath -> {
                    // Ensure it actually looks like a package (contains at least one *.go or *.gno file).
                    Path dir = stdlibRoot.resolve(importPath);
                    if (containsGnoOrGoFiles(dir)) {
                        importPaths.add(importPath);
                    }
                });
        } catch (IOException ignored) {
            return Collections.emptyList();
        }

        importPaths.sort(String::compareTo);
        return Collections.unmodifiableList(importPaths);
    }

    private static boolean isImportableStdlibPathForCompletion(String importPath) {
        if (importPath == null || importPath.isBlank()) {
            return false;
        }

        // Avoid offering internal/testdata packages in import completion.
        if (importPath.equals("internal") || importPath.startsWith("internal/") || importPath.contains("/internal/")) {
            return false;
        }
        if (importPath.equals("testdata") || importPath.startsWith("testdata/") || importPath.contains("/testdata/")) {
            return false;
        }

        // Ignore hidden dirs (".git", etc).
        String[] segments = importPath.split("/");
        for (String segment : segments) {
            if (segment.startsWith(".")) {
                return false;
            }
        }

        return true;
    }

    private static boolean containsGnoOrGoFiles(Path dir) {
        if (dir == null) {
            return false;
        }

        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .filter(Objects::nonNull)
                .map(Path::toString)
                .anyMatch(name -> name.endsWith(".gno") || name.endsWith(".go"));
        } catch (IOException ignored) {
            return false;
        }
    }
}
