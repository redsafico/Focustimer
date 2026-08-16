package com.example.focustimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.text.InputType
import android.graphics.drawable.GradientDrawable

class FocusMonitorService : Service() {

    companion object {
        const val ACTION_APP_ENTERED = "com.example.focustimer.ACTION_APP_ENTERED"
        const val ACTION_APP_LEFT = "com.example.focustimer.ACTION_APP_LEFT"
        const val EXTRA_PACKAGE = "com.example.focustimer.EXTRA_PACKAGE"

        private const val CHANNEL_ID = "focus_timer_monitor"
        private const val NOTIFICATION_ID = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    private var activePackage: String? = null
    private var promptPackage: String? = null
    private var reason = ""
    private var sessionStart = 0L
    private var allowedMs = 0L

    private var countdownTask: Runnable? = null
    private var vibrationTask: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }

        if (Settings.canDrawOverlays(this)) {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APP_ENTERED -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return START_STICKY

                if (activePackage != null && activePackage != pkg) {
                    stopSession()
                }

                if (
                    Settings.canDrawOverlays(this) &&
                    pkg in AppPrefs.getSelectedPackages(this) &&
                    activePackage == null &&
                    promptPackage != pkg
                ) {
                    promptPackage = pkg
                    showPrompt(pkg)
                }
            }

            ACTION_APP_LEFT -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE)

                if (
                    activePackage != null &&
                    pkg != null &&
                    pkg != activePackage &&
                    isRealLaunchableApp(pkg)
                ) {
                    stopSession()
                }
            }
        }

        return START_STICKY
    }

    private fun showPrompt(targetPackage: String) {
        removeOverlay()

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(targetPackage, 0)
            ).toString()
        } catch (_: Exception) { targetPackage }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(22))
            background = GradientDrawable().apply {
                setColor(Color.rgb(248, 248, 250))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0x22000000)
            }
            elevation = dp(10).toFloat()
        }

        val title = TextView(this).apply {
            text = "Antes de entrar en $label"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(20, 20, 20))
            setPadding(0, 0, 0, dp(12))
        }

        val desc = TextView(this).apply {
            text = "Escribe por qué vas a entrar y cuánto tiempo necesitas."
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(14))
        }

        val reasonInput = EditText(this).apply {
            hint = "¿Para qué vas a entrar?"
            textSize = 16f
            minLines = 2
            maxLines = 3
            gravity = Gravity.TOP or Gravity.START
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x33000000)
            }
        }

        val minutesInput = EditText(this).apply {
            hint = "Tiempo en minutos"
            textSize = 16f
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("10")
            setSelectAllOnFocus(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), 0x33000000)
            }
        }

        val start = Button(this).apply {
            text = "Iniciar temporizador"
            setOnClickListener {
                val why = reasonInput.text.toString().trim()
                val mins = minutesInput.text.toString().trim().toIntOrNull()

                if (why.isEmpty()) {
                    reasonInput.error = "Escribe el motivo"
                    reasonInput.requestFocus()
                    showKeyboard(reasonInput)
                    return@setOnClickListener
                }

                if (mins == null || mins <= 0) {
                    minutesInput.error = "Introduce un tiempo válido"
                    minutesInput.requestFocus()
                    showKeyboard(minutesInput)
                    return@setOnClickListener
                }

                removeOverlay()
                promptPackage = null
                startSession(targetPackage, why, mins)
            }
        }

        val cancel = Button(this).apply {
            text = "Cancelar"
            setOnClickListener {
                removeOverlay()
                promptPackage = null
            }
        }

        box.addView(title)
        box.addView(desc)
        box.addView(reasonInput)
        box.addView(Space(this).apply { minimumHeight = dp(10) })
        box.addView(minutesInput)
        box.addView(Space(this).apply { minimumHeight = dp(14) })
        box.addView(start)
        box.addView(cancel)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = dp(60)
        params.softInputMode =
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE

        windowManager?.addView(box, params)
        overlay = box

        reasonInput.requestFocus()
        box.postDelayed({ showKeyboard(reasonInput) }, 300)
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun startSession(pkg: String, why: String, minutes: Int) {
        activePackage = pkg
        reason = why
        sessionStart = System.currentTimeMillis()
        allowedMs = minutes * 60_000L

        showTimer()
        startCountdown()
        startVibration()
    }

    private fun showTimer() {
        removeOverlay()

        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = GradientDrawable().apply {
                setColor(0xE61B1B1F.toInt())
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0x33FFFFFF)
            }
            elevation = dp(8).toFloat()
        }

        val timer = TextView(this).apply {
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }

        val info = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xD9FFFFFF.toInt())
            maxLines = 4
        }

        box.addView(timer)
        box.addView(info)

        val params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = dp(70)

        var x0 = 0
        var y0 = 0
        var tx0 = 0f
        var ty0 = 0f

        box.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    x0 = params.x
                    y0 = params.y
                    tx0 = e.rawX
                    ty0 = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = x0 + (e.rawX - tx0).toInt()
                    params.y = y0 + (e.rawY - ty0).toInt()
                    try { windowManager?.updateViewLayout(box, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }

        box.tag = Pair(timer, info)
        windowManager?.addView(box, params)
        overlay = box
    }

    private fun startCountdown() {
        countdownTask?.let { handler.removeCallbacks(it) }

        val task = object : Runnable {
            override fun run() {
                if (activePackage == null) return

                val elapsed = System.currentTimeMillis() - sessionStart
                val remaining = allowedMs - elapsed

                val pair = overlay?.tag as? Pair<*, *>
                val timer = pair?.first as? TextView
                val info = pair?.second as? TextView

                if (remaining >= 0) {
                    timer?.text = formatTime(remaining)
                    info?.text = "Objetivo: $reason\\nUso: ${formatTime(elapsed)}"
                } else {
                    timer?.text = "+${formatTime(-remaining)}"
                    info?.text = "⚠ TIEMPO EXCEDIDO\\nObjetivo: $reason\\nUso: ${formatTime(elapsed)}"
                }

                handler.postDelayed(this, 500)
            }
        }

        countdownTask = task
        handler.post(task)
    }

    private fun startVibration() {
        vibrationTask?.let { handler.removeCallbacks(it) }

        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        val task = object : Runnable {
            override fun run() {
                if (activePackage == null) return

                val elapsed = System.currentTimeMillis() - sessionStart
                if (elapsed >= allowedMs) {
                    if (Build.VERSION.SDK_INT >= 26) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                500,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(500)
                    }
                }

                handler.postDelayed(this, 10_000)
            }
        }

        vibrationTask = task
        handler.postDelayed(task, allowedMs.coerceAtLeast(1L))
    }

    private fun isRealLaunchableApp(pkg: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(pkg)
            }

            packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun stopSession() {
        activePackage = null
        reason = ""
        sessionStart = 0L
        allowedMs = 0L

        countdownTask?.let { handler.removeCallbacks(it) }
        vibrationTask?.let { handler.removeCallbacks(it) }
        countdownTask = null
        vibrationTask = null

        removeOverlay()
    }

    private fun removeOverlay() {
        overlay?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    private fun formatTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60

        return if (h > 0)
            "%02d:%02d:%02d".format(h, m, s)
        else
            "%02d:%02d".format(m, s)
    }

    private fun notification(): android.app.Notification {
        return if (Build.VERSION.SDK_INT >= 26) {
            android.app.Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Focus Timer activo")
                .setContentText("Temporizador en segundo plano")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
                .setContentTitle("Focus Timer activo")
                .setContentText("Temporizador en segundo plano")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(true)
                .build()
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Timer",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopSession()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
