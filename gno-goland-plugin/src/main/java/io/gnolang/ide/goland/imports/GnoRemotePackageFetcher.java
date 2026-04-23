package io.gnolang.ide.goland.imports;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GnoRemotePackageFetcher {
    record RemotePackage(Map<String, String> sourceFiles, Set<String> importedRemotePackages) {
    }

    private static final Logger LOG = Logger.getInstance(GnoRemotePackageFetcher.class);

    private static final Pattern IMPORT_PATH_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9./_-]*$");
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9._+-]+\\.(?:gno|go)$");
    private static final Pattern FILE_PARAM_PATTERN = Pattern.compile(
        "(?:%24(?:source|download)%26file%3D|\\$source&file=|\\$download&file=)([^\"'\\s<>]+)"
    );
    private static final Pattern FILE_TEXT_PATTERN = Pattern.compile(">([A-Za-z0-9._+-]+\\.(?:gno|go))<");

    private static final Pattern SINGLE_IMPORT_PATTERN = Pattern.compile("(?m)^\\s*import\\s+\"([^\"]+)\"");
    private static final Pattern IMPORT_BLOCK_PATTERN = Pattern.compile("(?s)import\\s*\\((.*?)\\)");
    private static final Pattern QUOTED_IMPORT_PATTERN = Pattern.compile("\"([^\"]+)\"");

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    GnoRemotePackageFetcher(HttpClient httpClient, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.requestTimeout = requestTimeout;
    }

    static boolean isFetchableImportPath(String importPath) {
        if (importPath == null || importPath.isBlank()) {
            return false;
        }
        if (!IMPORT_PATH_PATTERN.matcher(importPath).matches()) {
            return false;
        }
        return importPath.startsWith("gno.land/");
    }

    RemotePackage fetchPackage(String importPath) throws IOException, InterruptedException {
        String packageUrl = toPackageUrl(importPath);
        String packageIndexBody = requestText(packageUrl);
        Set<String> fileNames = extractSourceFileNames(packageIndexBody);

        if (fileNames.isEmpty()) {
            throw new IOException("No source files found in remote package index: " + packageUrl);
        }

        Map<String, String> sourceFiles = new LinkedHashMap<>();
        Set<String> importedRemotePackages = new LinkedHashSet<>();
        for (String fileName : fileNames) {
            String source = downloadSourceFile(importPath, fileName);
            sourceFiles.put(fileName, source);
            importedRemotePackages.addAll(parseImportedRemotePackages(source));
        }

        return new RemotePackage(Map.copyOf(sourceFiles), Set.copyOf(importedRemotePackages));
    }

    private Set<String> extractSourceFileNames(String packageIndexBody) {
        Set<String> files = new LinkedHashSet<>();

        Matcher fileParamMatcher = FILE_PARAM_PATTERN.matcher(packageIndexBody);
        while (fileParamMatcher.find()) {
            String decoded = decodeToken(fileParamMatcher.group(1));
            if (decoded != null) {
                files.add(decoded);
            }
        }

        Matcher fileTextMatcher = FILE_TEXT_PATTERN.matcher(packageIndexBody);
        while (fileTextMatcher.find()) {
            String maybeFile = fileTextMatcher.group(1);
            if (FILE_NAME_PATTERN.matcher(maybeFile).matches()) {
                files.add(maybeFile);
            }
        }

        return files;
    }

    private String downloadSourceFile(String importPath, String fileName) throws IOException, InterruptedException {
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        List<String> candidates = List.of(
            toPackageUrl(importPath) + "%24download%26file%3D" + encodedFileName,
            toPackageUrl(importPath) + "%24source%26file%3D" + encodedFileName
        );

        IOException lastIoException = null;
        for (String candidate : candidates) {
            try {
                return requestText(candidate);
            } catch (IOException ioException) {
                lastIoException = ioException;
                LOG.debug("Failed candidate source URL: " + candidate, ioException);
            }
        }

        if (lastIoException != null) {
            throw lastIoException;
        }
        throw new IOException("Failed to download source file " + fileName + " from " + importPath);
    }

    private Set<String> parseImportedRemotePackages(String sourceCode) {
        Set<String> imports = new LinkedHashSet<>();

        Matcher singleImportMatcher = SINGLE_IMPORT_PATTERN.matcher(sourceCode);
        while (singleImportMatcher.find()) {
            String importPath = singleImportMatcher.group(1);
            if (isFetchableImportPath(importPath)) {
                imports.add(importPath);
            }
        }

        Matcher blockMatcher = IMPORT_BLOCK_PATTERN.matcher(sourceCode);
        while (blockMatcher.find()) {
            String blockBody = blockMatcher.group(1);
            Matcher quotedMatcher = QUOTED_IMPORT_PATTERN.matcher(blockBody);
            while (quotedMatcher.find()) {
                String importPath = quotedMatcher.group(1);
                if (isFetchableImportPath(importPath)) {
                    imports.add(importPath);
                }
            }
        }

        return imports;
    }

    private String requestText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .GET()
            .timeout(requestTimeout)
            .header("Accept", "text/plain, text/html;q=0.9, */*;q=0.8")
            .header("User-Agent", "gno-goland-plugin/0.0.1")
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return response.body();
        }

        throw new IOException("GET " + url + " returned HTTP " + statusCode);
    }

    private static String toPackageUrl(String importPath) {
        return "https://" + importPath;
    }

    private String decodeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        int ampersandIndex = decoded.indexOf('&');
        if (ampersandIndex > 0) {
            decoded = decoded.substring(0, ampersandIndex);
        }

        if (decoded.contains("/") || !FILE_NAME_PATTERN.matcher(decoded).matches()) {
            return null;
        }

        return decoded;
    }
}
