package io.gnolang.ide.goland.imports;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class GnoRemotePackageCacheService {
    private record FetchOutcome(Path packageDirectory, Set<String> importedRemotePackages, int downloadedFileCount) {
    }

    private static final Logger LOG = Logger.getInstance(GnoRemotePackageCacheService.class);
    private static final String CACHE_MARKER_FILE = ".gno-package-cache-meta";

    private final Project project;
    private final Path cacheRoot;
    private final GnoRemotePackageFetcher fetcher;
    private final ConcurrentHashMap<String, CompletableFuture<FetchOutcome>> inFlightFetches = new ConcurrentHashMap<>();
    private final Object importPathsCacheLock = new Object();

    private volatile boolean importPathsCacheDirty = true;
    private volatile List<String> cachedImportPaths = Collections.emptyList();

    public GnoRemotePackageCacheService(Project project) {
        this.project = project;
        this.cacheRoot = Path.of(
            PathManager.getSystemPath(),
            "gno-goland-plugin",
            "pkg-cache",
            project.getLocationHash()
        );

        HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.fetcher = new GnoRemotePackageFetcher(httpClient, Duration.ofSeconds(15));
    }

    static GnoRemotePackageCacheService getInstance(Project project) {
        return project.getService(GnoRemotePackageCacheService.class);
    }

    boolean isFetchableImportPath(String importPath) {
        return GnoRemotePackageFetcher.isFetchableImportPath(importPath);
    }

    @Nullable
    PsiDirectory resolveCachedDirectory(String importPath) {
        Path packageDirectory = packageDirectory(importPath);
        if (packageDirectory == null || !hasCompletedPackage(packageDirectory)) {
            return null;
        }

        LocalFileSystem fileSystem = LocalFileSystem.getInstance();
        VirtualFile virtualDirectory = fileSystem.findFileByNioFile(packageDirectory);
        if (virtualDirectory == null) {
            virtualDirectory = fileSystem.refreshAndFindFileByNioFile(packageDirectory);
        }
        if (virtualDirectory == null || !virtualDirectory.isDirectory()) {
            return null;
        }

        return PsiManager.getInstance(project).findDirectory(virtualDirectory);
    }

    void ensurePackageFetched(String importPath) {
        if (project.isDisposed() || !isFetchableImportPath(importPath)) {
            return;
        }

        Path packageDirectory = packageDirectory(importPath);
        if (packageDirectory == null || hasCompletedPackage(packageDirectory)) {
            return;
        }

        inFlightFetches.computeIfAbsent(importPath, key -> startFetch(importPath, packageDirectory));
    }

    List<String> listCachedImportPaths() {
        if (!importPathsCacheDirty) {
            return cachedImportPaths;
        }

        synchronized (importPathsCacheLock) {
            if (!importPathsCacheDirty) {
                return cachedImportPaths;
            }

            cachedImportPaths = scanCachedImportPaths();
            importPathsCacheDirty = false;
            return cachedImportPaths;
        }
    }

    private CompletableFuture<FetchOutcome> startFetch(String importPath, Path packageDirectory) {
        CompletableFuture<FetchOutcome> future = CompletableFuture.supplyAsync(
            () -> fetchAndCache(importPath, packageDirectory),
            AppExecutorUtil.getAppExecutorService()
        );

        future.whenComplete((outcome, throwable) -> {
            inFlightFetches.remove(importPath);

            if (throwable != null) {
                LOG.warn("Failed to fetch remote Gno package: " + importPath, throwable);
                return;
            }

            if (outcome == null || outcome.downloadedFileCount() == 0) {
                return;
            }

            markImportPathsCacheDirty();
            refreshAfterFetch(outcome.packageDirectory());
            for (String dependency : outcome.importedRemotePackages()) {
                ensurePackageFetched(dependency);
            }
        });

        return future;
    }

    private FetchOutcome fetchAndCache(String importPath, Path packageDirectory) {
        try {
            Files.createDirectories(packageDirectory);
            if (hasCompletedPackage(packageDirectory)) {
                return new FetchOutcome(packageDirectory, Set.of(), 0);
            }

            GnoRemotePackageFetcher.RemotePackage remotePackage = fetcher.fetchPackage(importPath);
            if (remotePackage.sourceFiles().isEmpty()) {
                return new FetchOutcome(packageDirectory, Set.of(), 0);
            }

            for (var entry : remotePackage.sourceFiles().entrySet()) {
                writeSourceFile(packageDirectory, entry.getKey(), entry.getValue());
            }

            writeCacheMarkerFile(packageDirectory, importPath, remotePackage.sourceFiles().keySet());
            return new FetchOutcome(
                packageDirectory,
                new HashSet<>(remotePackage.importedRemotePackages()),
                remotePackage.sourceFiles().size()
            );
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            LOG.warn("Remote package fetch interrupted: " + importPath, interruptedException);
            return new FetchOutcome(packageDirectory, Set.of(), 0);
        } catch (IOException ioException) {
            LOG.warn("Unable to cache remote package: " + importPath, ioException);
            return new FetchOutcome(packageDirectory, Set.of(), 0);
        }
    }

    private void writeSourceFile(Path packageDirectory, String fileName, String source) throws IOException {
        Path sourceFilePath = packageDirectory.resolve(fileName).normalize();
        if (!sourceFilePath.startsWith(packageDirectory)) {
            throw new IOException("Refusing to write source file outside package cache: " + fileName);
        }

        Files.writeString(
            sourceFilePath,
            source,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private void writeCacheMarkerFile(Path packageDirectory, String importPath, Set<String> files) throws IOException {
        List<String> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(Comparator.naturalOrder());

        StringBuilder content = new StringBuilder();
        content.append("import_path=").append(importPath).append('\n');
        content.append("cached_at=").append(Instant.now()).append('\n');
        content.append("files=").append(String.join(",", sortedFiles)).append('\n');

        Files.writeString(
            packageDirectory.resolve(CACHE_MARKER_FILE),
            content.toString(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    private void refreshAfterFetch(Path packageDirectory) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) {
                return;
            }

            VirtualFile virtualDirectory = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(packageDirectory);
            if (virtualDirectory != null) {
                VfsUtil.markDirtyAndRefresh(false, true, true, virtualDirectory);
            }

            DaemonCodeAnalyzer.getInstance(project).restart();
        });
    }

    @Nullable
    private Path packageDirectory(String importPath) {
        if (!isFetchableImportPath(importPath)) {
            return null;
        }

        Path resolved = cacheRoot.resolve(importPath).normalize();
        if (!resolved.startsWith(cacheRoot)) {
            return null;
        }

        return resolved;
    }

    private List<String> scanCachedImportPaths() {
        if (!Files.isDirectory(cacheRoot)) {
            return Collections.emptyList();
        }

        Set<String> importPaths = new HashSet<>();
        try (Stream<Path> paths = Files.walk(cacheRoot)) {
            paths
                .filter(path -> Files.isRegularFile(path) && CACHE_MARKER_FILE.equals(path.getFileName().toString()))
                .map(Path::getParent)
                .filter(path -> path != null && path.startsWith(cacheRoot) && hasSourceFiles(path))
                .map(path -> cacheRoot.relativize(path).toString().replace('\\', '/'))
                .filter(this::isFetchableImportPath)
                .forEach(importPaths::add);
        } catch (IOException ioException) {
            LOG.warn("Unable to scan cached remote Gno packages at: " + cacheRoot, ioException);
            return Collections.emptyList();
        }

        List<String> sortedImportPaths = new ArrayList<>(importPaths);
        sortedImportPaths.sort(Comparator.naturalOrder());
        return List.copyOf(sortedImportPaths);
    }

    private void markImportPathsCacheDirty() {
        importPathsCacheDirty = true;
    }

    private static boolean hasSourceFiles(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }

        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .filter(name -> name != null)
                .map(Path::toString)
                .anyMatch(GnoRemotePackageCacheService::isSourceFile);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isSourceFile(String fileName) {
        return fileName.endsWith(".gno") || fileName.endsWith(".go");
    }

    private static boolean hasCompletedPackage(Path directory) {
        return Files.isRegularFile(directory.resolve(CACHE_MARKER_FILE)) && hasSourceFiles(directory);
    }
}
