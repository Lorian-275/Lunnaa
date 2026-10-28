package com.lorian.lunna

import android.app.Application
import android.content.Context
import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

class AppViewModel(app: Application) : AndroidViewModel(app) {
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var query by mutableStateOf(""); private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var openPlaylist by mutableStateOf<Playlist?>(null); private set
    var playlistSongs by mutableStateOf<List<Song>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    private var searchJob: Job? = null

    // noBackupFilesDir: los audios no se copian al respaldo de Google. Cada canción: `<id>` (audio) + `<id>.json`.
    private val offlineDir = File(app.noBackupFilesDir, "songs").apply { mkdirs() }
    var downloaded by mutableStateOf(
        offlineDir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { f -> runCatching { Json.decodeFromString<Song>(f.readText()) }.getOrNull() }
            .associateBy { it.id },
    ); private set
    var downloadingIds by mutableStateOf<Set<String>>(emptySet()); private set

    private suspend fun attempt(error: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = error
        }
    }

    fun login(user: String, password: String) {
        val email = emailFor(user) ?: run { message = "El usuario solo lleva letras, números, punto o guion."; return }
        viewModelScope.launch {
            busy = true
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                message = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthRestException) {
                message = "Usuario o contraseña incorrectos."
            } catch (e: Exception) {
                message = "No se pudo conectar. Revisa tu internet."
            }
            busy = false
        }
    }

    fun logout(player: Player?) {
        player?.run { stop(); clearMediaItems() }
        viewModelScope.launch {
            attempt("No se pudo cerrar sesión.") { supabase.auth.signOut() }
            songs = emptyList(); playlists = emptyList(); openPlaylist = null; query = ""
            // Los audios son privados: no se quedan en el dispositivo sin una sesión.
            withContext(Dispatchers.IO) { offlineDir.listFiles()?.forEach { it.delete() } }
            downloaded = emptyMap()
        }
    }

    fun loadAll() {
        search(query)
        viewModelScope.launch { refreshPlaylists() }
    }

    var update by mutableStateOf<Release?>(null)
    var downloading by mutableStateOf(false); private set

    fun checkUpdate() = viewModelScope.launch {
        // Sin internet no pasa nada: se revisa otra vez la próxima vez que se abra.
        runCatching {
            supabase.from("app_releases").select {
                order("version_code", Order.DESCENDING)
                limit(1)
            }.decodeList<Release>().firstOrNull()
        }.getOrNull()?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }?.let { update = it }
    }

    fun installUpdate(context: Context, release: Release) {
        viewModelScope.launch {
            downloading = true
            attempt("No se pudo descargar la actualización.") {
                val bytes = supabase.storage.from("apk").downloadAuthenticated(release.apkPath)
                val file = withContext(Dispatchers.IO) {
                    File(context.cacheDir, "updates").apply { mkdirs() }.resolve("lunna.apk").apply { writeBytes(bytes) }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                // Android pide permiso para "instalar apps de esta fuente" la primera vez.
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            downloading = false
        }
    }

    fun search(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // espera a que se deje de escribir
            val clean = cleanQuery(q)
            try {
                // shortcut: Supabase devuelve máx. 1000 filas; con más canciones, las demás se encuentran buscando.
                songs = supabase.from("songs").select {
                    if (clean.isNotEmpty()) filter {
                        or {
                            ilike("title", "%$clean%")
                            ilike("artist", "%$clean%")
                        }
                    }
                    order("title", Order.ASCENDING)
                }.decodeList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Sin internet se muestran las descargas.
                songs = downloaded.values.filter {
                    clean.isEmpty() || it.title.contains(clean, true) || it.artist?.contains(clean, true) == true
                }.sortedBy { it.title.lowercase() }
                message = if (downloaded.isEmpty()) "No se pudieron cargar las canciones." else "Sin conexión: se muestran tus descargas."
            }
        }
    }

    private suspend fun refreshPlaylists() = attempt("No se pudieron cargar tus playlists.") {
        playlists = supabase.from("playlists").select { order("created_at", Order.ASCENDING) }.decodeList()
    }

    private suspend fun refreshPlaylistSongs(p: Playlist) = attempt("No se pudo abrir la playlist.") {
        playlistSongs = supabase.from("playlist_songs").select(Columns.raw("position, songs(*)")) {
            filter { eq("playlist_id", p.id) }
            order("position", Order.ASCENDING)
        }.decodeList<PlaylistEntry>().map { it.songs }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            attempt("No se pudo crear la playlist.") { supabase.from("playlists").insert(NewPlaylist(name.trim())) }
            refreshPlaylists()
        }
    }

    fun deletePlaylist(p: Playlist) {
        viewModelScope.launch {
            attempt("No se pudo borrar la playlist.") {
                supabase.from("playlists").delete { filter { eq("id", p.id) } }
                openPlaylist = null
            }
            refreshPlaylists()
        }
    }

    fun open(p: Playlist?) {
        openPlaylist = p
        playlistSongs = emptyList()
        if (p != null) viewModelScope.launch { refreshPlaylistSongs(p) }
    }

    fun addToPlaylist(p: Playlist, song: Song) {
        viewModelScope.launch {
            attempt("No se pudo agregar la canción.") {
                val current = supabase.from("playlist_songs").select {
                    filter { eq("playlist_id", p.id) }
                }.decodeList<PlaylistSong>()
                if (current.any { it.songId == song.id }) {
                    message = "Ya está en «${p.name}»."
                } else {
                    val next = (current.maxOfOrNull { it.position } ?: -1) + 1
                    supabase.from("playlist_songs").insert(PlaylistSong(p.id, song.id, next))
                    message = "Agregada a «${p.name}»."
                }
            }
        }
    }

    fun removeFromPlaylist(p: Playlist, song: Song) {
        viewModelScope.launch {
            attempt("No se pudo quitar la canción.") {
                supabase.from("playlist_songs").delete {
                    filter { eq("playlist_id", p.id); eq("song_id", song.id) }
                }
            }
            refreshPlaylistSongs(p)
        }
    }

    private suspend fun signUrls(ids: List<String>): Map<String, String> {
        val res = supabase.functions.invoke("sign-urls", body = buildJsonObject {
            putJsonArray("ids") { ids.forEach { add(it) } }
        })
        return Json.decodeFromString(res.bodyAsText())
    }

    fun download(song: Song) {
        if (song.id in downloadingIds || song.id in downloaded) return
        downloadingIds = downloadingIds + song.id
        viewModelScope.launch {
            try {
                val url = signUrls(listOf(song.id))[song.id] ?: error("sin audio")
                withContext(Dispatchers.IO) {
                    // Se baja a un .part y se renombra al final: una descarga cortada nunca queda como "descargada".
                    val part = File(offlineDir, "${song.id}.part")
                    URL(url).openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
                    check(part.renameTo(File(offlineDir, song.id)))
                    File(offlineDir, "${song.id}.json").writeText(Json.encodeToString(Song.serializer(), song))
                }
                downloaded = downloaded + (song.id to song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RestException) {
                message = if (e.statusCode == 429) "Se alcanzó el límite gratis de este mes." else "No se pudo descargar «${song.title}»."
            } catch (e: Exception) {
                File(offlineDir, "${song.id}.part").delete()
                message = "No se pudo descargar «${song.title}». Revisa tu internet."
            }
            downloadingIds = downloadingIds - song.id
        }
    }

    fun removeDownload(song: Song) {
        File(offlineDir, song.id).delete()
        File(offlineDir, "${song.id}.json").delete()
        downloaded = downloaded - song.id
        message = "Se quitó la descarga de «${song.title}»."
    }

    /** Pide los enlaces temporales de R2 para la cola y empieza a sonar `list[index]`. */
    fun play(player: Player?, list: List<Song>, index: Int) {
        if (player == null || index !in list.indices) return
        // sign-urls acepta hasta 500 canciones por llamada.
        val start = (index - 250).coerceAtLeast(0)
        val queue = list.subList(start, minOf(list.size, start + 500))
        viewModelScope.launch {
            // Las descargadas suenan desde el dispositivo; solo se firman las demás (y sin internet, se saltan).
            val remote = queue.filter { it.id !in downloaded }.map { it.id }
            var failure: Exception? = null
            val urls = if (remote.isEmpty()) emptyMap() else try {
                signUrls(remote)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e; emptyMap()
            }
            // shortcut: los enlaces duran 6 h; si la app queda pausada más tiempo, hay que volver a tocar la canción.
            val items = queue.mapNotNull { s ->
                val uri = if (s.id in downloaded) Uri.fromFile(File(offlineDir, s.id)) else Uri.parse(urls[s.id] ?: return@mapNotNull null)
                MediaItem.Builder()
                    .setMediaId(s.id)
                    .setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
                    .build()
            }
            if (items.none { it.mediaId == list[index].id }) {
                message = when {
                    failure is RestException && failure.statusCode == 429 -> "Se alcanzó el límite gratis de este mes."
                    failure is RestException -> "No se pudo reproducir."
                    failure != null -> "No se pudo reproducir. Revisa tu internet."
                    else -> "No se encontró el audio."
                }
                return@launch
            }
            player.setMediaItems(items, items.indexOfFirst { it.mediaId == list[index].id }, 0)
            player.prepare()
            player.play()
        }
    }
}
