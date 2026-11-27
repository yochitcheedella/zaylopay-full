import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import qaRuntime from './support/runtime.cjs';
test('artifact files stay in the dedicated output root', () => {
  const filename = qaRuntime.artifactPath('unit/example.json');
  assert.equal(filename, path.join(qaRuntime.artifactRoot, 'unit', 'example.json'));
});
test('artifact path cannot escape via parent traversal', () => {
  assert.throws(() => qaRuntime.artifactPath('../../outside.json'), /inside/);
});
test('a configured artifact prefix is normalized consistently', () => {
  assert.equal(qaRuntime.artifactPath('qa/example.png'), path.join(qaRuntime.artifactRoot, 'example.png'));
});

test('QA origin is configurable without editing application routes', () => { assert.equal(new URL(qaRuntime.qaUrl('/login')).pathname, '/login'); });
