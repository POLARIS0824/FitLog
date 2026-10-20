import { Agent } from '@earendil-works/pi-agent-core';
import { createAssistantMessageEventStream } from '@earendil-works/pi-ai';
import { createHash } from 'node:crypto';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const model = { id: 'replay', name: 'replay', provider: 'fixture', api: 'openai-completions',
  baseUrl: 'https://benchmark.invalid', reasoning: false, input: ['text'], contextWindow: 100000, maxTokens: 10000,
  cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 } };
const hash = text => createHash('sha256').update(text).digest('hex');

// Actual upstream Agent + injected stream function. No HTTP provider implementation is called.
export async function extract(input, { cancelAfterMs = null } = {}) {
  const started = performance.now();
  let calls = 0, events = 0, contexts = [], timer;
  const agent = new Agent({ initialState: { model, systemPrompt: 'fixture-extraction', tools: [] },
    streamFn: (_model, context, options) => {
      calls++;
      if (calls !== 1) throw new Error('More than one model call');
      const users = context.messages.filter(m => m.role === 'user');
      if (users.length !== 1) throw new Error('Context leaked');
      const content = users[0].content;
      const text = typeof content === 'string' ? content : content.map(p => p.text ?? '').join('');
      if (text !== input.text) throw new Error('Input changed');
      contexts.push(hash(text));
      const stream = createAssistantMessageEventStream();
      const message = { role: 'assistant', api: model.api, provider: model.provider, model: model.id,
        content: [], timestamp: Date.now(), stopReason: 'stop',
        usage: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, totalTokens: 0,
          cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 } } };
      void (async () => {
        try {
          await delay(input.delayMs ?? 30, undefined, { signal: options?.signal });
          if (input.fault === 'network') throw new Error('fixture');
          message.content = [{ type: 'text', text: input.responseJson }];
          stream.push({ type: 'start', partial: message });
          stream.push({ type: 'done', reason: 'stop', message });
        } catch {
          message.stopReason = options?.signal?.aborted ? 'aborted' : 'error';
          message.errorMessage = 'Fixture stopped';
          stream.push({ type: 'error', reason: message.stopReason, error: message });
        } finally { stream.end(message); }
      })();
      return stream;
    } });
  const initMs = performance.now() - started;
  agent.subscribe(() => { events++; });
  if (cancelAfterMs != null) timer = setTimeout(() => agent.abort(), cancelAfterMs);
  try {
    await agent.prompt(input.text);
    await agent.waitForIdle();
    const answer = agent.state.messages.filter(m => m.role === 'assistant').at(-1);
    const text = answer?.content.filter(p => p.type === 'text').map(p => p.text).join('');
    let protocol = false;
    try { const json = JSON.parse(text); protocol = json.schemaVersion === 1 && Array.isArray(json.sessions); } catch {}
    return { case_id: input.id, runtime_init_ms: initMs, runtime_events: events, stub_model_calls: calls,
      http_requests: 0, tool_calls: 0, context_hashes: contexts, response_hash: text ? hash(text) : null,
      protocol_valid: protocol, stop_reason: answer?.stopReason ?? 'error', latency_ms: performance.now() - started };
  } finally { clearTimeout(timer); agent.reset(); }
}

export async function replay(spec, concurrency = 4) {
  if (spec.mode !== 'replay' || spec.sourceRoot != null) throw new Error('Only frozen offline fixtures accepted');
  if (!Number.isInteger(concurrency) || concurrency < 1 || concurrency > 16) throw new Error('Invalid concurrency');
  const started = performance.now(), results = [];
  let next = 0;
  await Promise.all(Array.from({ length: concurrency }, async () => {
    while (next < spec.cases.length) {
      const input = spec.cases[next++];
      results.push(await extract(input));
    }
  }));
  return { runtime: '@earendil-works/pi-agent-core', runtime_version: '1.1.0', mode: 'replay',
    platform: 'node-desktop', node_version: process.version, concurrency, batch_ms: performance.now() - started,
    dataset_fingerprint: spec.datasetFingerprint, http_requests: 0, results,
    integration: 'Agent lifecycle and protocol smoke test only; no Kotlin validator, Room or Android bridge',
    final_winner: null };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const [, , specPath, output, concurrency = '4'] = process.argv;
  if (!specPath || !output) throw new Error('Usage: node replay.mjs spec.json output-directory [concurrency]');
  const spec = JSON.parse(await readFile(specPath, 'utf8'));
  const result = await replay(spec, Number(concurrency));
  await mkdir(output, { recursive: true });
  await writeFile(`${output}/pi-summary.json`, JSON.stringify(result, null, 2), { flag: 'wx' });
  console.log(`Pi offline replay: ${result.results.length} cases, zero HTTP requests`);
}
