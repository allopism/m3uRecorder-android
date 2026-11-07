package com.m3urecorder.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.m3urecorder.app.data.ApiClient
import com.m3urecorder.app.ui.theme.AppTheme

/** Cliente de la API para las imágenes (dirección y token actuales). */
val LocalApiClient = staticCompositionLocalOf<ApiClient?> { null }

/** Cargador de imágenes compartido: PNG/JPG y también logos SVG. */
val LocalImageLoader = staticCompositionLocalOf<ImageLoader?> { null }

fun buildImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(OkHttpNetworkFetcherFactory())
        add(SvgDecoder.Factory())
    }
    .crossfade(true)
    .build()

/**
 * Imagen de nuestro servidor. Envía el token (si lo hay) y, si no carga, muestra [fallback].
 */
@Composable
fun ServerImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current
    val api = LocalApiClient.current
    val loader = LocalImageLoader.current
    var failed by remember(url) { mutableStateOf(false) }

    if (api == null || loader == null || failed) {
        fallback()
        return
    }
    val request = remember(url) {
        val headers = NetworkHeaders.Builder().apply {
            api.authHeadersFor(url).forEach { (k, v) -> set(k, v) }
        }.build()
        ImageRequest.Builder(context).data(url).httpHeaders(headers).build()
    }
    AsyncImage(
        model = request,
        imageLoader = loader,
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
        onError = { failed = true }
    )
}

// ── Monogramas: canales sin logo ─────────────

private val MONO_HUES = listOf(215f, 165f, 22f, 275f, 340f, 42f, 190f, 125f, 250f, 5f)
private val SKIP_WORDS = Regex("^(hd|sd|fhd|uhd|4k|the|la|el|los|las|de|del|tv)$", RegexOption.IGNORE_CASE)

/** Mismo color para el mismo nombre (igual que la web). */
fun hueFor(name: String): Float {
    var h = 0L
    name.codePoints().forEach { cp -> h = (h * 31 + cp) and 0xFFFFFFFFL }
    return MONO_HUES[(h % MONO_HUES.size).toInt()]
}

/** "Radio Jazz 101" → "RJ"; "Ciencia TV" → "CT" (si quitar palabras deja solo una, no se quitan). */
fun initials(name: String): String {
    val words = name.replace(Regex("[(\\[].*?[)\\]]"), " ")
        .split(Regex("[\\s\\-_.·|/]+")).filter { it.isNotBlank() }
    val useful = words.filterNot { SKIP_WORDS.matches(it) }
    val picked = (if (useful.size >= 2) useful else words).take(2)
    return picked.joinToString("") { w -> if (w.all(Char::isDigit)) w.take(2) else w.take(1) }
        .uppercase().take(3).ifEmpty { "?" }
}

@Composable
fun Monogram(name: String, modifier: Modifier = Modifier, fontSizeSp: Float = 15f) {
    val c = AppTheme.colors
    val hue = hueFor(name)
    Box(
        modifier = modifier.background(Color.hsl(hue, c.monoSaturation, c.monoBgLightness)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials(name),
            color = Color.hsl(hue, c.monoSaturation, c.monoTextLightness),
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp
        )
    }
}

/** Logo del canal en un recuadro; si no hay logo (o falla), su monograma. */
@Composable
fun ChannelLogo(
    logoId: String,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(corner)
    val boxModifier = modifier
        .size(size)
        .clip(shape)
        .border(1.dp, c.border, shape)
    val mono = @Composable { Monogram(name, Modifier.fillMaxSize(), fontSizeSp = (size.value * 0.32f).coerceIn(10f, 26f)) }
    val api = LocalApiClient.current
    if (logoId.isBlank() || api == null) {
        Box(boxModifier) { mono() }
        return
    }
    Box(boxModifier.background(c.logoBg), contentAlignment = Alignment.Center) {
        ServerImage(
            url = api.logoUrl(logoId),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(size * 0.12f),
            fallback = { mono() }
        )
    }
}

/** Recuadro con un icono (p. ej. enlace pegado a mano, sin canal). */
@Composable
fun IconTile(icon: ImageVector, size: Dp, modifier: Modifier = Modifier, tint: Color = AppTheme.colors.text2) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(c.surface2)
            .border(1.dp, c.border, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.45f))
    }
}
