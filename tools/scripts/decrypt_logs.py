#!/usr/bin/env python3
"""
Decrypt logs encrypted by LogArchiver (RSA/ECB/PKCS1Padding, per-block doFinal).

Usage:
    python3 decrypt_logs.py <private_key.pem> <encrypted.zip>

Output:
    <encrypted>-decoded.zip
"""

import os
import re
import subprocess
import sys


def get_key_size_bytes(pk_path):
    r = subprocess.run(
        ["openssl", "rsa", "-in", pk_path, "-text", "-noout"],
        capture_output=True, text=True,
    )
    if r.returncode != 0:
        # Try DER format
        r = subprocess.run(
            ["openssl", "rsa", "-in", pk_path, "-inform", "DER", "-text", "-noout"],
            capture_output=True, text=True,
        )
    if r.returncode != 0:
        sys.exit(f"Cannot read private key: {r.stderr.strip()}")

    m = re.search(r"(\d+)\s*bit", r.stdout)
    if not m:
        sys.exit("Cannot determine RSA key size from private key")
    return int(m.group(1)) // 8


def try_decrypt(block, pk_path, inform):
    """Try decrypting a block with rsautl, then pkeyutl."""
    base = ["openssl", "rsautl", "-decrypt", "-inkey", pk_path, "-pkcs"]
    if inform == "DER":
        base += ["-keyform", "DER"]

    r = subprocess.run(base, input=block, capture_output=True)
    if r.returncode == 0:
        return r.stdout

    # Fallback: pkeyutl (newer openssl)
    base2 = [
        "openssl", "pkeyutl", "-decrypt",
        "-inkey", pk_path,
        "-pkopt", "rsa_padding_mode:pkcs1",
    ]
    if inform == "DER":
        base2 += ["-keyform", "DER"]

    r2 = subprocess.run(base2, input=block, capture_output=True)
    if r2.returncode == 0:
        return r2.stdout

    sys.exit(f"Decryption failed:\n  rsautl: {r.stderr.decode().strip()}\n  pkeyutl: {r2.stderr.decode().strip()}")


def detect_key_format(pk_path):
    with open(pk_path, "rb") as f:
        head = f.read(32)
    return "PEM" if b"-----BEGIN" in head else "DER"


def main():
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} <private_key.pem> <encrypted.zip>")
        sys.exit(1)

    pk_path = sys.argv[1]
    enc_path = sys.argv[2]

    for p, label in [(pk_path, "Private key"), (enc_path, "Encrypted file")]:
        if not os.path.isfile(p):
            sys.exit(f"{label} not found: {p}")

    inform = detect_key_format(pk_path)
    block_size = get_key_size_bytes(pk_path)

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
    print(f"Key: {block_size * 8}-bit RSA, block: {block_size} bytes, blocks: {n_blocks}")

    with open(out_path, "wb") as out:
        for i in range(n_blocks):
            offset = i * block_size
            block = data[offset : offset + block_size]
            print(f"\r  Decrypting {i + 1}/{n_blocks} ...", end="", flush=True)
            dec = try_decrypt(block, pk_path, inform)
            out.write(dec)

    print(f"\nDone: {out_path}")


if __name__ == "__main__":
    main()
