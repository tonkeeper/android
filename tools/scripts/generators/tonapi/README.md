# Tonapi OpenAPI Generator

This directory contains scripts to generate Kotlin client code from OpenAPI specifications for the
Tonapi and Battery APIs.

## Structure

```
tonapi/
├── generate-tonapi.sh      # Core generation script
├── generate-battery.sh     # Battery API generation wrapper
├── update-all.sh           # Regenerate all APIs
├── tonapi.sh               # Shared TonAPI spec generation wrapper
└── templates/              # Custom Mustache templates for code generation
    ├── licenseInfo.mustache           # Custom generated file header
    ├── data_class.mustache            # Data class template
    ├── data_class_req_var.mustache    # Required property template
    ├── data_class_opt_var.mustache    # Optional property template
    └── libraries/jvm-okhttp/
        └── api.mustache               # API client template
```

## Usage

### Regenerate All APIs

The simplest way to regenerate both Tonapi and Battery APIs:

```bash
./tools/scripts/generators/tonapi/update-all.sh
```

### Regenerate Specific API

**Main Tonapi API:**

```bash
./tools/scripts/generators/tonapi/tonapi.sh
```

**Battery API:**

```bash
./tools/scripts/generators/tonapi/generate-battery.sh
```

## Generated Modules

The generation creates code in the following module structure:

- **tonapi:core** - Infrastructure, serializers, and base utilities
    - Location: `tonapi/core/src/main/kotlin/io/`
    - Contains: `JsonAny.kt`, `Serializer.kt`, `infrastructure/`, `serializers/`
    - No generated API code - only shared utilities

- **tonapi:tonkeeper** - Main Tonapi API client
    - Location: `tonapi/main/src/main/kotlin/io/tonapi/`
    - Generated: 19 API classes, 265 model classes
    - Source: `shared/api/tonapi.yaml` (`api/tonapi.yaml` in the public mirror)

- **tonapi:battery** - Battery API client
    - Location: `tonapi/battery/src/main/kotlin/io/batteryapi/`
    - Generated: 6 API classes, 131 model classes
    - Source: `shared/api/battery.yaml` in the monorepo (`api/battery.yaml` in the public mirror)

- **tonapi:legacy** - Backward compatibility layer
    - No source code - just aggregates dependencies to core, main, and battery
    - Provides seamless migration path for existing code

## Requirements

### Tools

- **OpenAPI Generator CLI**: run through `shared/tools/openapi-generator.sh` in the monorepo
  (`tools/openapi-generator.sh` in the public mirror); it pins the version and fetches the jar on first use
- **Java 11+**: Required by OpenAPI Generator
- **Python 3 + PyYAML**: `python3 -m pip install -r tools/scripts/generators/tonapi/requirements.txt`

### Verification

```bash
generator="$(git rev-parse --show-toplevel)/shared/tools/openapi-generator.sh"
[ -f "$generator" ] || generator="$(git rev-parse --show-toplevel)/tools/openapi-generator.sh"
"$generator" version
```

## How It Works

### Generation Process

1. **Download/Read OpenAPI Spec**: Fetches the OpenAPI YAML specification
2. **Adapt a temporary copy**: `prepare-spec.py` rewrites a parsed copy for the templates (see below)
3. **Generate Kotlin Code**: Uses OpenAPI Generator with custom templates
4. **Format**: Shortens `kotlin.` type prefixes, same-package imports and blank lines
5. **Publish**: Replaces the generated package, removing stale models; restores previous sources if publication fails

Each invocation uses a unique temporary directory. Mandatory generation and post-processing errors
stop the script. Formatting does not depend on an optional local detekt installation.

To verify the checked-in client without changing it:

```bash
API_GENERATION_CHECK=1 bash tools/scripts/generators/tonapi/generate-multichain.sh
```

The shared sync stages both platforms and the backend spec, then publishes them together. A failure
on either platform leaves the previous spec and clients intact. An unchanged backend spec still skips
regeneration; older clients migrate when their API is synced after a backend deployment.

### Custom Templates

The generation uses custom Mustache templates to:

- Add custom file headers indicating files are auto-generated
- Support KotlinX Serialization with `@Serializable` and `@SerialName`
- Handle enum unknown default cases with custom serializers
- Generate coroutine-friendly suspend functions for API calls

### Package Structure

Generated packages maintain consistent naming:

- APIs: `io.{tonapi|batteryapi}.apis`
- Models: `io.{tonapi|batteryapi}.models`
- Infrastructure: `io.infrastructure` (only in tonapi:core)

**Important**: Package names and class names remain identical across all modules. Only the module
boundaries change. This ensures zero code changes are needed in dependent modules.

## Customization

### Modifying Templates

Edit the Mustache templates in `templates/` directory:

- `licenseInfo.mustache` - Change the generated file header
- `data_class.mustache` - Modify data class generation
- `libraries/jvm-okhttp/api.mustache` - Customize API client generation

### Post-Processing

Edit the sed commands in `generate-tonapi.sh` to add additional transformations.

### Generator Configuration

`generate-tonapi.sh` takes two optional per-API arguments after the target module, passed by the
`generate-<api>.sh` wrappers:

- `open-enums`: `true` decodes a string enum value the spec does not list as `unknown` instead of
  failing the whole response (trading, perps, battery, multichain)
- `schema-mappings`: `Schema=fully.qualified.Type,...` to use instead of a generated model

Specs shared with iOS live in `shared/api/` in the monorepo (`api/` in the public mirror).
They are updated by the monorepo's `shared/tools/api-sync.sh` (trading, perps, battery, multichain, tonapi); the rest are still under
`openapi/`. Optional per-package settings live in `configs/<package-name>.yaml`; Battery separates
the legacy TRON send body from the multichain body and avoids the enum entry `value` colliding
with its value property. It also keeps ChainKit's signed operation as raw JSON.
`prepare-spec.py` adapts a temporary parsed copy of the spec; the generated Kotlin is not patched:

- the spec is read by YAML 1.2 like on iOS (`on`, `yes`, `0755` stay strings) and handed to the
  generator as JSON, since the generator's own parser follows YAML 1.1;
- wallet authentication and credential defaults become template metadata;
- with open enums, every string enum gains an `unknown` value, the decoding fallback of the
  `openEnums` templates (numeric enums stay strict);
- `additionalProperties: true` maps to `io.JsonAny`;
- a oneOf without a discriminator becomes one model with every variant's properties, all optional;
  a property the variants declare differently is raw `JsonElement` (Perps `QuotePayloadMeta.data`).
Modify the generator command in
`generate-tonapi.sh` to change:

- Type mappings: `--type-mappings "number=kotlin.String"`
- Additional properties: Platform, serialization library, enum handling, etc.

## Troubleshooting

### Generation Fails

Check that:

1. Java is installed (the pinned generator wrapper downloads the jar itself)
2. The OpenAPI spec URL is accessible (for tonapi)
3. The shared `battery.yaml` file exists (for battery)

### Module Build Errors

After generation, run:

```bash
./gradlew :tonapi:core:build
./gradlew :tonapi:battery:build
./gradlew :tonapi:tonkeeper:build
```

If there are errors, check that:

1. All dependencies in `build.gradle.kts` are correct
2. The modules are included in `settings.gradle.kts`
3. Generated code compiles without errors

## Migration Guide

### For New Code

Use specific modules:

```kotlin
dependencies {
    implementation(projects.tonapi.core)      // Infrastructure only
    implementation(projects.tonapi.main)      // Tonapi only
    implementation(projects.tonapi.battery)   // Battery only
}
```

### For Existing Code

No changes needed! The old `tonapi` module now points to `tonapi:legacy`, which provides all APIs:

```kotlin
dependencies {
    implementation(projects.tonapi)  // → redirects to tonapi:legacy → works!
}
```

### Gradual Migration

1. Keep using `implementation(projects.tonapi)` for zero-downtime
2. Gradually update modules to use specific dependencies
3. Once all modules migrated, remove `tonapi:legacy` and `tonapi` modules
