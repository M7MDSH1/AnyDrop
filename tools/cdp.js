// Evaluates an expression in the app's WebView page over the DevTools protocol (debug builds).
// Usage: node cdp.js <port> <file with the expression> [url substring to pick the page]
const fs = require('fs');
const [port, file, want] = process.argv.slice(2);
(async () => {
  const pages = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
  const page = pages.find((p) => p.type === 'page' && (!want || p.url.includes(want))) || pages[0];
  if (!page) { console.log('no page'); process.exit(1); }
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  ws.onopen = () => ws.send(JSON.stringify({
    id: 1, method: 'Runtime.evaluate',
    params: { expression: fs.readFileSync(file, 'utf8'), returnByValue: true, awaitPromise: true }
  }));
  ws.onmessage = (e) => {
    const m = JSON.parse(e.data);
    if (m.id !== 1) return;
    const r = m.result && m.result.result;
    console.log(page.url.slice(0, 80));
    console.log(m.result && m.result.exceptionDetails ? 'EXCEPTION ' + JSON.stringify(m.result.exceptionDetails).slice(0, 400)
      : typeof r.value === 'string' ? r.value : JSON.stringify(r.value, null, 1));
    ws.close();
    process.exit(0);
  };
  ws.onerror = (e) => { console.log('ws error', e.message || e); process.exit(1); };
  setTimeout(() => { console.log('timeout'); process.exit(1); }, 15000);
})();
