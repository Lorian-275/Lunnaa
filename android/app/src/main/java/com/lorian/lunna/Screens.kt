package com.lorian.lunna

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.lorian.lunna.ui.theme.AccentBrush
import com.lorian.lunna.ui.theme.Lavender
import com.lorian.lunna.ui.theme.Night
import com.lorian.lunna.ui.theme.NightBrush
import com.lorian.lunna.ui.theme.OnAccent
import com.lorian.lunna.ui.theme.Pink
import com.lorian.lunna.ui.theme.SurfaceHi
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun App(status: SessionStatus, player: Player?, vm: AppViewModel = viewModel()) {
    when (status) {
        is SessionStatus.Initializing ->
            Box(Modifier.fillMaxSize().background(NightBrush), contentAlignment = Alignment.Center) { MoonLogo(72.dp) }
        // Sin internet al abrir, la sesión guardada falla al renovarse pero sigue siendo válida.
        is SessionStatus.Authenticated, is SessionStatus.RefreshFailure -> {
            LaunchedEffect(Unit) { vm.loadAll() }
            MainScreen(vm, player)
        }
        is SessionStatus.NotAuthenticated -> LoginScreen(vm)
    }
}

/* ---------- Piezas visuales ---------- */

/** Luna creciente con el degradado de la marca. */
@Composable
fun MoonLogo(size: Dp) {
    Canvas(Modifier.size(size).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
        drawCircle(Brush.linearGradient(listOf(Lavender, Pink), start = Offset.Zero, end = Offset(this.size.width, this.size.height)))
        drawCircle(
            Color.Black, radius = this.size.minDimension * .4f,
            center = Offset(this.size.width * .7f, this.size.height * .32f), blendMode = BlendMode.Clear,
        )
    }
}

/** Halo de color difuso para el fondo. */
@Composable
private fun Glow(color: Color, size: Dp, modifier: Modifier) {
    Box(modifier.size(size).background(Brush.radialGradient(listOf(color.copy(alpha = .32f), Color.Transparent)), CircleShape))
}

private val ArtPalettes = listOf(
    Color(0xFF8B5CF6) to Color(0xFFEC4899),
    Color(0xFF06B6D4) to Color(0xFF6366F1),
    Color(0xFFF59E0B) to Color(0xFFEF4444),
    Color(0xFF10B981) to Color(0xFF3B82F6),
    Color(0xFFF472B6) to Color(0xFFA78BFA),
    Color(0xFF6366F1) to Color(0xFF22D3EE),
)

/** Portada generada: cada canción o playlist tiene siempre el mismo degradado. */
@Composable
fun Artwork(seed: String, modifier: Modifier, corner: Dp = 12.dp, icon: ImageVector = LunnaIcons.MusicNote) {
    val (a, b) = ArtPalettes[(seed.hashCode() and Int.MAX_VALUE) % ArtPalettes.size]
    BoxWithConstraints(
        modifier.clip(RoundedCornerShape(corner)).background(Brush.linearGradient(listOf(a, b))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(maxWidth * .42f))
    }
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick, enabled = enabled, shape = CircleShape, contentPadding = PaddingValues(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        modifier = modifier.height(56.dp),
    ) {
        val fg = if (enabled) OnAccent else MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            Modifier.fillMaxSize().background(if (enabled) AccentBrush else SolidColor(MaterialTheme.colorScheme.outline)).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, tint = fg); Spacer(Modifier.width(8.dp)) }
            Text(text, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LunnaField(
    value: String, onValueChange: (String) -> Unit, label: String, icon: ImageVector,
    modifier: Modifier = Modifier.fillMaxWidth(),
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null) }, trailingIcon = trailing,
        singleLine = true, visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.primary, unfocusedBorderColor = c.outline,
            focusedContainerColor = c.surfaceVariant.copy(alpha = .55f), unfocusedContainerColor = c.surfaceVariant.copy(alpha = .35f),
            focusedLeadingIconColor = c.primary, unfocusedLeadingIconColor = c.onSurfaceVariant,
        ),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/* ---------- Login ---------- */

@Composable
fun LoginScreen(vm: AppViewModel) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    val canSend = !vm.busy && user.isNotBlank() && pass.isNotEmpty()
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(NightBrush)) {
        Glow(Lavender, 360.dp, Modifier.offset(x = (-140).dp, y = (-90).dp))
        Glow(Pink, 400.dp, Modifier.align(Alignment.BottomEnd).offset(x = 160.dp, y = 140.dp))
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            MoonLogo(84.dp)
            Spacer(Modifier.height(20.dp))
            Text("Lunna", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = c.onBackground)
            Text("Tu música, solo para los tuyos", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp)).background(c.surface.copy(alpha = .78f))
                    .border(1.dp, c.outline, RoundedCornerShape(28.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Inicia sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                LunnaField(
                    user, { user = it }, "Usuario", LunnaIcons.Person,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                )
                LunnaField(
                    pass, { pass = it }, "Contraseña", LunnaIcons.Lock,
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSend) vm.login(user, pass) }),
                    trailing = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(
                                if (showPass) LunnaIcons.VisibilityOff else LunnaIcons.Visibility,
                                contentDescription = if (showPass) "Ocultar contraseña" else "Mostrar contraseña",
                            )
                        }
                    },
                )
                vm.message?.let {
                    Text(
                        it, color = c.error, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.error.copy(alpha = .12f)).padding(12.dp),
                    )
                }
                GradientButton(if (vm.busy) "Entrando…" else "Entrar", onClick = { vm.login(user, pass) }, enabled = canSend, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "¿No tienes cuenta? Pídesela a quien administra Lunna.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

/* ---------- Estado del reproductor ---------- */

@Stable
class PlayerUi {
    var mediaId by mutableStateOf<String?>(null)
    var title by mutableStateOf<String?>(null)
    var artist by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    val fraction get() = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
}

@Composable
fun rememberPlayerUi(player: Player?): PlayerUi {
    val ui = remember { PlayerUi() }
    DisposableEffect(player) {
        if (player == null) {
            ui.title = null; ui.mediaId = null; ui.playing = false
            return@DisposableEffect onDispose {}
        }
        fun sync() {
            ui.mediaId = player.currentMediaItem?.mediaId
            ui.title = player.mediaMetadata.title?.toString()
            ui.artist = player.mediaMetadata.artist?.toString()
            ui.playing = player.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = sync()
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            ui.pos = player.currentPosition
            ui.dur = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    return ui
}

/* ---------- Pantalla principal ---------- */

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = when (h) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
    val name = supabase.auth.currentUserOrNull()?.email?.substringBefore('@')?.replaceFirstChar { it.uppercase() }
    return if (name != null) "$hello, $name" else hello
}

@Composable
fun MainScreen(vm: AppViewModel, player: Player?) {
    val ui = rememberPlayerUi(player)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf<Song?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(NightBrush)) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = c.onBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                Column {
                    if (player != null && ui.title != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MiniPlayer(ui, player, onOpen = { showPlayer = true }, modifier = Modifier.widthIn(max = 860.dp))
                        }
                    }
                    // En tablet las dos pestañas quedan juntas al centro en vez de irse a las orillas.
                    Box(Modifier.fillMaxWidth().background(Night.copy(alpha = .94f)), contentAlignment = Alignment.Center) {
                        NavigationBar(Modifier.widthIn(max = 480.dp), containerColor = Color.Transparent, tonalElevation = 0.dp) {
                            val colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.primary, selectedTextColor = c.primary, indicatorColor = c.primary.copy(alpha = .16f),
                                unselectedIconColor = c.onSurfaceVariant, unselectedTextColor = c.onSurfaceVariant,
                            )
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(LunnaIcons.Library, null) }, label = { Text("Biblioteca") }, colors = colors)
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(LunnaIcons.Queue, null) }, label = { Text("Playlists") }, colors = colors)
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 860.dp)) {
                    if (tab == 0) Library(vm, player, ui.mediaId, onAdd = { adding = it }) else Playlists(vm, player, ui.mediaId)
                }
            }
        }
    }
    adding?.let { song -> AddToPlaylistDialog(vm, song, onDismiss = { adding = null }) }
    if (showPlayer && player != null && ui.title != null) FullPlayer(ui, player, onDismiss = { showPlayer = false })
}

@Composable
fun SongRow(song: Song, current: Boolean, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp)).background(if (current) c.primary.copy(alpha = .14f) else Color.Transparent)
            .clickable(onClickLabel = "Reproducir", onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song.id, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (current) c.primary else c.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(if (current) "Sonando" else null, song.artist, song.durationMs?.let(::fmtTime)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

@Composable
private fun Header(eyebrow: String, title: String, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
        action()
    }
}

@Composable
fun Library(vm: AppViewModel, player: Player?, currentId: String?, onAdd: (Song) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Header(greeting(), "Tu biblioteca") {
            IconButton(onClick = { vm.logout(player) }) { Icon(LunnaIcons.Logout, "Cerrar sesión", tint = c.onSurfaceVariant) }
        }
        OutlinedTextField(
            value = vm.query, onValueChange = vm::search, singleLine = true,
            placeholder = { Text("Buscar canción o artista") },
            leadingIcon = { Icon(LunnaIcons.Search, null) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) IconButton(onClick = { vm.search("") }) { Icon(LunnaIcons.Close, "Borrar búsqueda") }
            },
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.primary, unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = c.surfaceVariant, unfocusedContainerColor = c.surfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        if (vm.songs.isEmpty()) {
            if (vm.query.isBlank()) EmptyState(LunnaIcons.MusicNote, "Todavía no hay canciones", "Cuando se suban canciones aparecerán aquí.")
            else EmptyState(LunnaIcons.Search, "Sin resultados", "Prueba con otra palabra del título o del artista.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(vm.songs, key = { _, s -> s.id }) { i, s ->
                SongRow(s, current = s.id == currentId, onClick = { vm.play(player, vm.songs, i) }) {
                    IconButton(onClick = { onAdd(s) }) { Icon(LunnaIcons.PlaylistAdd, "Agregar a playlist", tint = c.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun Playlists(vm: AppViewModel, player: Player?, currentId: String?) {
    val open = vm.openPlaylist
    if (open == null) PlaylistList(vm) else PlaylistDetail(vm, open, player, currentId)
}

@Composable
private fun PlaylistList(vm: AppViewModel) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Header("${vm.playlists.size} ${if (vm.playlists.size == 1) "lista" else "listas"}", "Tus playlists") {
            FilledTonalButton(onClick = { creating = true }) {
                Icon(LunnaIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nueva")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (vm.playlists.isEmpty()) {
            EmptyState(LunnaIcons.Queue, "Aún no tienes playlists", "Crea una con «Nueva» y agrégale canciones desde la Biblioteca.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
            items(vm.playlists, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).clip(RoundedCornerShape(16.dp))
                        .clickable(onClickLabel = "Abrir") { vm.open(p) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(p.id, Modifier.size(60.dp), corner = 14.dp, icon = LunnaIcons.Queue)
                    Spacer(Modifier.width(14.dp))
                    Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (creating) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nueva playlist") },
            text = {
                LunnaField(
                    name, { name = it }, "Nombre", LunnaIcons.Queue,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) { vm.createPlaylist(name); creating = false } }),
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.createPlaylist(name); creating = false }, enabled = name.isNotBlank()) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PlaylistDetail(vm: AppViewModel, open: Playlist, player: Player?, currentId: String?) {
    var confirmDelete by remember(open.id) { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.open(null) }) { Icon(LunnaIcons.Back, "Volver a playlists") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = true }) { Icon(LunnaIcons.Delete, "Borrar playlist", tint = c.onSurfaceVariant) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(open.id, Modifier.size(168.dp), corner = 28.dp, icon = LunnaIcons.Queue)
                Spacer(Modifier.height(16.dp))
                Text(open.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                Text(
                    "${vm.playlistSongs.size} ${if (vm.playlistSongs.size == 1) "canción" else "canciones"}",
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                GradientButton("Reproducir", onClick = { vm.play(player, vm.playlistSongs, 0) },
                    enabled = vm.playlistSongs.isNotEmpty(), icon = LunnaIcons.Play)
                Spacer(Modifier.height(8.dp))
            }
            if (vm.playlistSongs.isEmpty()) {
                EmptyState(LunnaIcons.PlaylistAdd, "Playlist vacía", "Agrega canciones desde la Biblioteca con el botón de agregar.")
            }
        }
        itemsIndexed(vm.playlistSongs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, current = s.id == currentId, onClick = { vm.play(player, vm.playlistSongs, i) }) {
                IconButton(onClick = { vm.removeFromPlaylist(open, s) }) { Icon(LunnaIcons.Close, "Quitar de la playlist", tint = c.onSurfaceVariant) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar «${open.name}»?") },
            text = { Text("Se borra la lista, no las canciones.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePlaylist(open) }) { Text("Borrar", color = c.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun AddToPlaylistDialog(vm: AppViewModel, song: Song, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar a playlist") },
        text = {
            if (vm.playlists.isEmpty()) Text("Primero crea una playlist en la pestaña Playlists.")
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(vm.playlists, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { vm.addToPlaylist(p, song); onDismiss() }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(p.id, Modifier.size(40.dp), corner = 10.dp, icon = LunnaIcons.Queue)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

/* ---------- Reproductor ---------- */

@Composable
fun MiniPlayer(ui: PlayerUi, player: Player, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp)).background(Brush.horizontalGradient(listOf(SurfaceHi, c.surface)))
            .border(1.dp, c.outline, RoundedCornerShape(20.dp))
            .clickable(onClickLabel = "Abrir reproductor", onClick = onOpen),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(ui.mediaId ?: "", Modifier.size(46.dp), corner = 10.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.title ?: "", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ui.artist?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(AccentBrush)
                    .clickable(onClickLabel = if (ui.playing) "Pausar" else "Reproducir") { if (ui.playing) player.pause() else player.play() }
                    .semantics { contentDescription = if (ui.playing) "Pausar" else "Reproducir" },
                contentAlignment = Alignment.Center,
            ) { Icon(if (ui.playing) LunnaIcons.Pause else LunnaIcons.Play, null, tint = OnAccent) }
            IconButton(onClick = { player.seekToNext() }) { Icon(LunnaIcons.Next, "Siguiente") }
        }
        LinearProgressIndicator(
            progress = { ui.fraction }, modifier = Modifier.fillMaxWidth().height(3.dp),
            color = c.primary, trackColor = Color.Transparent, gapSize = 0.dp, drawStopIndicator = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(ui: PlayerUi, player: Player, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var drag by remember { mutableStateOf<Float?>(null) }
    val shown = drag ?: ui.fraction
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Night,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Reproduciendo", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Artwork(ui.mediaId ?: "", Modifier.widthIn(max = 360.dp).fillMaxWidth(.82f).aspectRatio(1f), corner = 32.dp)
            Spacer(Modifier.height(28.dp))
            Text(ui.title ?: "", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ui.artist?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center) }
            Spacer(Modifier.height(20.dp))
            // Solo se busca al soltar: cada salto es una lectura nueva en R2.
            Slider(
                value = shown, onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { player.seekTo((it * ui.dur).toLong()) }; drag = null },
                enabled = ui.dur > 0,
                colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.outline),
                modifier = Modifier.widthIn(max = 520.dp).semantics { contentDescription = "Posición de la canción" },
            )
            Row(Modifier.widthIn(max = 520.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime((shown * ui.dur).toLong()), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Text(fmtTime(ui.dur), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { player.seekToPrevious() }, Modifier.size(56.dp)) {
                    Icon(LunnaIcons.Previous, "Anterior", Modifier.size(36.dp))
                }
                Box(
                    Modifier.size(80.dp).clip(CircleShape).background(AccentBrush)
                        .clickable(onClickLabel = if (ui.playing) "Pausar" else "Reproducir") { if (ui.playing) player.pause() else player.play() }
                        .semantics { contentDescription = if (ui.playing) "Pausar" else "Reproducir" },
                    contentAlignment = Alignment.Center,
                ) { Icon(if (ui.playing) LunnaIcons.Pause else LunnaIcons.Play, null, tint = OnAccent, modifier = Modifier.size(42.dp)) }
                IconButton(onClick = { player.seekToNext() }, Modifier.size(56.dp)) {
                    Icon(LunnaIcons.Next, "Siguiente", Modifier.size(36.dp))
                }
            }
        }
    }
}
