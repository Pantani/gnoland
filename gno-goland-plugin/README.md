# Gno GoLand Plugin

This plugin adds baseline Gno support to GoLand by making `*.gno` files behave
like Go files inside the IDE.

## What it does

- Maps `*.gno` files to Go's native file type and editor pipeline.
- Maps `gno.mod` to GoLand's native `go.mod` editor support.
- Suppresses common false-positive highlights for unresolved references/imports
  in `*.gno` files.
- Suppresses warning-level highlights in `*.gno` files to reduce noise while
  writing Gno code.
- Suppresses Go inspections on `*.gno` files to avoid invalid IDE diagnostics.
- Adds import support for:
  - `std`
  - `gno.land/p/nt/ufmt`
  - `gno.land/p/nt/avl`
  - `gno.land/p/nt/testutils`
- Resolves Gno stdlib imports (from `gnovm/stdlibs`, e.g. `chain`, `crypto/sha256`, `encoding/json`, etc.) when available.
- Also accepts `gno.land/p/demo/{ufmt,avl,testutils}` import paths.
- Accepts short aliases (`ufmt`, `avl`, `testutils`) as IDE-only compatibility.
- Treats `realm`, `address`, and `cross` as built-in language symbols (no import).
- Adds a `Gno` Run Configuration to run `gno` commands (and a context action for `*.gno` files).

## Stdlib Resolution

For full stdlib navigation and completion, the plugin needs access to the Gno stdlibs folder (`gnovm/stdlibs`).

Supported locations:

- Open a checkout of `github.com/gnolang/gno` (or any nested folder within it) as the GoLand project.
- Or set `GNOROOT` in the GoLand process environment to a checkout of `github.com/gnolang/gno`.

## Build

Requirements:

- JDK 17+
- Gradle 8+

Build the plugin zip:

```bash
cd misc/ide/gno-goland-plugin
gradle buildPlugin
```

The output zip is written to:

```text
misc/ide/gno-goland-plugin/build/distributions/
```

## Install in GoLand

1. Open `Settings` -> `Plugins`.
2. Click the gear icon.
3. Choose `Install Plugin from Disk...`.
4. Select the built plugin zip from `build/distributions`.
