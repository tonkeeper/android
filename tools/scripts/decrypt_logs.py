#!/usr/bin/env python3
"""
Decrypt logs encrypted by LogArchiver (RSA/ECB/PKCS1Padding, per-block doFinal).

Usage:
    python3 decrypt_logs.py <private_key.pem> <encrypted.zip>

Output:
    <encrypted>-decoded.zip
"""

import os
import subprocess
import sys

_VENV_DIR = os.path.join(
    os.environ.get("XDG_CACHE_HOME", os.path.expanduser("~/.cache")),
    "tonkeeper",
    "decrypt-logs-venv",
)


def _can_import(python):
    try:
        return subprocess.run(
            [python, "-c", "import cryptography"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        ).returncode == 0
    except OSError:
        return False


def _create_venv():
    venv_python = os.path.join(_VENV_DIR, "bin", "python3")
    if not os.path.isfile(venv_python):
        print("Installing 'cryptography' into a local venv (first run only) ...")
        subprocess.run([sys.executable, "-m", "venv", _VENV_DIR], check=True)
    if not _can_import(venv_python):
        subprocess.run(
            [venv_python, "-m", "pip", "install", "--quiet", "--upgrade", "pip", "cryptography"],
            check=True,
        )
    return venv_python


def _reexec_with_cryptography():
    """Re-run this script under an interpreter that has 'cryptography'."""
    if os.environ.get("DECRYPT_LOGS_BOOTSTRAPPED"):
        sys.exit("Requires the 'cryptography' package: pip3 install cryptography")

    candidates = [
        os.path.join(_VENV_DIR, "bin", "python3"),
        "/usr/bin/python3",
        "/opt/homebrew/bin/python3",
    ]
    python = next((c for c in candidates if c != sys.executable and _can_import(c)), None)

    if python is None:
        try:
            python = _create_venv()
        except (subprocess.CalledProcessError, OSError) as e:
            sys.exit(f"Requires the 'cryptography' package and auto-install failed: {e}")

    env = dict(os.environ, DECRYPT_LOGS_BOOTSTRAPPED="1")
    os.execve(python, [python, os.path.abspath(__file__)] + sys.argv[1:], env)


try:
    from cryptography.hazmat.primitives import serialization
    from cryptography.hazmat.primitives.asymmetric import padding
except ImportError:
    _reexec_with_cryptography()


def load_private_key(pk_path):
    with open(pk_path, "rb") as f:
        raw = f.read()

    if b"-----BEGIN" in raw[:64]:
        loader = serialization.load_pem_private_key
    else:
        loader = serialization.load_der_private_key

    try:
        return loader(raw, password=None)
    except Exception as e:
        sys.exit(f"Cannot read private key: {e}")


def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <private_key.pem> <encrypted.zip>")
        sys.exit(1)

    pk_path = sys.argv[1]
    enc_path = sys.argv[2]

    for p, label in [(pk_path, "Private key"), (enc_path, "Encrypted file")]:
        if not os.path.isfile(p):
            sys.exit(f"{label} not found: {p}")

    key = load_private_key(pk_path)
    block_size = key.key_size // 8

    base, ext = os.path.splitext(enc_path)
    out_path = f"{base}-decoded{ext}"

    with open(enc_path, "rb") as f:
        data = f.read()

    total = len(data)
    if total == 0:
        sys.exit("Encrypted file is empty")
    if total % block_size != 0:
        print(f"Warning: file size ({total}) is not a multiple of block size ({block_size})")

    n_blocks = total // block_size
    print(f"Key: {key.key_size}-bit RSA, block: {block_size} bytes, blocks: {n_blocks}")

    pkcs1 = padding.PKCS1v15()
    with open(out_path, "wb") as out:
        for i in range(n_blocks):
            offset = i * block_size
            block = data[offset : offset + block_size]
            try:
                dec = key.decrypt(block, pkcs1)
            except Exception as e:
                sys.exit(f"\nDecryption failed at block {i + 1}/{n_blocks}: {e}")
            out.write(dec)
            if (i + 1) % 100 == 0 or i + 1 == n_blocks:
                print(f"\r  Decrypting {i + 1}/{n_blocks} ...", end="", flush=True)

    print(f"\nDone: {out_path}")


if __name__ == "__main__":
    main()
