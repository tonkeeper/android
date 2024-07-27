// this script is intended to be runed with bun.sh
// it will generate a json file with all the translations using chatgpt
//
// Usage:
//  bun translate.js spanish
//
//  third param is an language you are translating to
//
// As a result new file "hy.xml" will be created with all the translations
// file will be updates as script translating keys one by one

import { readdir } from "node:fs/promises";

const translateTo = Bun.argv[2]
let translated = 0
let total = 0

// You need to put your OpenAI API key here
const OPENAI_API_KEY = "";

async function askChatGPT(messages, model = "gpt-4o") {
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${OPENAI_API_KEY}`,
  };

  const data = {
    model: model,
    messages: messages.map((msg) => ({
      role: msg.role,
      content: msg.content
    })),
    max_tokens: 150,
    temperature: 0.5,
  };

  try {
    const response = await Bun.fetch(`https://api.openai.com/v1/chat/completions`, {
      method: "POST",
      headers: headers,
      body: JSON.stringify(data),
    });

    if (!response.ok) {
      throw new Error(`Error ${response.status}: ${response.statusText}`);
    }

    const result = await response.json();
    return result.choices[0].message.content;
  } catch (error) {
    console.error('Error asking ChatGPT:', error);
    throw error;
  }
}

async function query(prompt, message) {
  const messages = [
    { role: "system", content: prompt },
    { role: "user", content: message }
  ];

  let resp = await askChatGPT(messages)
  resp = resp.replace('```json', '').replace('```', '')
  return resp
}

function appendResult(result, value, lang) {
  if (!result) result = {};
  if (typeof value === 'string') {
    result[lang] = value;
  }
  if (typeof value === 'object') {
    for (let k in value) {
      result[k] = appendResult(result[k], value[k], lang);
    }
  }
  return result
}

function xmlToObject(xml) {
  const result = {};
  const regex = /<string name="([^"]+)">([^<]+)<\/string>/g;
  let match;
  while (match = regex.exec(xml)) {
    const [, key, value] = match;
    result[key] = value;
  }
  return result;
}

function wrapToXML(obj) {
  let lines = []
  for (let k in obj) {
    lines.push(`    <string name="${k}">${obj[k]}</string>`)
  }
  return `<resources>
${lines.join('\n')}
</resources>`
}

async function scanOriginalFile() {
  let result = {}
  let langFile = Bun.file('src/main/res/values/strings.xml')
  let xml = await langFile.text()
  let json = xmlToObject(xml)
  for (let k in json) {
    result[k] = appendResult(result[k], json[k], 'en');
  }
  // console.log('result', result)
  return result
}

async function findOutSetup(langIdentifyer) {
  let prompt = `You will get an message with an language identifyer, eather in form of language name, or short code of it,
I will ask you to response with an valid JSON of following structure:
just json object with fields: code - short code of language, name - name of language, nativeName - native name of language, rtl - boolean if language is right to left.
example:
{"code": "en", "name": "English", "nativeName": "English", "rtl": false}`
  let response = JSON.parse(await query(prompt, langIdentifyer))
  return response
}

function countKeysRecursive(obj, langCode) {
  for (let k in obj) {
    if (typeof obj[k] === 'string') { // we found a lang key
      if (obj[langCode]) {
        translated++
      }
      total++
      return
    }
    if (typeof obj[k] === 'object') {
      countKeysRecursive(obj[k], langCode);
    }
  }
}

function clearTranslation(obj, langCode) {
  let cleared = {}
  for (let k in obj) {
    if (typeof obj[k] === 'string') { // we found a lang key
      return obj[langCode] // replace it with final lang code
    }
    if (typeof obj[k] === 'object') {
      let value = clearTranslation(obj[k], langCode);
      if (value && JSON.stringify(value) !== '{}') {
        cleared[k] = value
      }
    }
  }
  return cleared
}


async function doTranslate(obj, setup, keyChain) {
  let key = keyChain.join('.')
  let prompt = `You are an translator bot. You are helping to translate JSON lang file for an javascript application to a ${setup.name} lanuage. You will get an message with an json object of following structure – each key is a lang identifyer (example: "en"), and value is the translation in that language. Using this info please translate same phrase to ${setup.name} language and return only translation string without quotes.
Never reply with anything except translation. Never ask for help or anything else, make sure to return only translation.
If you see any unordinary symbols like quotes or anything else – try to preserve the same symbols in the translation.
You all languages from json you get for translation to generate most sutable language key in return. Do not add any new symbols like "\\n", wich wasnt presented in original string.
Try to generate translation not much longer or much shorter than it is in other languages.
Some hint for the translation – you are translating an language key stored in ${key} field.`

  let description = obj['description']
  let langs = Object.assign({}, obj)
  delete langs['description']

  if (description) {
    prompt += `\nIts also an additional description provided for this field, wich may help you with translation: ${description}`
    console.log("DESC:", description)
  }

  let response = await query(prompt, JSON.stringify(langs))
  response = response.replace(/^"|"$/gm, '');
  console.log(`\nTanslating \x1B[31m${translated} \x1b[37mfrom \x1B[31m${total}\x1b[37m:`)
  let p = `\x1B[34m${key}\x1b[37m\n`
  if (obj["en"]) {
    p += `en: ` + obj["en"]
  } else if (obj["ru-RU"]) {
    p += `ru: ` + obj["ru-RU"]
  }
  p += "\n\x1B[32m" + setup.code + ': ' + response + '\x1b[37m'
  console.log(p)
  translated++
  return response
}

async function translate(obj, setup, cb, keyChain) {
  for (let k in obj) {
    if (typeof obj[k] === 'string') { // we found a lang key
      if (obj[setup.code]) {
        return obj // skip translation for that one
      }
      obj[setup.code] = await doTranslate(obj, setup, keyChain)
      await cb() // write file here
      return obj
    }
    if (typeof obj[k] === 'object') {
      obj[k] = await translate(obj[k], setup, cb, [...keyChain, k]);
    }
  }
  return obj
}

async function main() {
  console.log('Hello, we are about to generate lang files, but first we going to read all the translation files and build an translations map.')

  let langs = await scanOriginalFile();

  let setup = await findOutSetup(translateTo)

  let fileName = './src/main/res/values-' + setup.code + '/strings.xml'
  console.log('We are going to create new tranlslation for', translateTo, 'language and write it to', fileName)

  const output = Bun.file(fileName);
  if (output.size !== 0) {
    console.log('File already exists, so we going to update it.')
  }

  console.log('setup', setup)
  countKeysRecursive(langs, setup.code)
  await translate(langs, setup, async () => {
    let translatedLang = clearTranslation(langs, setup.code)
    await Bun.write(fileName, wrapToXML(translatedLang));
  }, [])
}
main()