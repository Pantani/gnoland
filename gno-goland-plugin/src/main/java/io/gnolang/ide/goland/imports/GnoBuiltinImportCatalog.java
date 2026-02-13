package io.gnolang.ide.goland.imports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class GnoBuiltinImportCatalog {
    static final class BuiltinImport {
        private final String displayImportPath;
        private final String canonicalImportPath;
        private final List<String> acceptedImportPaths;
        private final List<String> projectRelativeDirs;

        BuiltinImport(
            String displayImportPath,
            String canonicalImportPath,
            List<String> acceptedImportPaths,
            List<String> projectRelativeDirs
        ) {
            this.displayImportPath = displayImportPath;
            this.canonicalImportPath = canonicalImportPath;
            this.acceptedImportPaths = acceptedImportPaths;
            this.projectRelativeDirs = projectRelativeDirs;
        }

        String displayImportPath() {
            return displayImportPath;
        }

        String canonicalImportPath() {
            return canonicalImportPath;
        }

        List<String> acceptedImportPaths() {
            return acceptedImportPaths;
        }

        List<String> projectRelativeDirs() {
            return projectRelativeDirs;
        }
    }

    private static final List<BuiltinImport> BUILTIN_IMPORTS = List.of(
        new BuiltinImport(
            "std",
            "std",
            List.of("std"),
            List.of(
                "gnovm/stdlibs/builtin",
                "tm2/pkg/std"
            )
        ),
        new BuiltinImport(
            "gno.land/p/nt/ufmt",
            "gno.land/p/nt/ufmt",
            List.of("gno.land/p/nt/ufmt", "gno.land/p/demo/ufmt", "ufmt"),
            List.of("examples/gno.land/p/nt/ufmt")
        ),
        new BuiltinImport(
            "gno.land/p/nt/avl",
            "gno.land/p/nt/avl",
            List.of("gno.land/p/nt/avl", "gno.land/p/demo/avl", "avl"),
            List.of("examples/gno.land/p/nt/avl")
        ),
        new BuiltinImport(
            "gno.land/p/nt/testutils",
            "gno.land/p/nt/testutils",
            List.of("gno.land/p/nt/testutils", "gno.land/p/demo/testutils", "testutils"),
            List.of("examples/gno.land/p/nt/testutils")
        )
    );

    private GnoBuiltinImportCatalog() {
    }

    static BuiltinImport findByImportPath(String importPath) {
        if (importPath == null || importPath.isEmpty()) {
            return null;
        }

        for (BuiltinImport builtinImport : BUILTIN_IMPORTS) {
            if (builtinImport.acceptedImportPaths().stream().filter(Objects::nonNull).anyMatch(importPath::equals)) {
                return builtinImport;
            }
        }

        return null;
    }

    static List<BuiltinImport> all() {
        return Collections.unmodifiableList(new ArrayList<>(BUILTIN_IMPORTS));
    }
}
