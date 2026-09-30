package com.akashrajeev.voicebeam

import android.Manifest
import android.util.Base64
import android.util.Log
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.akashrajeev.voicebeam.engine.SettingsStore
import com.akashrajeev.voicebeam.ml.AsrBackend
import com.akashrajeev.voicebeam.ml.WhisperCaptionRecognizer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Experiment-only smoke and settings UI. Not the full product regression suite. */
@RunWith(AndroidJUnit4::class)
class WhisperExperimentTest {
    private val perms = GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(perms).around(compose)

    @Test fun packagedModelsAndSelection() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(AsrBackend.WHISPER_TINY, SettingsStore(ctx).load().asrBackend)
        for (name in listOf("ggml-tiny.en-q5_1.bin", "ggml-base.en-q5_1.bin")) {
            ctx.assets.open("models/whisper/$name").use { assertTrue(it.available() > 30_000_000) }
        }
        val engine = (compose.activity.application as VoiceBeamApp).engine
        compose.runOnUiThread { engine.updateSettings { it.copy(onboarded = true) } }
        compose.waitUntil(180_000) { engine.state.value.modelsReady }
        compose.onNodeWithTag("start").performScrollTo().performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Offline ASR test - CPU only (2 threads)").assertExists()
        compose.onNodeWithText("Base.en (60 MB)").performScrollTo().performClick()
        assertEquals(AsrBackend.WHISPER_BASE, engine.settings.value.asrBackend)
        compose.mainClock.advanceTimeBy(1000)
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(shot)
        val bytes = ByteArrayOutputStream()
        shot.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        Log.i("VBSHT", "BEGIN whisper-settings")
        encoded.chunked(3000).forEach { Log.i("VBSHT", "D " + it) }
        Log.i("VBSHT", "END whisper-settings")
        compose.runOnUiThread { engine.updateSettings { it.copy(asrBackend = AsrBackend.WHISPER_TINY) } }
    }
}
