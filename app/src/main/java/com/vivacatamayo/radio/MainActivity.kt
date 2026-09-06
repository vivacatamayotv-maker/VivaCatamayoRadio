package com.vivacatamayo.radio

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
    private lateinit var playButton: Button
    private lateinit var currentProgram: TextView
    private lateinit var recentTracksView: TextView
    private lateinit var sleepButton: Button
    private val recentTracks = mutableListOf<String>()
    private var sleepMinutes = 0

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
            playButton.text = "▶  REINTENTAR"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(6, 25, 93)
        window.navigationBarColor = Color.rgb(6, 25, 93)
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
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(28), dp(20), dp(30))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(4, 20, 83), Color.rgb(7, 67, 190), Color.rgb(0, 187, 242))
            )
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(text("●  EN VIVO · 24/7", 15, true, Color.rgb(255, 92, 112)).apply { gravity = Gravity.CENTER })
        root.addView(space(18))
        root.addView(text("VIVA CATAMAYO", 34, true, Color.WHITE).apply { gravity = Gravity.CENTER })
        root.addView(text("TV · Tu voz digital", 18, false, Color.WHITE).apply { gravity = Gravity.CENTER })
        root.addView(text(RadioConfig.LOCATION, 12, false, Color.argb(190, 255, 255, 255)).apply { gravity = Gravity.CENTER })
        root.addView(space(24))

        status = text("Conectando…", 14, true, Color.rgb(88, 235, 255)).apply { gravity = Gravity.CENTER }
        root.addView(status)
        root.addView(space(12))

        playButton = Button(this).apply {
            text = "▶  ESCUCHAR EN VIVO"
            textSize = 20f
            setTextColor(Color.rgb(9, 61, 190))
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(34).toFloat()
            }
            setPadding(dp(18), dp(16), dp(18), dp(16))
            setOnClickListener {
                val p = controller
                if (p == null) {
                    connectController()
                    return@setOnClickListener
                }
                if (p.isPlaying) p.pause() else {
                    if (p.playbackState == Player.STATE_IDLE) p.prepare()
                    p.play()
                }
            }
        }
        root.addView(playButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))
        root.addView(space(24))

        root.addView(sectionLabel("AHORA AL AIRE"))
        nowPlaying = text(RadioConfig.STATION_NAME, 21, true, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        }
        root.addView(nowPlaying)
        root.addView(space(22))

        root.addView(infoPanel().apply {
            addView(sectionLabel("PROGRAMACIÓN ACTUAL"))
            currentProgram = text("VivaCatamayo Radio Web", 16, true, Color.WHITE)
            addView(currentProgram)
            addView(space(12))
            addView(sectionLabel("PROGRAMACIÓN DESTACADA"))
            addView(text("06h00–09h00 · Amanecer Musical\n18h00 · Catamayo de Antaño", 14, false, Color.argb(220, 255, 255, 255)))
        })
        root.addView(space(12))

        root.addView(infoPanel().apply {
            addView(sectionLabel("ÚLTIMOS TEMAS DETECTADOS"))
            recentTracksView = text("Esperando metadata del servidor…", 13, false, Color.argb(220, 255, 255, 255))
            addView(recentTracksView)
        })
        root.addView(space(14))

        sleepButton = actionButton("🌙  Temporizador · OFF") { cycleSleepTimer() }
        root.addView(sleepButton)
        root.addView(space(10))
        root.addView(actionButton("💬  Enviar reporte") { openWhatsApp() })
        root.addView(space(10))
        root.addView(actionButton("🌐  vivacatamayo.com") { openUrl(RadioConfig.WEBSITE_URL) })
        root.addView(space(10))
        root.addView(actionButton("f  Facebook VivaCatamayoTV") { openUrl(RadioConfig.FACEBOOK_URL) })
        root.addView(space(10))
        root.addView(actionButton("📻  Página de Radio Web") { openUrl(RadioConfig.RADIO_PAGE_URL) })
        root.addView(space(10))
        root.addView(actionButton("↗  Compartir emisora") { shareStation() })
        root.addView(space(24))
        root.addView(text("VivaCatamayo Radio v1.2.0 · Señal online 24/7", 11, false, Color.argb(170, 255, 255, 255)).apply { gravity = Gravity.CENTER })
        return scroll
    }

    private fun infoPanel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = GradientDrawable().apply {
            setColor(Color.argb(38, 255, 255, 255))
            setStroke(dp(1), Color.argb(70, 255, 255, 255))
            cornerRadius = dp(18).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun sectionLabel(value: String) = text(value, 11, true, Color.rgb(130, 235, 255)).apply {
        letterSpacing = 0.08f
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = GradientDrawable().apply {
            setColor(Color.argb(45, 255, 255, 255))
            setStroke(dp(1), Color.argb(100, 255, 255, 255))
            cornerRadius = dp(26).toFloat()
        }
        setOnClickListener { action() }
    }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)) }

    private fun connectController() {
        if (controllerFuture != null) return
        status.text = "Conectando…"
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it
                it.addListener(listener)
                renderState()
            }.onFailure {
                status.text = "No se pudo conectar"
                Toast.makeText(this, "Error al iniciar el reproductor", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun renderState() {
        val p = controller ?: return
        when {
            p.isPlaying -> {
                status.text = "● Señal en vivo 24/7"
                playButton.text = "❚❚  PAUSAR"
            }
            p.playbackState == Player.STATE_BUFFERING -> {
                status.text = "Conectando con VivaCatamayo…"
                playButton.text = "…  CONECTANDO"
            }
            else -> {
                status.text = "Lista para escuchar"
                playButton.text = "▶  ESCUCHAR EN VIVO"
            }
        }
    }

    private fun acceptDynamicTitle(title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        nowPlaying.text = clean
        if (recentTracks.firstOrNull() != clean) {
            recentTracks.remove(clean)
            recentTracks.add(0, clean)
            while (recentTracks.size > 5) recentTracks.removeAt(recentTracks.lastIndex)
            saveRecentTracks()
            renderRecentTracks()
        }
    }

    private fun loadRecentTracks() {
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_RECENT_TRACKS, "").orEmpty()
        if (raw.isNotBlank()) recentTracks.addAll(raw.split(TRACK_SEPARATOR).filter { it.isNotBlank() }.take(5))
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
            recentTracks.mapIndexed { index, title -> "${index + 1}. $title" }.joinToString("\n")
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
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun space(h: Int) = TextView(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val PREFS = "vct_radio_prefs"
        private const val KEY_RECENT_TRACKS = "recent_tracks"
        private const val TRACK_SEPARATOR = "\u001E"
    }
}
