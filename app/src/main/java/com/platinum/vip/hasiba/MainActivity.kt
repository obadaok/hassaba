package com.platinum.vip.hasiba

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.ActionMode
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.mikepenz.iconics.view.IconicsImageView
import com.mikepenz.iconics.typeface.library.fontawesome.FontAwesome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var etInput: EditText
    private lateinit var tvPreview: TextView
    private lateinit var displayContainer: LinearLayout

    private lateinit var btnFloat: IconicsImageView
    private lateinit var btnMenu: IconicsImageView
    private lateinit var tvFloatBadge: TextView
    private lateinit var btnVoiceInput: IconicsImageView

    private var circularMenu: CircularMenuView? = null
    private var floatingWindowsCount = 0

    private lateinit var voiceInputHelper: VoiceInputHelper
    private var isVoiceListening = false

    private val PREFS_NAME = "AppPrefs"
    private val KEY_THEME = "theme_mode"
    private val KEY_POLICY_ACCEPTED = "policy_accepted_v1"
    private val VOICE_PERMISSION_CODE = 456

    private val historyLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("selected_expression")?.let { expression ->
                etInput.setText(expression)
                etInput.setSelection(expression.length)
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (checkOverlayPermission()) {
            startCalculatorService()
        }
    }

    private val windowsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            floatingWindowsCount = intent?.getIntExtra("count", 0) ?: 0
            updateFloatBadge()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_THEME)) {
            prefs.edit().putBoolean(KEY_THEME, true).apply()
        }
        val isDarkMode = prefs.getBoolean(KEY_THEME, true)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        etInput = findViewById(R.id.etInput)
        tvPreview = findViewById(R.id.tvPreview)
        displayContainer = findViewById(R.id.displayContainer)
        btnFloat = findViewById(R.id.btnFloat)
        btnMenu = findViewById(R.id.btnMenu)
        tvFloatBadge = findViewById(R.id.tvFloatBadge)
        btnVoiceInput = findViewById(R.id.btnVoiceInput)

        val filter = IntentFilter("ACTION_UPDATE_WINDOWS_COUNT")
        ContextCompat.registerReceiver(this, windowsReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setupInputField()
        setupLivePreviewAndFormatting()
        setupButtons()
        setupTopBar()
        setupVoiceInput()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (circularMenu != null) {
                    hideCircularMenu()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        checkPrivacyPolicy()
    }

    private fun setupInputField() {
        etInput.showSoftInputOnFocus = false
        etInput.isCursorVisible = false
        etInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                etInput.isCursorVisible = !s.isNullOrEmpty()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etInput.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val text = etInput.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("calculator", text))
            }
            true
        }

        etInput.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) {}
        }
        etInput.requestFocus()
    }

    private fun updateFloatBadge() {
        if (floatingWindowsCount > 0) {
            tvFloatBadge.visibility = View.VISIBLE
            tvFloatBadge.text = floatingWindowsCount.toString()
            btnFloat.setColorFilter(ContextCompat.getColor(this, R.color.bronze_accent))
        } else {
            tvFloatBadge.visibility = View.GONE
            btnFloat.setColorFilter(ContextCompat.getColor(this, R.color.icon_normal))
        }
    }

    private fun checkPrivacyPolicy() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAccepted = prefs.getBoolean(KEY_POLICY_ACCEPTED, false)
        if (!isAccepted) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_privacy_policy, null)
            val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
                .setView(dialogView)
                .setCancelable(false)
                .create()
            val btnAccept = dialogView.findViewById<MaterialButton>(R.id.btnAccept)
            btnAccept.setOnClickListener {
                prefs.edit().putBoolean(KEY_POLICY_ACCEPTED, true).apply()
                dialog.dismiss()
                initializeAppFeatures()
            }
            dialog.show()
        } else {
            initializeAppFeatures()
        }
    }

    private fun initializeAppFeatures() {
        checkAutoUpdate()
    }

    private fun setupTopBar() {
        btnFloat.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            sendBroadcast(Intent("ACTION_CLOSE_ALL_FLOATING"))
            floatingWindowsCount = 0
            updateFloatBadge()
            true
        }
        btnFloat.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            it.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100)
                .withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
            launchFloatingCalculator()
        }
        btnMenu.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            showCircularMenu()
        }
    }

    private fun showCircularMenu() {
        if (circularMenu != null) {
            hideCircularMenu()
            return
        }
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        circularMenu = CircularMenuView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            onMenuDismissed = { circularMenu = null }
        }
        circularMenu?.addMenuItem(FontAwesome.Icon.faw_history, "السجل") {
            hideCircularMenu()
            historyLauncher.launch(Intent(this, HistoryActivity::class.java))
        }
        circularMenu?.addMenuItem(FontAwesome.Icon.faw_sync_alt, "التحديث") {
            hideCircularMenu()
            startActivity(Intent(this, UpdateActivity::class.java))
        }
        circularMenu?.addMenuItem(FontAwesome.Icon.faw_palette, "المظهر") {
            hideCircularMenu()
            toggleTheme()
        }
        circularMenu?.addMenuItem(FontAwesome.Icon.faw_info_circle, "عن التطبيق") {
            hideCircularMenu()
            startActivity(Intent(this@MainActivity, DeveloperInfoActivity::class.java))
        }
        rootView.addView(circularMenu)
        circularMenu?.show()
    }

    private fun hideCircularMenu() {
        circularMenu?.hide {
            val rootView = findViewById<ViewGroup>(android.R.id.content)
            rootView.removeView(circularMenu)
            circularMenu = null
        }
    }

    private fun checkAutoUpdate() {
        lifecycleScope.launch {
            delay(2000)
            try {
                val updateInfo = UpdateManager.checkForUpdate(this@MainActivity)
                if (updateInfo != null) {
                    showUpdateDialog(updateInfo)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateManager.UpdateInfo) {
        if (isFinishing || isDestroyed) return
        val titleText = if (updateInfo.mandatory) "تحديث إلزامي مطلوب" else "تحديث جديد متوفر"
        val cleanChangelog = updateInfo.changelog.replace(Regex("[\\p{So}\\p{Cn}]"), "")
        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialogTheme)
            .setTitle(titleText)
            .setMessage("الإصدار الجديد (${updateInfo.versionName}) متاح للتحميل الآن.\n\nما الجديد:\n$cleanChangelog\n\nحجم التحديث: ${UpdateManager.formatFileSize(updateInfo.fileSize)}")
            .setCancelable(!updateInfo.mandatory)
        builder.setPositiveButton("تحديث الآن") { _, _ ->
            UpdateManager.openOnGooglePlay(this@MainActivity)
            if (updateInfo.mandatory) finish()
        }
        if (!updateInfo.mandatory) {
            builder.setNegativeButton("لاحقاً") { dialog, _ -> dialog.dismiss() }
        }
        builder.show()
    }

    private fun toggleTheme() {
        val newMode = AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_THEME, newMode).apply()
        val rootView = findViewById<View>(android.R.id.content)
        rootView.animate().alpha(0f).setDuration(150)
            .withEndAction {
                AppCompatDelegate.setDefaultNightMode(if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO)
            }.start()
    }

    private fun launchFloatingCalculator() {
        if (checkOverlayPermission()) {
            startCalculatorService()
        } else {
            requestOverlayPermission()
        }
    }

    private fun startCalculatorService() {
        val intent = Intent(this, FloatingCalculatorService::class.java).apply { action = "CREATE" }
        @Suppress("DEPRECATION")
        startService(intent)
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun handleInput(key: String) {
        val currentText = etInput.text
        val cursor = etInput.selectionStart
        val operators = "+-×÷%"
        if (operators.contains(key) && (cursor == 0 || operators.contains(currentText[cursor - 1].toString()))) return
        if (key == ".") {
            val textBefore = currentText.subSequence(0, cursor).toString()
            val lastOp = textBefore.lastIndexOfAny(operators.toCharArray())
            val currentNum = if (lastOp != -1) textBefore.substring(lastOp + 1) else textBefore
            if (currentNum.contains(".")) return
        }
        etInput.text.insert(cursor, key)
    }

    private fun setupButtons() {
        val grid = findViewById<GridLayout>(R.id.keypadGrid)
        val listener = View.OnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                .withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80)
                        .setInterpolator(OvershootInterpolator()).start()
                }.start()
            val key = (v as Button).text.toString()
            when (key) {
                "AC" -> {
                    etInput.animate().alpha(0f).setDuration(100)
                        .withEndAction {
                            etInput.setText("")
                            etInput.animate().alpha(1f).setDuration(100).start()
                        }.start()
                }
                "⌫" -> {
                    if (etInput.selectionStart > 0) {
                        etInput.animate().translationX(10f).setDuration(80)
                            .withEndAction {
                                etInput.text.delete(etInput.selectionStart - 1, etInput.selectionStart)
                                etInput.animate().translationX(0f).setDuration(80).start()
                            }.start()
                    }
                }
                "=" -> {
                    val expression = etInput.text.toString().trimEnd('+', '-', '×', '÷', '%')
                    if (expression.isNotEmpty()) {
                        val result = CalculatorEngine.evaluate(expression, roundToFour = true)
                        if (result.isNotEmpty() && result != "خطأ") {
                            displayContainer.animate()
                                .scaleX(1.08f).scaleY(1.08f).setDuration(200)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .withEndAction {
                                    HistoryManager.add(this@MainActivity, expression, result)
                                    etInput.setText(result)
                                    etInput.setSelection(result.length)
                                    tvPreview.visibility = View.INVISIBLE

                                    displayContainer.animate()
                                        .scaleX(1f).scaleY(1f).setDuration(300)
                                        .setInterpolator(OvershootInterpolator()).start()
                                }.start()
                        }
                    }
                }
                else -> handleInput(key)
            }
        }
        for (i in 0 until grid.childCount) (grid.getChildAt(i) as? Button)?.setOnClickListener(listener)
    }

    private fun setupLivePreviewAndFormatting() {
        etInput.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true
                val text = s.toString().trim()
                val operatorColor = ContextCompat.getColor(this@MainActivity, R.color.bronze_accent)
                val operators = "+-×÷%"
                text.forEachIndexed { index, char ->
                    if (char in operators) {
                        s?.setSpan(ForegroundColorSpan(operatorColor), index, index + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                etInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, when {
                    text.length > 15 -> 35f
                    text.length > 10 -> 40f
                    else -> 55f
                })
                if (text.isEmpty()) {
                    tvPreview.animate().alpha(0f).setDuration(150)
                        .withEndAction { tvPreview.visibility = View.INVISIBLE }.start()
                    isUpdating = false
                    return
                }
                val cleanText = text.trimEnd('+', '-', '×', '÷', '%')
                val result = CalculatorEngine.evaluate(cleanText, roundToFour = true)
                if (result.isNotEmpty() && result != "خطأ" && result != text) {
                    tvPreview.text = "$result"
                    if (tvPreview.visibility != View.VISIBLE) {
                        tvPreview.visibility = View.VISIBLE
                        tvPreview.alpha = 0f
                        tvPreview.scaleY = 0.8f
                        tvPreview.animate().alpha(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(OvershootInterpolator()).start()
                    }
                    tvPreview.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                } else {
                    tvPreview.animate().alpha(0f).setDuration(150)
                        .withEndAction { tvPreview.visibility = View.INVISIBLE }.start()
                }
                isUpdating = false
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(windowsReceiver) } catch (e: Exception) {}
        voiceInputHelper.destroy()
    }

    // -------------------- Voice Input Setup --------------------
    private fun setupVoiceInput() {
        voiceInputHelper = VoiceInputHelper(
            activity = this,
            onResult = { mathExpression ->
                val currentText = etInput.text.toString()
                val newText = if (currentText.isEmpty() || currentText == "0") mathExpression else "$currentText$mathExpression"
                etInput.setText(newText)
                etInput.setSelection(newText.length)
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            },
            onListeningStart = {
                isVoiceListening = true
                startVoiceButtonAnimation()
            },
            onListeningStop = {
                isVoiceListening = false
                stopVoiceButtonAnimation()
            }
        )

        btnVoiceInput.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (voiceInputHelper.checkVoicePermission()) {
                voiceInputHelper.startListening()
            } else {
                voiceInputHelper.requestVoicePermission()
            }
        }
    }

    private fun startVoiceButtonAnimation() {
        val pulseAnimator = ValueAnimator.ofFloat(1f, 1.3f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                btnVoiceInput.scaleX = scale
                btnVoiceInput.scaleY = scale
            }
        }
        btnVoiceInput.setTag(pulseAnimator)
        pulseAnimator.start()
        btnVoiceInput.setColorFilter(ContextCompat.getColor(this, R.color.danger))
    }

    private fun stopVoiceButtonAnimation() {
        (btnVoiceInput.getTag() as? ValueAnimator)?.cancel()
        btnVoiceInput.scaleX = 1f
        btnVoiceInput.scaleY = 1f
        btnVoiceInput.setColorFilter(ContextCompat.getColor(this, R.color.bronze_accent))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VOICE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                voiceInputHelper.startListening()
            } else {
                Toast.makeText(this, "صلاحية الميكروفون مطلوبة للإدخال الصوتي", Toast.LENGTH_SHORT).show()
            }
        }
    }
}