#!/usr/bin/env python3
"""
Find a seed phrase that is valid BOTH as a standard TON mnemonic and as a BIP39
mnemonic -- the ambiguous "Both" case (~1/256) handled by the in-app
"Choose wallet" screen.

This mirrors com.tonapps.blockchain.MnemonicHelper.mnemonicType:

  isTon   = org.ton.kotlin.crypto.mnemonic.Mnemonic(words).isValid()  (empty password
            => isBasicSeed): HMAC-SHA512 entropy, then PBKDF2-SHA512 with salt
            "TON seed version" / 390 iterations, first byte == 0.
  isBip39 = words in the BIP39 English wordlist, count % 3 == 0, and the trailing
            checksum bits match SHA-256(entropy).
  Both    = isTon && isBip39.

Strategy: build a valid BIP39 mnemonic from random entropy (so the BIP39 side is
always satisfied), then keep going until the TON basic-seed check also passes.
On average ~256 attempts for 24 words.

Usage:
    python3 find_ton_bip39_mnemonic.py                 # one 24-word phrase
    python3 find_ton_bip39_mnemonic.py --words 12      # 12-word phrase
    python3 find_ton_bip39_mnemonic.py --count 3       # find three
    python3 find_ton_bip39_mnemonic.py --seed 42       # reproducible
"""

import argparse
import hashlib
import hmac
import os
import secrets

# TON validity constants, copied from org.ton.kotlin.crypto.mnemonic.Mnemonic.
TON_BASIC_SALT = b"TON seed version"
TON_BASIC_ITERATIONS = 390  # max(1, floor(100000 / 256))

WORDLIST_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bip39_english.txt")


def load_wordlist():
    with open(WORDLIST_PATH, "r", encoding="utf-8") as f:
        words = [w.strip() for w in f if w.strip()]
    if len(words) != 2048:
        raise SystemExit(f"Expected 2048 BIP39 words, got {len(words)} from {WORDLIST_PATH}")
    return words


def entropy_bits_for(word_count):
    # BIP39: total bits = word_count * 11, of which 1/33 is checksum.
    if word_count % 3 != 0 or not (12 <= word_count <= 24):
        raise SystemExit("word count must be one of 12, 15, 18, 21, 24")
    total_bits = word_count * 11
    return total_bits * 32 // 33  # ENT


def make_bip39_mnemonic(wordlist, word_count, rng):
    """Build a checksum-correct BIP39 mnemonic from fresh random entropy."""
    ent = entropy_bits_for(word_count)
    entropy = rng(ent // 8)
    checksum_len = ent // 32
    checksum = hashlib.sha256(entropy).digest()

    bits = "".join(f"{b:08b}" for b in entropy)
    bits += "".join(f"{b:08b}" for b in checksum)[:checksum_len]

    indices = [int(bits[i:i + 11], 2) for i in range(0, len(bits), 11)]
    return [wordlist[i] for i in indices]


def is_valid_bip39(words, wordlist):
    """Independent BIP39 re-check (matches MnemonicHelper.isValidBip39Mnemonic)."""
    if len(words) % 3 != 0 or not words:
        return False
    if any(w not in wordlist for w in words):
        return False
    index = {w: i for i, w in enumerate(wordlist)}
    bits = "".join(f"{index[w]:011b}" for w in words)
    divider = (len(bits) // 33) * 32
    entropy_bits, checksum_bits = bits[:divider], bits[divider:]
    entropy = int(entropy_bits, 2).to_bytes(len(entropy_bits) // 8, "big")
    expected = "".join(f"{b:08b}" for b in hashlib.sha256(entropy).digest())[:len(checksum_bits)]
    return expected == checksum_bits


def is_valid_ton(words):
    """Standard TON mnemonic check with empty password (isBasicSeed)."""
    entropy = hmac.new(" ".join(words).encode("utf-8"), b"", hashlib.sha512).digest()
    seed = hashlib.pbkdf2_hmac("sha512", entropy, TON_BASIC_SALT, TON_BASIC_ITERATIONS, dklen=64)
    return seed[0] == 0


def main():
    parser = argparse.ArgumentParser(description="Find a mnemonic valid as both TON and BIP39.")
    parser.add_argument("--words", type=int, default=24, help="word count (12/15/18/21/24, default 24)")
    parser.add_argument("--count", type=int, default=1, help="how many phrases to find (default 1)")
    parser.add_argument("--seed", type=int, default=None, help="seed the RNG for reproducible output")
    args = parser.parse_args()

    wordlist = load_wordlist()

    if args.seed is not None:
        import random
        r = random.Random(args.seed)
        rng = lambda n: bytes(r.getrandbits(8) for _ in range(n))
    else:
        rng = secrets.token_bytes

    found = 0
    attempts = 0
    while found < args.count:
        attempts += 1
        words = make_bip39_mnemonic(wordlist, args.words, rng)
        if not is_valid_ton(words):
            continue

        # Sanity: both validators must agree before we print it.
        assert is_valid_bip39(words, wordlist), "constructed phrase failed BIP39 re-check"
        assert is_valid_ton(words), "phrase failed TON re-check"

        found += 1
        phrase = " ".join(words)
        print(f"# match {found}/{args.count}  (after {attempts} attempts)")
        print(phrase)
        print(f"  TON valid:   {is_valid_ton(words)}")
        print(f"  BIP39 valid: {is_valid_bip39(words, wordlist)}")
        print()

    print(f"Done. {found} phrase(s), {attempts} total attempts "
          f"(~1/256 expected hit rate per phrase for 24 words).")


if __name__ == "__main__":
    main()
