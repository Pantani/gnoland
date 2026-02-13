# IDE Tooling

This directory is a Gradle aggregator for IDE integrations.

## Build the GoLand plugin

From this directory:

```bash
gradle buildPlugin
```

Or explicitly:

```bash
gradle :gno-goland-plugin:buildPlugin
```

The plugin zip is generated under:

```text
gno-goland-plugin/build/distributions/
```

## Notes

- GoLand `2024.3` requires Java 21 for plugin builds.
- This build is configured to auto-detect or auto-download a matching JDK via Gradle toolchains.
