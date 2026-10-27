package com.lorian.lunna

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
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

class AppViewModel : ViewModel() {
    var songs by mutableStateOf<List<Song>>(emptyList()); private set
    var query by mutableStateOf(""); private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList()); private set
    var openPlaylist by mutableStateOf<Playlist?>(null); private set
    var playlistSongs by mutableStateOf<List<Song>>(emptyList()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)

    private var searchJob: Job? = null

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
        }
    }

    fun loadAll() {
        search(query)
        viewModelScope.launch { refreshPlaylists() }
    }

    fun search(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300) // espera a que se deje de escribir
            attempt("No se pudieron cargar las canciones.") {
                val clean = cleanQuery(q)
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

    /** Pide los enlaces temporales de R2 para la cola y empieza a sonar `list[index]`. */
    fun play(player: Player?, list: List<Song>, index: Int) {
        if (player == null || index !in list.indices) return
        // sign-urls acepta hasta 500 canciones por llamada.
        val start = (index - 250).coerceAtLeast(0)
        val queue = list.subList(start, minOf(list.size, start + 500))
        viewModelScope.launch {
            try {
                val res = supabase.functions.invoke("sign-urls", body = buildJsonObject {
                    putJsonArray("ids") { queue.forEach { add(it.id) } }
                })
                val urls = Json.decodeFromString<Map<String, String>>(res.bodyAsText())
                // shortcut: los enlaces duran 6 h; si la app queda pausada más tiempo, hay que volver a tocar la canción.
                val items = queue.mapNotNull { s ->
                    val url = urls[s.id] ?: return@mapNotNull null
                    MediaItem.Builder()
                        .setMediaId(s.id)
                        .setUri(url)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).build())
                        .build()
                }
                if (items.isEmpty()) { message = "No se encontró el audio."; return@launch }
                val startAt = items.indexOfFirst { it.mediaId == list[index].id }.coerceAtLeast(0)
                player.setMediaItems(items, startAt, 0)
                player.prepare()
                player.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RestException) {
                message = if (e.statusCode == 429) "Se alcanzó el límite gratis de este mes." else "No se pudo reproducir."
            } catch (e: Exception) {
                message = "No se pudo reproducir. Revisa tu internet."
            }
        }
    }
}
