package com.mytvlauncher.app.data

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Phone companion: a tiny web server on the TV (port 8765, home Wi-Fi only)
 * serving a phone-friendly page to search shows, open them on the TV,
 * launch apps and browse My List — no typing with the remote.
 *
 * Every request needs the secret token from the URL/QR code shown in
 * Settings › Phone companion; "New link" rotates it.
 *
 * Runs inside the launcher process, so it's up whenever the launcher is
 * (which, as the default home app, is nearly always).
 */
class CompanionServer(
    private val context: Context,
    private val apps: AppRepository,
    private val content: ContentRepository,
    private val myList: MyListRepository
) {
    companion object {
        const val PORT = 8765
    }

    private val prefs = context.getSharedPreferences("companion", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var socket: ServerSocket? = null

    val token: String get() = prefs.getString("token", null) ?: newToken()

    fun newToken(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val r = SecureRandom()
        val t = (1..8).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("")
        prefs.edit().putString("token", t).apply()
        return t
    }

    fun url(): String? = ipAddress()?.let { "http://$it:$PORT/?t=$token" }

    fun start() {
        if (socket != null) return
        thread(name = "companion", isDaemon = true) {
            try {
                val server = ServerSocket(PORT)
                socket = server
                while (!server.isClosed) {
                    val client = server.accept()
                    thread(isDaemon = true) { handle(client) }
                }
            } catch (e: Exception) {
                socket = null
            }
        }
    }

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) {
            prefs.edit().putBoolean("enabled", value).apply()
            if (value) start() else stop()
        }

    fun stop() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun handle(client: Socket): Unit = client.use { s ->
        try {
            s.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            val requestLine = reader.readLine() ?: return@use
            while (true) { val l = reader.readLine(); if (l.isNullOrEmpty()) break }
            val target = requestLine.split(" ").getOrNull(1) ?: "/"
            val path = target.substringBefore('?')
            val params = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
            }
            val out = s.getOutputStream()
            if (params["t"] != token) {
                respond(out, 403, "text/plain", "Open the link shown on the TV (Settings › Phone companion).".toByteArray())
                return@use
            }
            when (path) {
                "/" -> respond(out, 200, "text/html; charset=utf-8", PAGE.toByteArray())
                "/api/apps" -> json(out, JSONArray().apply {
                    apps.apps.value.sortedBy { it.label.lowercase() }.forEach {
                        put(JSONObject().put("label", it.label).put("pkg", it.packageName))
                    }
                })
                "/api/icon" -> {
                    val app = apps.appFor(params["pkg"] ?: "")
                    if (app == null) respond(out, 404, "text/plain", ByteArray(0))
                    else {
                        val bytes = ByteArrayOutputStream()
                        app.icon.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, bytes)
                        respond(out, 200, "image/png", bytes.toByteArray())
                    }
                }
                "/api/launch" -> {
                    val app = apps.appFor(params["pkg"] ?: "")
                    if (app != null) main.post { apps.launch(app) }
                    json(out, JSONObject().put("ok", app != null))
                }
                "/api/search" -> {
                    val installed = apps.apps.value.map { it.packageName }.toSet()
                    val hits = runBlocking { content.search(params["q"] ?: "", installed) }
                    json(out, JSONArray().apply { hits.forEach { put(hitJson(it.item, it.onYourApps.map { p -> p.first }, it.elsewhere)) } })
                }
                "/api/mylist" -> json(out, JSONArray().apply {
                    myList.items.value.forEach { put(hitJson(it, listOfNotNull(it.serviceName.ifBlank { null }), emptyList())) }
                })
                "/api/open" -> {
                    val app = apps.appFor(params["pkg"] ?: "")
                    val title = params["title"] ?: ""
                    if (app != null) main.post { apps.openTitle(app, title) }
                    json(out, JSONObject().put("ok", app != null))
                }
                "/api/save" -> {
                    val item = runCatching { contentItemFromJson(JSONObject(params["item"] ?: "{}")) }.getOrNull()
                    val added = item?.let { myList.toggle(it) }
                    json(out, JSONObject().put("ok", item != null).put("saved", added == true))
                }
                else -> respond(out, 404, "text/plain", "Not found".toByteArray())
            }
        } catch (_: Exception) { }
    }

    private fun hitJson(item: ContentItem, yours: List<String>, elsewhere: List<String>) =
        item.toJson().put("yours", JSONArray(yours)).put("elsewhere", JSONArray(elsewhere))

    private fun json(out: OutputStream, body: Any) =
        respond(out, 200, "application/json; charset=utf-8", body.toString().toByteArray())

    private fun respond(out: OutputStream, code: Int, type: String, body: ByteArray) {
        val status = when (code) { 200 -> "OK"; 403 -> "Forbidden"; else -> "Not Found" }
        out.write(("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(body)
        out.flush()
    }

    private fun ipAddress(): String? = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getLinkProperties(cm.activeNetwork)?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress
    } catch (e: Exception) { null }
}

// The phone page. Plain JS with string concatenation (no template literals,
// so Kotlin's "$" templating never touches it).
private val PAGE = """
<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>TV remote</title>
<style>
:root{--bg:#0e0e12;--card:#1b1b22;--text:#f2f2f5;--muted:#9a9aa6;--accent:#e5484d}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,Roboto,sans-serif}
header{padding:18px 16px 8px;font-size:20px;font-weight:600}header small{display:block;color:var(--muted);font-size:13px;font-weight:400;margin-top:2px}
.tabs{display:flex;gap:8px;padding:8px 16px}.tabs button{flex:1;padding:10px;border-radius:10px;border:0;background:var(--card);color:var(--text);font-size:15px}
.tabs button.on{background:var(--accent)}
.search{display:flex;gap:8px;padding:8px 16px}.search input{flex:1;padding:12px;border-radius:10px;border:1px solid #333;background:var(--card);color:var(--text);font-size:16px}
.search button{padding:0 16px;border-radius:10px;border:0;background:var(--accent);color:#fff;font-size:15px}
#list{padding:8px 16px 40px;display:grid;gap:12px}
.item{display:flex;gap:12px;background:var(--card);border-radius:12px;padding:10px;align-items:center}
.item img{width:64px;height:96px;object-fit:cover;border-radius:8px;background:#222}
.item .t{font-weight:600}.item .m{color:var(--muted);font-size:13px;margin:2px 0 8px}
.item button{margin:2px 6px 2px 0;padding:8px 12px;border-radius:8px;border:0;background:#2b2b35;color:var(--text);font-size:14px}
.item button.go{background:var(--accent);color:#fff}
.apps{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
.app{background:var(--card);border-radius:12px;padding:12px 6px;text-align:center;font-size:13px;border:0;color:var(--text)}
.app img{width:44px;height:44px;display:block;margin:0 auto 6px}
.note{color:var(--muted);font-size:14px;padding:4px 2px}
</style></head><body>
<header>Rajat's TV<small>Pick something here — it opens on the TV</small></header>
<div class="tabs"><button id="tSearch" class="on">Search</button><button id="tApps">Apps</button><button id="tList">My List</button></div>
<div class="search" id="sbar"><input id="q" placeholder="Show, film or app" enterkeyhint="search"><button id="go">Go</button></div>
<div id="list"><div class="note">Search for a show or film to see which of your apps has it.</div></div>
<script>
var T=new URLSearchParams(location.search).get('t');
function api(p){return fetch(p+(p.indexOf('?')<0?'?':'&')+'t='+encodeURIComponent(T)).then(function(r){return r.json()})}
function el(tag,cls,txt){var e=document.createElement(tag);if(cls)e.className=cls;if(txt!=null)e.textContent=txt;return e}
var L=document.getElementById('list');
function tab(id){['tSearch','tApps','tList'].forEach(function(x){document.getElementById(x).classList.toggle('on',x==id)});document.getElementById('sbar').style.display=id=='tSearch'?'flex':'none'}
function note(t){L.innerHTML='';L.appendChild(el('div','note',t))}
function showItems(items,emptyMsg){
  L.className='';L.innerHTML='';
  if(!items.length){note(emptyMsg);return}
  items.forEach(function(it){
    var row=el('div','item');var img=el('img');img.src=it.poster||it.backdrop||'';row.appendChild(img);
    var box=el('div');box.appendChild(el('div','t',it.title));
    var where=it.yours.length?('On '+it.yours.join(' · ')):(it.elsewhere.length?('Not on your apps · '+it.elsewhere.join(' · ')):'Not streaming in India');
    box.appendChild(el('div','m',(it.year?it.year+' · ':'')+where));
    if(it.pkg){var b=el('button','go','Play on TV');b.onclick=function(){api('/api/open?pkg='+encodeURIComponent(it.pkg)+'&title='+encodeURIComponent(it.title)).then(function(){b.textContent='Opening on TV…'})};box.appendChild(b)}
    var s=el('button',null,'My List');s.onclick=function(){var copy={};for(var k in it){if(k!='yours'&&k!='elsewhere')copy[k]=it[k]}api('/api/save?item='+encodeURIComponent(JSON.stringify(copy))).then(function(r){s.textContent=r.saved?'Saved ✓':'Removed'})};box.appendChild(s);
    row.appendChild(box);L.appendChild(row)});
}
function search(){var q=document.getElementById('q').value.trim();if(!q)return;note('Searching…');
  Promise.all([api('/api/search?q='+encodeURIComponent(q)),api('/api/apps')]).then(function(r){
    var apps=r[1].filter(function(a){return a.label.toLowerCase().indexOf(q.toLowerCase())>=0});
    showItems(r[0],'Nothing found.');
    if(apps.length){var g=el('div','apps');apps.forEach(function(a){g.appendChild(appTile(a))});L.insertBefore(g,L.firstChild)}
  }).catch(function(){note('Couldn\'t reach the TV.')})}
function appTile(a){var b=el('button','app');var i=el('img');i.src='/api/icon?pkg='+encodeURIComponent(a.pkg)+'&t='+encodeURIComponent(T);b.appendChild(i);b.appendChild(document.createTextNode(a.label));b.onclick=function(){api('/api/launch?pkg='+encodeURIComponent(a.pkg))};return b}
document.getElementById('go').onclick=search;
document.getElementById('q').onkeydown=function(e){if(e.key=='Enter')search()};
document.getElementById('tSearch').onclick=function(){tab('tSearch');note('Search for a show or film to see which of your apps has it.')};
document.getElementById('tApps').onclick=function(){tab('tApps');api('/api/apps').then(function(a){L.innerHTML='';var g=el('div','apps');a.forEach(function(x){g.appendChild(appTile(x))});L.appendChild(g)})};
document.getElementById('tList').onclick=function(){tab('tList');api('/api/mylist').then(function(i){showItems(i,'My List is empty. Save shows from search, or hold OK on one on the TV.')})};
</script></body></html>
"""
