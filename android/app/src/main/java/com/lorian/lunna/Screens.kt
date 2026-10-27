package com.lorian.lunna

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay

@Composable
fun App(status: SessionStatus, player: Player?, vm: AppViewModel = viewModel()) {
    when (status) {
        is SessionStatus.Initializing ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        // Sin internet al abrir, la sesión guardada falla al renovarse pero sigue siendo válida.
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> {
            LaunchedEffect(Unit) { vm.loadAll() }
            MainScreen(vm, player)
        }
        is SessionStatus.NotAuthenticated -> LoginScreen(vm)
    }
}

@Composable
fun LoginScreen(vm: AppViewModel) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    val canSend = !vm.busy && user.isNotBlank() && pass.isNotEmpty()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Lunna", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = user, onValueChange = { user = it }, label = { Text("Usuario") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pass, onValueChange = { pass = it }, label = { Text("Contraseña") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSend) vm.login(user, pass) }),
                modifier = Modifier.fillMaxWidth(),
            )
            vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.login(user, pass) }, enabled = canSend, modifier = Modifier.fillMaxWidth()) {
                Text(if (vm.busy) "Entrando…" else "Entrar")
            }
        }
    }
}

@Composable
fun MainScreen(vm: AppViewModel, player: Player?) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf<Song?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                MiniPlayer(player)
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("♪") }, label = { Text("Biblioteca") })
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("☰") }, label = { Text("Playlists") })
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            if (tab == 0) Library(vm, player, onAdd = { adding = it }) else Playlists(vm, player)
        }
    }
    adding?.let { song -> AddToPlaylistDialog(vm, song, onDismiss = { adding = null }) }
}

@Composable
fun SongRow(song: Song, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(listOfNotNull(song.artist, song.durationMs?.let(::fmtTime)).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClickLabel = "Reproducir", onClick = onClick),
    )
}

@Composable
fun Library(vm: AppViewModel, player: Player?, onAdd: (Song) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.query, onValueChange = vm::search, singleLine = true,
                placeholder = { Text("Buscar canción o artista") }, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.logout(player) }) { Text("Salir") }
        }
        if (vm.songs.isEmpty()) {
            Text(
                if (vm.query.isBlank()) "Todavía no hay canciones." else "Sin resultados.",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn {
            itemsIndexed(vm.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(s, onClick = { vm.play(player, vm.songs, i) }) {
                    TextButton(onClick = { onAdd(s) }) { Text("+ Playlist") }
                }
            }
        }
    }
}

@Composable
fun Playlists(vm: AppViewModel, player: Player?) {
    val open = vm.openPlaylist
    if (open == null) {
        var name by rememberSaveable { mutableStateOf("") }
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nueva playlist") },
                    singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { vm.createPlaylist(name); name = "" }, enabled = name.isNotBlank()) { Text("Crear") }
            }
            if (vm.playlists.isEmpty()) {
                Text("Aún no tienes playlists.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(vm.playlists, key = { it.id }) { p ->
                    ListItem(headlineContent = { Text(p.name) }, modifier = Modifier.clickable { vm.open(p) })
                }
            }
        }
    } else {
        var confirmDelete by remember(open.id) { mutableStateOf(false) }
        Column {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.open(null) }) { Text("← Volver") }
                Text(open.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.play(player, vm.playlistSongs, 0) }, enabled = vm.playlistSongs.isNotEmpty()) { Text("▶ Reproducir") }
                OutlinedButton(onClick = { if (confirmDelete) vm.deletePlaylist(open) else confirmDelete = true }) {
                    Text(if (confirmDelete) "¿Borrar? Toca otra vez" else "Borrar playlist")
                }
            }
            if (vm.playlistSongs.isEmpty()) {
                Text("Agrega canciones desde la Biblioteca con «+ Playlist».", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                itemsIndexed(vm.playlistSongs, key = { _, s -> s.id }) { i, s ->
                    SongRow(s, onClick = { vm.play(player, vm.playlistSongs, i) }) {
                        TextButton(onClick = { vm.removeFromPlaylist(open, s) }) { Text("Quitar") }
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(vm: AppViewModel, song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar «${song.title}»") },
        text = {
            if (vm.playlists.isEmpty()) Text("Primero crea una playlist en la pestaña Playlists.")
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(vm.playlists, key = { it.id }) { p ->
                    ListItem(headlineContent = { Text(p.name) },
                        modifier = Modifier.clickable { vm.addToPlaylist(p, song); onDismiss() })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
fun MiniPlayer(player: Player?) {
    if (player == null) return
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(player) {
        fun sync() {
            title = player.mediaMetadata.title?.toString()
            artist = player.mediaMetadata.artist?.toString()
            playing = player.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = sync()
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            dur = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    val t = title ?: return
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            // Solo se busca al soltar: cada salto es una lectura nueva en R2.
            Slider(
                value = drag ?: if (dur > 0) pos.toFloat() / dur else 0f,
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { player.seekTo((it * dur).toLong()) }; drag = null },
                enabled = dur > 0,
                modifier = Modifier.semantics { contentDescription = "Posición de la canción" },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(artist, "${fmtTime(pos)} / ${fmtTime(dur)}").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { player.seekToPrevious() }, Modifier.semantics { contentDescription = "Anterior" }) { Text("⏮") }
                IconButton(onClick = { if (playing) player.pause() else player.play() },
                    Modifier.semantics { contentDescription = if (playing) "Pausar" else "Reproducir" }) { Text(if (playing) "⏸" else "▶") }
                IconButton(onClick = { player.seekToNext() }, Modifier.semantics { contentDescription = "Siguiente" }) { Text("⏭") }
            }
        }
    }
}
