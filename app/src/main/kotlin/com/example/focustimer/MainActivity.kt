package com.example.focustimer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 24)
            setBackgroundColor(Color.rgb(250, 248, 252))
        }

        val title = TextView(this).apply {
            text = "Focus Timer"
            textSize = 30f
            setTextColor(Color.rgb(30, 28, 34))
        }

        val subtitle = TextView(this).apply {
            text = "Elige las aplicaciones que quieres controlar."
            textSize = 16f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 20)
        }

        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        val permissions = Button(this).apply {
            text = "Configurar permisos"
            setOnClickListener { openAccessibilitySettings() }
        }

        val overlay = Button(this).apply {
            text = "Permiso para mostrar sobre otras apps"
            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            addView(list)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(permissions)
        root.addView(overlay)

        val appsTitle = TextView(this).apply {
            text = "Aplicaciones"
            textSize = 20f
            setTextColor(Color.rgb(30, 28, 34))
            setPadding(0, 24, 0, 10)
        }

        root.addView(appsTitle)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        loadApps()
        updateStatus()
    }

    private fun loadApps() {
        list.removeAllViews()

        val pm = packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy {
                it.loadLabel(pm).toString().lowercase()
            }

        val selected = AppPrefs.getSelectedPackages(this).toMutableSet()

        apps.forEach { info ->
            val pkg = info.activityInfo.packageName
            val appInfo = info.activityInfo.applicationInfo

            val check = CheckBox(this).apply {
                text = info.loadLabel(pm).toString()
                textSize = 17f
                gravity = Gravity.CENTER_VERTICAL
                isChecked = pkg in selected
                setPadding(0, 8, 0, 8)

                setCompoundDrawablesWithIntrinsicBounds(
                    appInfo.loadIcon(pm),
                    null, null, null
                )
                compoundDrawablePadding = 18

                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(pkg) else selected.remove(pkg)
                    AppPrefs.setSelectedPackages(this@MainActivity, selected)
                }
            }

            list.addView(
                check,
                LinearLayout.LayoutParams(-1, 58)
            )
        }
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessibilityOk = isAccessibilityEnabled()

        status.text =
            "Estado\\n" +
            "${if (overlayOk) "✓" else "✗"} Superposición\\n" +
            "${if (accessibilityOk) "✓" else "✗"} Accesibilidad"

        status.setTextColor(
            if (overlayOk && accessibilityOk)
                Color.rgb(30, 120, 70)
            else
                Color.rgb(170, 70, 40)
        )
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(
            this,
            FocusAccessibilityService::class.java
        )

        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.split(':').any {
            ComponentName.unflattenFromString(it) == expected
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {}
    }
}
