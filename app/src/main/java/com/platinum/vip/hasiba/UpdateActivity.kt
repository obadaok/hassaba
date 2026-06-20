package com.platinum.vip.hasiba

import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.platinum.vip.hasiba.databinding.ActivityUpdateBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UpdateActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUpdateBinding
    private var currentUpdateInfo: UpdateManager.UpdateInfo? = null

    sealed class UpdateState {
        object Checking : UpdateState()
        data class Available(val info: UpdateManager.UpdateInfo) : UpdateState()
        object UpToDate : UpdateState()
        object Error : UpdateState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUpdateBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        setupAnimations()

        checkForUpdates()
    }

    private fun setupButtons() {
        binding.apply {
            btnUpdate.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                currentUpdateInfo?.let {
                    UpdateManager.openOnGooglePlay(this@UpdateActivity)
                }
                finish()
            }
            btnSkip.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                finish()
            }
            btnClose.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                finish()
            }
            btnRetry.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                checkForUpdates()
            }
        }
    }

    private fun setupAnimations() {
        val rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                binding.iconChecking.rotation = animation.animatedValue as Float
            }
        }
        binding.iconChecking.tag = rotationAnimator
    }

    private fun checkForUpdates() {
        updateUIState(UpdateState.Checking)

        lifecycleScope.launch {
            try {
                val updateInfo = UpdateManager.checkForUpdate(this@UpdateActivity)
                delay(1200)

                if (updateInfo != null) {
                    currentUpdateInfo = updateInfo
                    updateUIState(UpdateState.Available(updateInfo))
                } else {
                    updateUIState(UpdateState.UpToDate)
                }
            } catch (e: Exception) {
                updateUIState(UpdateState.Error)
            }
        }
    }

    private fun updateUIState(state: UpdateState) {
        binding.apply {
            containerChecking.visibility = View.GONE
            containerUpdateAvailable.visibility = View.GONE
            containerUpToDate.visibility = View.GONE
            (iconChecking.tag as? ValueAnimator)?.cancel()

            when (state) {
                is UpdateState.Checking -> {
                    containerChecking.visibility = View.VISIBLE
                    (iconChecking.tag as? ValueAnimator)?.start()
                    containerChecking.alpha = 0f
                    containerChecking.scaleX = 0.8f
                    containerChecking.scaleY = 0.8f
                    containerChecking.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start()
                }
                is UpdateState.Available -> {
                    containerUpdateAvailable.visibility = View.VISIBLE
                    tvNewVersion.text = "الإصدار ${state.info.versionName}"
                    tvCurrentVersion.text = "الحالي: ${getCurrentVersion()}"
                    tvFileSize.text = "الحجم: ${UpdateManager.formatFileSize(state.info.fileSize)}"

                    val cleanChangelog = state.info.changelog.replace(Regex("[\\p{So}\\p{Cn}]"), "")
                    tvChangelog.text = cleanChangelog

                    btnSkip.visibility = if (state.info.mandatory) View.GONE else View.VISIBLE

                    if (state.info.mandatory) {
                        btnUpdate.text = "تحديث إلزامي"
                    }

                    cardUpdateInfo.alpha = 0f
                    cardUpdateInfo.translationY = -50f
                    cardUpdateInfo.animate().alpha(1f).translationY(0f).setDuration(400).start()

                    cardChangelog.alpha = 0f
                    cardChangelog.translationY = -50f
                    cardChangelog.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(100).start()

                    iconUpdate.scaleX = 0.5f
                    iconUpdate.scaleY = 0.5f
                    iconUpdate.animate().scaleX(1f).scaleY(1f).setDuration(500).setInterpolator(AccelerateDecelerateInterpolator()).start()
                }
                is UpdateState.UpToDate -> {
                    containerUpToDate.visibility = View.VISIBLE
                    tvCurrentVersionUpToDate.text = "الإصدار الحالي: ${getCurrentVersion()}"

                    iconUpToDate.scaleX = 0.3f
                    iconUpToDate.scaleY = 0.3f
                    iconUpToDate.animate().scaleX(1f).scaleY(1f).setDuration(600).setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            iconUpToDate.animate().scaleX(0.95f).scaleY(0.95f).setDuration(800)
                                .withEndAction { iconUpToDate.animate().scaleX(1f).scaleY(1f).setDuration(800).start() }.start()
                        }.start()
                }
                is UpdateState.Error -> {
                    containerUpToDate.visibility = View.GONE
                    // عرض رسالة خطأ
                    androidx.appcompat.app.AlertDialog.Builder(this@UpdateActivity)
                        .setTitle("خطأ في الاتصال")
                        .setMessage("تعذر التحقق من التحديثات\nتحقق من اتصالك بالإنترنت")
                        .setPositiveButton("إعادة المحاولة") { _, _ -> checkForUpdates() }
                        .setNegativeButton("إغلاق") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
            }
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            info.versionName ?: "غير معروف"
        } catch (e: Exception) {
            "غير معروف"
        }
    }
}