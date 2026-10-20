import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extract, replay } from './replay.mjs';

const fixture = { id: 'a', text: 'bench 60kg', delayMs: 10, responseJson: '{"schemaVersion":1,"sessions":[]}' };
test('Actual Agent makes exactly one stub call, completes events and resets independent contexts', async () => {
  const a = await extract(fixture), b = await extract({ ...fixture, id: 'b', text: 'different diary' });
  assert.equal(a.stub_model_calls, 1); assert.equal(a.http_requests, 0);
  assert.ok(a.runtime_events > 0); assert.ok(a.protocol_valid);
  assert.notDeepEqual(a.context_hashes, b.context_hashes);
});
test('Cancellation terminates one in-flight call without continuing', async () => {
  const result = await extract({ ...fixture, delayMs: 1000 }, { cancelAfterMs: 20 });
  assert.equal(result.stop_reason, 'aborted'); assert.equal(result.stub_model_calls, 1);
  assert.equal(result.protocol_valid, false);
});
test('Provider failure and damaged output do not trigger retry', async () => {
  const failure = await extract({ ...fixture, fault: 'network' });
  assert.equal(failure.stop_reason, 'error'); assert.equal(failure.stub_model_calls, 1);
  const malformed = await extract({ ...fixture, responseJson: '{' });
  assert.equal(malformed.protocol_valid, false); assert.equal(malformed.stub_model_calls, 1);
});
test('Replay rejects live and source-backed inputs', async () => {
  await assert.rejects(replay({ mode: 'live', cases: [fixture] }));
  await assert.rejects(replay({ mode: 'replay', sourceRoot: 'C:/private', cases: [fixture] }));
});
