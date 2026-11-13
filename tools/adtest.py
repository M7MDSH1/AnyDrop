import http.server, sys

PAGE = '''<!doctype html><html><head><meta charset="utf-8"><title>Ad test</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>body{font:15px sans-serif;margin:12px} li{margin:4px 0} button,a{display:block;margin:10px 0;font-size:16px;padding:10px}</style>
</head><body>
<h3>Ad test</h3>
<ins class="adsbygoogle" style="display:block" data-ad-client="ca-pub-0" data-ad-slot="1"></ins>
<div id="gpt-slot"></div>
<ul id="out"></ul>
<button id="pop">POPUP-OTHER-SITE</button>
<button id="popad">POPUP-AD-HOST</button>
<a id="blank" target="_blank" href="https://example.org/">LINK-NEW-TAB</a>
<a id="adlink" href="https://ad.doubleclick.net/ddm/clk/1">LINK-AD-CLICK</a>
<script>
var out = document.getElementById('out');
function say(k, v) { var li = document.createElement('li'); li.textContent = k + '=' + v; out.appendChild(li); }
function script(name, src, after) {
  var s = document.createElement('script'); s.src = src;
  s.onload = function () { say(name, 'LOADED' + (after ? ':' + after() : '')); };
  s.onerror = function () { say(name, 'FAILED'); };
  document.head.appendChild(s);
}
// 1. the scripts sites test for
(window.adsbygoogle = window.adsbygoogle || []).push({});
script('adsense', 'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js', function () {
  var ins = document.querySelector('ins.adsbygoogle');
  return 'loaded-' + !!(window.adsbygoogle && window.adsbygoogle.loaded) + ',status-' + ins.getAttribute('data-adsbygoogle-status') + ',inner-' + ins.innerHTML.length;
});
window.googletag = window.googletag || { cmd: [] };
googletag.cmd.push(function () {
  var slot = googletag.defineSlot('/1/test', [300, 250], 'gpt-slot').addService(googletag.pubads());
  googletag.pubads().addEventListener('slotRenderEnded', function (e) { say('gpt-render', 'isEmpty-' + e.isEmpty); });
  googletag.pubads().enableSingleRequest(); googletag.enableServices(); googletag.display('gpt-slot');
});
script('gpt', 'https://securepubads.g.doubleclick.net/tag/js/gpt.js', function () { return 'apiReady-' + !!googletag.apiReady; });
script('taboola', 'https://cdn.taboola.com/libtrc/test/loader.js');
script('adstatus', 'https://static.doubleclick.net/instream/ad_status.js', function () { return 'google_ad_status-' + window.google_ad_status; });
// 2. bait element (FuckAdBlock style)
var bait = document.createElement('div');
bait.className = 'pub_300x250 pub_300x250m pub_728x90 text-ad textAd text_ad text_ads text-ads text-ad-links adsbox ad-banner';
bait.style.cssText = 'width:1px;height:1px;position:absolute;left:-10000px;top:-1000px;';
document.body.appendChild(bait);
setTimeout(function () {
  var hidden = bait.offsetParent === null || bait.offsetHeight === 0 || bait.clientHeight === 0 || getComputedStyle(bait).display === 'none';
  say('bait', hidden ? 'HIDDEN' : 'UNTOUCHED');
}, 300);
// 3. fetches
fetch('https://cdn.taboola.com/x.js', { mode: 'no-cors' }).then(function () { say('fetch-nocors', 'OK'); }, function () { say('fetch-nocors', 'FAILED'); });
fetch('https://ib.adnxs.com/ut/v3', { credentials: 'include' }).then(function (r) { say('fetch-cors', 'OK-' + r.status); }, function () { say('fetch-cors', 'FAILED'); });
var img = new Image(); img.onload = function () { say('pixel', 'LOADED-' + img.width + 'x' + img.height); }; img.onerror = function () { say('pixel', 'FAILED'); };
img.src = 'https://ad.doubleclick.net/pixel.gif';
say('injected', Object.keys(window).filter(function (k) { return /anydrop|adblock/i.test(k); }).join(',') || 'none');
// 4. windows
document.getElementById('pop').onclick = function () { var w = window.open('https://example.org/?popup'); say('open-other', w ? 'WINDOW' : 'NULL'); };
document.getElementById('popad').onclick = function () { var w = window.open('https://www.popads.net/'); say('open-ad', w ? 'WINDOW' : 'NULL'); };
</script></body></html>'''.encode()


class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(PAGE)))
        self.end_headers()
        self.wfile.write(PAGE)

    def log_message(self, *a):
        pass


http.server.ThreadingHTTPServer(('127.0.0.1', int(sys.argv[1])), H).serve_forever()
