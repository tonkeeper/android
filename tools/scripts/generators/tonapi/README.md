# Tonapi OpenAPI Generator

This directory contains scripts to generate Kotlin client code from OpenAPI specifications for the
Tonapi and Battery APIs.

## Structure

```
tonapi/
├── generate-tonapi.sh      # Core generation script
├── generate-battery.sh     # Battery API generation wrapper
├── update-all.sh           # Regenerate all APIs
├── tonapi.sh               # Legacy tonapi wrapper (deprecated)
├── battery-api.yml         # Battery API OpenAPI specification
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
./tools/scripts/generators/tonapi/generate-tonapi.sh \
    "https://raw.githubusercontent.com/tonkeeper/opentonapi/master/api/openapi.yml" \
    "tonapi" \
    "tonapi:tonkeeper"
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
    - Source: https://raw.githubusercontent.com/tonkeeper/opentonapi/master/api/openapi.yml

- **tonapi:battery** - Battery API client
    - Location: `tonapi/battery/src/main/kotlin/io/batteryapi/`
    - Generated: 4 API classes, 66 model classes
    - Source: `battery-api.yml` (local file)

- **tonapi:legacy** - Backward compatibility layer
    - No source code - just aggregates dependencies to core, main, and battery
    - Provides seamless migration path for existing code

## Requirements

### Tools

- **OpenAPI Generator CLI**: Install with `brew install openapi-generator`
- **Java 11+**: Required by OpenAPI Generator
- **Detekt Formatting** (optional): For code formatting (jar located at
  `tools/scripts/detekt-formatting.jar`)

### Verification

Check if OpenAPI Generator is installed:

```bash
openapi-generator version
```

## How It Works

### Generation Process

1. **Download/Read OpenAPI Spec**: Fetches the OpenAPI YAML specification
2. **Generate Kotlin Code**: Uses OpenAPI Generator with custom templates
3. **Post-Process**: Applies sed transformations:
    - Replace `Map<String, Any>` with `Map<String, io.JsonAny>`
    - Clean up enum unknown default cases
    - Replace `.values()` with `.entries` (Kotlin 1.9+ compatibility)
4. **Copy to Module**: Moves generated code to target module directory
5. **Remove Infrastructure**: Deletes generated infrastructure (kept only in core module)
6. **Format Code**: Runs detekt formatting for consistent style

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

Modify the `openapi-generator generate` command in `generate-tonapi.sh` to change:

- Type mappings: `--type-mappings "number=kotlin.String"`
- Additional properties: Platform, serialization library, enum handling, etc.

## Troubleshooting

### Generation Fails

Check that:

1. OpenAPI Generator is installed and in PATH
2. The OpenAPI spec URL is accessible (for tonapi)
3. The `battery-api.yml` file exists (for battery)

### Formatting Errors

The detekt formatting step is optional and will be skipped if the jar is not found. If you see
formatting errors, ensure `tools/scripts/detekt-formatting.jar` exists.

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
