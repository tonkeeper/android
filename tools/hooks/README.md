# Detekt Pre-Commit Hook Setup

This directory contains a pre-commit hook that automatically runs detekt on staged Kotlin files
before commits.

## Installation

The hook is **automatically installed** every time you run any Gradle command (build, sync, clean,
etc.).

The pre-commit hook is copied from `tools/hooks/pre-commit` to `.git/hooks/pre-commit`
automatically.

**Note for git worktrees:** The automatic installation only works for the main repository's
`.git/hooks`. For worktrees, you'll need to manually copy the hook once:

```bash
cp tools/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

## How It Works

1. **Pre-commit hook** ([tools/hooks/pre-commit](pre-commit)) - Lightweight wrapper that finds the
   project root and delegates to the executor
2. **Executor script** ([tools/scripts/pre-commit-exec](../scripts/pre-commit-exec)) - Main logic
   that:
    - Downloads detekt JARs if not present (v1.23.8)
    - Checks only staged Kotlin files (`.kt` and `.kts`)
    - Runs detekt analysis with formatting rules
    - Fails the commit if issues are found

## First Run

On your first commit after installation:

- Detekt JARs will be automatically downloaded to `tools/build/`
- This takes ~5-10 seconds (only happens once)
- The JARs are cached and reused for subsequent commits

## Usage

### Normal Commit Flow

```bash
# Stage your changes
git add MyFile.kt

# Commit (hook runs automatically)
git commit -m "Your commit message"
```

If detekt finds issues:

- The commit will be blocked
- Issues will be displayed in the terminal
- Fix the issues and commit again

### Bypassing the Hook

If you need to commit without running detekt:

```bash
git commit --no-verify -m "Your commit message"
```

**Note:** Use sparingly - it's better to fix the issues!

### No Kotlin Files

If you're only committing non-Kotlin files:

- The hook will detect this automatically
- Skip detekt analysis
- Commit proceeds immediately

## Performance

Expected execution times:

- **No Kotlin files:** <1 second (skipped)
- **1-5 files:** ~2-5 seconds
- **6-20 files:** ~5-15 seconds
- **20+ files:** ~15-30 seconds

## Configuration

### Detekt Version

To upgrade detekt, edit [tools/scripts/pre-commit-exec](../scripts/pre-commit-exec):

```bash
DETEKT_VERSION="1.23.8"  # Change this version
```

Then delete the old JARs:

```bash
rm -rf tools/build/
```

Next commit will download the new version.

### Custom Detekt Config

Currently using detekt's default configuration. To add custom rules:

1. Create a config file:
   ```bash
   mkdir -p config/detekt
   touch config/detekt/detekt.yml
   ```

2. Edit [tools/scripts/pre-commit-exec](../scripts/pre-commit-exec) and add the `--config` flag:
   ```bash
   java -jar "$DETEKT_CLI_JAR" \
       --input "$DETEKT_INPUT_ABSOLUTE" \
       --plugins "$DETEKT_FORMATTING_JAR" \
       --config "$PROJECT_ROOT/config/detekt/detekt.yml"
   ```

### Baseline File (Ignore Existing Issues)

To create a baseline that ignores existing issues:

1. Generate baseline:
   ```bash
   java -jar tools/build/detekt-cli-1.23.8-all.jar \
       --input . \
       --create-baseline \
       --baseline config/detekt/baseline.xml
   ```

2. Edit [tools/scripts/pre-commit-exec](../scripts/pre-commit-exec) and add:
   ```bash
   --baseline "$PROJECT_ROOT/config/detekt/baseline.xml"
   ```

## Troubleshooting

### Hook not running

Check if the hook is installed:

```bash
ls -la .git/hooks/pre-commit
```

If missing, run any Gradle command to trigger automatic installation:

```bash
./gradlew tasks
```

### Java not found

Ensure Java is installed:

```bash
java -version
```

The hook requires Java to run detekt.

### Download failures

If detekt download fails:

1. Check your internet connection
2. Manually download JARs from:
    - [detekt-cli-1.23.8-all.jar](https://github.com/detekt/detekt/releases/download/v1.23.8/detekt-cli-1.23.8-all.jar)
    - [detekt-formatting-1.23.8.jar](https://repo1.maven.org/maven2/io/gitlab/arturbosch/detekt/detekt-formatting/1.23.8/detekt-formatting-1.23.8.jar)
3. Place them in `tools/build/`

### Hook fails in worktree

If you're using git worktrees, you need to manually copy the hook in each worktree:

```bash
cd /path/to/your/worktree
cp tools/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

The hook will automatically find the project root regardless of which worktree you're in.

## Files Modified

This setup adds/modifies these files:

- `tools/hooks/pre-commit` - Hook wrapper script
- `tools/scripts/pre-commit-exec` - Main executor script
- `build.gradle.kts` - Added automatic hook installation on Gradle runs
- `.gitignore` - Added `tools/build/` to ignore downloaded JARs
- `.git/hooks/pre-commit` - Installed hook (not in version control)

## Uninstalling

To remove the hook:

```bash
rm .git/hooks/pre-commit
```

The source files in `tools/` will remain for future use.
