package com.vivacatamayo.radio

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.TimeZone

class MainActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private lateinit var status: TextView
    private lateinit var nowPlaying: TextView
    private lateinit var metadataState: TextView
    private lateinit var playButton: Button
    private lateinit var currentProgram: TextView
    private lateinit var recentTracksView: TextView
    private lateinit var sleepButton: Button
    private lateinit var equalizer: TextView
    private val recentTracks = mutableListOf<String>()
    private var sleepMinutes = 0
    private var equalizerAnimator: ObjectAnimator? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = renderState()
        override fun onPlaybackStateChanged(playbackState: Int) = renderState()

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val title = mediaMetadata.title?.toString()?.trim().orEmpty()
            if (title.isNotBlank() && title != RadioConfig.STATION_NAME) acceptDynamicTitle(title)
        }

        override fun onMetadata(metadata: Metadata) {
            for (i in 0 until metadata.length()) {
                val entry = metadata[i]
                if (entry is IcyInfo) {
                    val title = entry.title?.trim().orEmpty()
                    if (title.isNotBlank()) acceptDynamicTitle(title)
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            status.text = "Sin conexión · toca para reintentar"
            playButton.text = "↻"
            setEqualizerActive(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = VCT_BLUE
        window.navigationBarColor = VCT_BLUE
        loadRecentTracks()
        setContentView(buildUi())
        renderRecentTracks()
        renderCurrentProgram()
        renderSleepButton()
    }

    override fun onStart() {
        super.onStart()
        renderCurrentProgram()
        connectController()
    }

    override fun onStop() {
        equalizerAnimator?.cancel()
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun buildUi(): ScrollView {
        val tablet = resources.configuration.smallestScreenWidthDp >= 600
        val horizontalPadding = if (tablet) dp(120) else dp(18)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(VCT_BLUE)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(horizontalPadding, dp(22), horizontalPadding, dp(32))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 17, 68), Color.rgb(6, 42, 130), Color.rgb(0, 128, 214))
            )
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandRow.addView(text("VIVA CATAMAYO", 17, true, Color.WHITE), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        brandRow.addView(livePill())
        root.addView(brandRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(space(16))

        val hero = panel(30).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(230, 5, 27, 104), Color.argb(225, 7, 83, 201))
            ).apply {
                setStroke(dp(1), Color.argb(105, 86, 235, 255))
                cornerRadius = dp(30).toFloat()
            }
            elevation = dp(8).toFloat()
        }
        hero.addView(text("RADIO WEB", 12, true, VCT_CYAN).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
        })
        hero.addView(space(6))
        hero.addView(text("VivaCatamayo", 32, true, Color.WHITE).apply { gravity = Gravity.CENTER })
        hero.addView(text("Tu voz digital · Catamayo, Loja", 14, false, Color.argb(215, 255, 255, 255)).apply { gravity = Gravity.CENTER })
        hero.addView(space(18))

        equalizer = text("▂  ▄  ▆  █  ▆  ▄  ▂", 20, true, VCT_CYAN).apply {
            gravity = Gravity.CENTER
            alpha = 0.55f
        }
        hero.addView(equalizer)
        hero.addView(space(14))

        playButton = Button(this).apply {
            text = "▶"
            textSize = 36f
            setTextColor(VCT_BLUE_MID)
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(4), Color.argb(180, 0, 221, 251))
            }
            elevation = dp(10).toFloat()
            setOnClickListener {
                val p = controller
                if (p == null) {
                    connectController()
                    return@setOnClickListener
                }
                if (p.isPlaying) {
                    p.pause()
                } else {
                    if (p.playbackState == Player.STATE_IDLE) p.prepare()
                    p.play()
                }
            }
        }
        hero.addView(playButton, LinearLayout.LayoutParams(dp(112), dp(112)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        hero.addView(space(14))

        status = text("Conectando…", 14, true, VCT_CYAN).apply { gravity = Gravity.CENTER }
        hero.addView(status)
        root.addView(hero)
        root.addView(space(14))

        val nowCard = panel(22).apply {
            addView(sectionLabel("AHORA AL AIRE"))
            addView(space(6))
            nowPlaying = text(RadioConfig.STATION_NAME, 21, true, Color.WHITE)
            addView(nowPlaying)
            addView(space(5))
            metadataState = text("Esperando información del servidor…", 12, false, Color.argb(185, 255, 255, 255))
            addView(metadataState)
        }
        root.addView(nowCard)
        root.addView(space(12))

        val programCard = panel(22).apply {
            addView(sectionLabel("EN ESTE MOMENTO"))
            addView(space(5))
            currentProgram = text("VivaCatamayo Radio Web", 17, true, Color.WHITE)
            addView(currentProgram)
            addView(space(13))
            addView(divider())
            addView(space(12))
            addView(text("06h00–09h00  ·  Amanecer Musical\n18h00  ·  Catamayo de Antaño", 14, false, Color.argb(220, 255, 255, 255)))
        }
        root.addView(programCard)
        root.addView(space(12))

        val recentCard = panel(22).apply {
            addView(sectionLabel("RECIENTEMENTE EN LA RADIO"))
            addView(space(7))
            recentTracksView = text("Esperando metadata…", 13, false, Color.argb(220, 255, 255, 255))
            recentTracksView.setLineSpacing(dp(3).toFloat(), 1f)
            addView(recentTracksView)
        }
        root.addView(recentCard)
        root.addView(space(14))

        sleepButton = primaryUtilityButton("🌙  Temporizador · OFF") { cycleSleepTimer() }
        root.addView(sleepButton)
        root.addView(space(10))

        root.addView(actionRow(
            "💬  Reporte" to { openWhatsApp() },
            "🌐  Web" to { openUrl(RadioConfig.WEBSITE_URL) }
        ))
        root.addView(space(8))
        root.addView(actionRow(
            "f  Facebook" to { openUrl(RadioConfig.FACEBOOK_URL) },
            "📻  Radio Web" to { openUrl(RadioConfig.RADIO_PAGE_URL) }
        ))
        root.addView(space(8))
        root.addView(primaryUtilityButton("↗  Compartir VivaCatamayo Radio") { shareStation() })
        root.addView(space(24))

        root.addView(text("VivaCatamayo Radio v1.3.0", 11, true, Color.argb(180, 255, 255, 255)).apply { gravity = Gravity.CENTER })
        root.addView(text("Señal online 24/7 · ${RadioConfig.LOCATION}", 10, false, Color.argb(145, 255, 255, 255)).apply { gravity = Gravity.CENTER })
        return scroll
    }

    private fun panel(radius: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(16), dp(17), dp(16))
        background = GradientDrawable().apply {
            setColor(Color.argb(42, 255, 255, 255))
            setStroke(dp(1), Color.argb(72, 255, 255, 255))
            cornerRadius = dp(radius).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun livePill() = text("●  EN VIVO", 11, true, Color.WHITE).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(7), dp(12), dp(7))
        background = GradientDrawable().apply {
            setColor(Color.argb(185, 255, 80, 105))
            cornerRadius = dp(20).toFloat()
        }
    }

    private fun sectionLabel(value: String) = text(value, 11, true, VCT_CYAN).apply {
        letterSpacing = 0.10f
    }

    private fun divider() = TextView(this).apply {
        background = GradientDrawable().apply { setColor(Color.argb(55, 255, 255, 255)) }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun primaryUtilityButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 15f
        setTextColor(Color.WHITE)
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            setColor(Color.argb(48, 255, 255, 255))
            setStroke(dp(1), Color.argb(105, 255, 255, 255))
            cornerRadius = dp(24).toFloat()
        }
        setOnClickListener { action() }
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)) }

    private fun actionRow(left: Pair<String, () -> Unit>, right: Pair<String, () -> Unit>) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(smallActionButton(left.first, left.second), LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginEnd = dp(4) })
        addView(smallActionButton(right.first, right.second), LinearLayout.LayoutParams(0, dp(54), 1f).apply { marginStart = dp(4) })
    }

    private fun smallActionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            setColor(Color.argb(42, 255, 255, 255))
            setStroke(dp(1), Color.argb(92, 255, 255, 255))
            cornerRadius = dp(22).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun connectController() {
        if (controllerFuture != null) return
        if (::status.isInitialized) status.text = "Conectando con VivaCatamayo…"
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                renderState()
            }.onFailure {
                status.text = "No se pudo iniciar la señal"
                setEqualizerActive(false)
                Toast.makeText(this, "Error al iniciar el reproductor", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renderState() {
        val p = controller ?: return
        when {
            p.isPlaying -> {
                status.text = "● Señal en vivo · reproduciendo"
                playButton.text = "❚❚"
                setEqualizerActive(true)
            }
            p.playbackState == Player.STATE_BUFFERING -> {
                status.text = "Conectando con VivaCatamayo…"
                playButton.text = "…"
                setEqualizerActive(true)
            }
            else -> {
                status.text = "Lista para escuchar"
                playButton.text = "▶"
                setEqualizerActive(false)
            }
        }
    }

    private fun setEqualizerActive(active: Boolean) {
        if (!::equalizer.isInitialized) return
        equalizerAnimator?.cancel()
        equalizerAnimator = null
        if (active) {
            equalizerAnimator = ObjectAnimator.ofFloat(equalizer, "alpha", 0.32f, 1f).apply {
                duration = 620
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        } else {
            equalizer.alpha = 0.50f
        }
    }

    private fun acceptDynamicTitle(title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        nowPlaying.text = clean
        metadataState.text = "Metadata en vivo recibida del servidor"
        if (recentTracks.firstOrNull() != clean) {
            recentTracks.remove(clean)
            recentTracks.add(0, clean)
            while (recentTracks.size > 5) recentTracks.removeAt(recentTracks.lastIndex)
            saveRecentTracks()
            renderRecentTracks()
        }
    }

    private fun loadRecentTracks() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT_TRACKS, "").orEmpty()
        if (raw.isNotBlank()) recentTracks.addAll(raw.split(TRACK_SEPARATOR).filter { it.isNotBlank() }.take(5))
        sleepMinutes = prefs.getInt(KEY_SLEEP_MINUTES, 0)
    }

    private fun saveRecentTracks() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_RECENT_TRACKS, recentTracks.joinToString(TRACK_SEPARATOR))
            .apply()
    }

    private fun renderRecentTracks() {
        if (!::recentTracksView.isInitialized) return
        recentTracksView.text = if (recentTracks.isEmpty()) {
            "Los títulos aparecerán aquí cuando el servidor los envíe."
        } else {
            recentTracks.mapIndexed { index, title -> "${index + 1}.  $title" }.joinToString("\n")
        }
    }

    private fun renderCurrentProgram() {
        if (!::currentProgram.isInitialized) return
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("America/Guayaquil"))
        val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        currentProgram.text = when (minutes) {
            in 360 until 540 -> "Amanecer Musical · 06h00–09h00"
            in 1080 until 1140 -> "Catamayo de Antaño · desde las 18h00"
            else -> "VivaCatamayo Radio Web · programación continua"
        }
    }

    private fun cycleSleepTimer() {
        sleepMinutes = when (sleepMinutes) {
            0 -> 15
            15 -> 30
            30 -> 60
            else -> 0
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SLEEP_MINUTES, sleepMinutes).apply()
        val intent = Intent(this, PlaybackService::class.java)
        if (sleepMinutes == 0) {
            intent.action = PlaybackService.ACTION_CANCEL_SLEEP_TIMER
            startService(intent)
            Toast.makeText(this, "Temporizador desactivado", Toast.LENGTH_SHORT).show()
        } else {
            intent.action = PlaybackService.ACTION_SET_SLEEP_TIMER
            intent.putExtra(PlaybackService.EXTRA_SLEEP_MINUTES, sleepMinutes)
            startService(intent)
            Toast.makeText(this, "La radio se pausará en $sleepMinutes minutos", Toast.LENGTH_SHORT).show()
        }
        renderSleepButton()
    }

    private fun renderSleepButton() {
        if (!::sleepButton.isInitialized) return
        sleepButton.text = if (sleepMinutes == 0) "🌙  Temporizador · OFF" else "🌙  Temporizador · $sleepMinutes min"
    }

    private fun openWhatsApp() {
        val message = URLEncoder.encode(
            "Hola VivaCatamayoTV, quiero enviar un reporte desde la app VivaCatamayo Radio.",
            StandardCharsets.UTF_8.toString()
        )
        openUrl("https://wa.me/${RadioConfig.WHATSAPP_NUMBER}?text=$message")
    }

    private fun shareStation() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Escucha ${RadioConfig.STATION_NAME}: ${RadioConfig.RADIO_PAGE_URL}")
        }, "Compartir VivaCatamayo Radio"))
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show() }
    }

    private fun text(value: String, size: Int, bold: Boolean, color: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        includeFontPadding = false
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun space(h: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "vct_radio_prefs"
        private const val KEY_RECENT_TRACKS = "recent_tracks"
        private const val KEY_SLEEP_MINUTES = "sleep_minutes"
        private const val TRACK_SEPARATOR = "\u001E"
        private val VCT_BLUE = Color.rgb(6, 25, 93)
        private val VCT_BLUE_MID = Color.rgb(11, 87, 208)
        private val VCT_CYAN = Color.rgb(0, 221, 251)
    }
}
