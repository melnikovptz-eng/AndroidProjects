package ru.voiceactivation.demo

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("voice_activation", MODE_PRIVATE) }
    private var selectedPackage: String?
        get() = prefs.getString(KEY_TARGET_PACKAGE, null)
        set(value) { prefs.edit().putString(KEY_TARGET_PACKAGE, value).apply() }

    private lateinit var selectedLabel: TextView
    private lateinit var engineStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        refreshSelection()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(22), dp(28), dp(22), dp(20))
        }

        val title = TextView(this).apply {
            text = "VoiceActivation"
            textSize = 27f
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "Голосовая активация с проверкой владельца"
            textSize = 15f
            setPadding(0, dp(6), 0, dp(22))
        }
        root.addView(subtitle, matchWrap())

        root.addView(section("Приложение для запуска"))
        selectedLabel = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(10), 0, dp(12))
        }
        root.addView(selectedLabel, matchWrap())

        root.addView(actionButton("Выбрать приложение") { showAppPicker() })
        root.addView(actionButton("Проверить запуск выбранного приложения") { launchSelectedApp() })

        root.addView(section("Состояние голосового движка").apply {
            setPadding(0, dp(26), 0, dp(8))
        })
        engineStatus = TextView(this).apply {
            text = "НЕ ПОДКЛЮЧЁН\nРаспознавание ключевой фразы и проверка голоса ещё не реализованы."
            textSize = 14f
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setBackgroundColor(0xFFEFEFEF.toInt())
        }
        root.addView(engineStatus, matchWrap())

        val note = TextView(this).apply {
            text = "Автоматический запуск приложения будет включён только после реального обнаружения ключевой фразы и успешной проверки голоса владельца."
            textSize = 13f
            setPadding(0, dp(18), 0, 0)
        }
        root.addView(note, matchWrap())

        setContentView(root)
    }

    private fun showAppPicker() {
        val apps = queryLaunchableApps()
        if (apps.isEmpty()) {
            Toast.makeText(this, "Не найдено приложений с экраном запуска", Toast.LENGTH_LONG).show()
            return
        }
        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Выберите приложение")
            .setItems(labels) { _, which ->
                selectedPackage = apps[which].activityInfo.packageName
                refreshSelection()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun queryLaunchableApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }
        return resolved
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
    }

    private fun refreshSelection() {
        val pkg = selectedPackage
        if (pkg.isNullOrBlank()) {
            selectedLabel.text = "Не выбрано"
            return
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        selectedLabel.text = if (launch != null) {
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
            "$label\n$pkg"
        } else {
            "Приложение недоступно: $pkg"
        }
    }

    private fun launchSelectedApp() {
        val pkg = selectedPackage
        if (pkg.isNullOrBlank()) {
            Toast.makeText(this, "Сначала выберите приложение", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "Приложение удалено или не имеет экрана запуска", Toast.LENGTH_LONG).show()
            refreshSelection()
            return
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть приложение: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setPadding(0, dp(10), 0, dp(8))
    }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { action() }
        isAllCaps = false
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val KEY_TARGET_PACKAGE = "target_package"
    }
}
