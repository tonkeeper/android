"""Publish staged API sources together, restoring the previous sources on failure."""

from pathlib import Path
import argparse
import difflib
import filecmp
import os
import shutil
import signal
import sys
import tempfile


def targets_of(stage):
    targets = (stage / ".api-generation-targets").read_text().splitlines()
    if not targets or len(targets) != len(set(targets)):
        raise ValueError("Missing or duplicate API generation targets")
    for relative in targets:
        path = Path(relative)
        if path.is_absolute() or ".." in path.parts or not path.parts:
            raise ValueError(f"Invalid API generation target: {relative}")
    return targets


def check(stage, root):
    stage, root = Path(stage), Path(root)
    matched = True
    for relative in targets_of(stage):
        source, target = stage / relative, root / relative
        if not source.exists():
            raise ValueError(f"Missing staged API generation target: {relative}")
        if source.is_file():
            if not target.is_file() or not filecmp.cmp(source, target, shallow=False):
                print(f"API generation file mismatch: {relative}")
                matched = False
            continue
        files = {p.relative_to(source) for p in source.rglob("*") if p.is_file()}
        files |= {p.relative_to(target) for p in target.rglob("*") if p.is_file()}
        for path in sorted(files):
            generated, committed = source / path, target / path
            if not committed.is_file() or not generated.is_file():
                print(f"API generation file mismatch: {relative}/{path}")
                matched = False
            elif not filecmp.cmp(generated, committed, shallow=False):
                matched = False
                print("".join(list(difflib.unified_diff(
                    committed.read_text().splitlines(True), generated.read_text().splitlines(True),
                    fromfile=str(committed), tofile=str(generated),
                ))[:50]))
    return matched


def publish(stage, root):
    stage, root = Path(stage).resolve(), Path(root).resolve()
    targets = targets_of(stage)
    prepared = []
    publishing = []
    committed = False
    try:
        for relative in targets:
            source, target = stage / relative, root / relative
            if not source.exists():
                raise ValueError(f"Missing staged API generation target: {relative}")
            target.parent.mkdir(parents=True, exist_ok=True)
            # Outside every source root: a copy left by a killed run must not compile as duplicate types.
            temporary = Path(tempfile.mkdtemp(prefix=".api-generation-", dir=root))
            prepared.append((target, temporary))
            if source.is_dir():
                shutil.copytree(source, temporary / "new")
            else:
                shutil.copy2(source, temporary / "new")

        for target, temporary in prepared:
            publishing.append((target, temporary))
            if target.exists():
                os.replace(target, temporary / "previous")
            os.replace(temporary / "new", target)
        committed = True
    except BaseException:
        for target, temporary in reversed(publishing):
            if (temporary / "previous").exists():
                if target.is_dir():
                    shutil.rmtree(target)
                elif target.exists():
                    target.unlink()
                os.replace(temporary / "previous", target)
            elif not (temporary / "new").exists() and target.exists():
                if target.is_dir():
                    shutil.rmtree(target)
                else:
                    target.unlink()
        raise
    finally:
        for _, temporary in prepared:
            if committed or not (temporary / "previous").exists():
                shutil.rmtree(temporary)
            else:
                print(f"API rollback backup retained at {temporary / 'previous'}", file=sys.stderr)


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(143))
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("stage")
    parser.add_argument("root")
    args = parser.parse_args()
    if args.check:
        sys.exit(0 if check(args.stage, args.root) else 1)
    publish(args.stage, args.root)
