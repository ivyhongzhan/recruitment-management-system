import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { filterRecords, summarize, csvForRecords, formatPercent } from './core.mjs';
const records = JSON.parse(fs.readFileSync(new URL('./data/synthetic_applications.json', import.meta.url), 'utf8'));

test('data is 360 synthetic records with no names or emails', () => {
  assert.equal(records.length, 360);
  assert.ok(records.every(r => typeof r.id === 'string' && r.id.startsWith('SYN-')));
  assert.ok(records.every(r => !('name' in r) && !('email' in r)));
});
test('funnel monotonically decreases by reached stage', () => {
  const values = summarize(records).funnel.map(x => x.reached);
  assert.ok(values.every((v,i) => i === 0 || v <= values[i-1]));
});
test('filtering produces a subset with its own denominator', () => {
  const d = records[0].department;
  const filtered = filterRecords(records, {department:d});
  assert.ok(filtered.length > 0 && filtered.length < records.length);
  assert.equal(summarize(filtered).funnel[0].denominator_all_applicants, filtered.length);
});
test('channels use within-channel denominators', () => {
  const r = summarize(records);
  for (const row of r.channels) {
    assert.equal(row.applicants, records.filter(x => x.channel===row.channel).length);
  }
});
test('empty selection returns undefined reach rates as N/A', () => {
  const r=summarize([]);
  assert.equal(r.funnel[0].percent_of_applicants, null);
  assert.equal(formatPercent(r.channels[0].offer_reach_percent), 'N/A');
});
test('CSV quotes quotes and commas', () => {
  const s=csvForRecords([{id:'A,"B', department:'X', role:'Y'}]);
  assert.ok(s.includes('"A,""B"'));
});
