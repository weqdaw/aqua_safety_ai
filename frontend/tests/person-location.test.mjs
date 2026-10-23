import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

const source = readFileSync(new URL('../src/utils/personLocation.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 } }).outputText;
const { parseGpsPoint, gpsToGaode, gaodeToGps } = await import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);

test('GPS API uses longitude first and accepts PostGIS WKT', () => {
  assert.deepEqual(parseGpsPoint(' SRID=4326;POINT (116.391  39.907) '), [116.391, 39.907]);
});
test('invalid server positions do not produce globe markers', () => {
  for (const value of [null, '', 'POINT(NaN 31)', 'POINT(121 Infinity)', 'POINT(181 31)', 'POINT(121 91)', 'POINT(0 0)', 'LINESTRING(121 31)']) assert.equal(parseGpsPoint(value), null);
});
test('GPS rendering agrees with the mobile map reference', () => {
  const [lng, lat] = gpsToGaode(116.391, 39.907);
  assert.ok(Math.abs(lng - 116.397241) < .00001);
  assert.ok(Math.abs(lat - 39.908401) < .00001);
  assert.deepEqual(gpsToGaode(151.2, -33.8), [151.2, -33.8]);
});
test('manual personnel map clicks round trip through the GPS API without double shifting', () => {
  for (const gps of [[116.391,39.907], [110.25,25.23], [121.5,31.2], [151.2,-33.8]]) {
    const restored = gaodeToGps(...gpsToGaode(...gps));
    assert.ok(Math.abs(restored[0]-gps[0]) < 1e-7);
    assert.ok(Math.abs(restored[1]-gps[1]) < 1e-7);
  }
});
