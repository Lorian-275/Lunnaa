package com.lorian.lunna

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// La URL y la clave anon son públicas: lo que protege los datos son las reglas RLS de la base.
val supabase = createSupabaseClient(
    supabaseUrl = "https://ztjiojoulouglqcueobe.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inp0amlvam91bG91Z2xxY3Vlb2JlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTMxMjAyODMsImV4cCI6MjEwODY5NjI4M30.m2d8wkn8opaY25-Rivg8VssZD1QpAZxE5zcrQmVNWpQ",
) {
    install(Auth)
    install(Postgrest)
    install(Functions)
}

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
)

@Serializable
data class Playlist(val id: String, val name: String)

@Serializable
data class NewPlaylist(val name: String)

@Serializable
data class PlaylistSong(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("song_id") val songId: String,
    val position: Int,
)

@Serializable
data class PlaylistEntry(val position: Int, val songs: Song)
