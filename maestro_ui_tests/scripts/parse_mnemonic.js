const words = phrase.trim().split(/\s+/);

if (words.length !== 24) {
  throw new Error(`Expected 24 words, but got ${words.length}. Check your phrase.`);
}

var word = [];

for (let i = 0; i < words.length; i++) {
  const index = i + 1;
  word[index] = words[i]; 
}

output.word = word;