"""Serves a stand-in YouTube watch page that runs the app's YouTubeAds script (read from the
Kotlin source) and reports what it did. Usage: yttest.py <port> <path to YouTubeAds.kt>"""
import http.server, json, os, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
VIDEO = open(os.path.join(ROOT, 'clip.mp4'), 'rb').read()
src = open(sys.argv[2], encoding='utf-8').read()
a = src.index('val JS = """') + len('val JS = """')
SCRIPT = src[a:src.index('""".trimIndent()', a)]

PLAYER = {'videoDetails': {'title': 'kept'}, 'streamingData': {'ok': 1}, 'adPlacements': [1], 'playerAds': [2],
          'adSlots': [3], 'auxiliaryUi': {'messageRenderers': {'enforcementMessageViewModel': {'x': 1}, 'other': 1}}}

PAGE = ('''<!doctype html><html><head><meta charset="utf-8"><title>YT test</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<script>''' + SCRIPT + '''</script>
<script>var ytInitialPlayerResponse = ''' + json.dumps(PLAYER) + ''';</script>
<style>body{font:15px sans-serif;margin:12px} li{margin:3px 0} button{font-size:16px;padding:10px;margin:8px 0;display:block} video{width:200px}</style>
</head><body>
<ul id="out"></ul>
<ytm-promoted-video-renderer id="feedad">FEED AD</ytm-promoted-video-renderer>
<div id="normal">NORMAL CARD</div>
<div id="movie_player" class="html5-video-player">
  <video id="v" src="/v/clip.mp4" preload="auto" playsinline></video>
  <button class="ytp-skip-ad-button" id="skip">Skip</button>
</div>
<button id="startad">START-AD</button>
<button id="content">PLAY-CONTENT</button>
<script>
var out = document.getElementById('out');
function say(k, v) { var li = document.createElement('li'); li.textContent = k + '=' + v; out.appendChild(li); }
function left(o) { return ['adPlacements', 'playerAds', 'adSlots'].filter(function (k) { return k in o; }).join('+') || 'none'; }
var p = window.ytInitialPlayerResponse;
say('initial-ads', left(p) + ',kept-' + (p.videoDetails.title === 'kept' && p.streamingData.ok === 1));
say('initial-nag', ('enforcementMessageViewModel' in p.auxiliaryUi.messageRenderers ? 'LEFT' : 'gone') + ',other-' + p.auxiliaryUi.messageRenderers.other);
var parsed = JSON.parse('{"playerResponse":{"adPlacements":[1],"streamingData":{"a":1}},"adSlots":[1],"keep":[1,2,3]}');
say('parse', left(parsed) + '/' + left(parsed.playerResponse) + ',kept-' + (parsed.keep.length === 3 && parsed.playerResponse.streamingData.a === 1));
say('parse-plain', JSON.stringify(JSON.parse('[1,{"a":"b"},"x",null]')));
say('native-look', /\\[native code\\]/.test(Function.prototype.toString.call(JSON.parse)) && /\\[native code\\]/.test(Function.prototype.toString.call(Response.prototype.json)));
say('visible-keys', Object.keys(window).filter(function (k) { return /ytq|anydrop/i.test(k); }).join(',') || 'none');
fetch('/youtubei/v1/player').then(function (r) { return r.json(); }).then(function (j) { say('fetch-player', left(j) + ',kept-' + (j.videoDetails.title === 'kept')); });
fetch('/youtubei/v1/get_watch').then(function (r) { return r.json(); }).then(function (j) { say('fetch-get_watch', left(j[0].playerResponse) + ',items-' + j.length); });
say('feed-ad', getComputedStyle(document.getElementById('feedad')).display + ',normal-' + getComputedStyle(document.getElementById('normal')).display);

var v = document.getElementById('v'), player = document.getElementById('movie_player'), skips = 0, t0 = 0;
document.getElementById('skip').onclick = function () { skips++; };
v.addEventListener('ended', function () {
  if (!player.classList.contains('ad-showing')) { say('content-ended', 'at-' + v.currentTime.toFixed(1)); return; }
  var took = Date.now() - t0;
  player.classList.remove('ad-showing');           // what YouTube does when the ad is over
  setTimeout(function () { say('ad', 'ended-in-' + (took < 1500 ? 'under-1.5s' : took + 'ms') + ',skip-clicks-' + (skips > 0) + ',muted-after-' + v.muted); }, 900);
});
document.getElementById('startad').onclick = function () {
  v.muted = false; v.currentTime = 0; t0 = Date.now(); skips = 0;
  player.classList.add('ad-showing');
  v.play();
  setTimeout(function () { say('ad-during', 'muted-' + v.muted); }, 60);
};
document.getElementById('content').onclick = function () {
  v.muted = false; v.currentTime = 0; v.play();
  setTimeout(function () { say('content', 'time-' + v.currentTime.toFixed(1) + ',muted-' + v.muted + ',duration-' + v.duration.toFixed(1)); }, 1500);
};
</script></body></html>''').encode()


class H(http.server.BaseHTTPRequestHandler):
    def _send(self, ctype, body):
        self.send_response(200)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Accept-Ranges', 'bytes')
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith('/v/'):
            self._send('video/mp4', VIDEO)
        elif self.path.startswith('/youtubei/v1/player'):
            self._send('application/json', json.dumps(PLAYER).encode())
        elif self.path.startswith('/youtubei/v1/get_watch'):
            self._send('application/json', json.dumps([{'playerResponse': PLAYER}, {'response': {}}]).encode())
        else:
            self._send('text/html; charset=utf-8', PAGE)

    do_HEAD = do_GET

    def log_message(self, *a):
        pass


http.server.ThreadingHTTPServer(('127.0.0.1', int(sys.argv[1])), H).serve_forever()
