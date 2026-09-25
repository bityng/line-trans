#!/usr/bin/env node
/*
 * 生成内置离线词库（assets/dict/core.tsv）
 *
 * 数据来源：ECDICT（https://github.com/skywind3000/ECDICT，MIT）
 *   - ecdict.csv     词条、音标、英文释义、中文释义、词频等
 *   - lemma.en.txt   词形还原表（ran → run），用于查不到原形时回退
 *
 * 用法：
 *   node tools/build-local-dict.mjs <ecdict.csv> <lemma.en.txt> <输出目录>
 * 例：
 *   node tools/build-local-dict.mjs ecdict.csv lemma.en.txt app/src/main/assets/dict
 *
 * 产物：
 *   core.tsv   常用词条：`单词\t音标\t中文释义`（释义里多条用「；」分隔）
 *   lemma.tsv  词形还原：`变形\t原形`
 */

import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';

const [, , csvPath, lemmaPath, outDir] = process.argv;
if (!csvPath || !lemmaPath || !outDir) {
  console.error('用法: node tools/build-local-dict.mjs <ecdict.csv> <lemma.en.txt> <输出目录>');
  process.exit(1);
}

const LIMIT = Number(process.env.DICT_LIMIT || 40000);
const CJK = /[\u4e00-\u9fff]/;

/** 简单 CSV 行解析（支持双引号包裹与转义） */
function splitCsv(line) {
  const out = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '"') {
        if (line[i + 1] === '"') { field += '"'; i++; } else quoted = false;
      } else field += c;
    } else if (c === '"') {
      quoted = true;
    } else if (c === ',') {
      out.push(field); field = '';
    } else field += c;
  }
  out.push(field);
  return out;
}

function cleanMeaning(text) {
  return text
    .replace(/\\n/g, '；')
    .replace(/\\r/g, ' ')
    .replace(/\\t/g, ' ')
    .replace(/\[网络\][^；]*；?/g, '')
    .split(/[；\n]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 6)
    .join('；');
}

function score(row) {
  let s = 0;
  if (row.oxford === '1') s += 8;
  s += Number(row.collins || 0) * 2;
  if (Number(row.bnc) > 0) s += 2;
  if (Number(row.frq) > 0) s += 2;
  if (row.tag) s += 1;
  if (/^[a-z-]+$/.test(row.word)) s += 1;   // 纯单词优于短语/专名
  if (row.word.length <= 12) s += 1;
  return s;
}

function rank(row) {
  const frq = Number(row.frq) || 999999;
  const bnc = Number(row.bnc) || 999999;
  return frq * 2 + bnc;
}

async function readEntries() {
  const rows = [];
  const rl = readline.createInterface({ input: fs.createReadStream(csvPath), crlfDelay: Infinity });
  let header = true;
  for await (const line of rl) {
    if (header) { header = false; continue; }
    if (!line.trim()) continue;
    const parts = splitCsv(line);
    if (parts.length < 5) continue;
    const row = {
      word: (parts[0] || '').trim(),
      phonetic: (parts[1] || '').trim(),
      definition: (parts[2] || '').trim(),
      translation: (parts[3] || '').trim(),
      collins: parts[5] || '',
      oxford: parts[6] || '',
      tag: parts[7] || '',
      bnc: parts[8] || '',
      frq: parts[9] || ''
    };
    if (!row.word || !CJK.test(row.translation)) continue;
    if (row.word.length > 32) continue;
    rows.push(row);
  }
  return rows;
}

const rows = (await readEntries())
  .sort((a, b) => score(b) - score(a) || rank(a) - rank(b))
  .slice(0, LIMIT);

fs.mkdirSync(outDir, { recursive: true });
const coreLines = [];
for (const row of rows) {
  const meaning = cleanMeaning(row.translation);
  if (!meaning) continue;
  coreLines.push([row.word.toLowerCase(), row.phonetic, meaning].join('\t'));
}
fs.writeFileSync(path.join(outDir, 'core.tsv'), coreLines.join('\n') + '\n', 'utf8');
console.log('core.tsv 词条数：' + coreLines.length +
  ' 大小：' + (fs.statSync(path.join(outDir, 'core.tsv')).size / 1048576).toFixed(1) + ' MB');

const lemmaLines = [];
for (const line of fs.readFileSync(lemmaPath, 'utf8').split(/\r?\n/)) {
  if (!line || line.startsWith(';') || line.startsWith('#')) continue;
  const parts = line.trim().split(/\s+/);
  if (parts.length < 2) continue;
  const form = parts[0].toLowerCase();
  const base = parts[1].toLowerCase();
  if (form === base) continue;
  lemmaLines.push(form + '\t' + base);
}
fs.writeFileSync(path.join(outDir, 'lemma.tsv'), lemmaLines.join('\n') + '\n', 'utf8');
console.log('lemma.tsv 条数：' + lemmaLines.length);
