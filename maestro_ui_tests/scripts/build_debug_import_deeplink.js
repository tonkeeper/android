const p = phrase.trim();
if (!p) {
  throw new Error("phrase is empty (set MNEMONIC env when running this flow)");
}

const words = p.split(/\s+/);
if (words.length !== 12 && words.length !== 24) {
  throw new Error(`Expected 12 or 24 words, got ${words.length}`);
}

output.url = `tonkeeper://debug-import?phrase=${encodeURIComponent(p)}`;
