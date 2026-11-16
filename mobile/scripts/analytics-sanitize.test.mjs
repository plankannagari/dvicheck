// Plain-node tests for src/analytics/analyticsSchema.js (Day 36).
// Run: npm run test:analytics   (no Jest in this project; uses node:test + node:assert)

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { sanitize, EVENTS, ALLOWED_PROPS } from '../src/analytics/analyticsSchema.js';

test('1. allowed props pass through unchanged', () => {
  const input = { screen: 'Home', item_count: 3, enabled: true };
  assert.deepEqual(sanitize(input), { screen: 'Home', item_count: 3, enabled: true });
});

test('2. non-allowlisted keys are dropped', () => {
  const forbidden = {
    phone: '+11234567890',
    amount: 12.5,
    total: '87.40',
    storeName: 'FRESH MART',
    email: 'someone@example.com',
    items: 'Milk 2L',
    receiptText: 'TOTAL 87.40',
  };
  for (const key of Object.keys(forbidden)) {
    assert.equal(ALLOWED_PROPS.has(key), false, `${key} must not be allow-listed`);
  }
  assert.deepEqual(sanitize(forbidden), {});
});

test('3. string values are truncated to 40 characters', () => {
  const long = 'a'.repeat(41) + 'bcdef';
  const out = sanitize({ screen: long, source: 'x'.repeat(40) });
  assert.equal(out.screen, 'a'.repeat(40));
  assert.equal(out.screen.length, 40);
  assert.equal(out.source, 'x'.repeat(40), 'exactly 40 chars is kept as-is');
});

test('4. objects, arrays, null and undefined values for allowed keys are dropped', () => {
  const out = sanitize({
    screen: { name: 'Home' },
    source: ['camera'],
    feature: null,
    reason: undefined,
    item_count: 2, // control: a valid value survives
  });
  assert.deepEqual(out, { item_count: 2 });
});

test("5. sanitize(undefined) and sanitize('x') return {}", () => {
  assert.deepEqual(sanitize(undefined), {});
  assert.deepEqual(sanitize('x'), {});
});

test('6. mixed props: only allowed keys returned, input not mutated', () => {
  const input = {
    screen: 'Scan',
    phone: '+11234567890',
    item_count: 7,
    storeName: 'FRESH MART',
    is_pro: false,
  };
  const snapshot = structuredClone(input);
  const out = sanitize(input);

  assert.deepEqual(out, { screen: 'Scan', item_count: 7, is_pro: false });
  assert.notEqual(out, input, 'returns a new object');
  assert.deepEqual(input, snapshot, 'input is not mutated');
});

test('7. every EVENTS value is unique and snake_case', () => {
  const values = Object.values(EVENTS);
  assert.ok(values.length > 0);
  assert.equal(new Set(values).size, values.length, 'event names must be unique');
  for (const name of values) {
    assert.match(name, /^[a-z]+(_[a-z]+)*$/, `"${name}" is not snake_case`);
  }
});
