package ru.voiceactivation.demo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import ru.voiceactivation.demo.voice.OwnerProfileStore
import ru.voiceactivation.demo.voice.PcmCapture
import ru.voiceactivation.demo.voice.SpeakerEngine
import ru.voiceactivation.demo.voice.VoiceModelRepository
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("voice_activation", MODE_PRIVATE) }
    private val models by lazy { VoiceModelRepository(this) }

    private var selectedPackage: String?
        get() = prefs.getString(KEY_TARGET_PACKAGE, null)
        set(value) { prefs.edit().putString(KEY_TARGET_PACKAGE, value).apply() }

    private lateinit var selectedLabel: TextView
    private lateinit var russianModelStatus: TextView
    private lateinit var speakerModelStatus: TextView
    private lateinit var ownerStatus: TextView
    private lateinit var wakePhraseInput: EditText
    private var pendingPermissionAction: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        if (::selectedLabel.isInitialized) refreshState()
    }

    private fun render() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(dp(22), dp(24), dp(22), dp(20))
        }

        root.addView(TextView(this).apply {
            text = "VoiceActivation"
            textSize = 27f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Голосовая активация · локальная обработка"
            textSize = 14f
            setPadding(0, dp(5), 0, dp(18))
        }, matchWrap())

        root.addView(section("1. Приложение для запуска"), matchWrap())
        selectedLabel = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(8), 0, dp(10))
        }
        root.addView(selectedLabel, matchWrap())
        root.addView(actionButton("Выбрать приложение") { showAppPicker() }, matchWrap())
        root.addView(actionButton("Проверить ручной запуск") { launchSelectedApp() }, matchWrap())

        root.addView(section("2. Русская ключевая фраза"), matchWrap())
        wakePhraseInput = EditText(this).apply {
            setSingleLine(true)
            hint = "Например: окей помощник"
            setText(prefs.getString(KEY_WAKE_PHRASE, "окей помощник"))
        }
        root.addView(wakePhraseInput, matchWrap())
        root.addView(actionButton("Импортировать русскую модель") { pickRussianModel() }, matchWrap())
        russianModelStatus = statusText()
        root.addView(russianModelStatus, matchWrap())

        root.addView(section("3. Голос владельца"), matchWrap())
        root.addView(actionButton("Импортировать модель голоса") { pickSpeakerModel() }, matchWrap())
        speakerModelStatus = statusText()
        root.addView(speakerModelStatus, matchWrap())
        ownerStatus = statusText()
        root.addView(ownerStatus, matchWrap())
        root.addView(actionButton("Записать голос владельца (3 образца)") { requestMicForEnrollment() }, matchWrap())
        root.addView(actionButton("Удалить голосовой профиль") { confirmDeleteVoiceProfile() }, matchWrap())

        root.addView(section("4. Голосовая активация"), matchWrap())
        root.addView(actionButton("Запустить прослушивание") { requestPermissionsForListening() }, matchWrap())
        root.addView(actionButton("Остановить прослушивание") {
            stopService(Intent(this, VoiceActivationService::class.java))
            Toast.makeText(this, "Прослушивание остановлено", Toast.LENGTH_SHORT).show()
        }, matchWrap())

        root.addView(TextView(this).apply {
            text = "Микрофон активен только при запущенной службе. Пока приложение слушает, Android показывает постоянное уведомление. Если система запрещает автоматическое открытие другого приложения, уведомление предложит открыть его нажатием."
            textSize = 12f
            setPadding(0, dp(14), 0, 0)
        }, matchWrap())

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
                refreshState()
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

    private fun pickRussianModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_RUSSIAN_MODEL)
    }

    private fun pickSpeakerModel() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }
        startActivityForResult(intent, REQUEST_SPEAKER_MODEL)
    }

    @Deprecated("Handled for compatibility with platform Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        try {
            when (requestCode) {
                REQUEST_RUSSIAN_MODEL -> {
                    val uris = mutableListOf<Uri>()
                    data.clipData?.let { clip ->
                        for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
                    }
                    data.data?.let { if (uris.isEmpty()) uris += it }
                    val dir = models.importRussianWakeModel(uris)
                    Toast.makeText(this, "Русская модель импортирована: ${dir.name}", Toast.LENGTH_LONG).show()
                }
                REQUEST_SPEAKER_MODEL -> {
                    val uri = data.data ?: error("Файл модели не выбран")
                    val file = models.importSpeakerModel(uri)
                    Toast.makeText(this, "Модель голоса импортирована: ${file.length()} байт", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка импорта: ${e.message}", Toast.LENGTH_LONG).show()
        }
        refreshState()
    }

    private fun requestMicForEnrollment() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingPermissionAction = ACTION_ENROLL
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
            return
        }
        enrollOwnerVoice()
    }

    private fun requestPermissionsForListening() {
        val required = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) required += Manifest.permission.POST_NOTIFICATIONS
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            pendingPermissionAction = ACTION_LISTEN
            requestPermissions(missing.toTypedArray(), REQUEST_LISTEN)
            return
        }
        startListening()
    }

    @Deprecated("Platform permission callback")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (!granted) {
            Toast.makeText(this, "Необходимые разрешения не предоставлены", Toast.LENGTH_LONG).show()
            return
        }
        when (action) {
            ACTION_ENROLL -> enrollOwnerVoice()
            ACTION_LISTEN -> startListening()
        }
    }

    private fun enrollOwnerVoice() {
        val model = models.speakerModelFile()
        if (!model.isFile) {
            Toast.makeText(this, "Сначала импортируйте модель голоса", Toast.LENGTH_LONG).show()
            return
        }
        thread(name = "VoiceOwnerEnrollment") {
            try {
                val capture = PcmCapture(this)
                val samples = ArrayList<FloatArray>()
                repeat(SpeakerEngine.MIN_ENROLLMENT_UTTERANCES) { index ->
                    runOnUiThread {
                        ownerStatus.text = "Образец ${index + 1}/3: произнесите фразу 4 секунды"
                    }
                    Thread.sleep(700)
                    samples += capture.recordUtterance()
                }
                SpeakerEngine(
                    modelPath = model.absolutePath,
                    profileStore = OwnerProfileStore(this),
                ).use { engine ->
                    check(engine.enrollOwner(samples)) { "Не удалось сохранить голосовой профиль" }
                }
                runOnUiThread {
                    ownerStatus.text = "Голос владельца зарегистрирован и сохранён в зашифрованном виде"
                    Toast.makeText(this, "Регистрация голоса завершена", Toast.LENGTH_LONG).show()
                    refreshState()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    ownerStatus.text = "Ошибка регистрации: ${e.message}"
                    Toast.makeText(this, "Ошибка регистрации голоса", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun confirmDeleteVoiceProfile() {
        AlertDialog.Builder(this)
            .setTitle("Удалить голосовой профиль?")
            .setMessage("Зашифрованный профиль владельца будет удалён. Для активации потребуется новая регистрация.")
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                OwnerProfileStore(this).clear()
                ownerStatus.text = "Профиль владельца удалён"
                Toast.makeText(this, "Голосовой профиль удалён", Toast.LENGTH_SHORT).show()
                refreshState()
            }
            .show()
    }

    private fun startListening() {
        val target = selectedPackage
        if (target.isNullOrBlank() || packageManager.getLaunchIntentForPackage(target) == null) {
            Toast.makeText(this, "Выберите доступное приложение для запуска", Toast.LENGTH_LONG).show()
            refreshState()
            return
        }
        if (!models.russianModelDirectory().resolve("model.onnx").isFile ||
            !models.russianModelDirectory().resolve("tokens.txt").isFile ||
            !models.speakerModelFile().isFile ||
            !OwnerProfileStore(this).hasProfile()
        ) {
            Toast.makeText(this, "Импортируйте обе модели и зарегистрируйте голос", Toast.LENGTH_LONG).show()
            return
        }
        val phrase = wakePhraseInput.text.toString().trim()
        if (phrase.isBlank()) {
            Toast.makeText(this, "Введите ключевую фразу", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString(KEY_WAKE_PHRASE, phrase).apply()
        val serviceIntent = Intent(this, VoiceActivationService::class.java).apply {
            putExtra(VoiceActivationService.EXTRA_TARGET_PACKAGE, target)
            putExtra(VoiceActivationService.EXTRA_WAKE_PHRASE, phrase)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
        Toast.makeText(this, "Голосовая активация запущена", Toast.LENGTH_SHORT).show()
    }

    private fun launchSelectedApp() {
        val pkg = selectedPackage
        if (pkg.isNullOrBlank()) {
            Toast.makeText(this, "Сначала выберите приложение", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(this, "Приложение удалено или недоступно", Toast.LENGTH_LONG).show()
            refreshState()
            return
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть приложение: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshState() {
        val pkg = selectedPackage
        selectedLabel.text = if (pkg.isNullOrBlank()) {
            "Не выбрано"
        } else {
            val label = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { "Приложение недоступно" }
            "$label\n$pkg"
        }
        val russianDir = models.russianModelDirectory()
        val russianReady = russianDir.resolve("model.onnx").isFile && russianDir.resolve("tokens.txt").isFile
        russianModelStatus.text = if (russianReady) "Русская модель: установлена" else "Русская модель: не установлена"
        speakerModelStatus.text = if (models.speakerModelFile().isFile) "Модель голоса: установлена" else "Модель голоса: не установлена"
        ownerStatus.text = if (!OwnerProfileStore(this).hasProfile()) {
            "Профиль владельца: не зарегистрирован"
        } else {
            "Профиль владельца: сохранён (AES-GCM / Android Keystore)"
        }
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setPadding(0, dp(18), 0, dp(7))
    }

    private fun statusText() = TextView(this).apply {
        textSize = 13f
        setPadding(0, dp(6), 0, dp(8))
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
        private const val KEY_WAKE_PHRASE = "wake_phrase"
        private const val REQUEST_RUSSIAN_MODEL = 101
        private const val REQUEST_SPEAKER_MODEL = 102
        private const val REQUEST_MIC = 103
        private const val REQUEST_LISTEN = 104
        private const val ACTION_ENROLL = "enroll"
        private const val ACTION_LISTEN = "listen"
    }
}
