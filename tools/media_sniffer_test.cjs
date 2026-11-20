// Run with: node tools/media_sniffer_test.cjs
// Executes the production WebView script against synthetic pages; no network needed.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/anydrop/app/engine/WebVideoExtractor.kt'), 'utf8');
const script = source.split('val CORE_JS = """')[1].split('""".trimIndent()')[0].replaceAll("${'$'}", '$');
function scan(html, json) {
  const document = {
    readyState: 'complete', title: 'Test clip', documentElement: { innerHTML: html },
    querySelector: () => null,
    querySelectorAll: selector => selector.startsWith('script[') && json ? [{ textContent: JSON.stringify(json) }] : []
  };
  const context = { window: {}, document, location: { protocol: 'https:', href: 'https://host.example/page' } };
  vm.runInNewContext(script, context);
  return JSON.parse(context.window.__ad.collect()).hits.map(hit => hit.url);
}
const tests = [
  ['BBCode label is not a media URL', () => assert.deepEqual(scan('[url=https://host.example/p6ldojqdup8w]Example.1080p.WEB.mp4[/url]'), [])],
  ['real media beside BBCode remains available', () => assert.deepEqual(scan('[url=https://host.example/page]Example.mp4[/url]<script>file="https://cdn.example/clip.mp4?token=abc&x=1"</script>'), ['https://cdn.example/clip.mp4?token=abc&x=1'])],
  ['direct media in BBCode excludes closing markup', () => assert.deepEqual(scan('[url]https://cdn.example/clip.mp4?token=abc[/url]'), ['https://cdn.example/clip.mp4?token=abc'])],
  ['escaped JSON-style URLs remain usable', () => assert.deepEqual(scan(String.raw`var file="https:\/\/cdn.example\/clip.mov?token=a%2Fb&x=1"`), ['https://cdn.example/clip.mov?token=a%2Fb&x=1'])],
  ['encoded filename brackets remain usable', () => assert.deepEqual(scan('<a href="https://cdn.example/clip%5BHD%5D.mp4">Video</a>'), ['https://cdn.example/clip%5BHD%5D.mp4'])],
  ['structured video URLs retain literal brackets', () => assert.deepEqual(scan('', { contentUrl: 'https://cdn.example/clip[HD].mp4' }), ['https://cdn.example/clip[HD].mp4'])]
];
let failures = 0;
for (const [name, test] of tests) {
  try { test(); console.log('PASS ' + name); }
  catch (error) { failures++; console.error('FAIL ' + name + '\n' + error.message); }
}
process.exitCode = failures ? 1 : 0;
