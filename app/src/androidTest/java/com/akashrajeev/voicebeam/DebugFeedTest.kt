package com.akashrajeev.voicebeam

import android.Manifest
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import androidx.test.rule.GrantPermissionRule
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Exercises the demo feed: a recorded two-person clip drives the real face,
 * lip-gate, voice and caption pipelines. Takes screenshots through logcat
 * (VBSHT chunks) so CI can reassemble them.
 */
@RunWith(AndroidJUnit4::class)
class DebugFeedTest {
    private val perms = GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain = RuleChain.outerRule(perms).around(compose)

    private val engine get() = (compose.activity.application as VoiceBeamApp).engine

    @Test fun demoFeedDrivesFacesLockAndCaptions() {
        compose.runOnUiThread { engine.updateSettings { it.copy(debugFeed = true, onboarded = true) } }
        compose.waitUntil(90_000) { engine.state.value.modelsReady }
        if (compose.onAllNodes(androidx.compose.ui.test.hasTestTag("start")).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("start").performScrollTo().assertIsEnabled().performClick()
        }
        compose.waitUntil(30_000) { engine.state.value.listening }

        // Give the demo branch a moment, then record what the screen looks like.
        Thread.sleep(8000)
        shot("0-screen")

        // Two faces from the recorded clip (ML Kit on the emulator, MediaPipe on a phone).
        compose.waitUntil(90_000) { engine.state.value.faces.size >= 2 }
        shot("1-faces")

        // Tap the left face (Armstrong) and lock on.
        compose.onNodeWithTag("faces").performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * 0.25f, height * 0.5f))
            up()
        }
        compose.waitUntil(15_000) { engine.state.value.lockedId != null }
        shot("2-locked")

        // The clip alternates talkers every 15 s; the locked person must light up.
        compose.waitUntil(75_000) { engine.state.value.lockedSpeaking > 0.35f }
        shot("3-speaking")

        // Captions from the clip's speech. Ground truth from an offline run of the
        // same ASR model: Armstrong says "...approach ... safety into the spacecraft...",
        // Aldrin says "the Russians are to be congratulated...".
        compose.waitUntil(180_000) {
            engine.state.value.segments.any {
                it.text.contains("APPROACH") || it.text.contains("SAFETY") ||
                    it.text.contains("RUSSIAN") || it.text.contains("CONGRATULATED")
            }
        }
        shot("4-captions")
        assertTrue(engine.state.value.segments.isNotEmpty())
    }

    private fun shot(name: String) {
        try {
            val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
            val buf = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 90, buf)
            val b64 = Base64.encodeToString(buf.toByteArray(), Base64.NO_WRAP)
            Log.i("VBSHT", "BEGIN $name")
            var i = 0
            while (i < b64.length) {
                Log.i("VBSHT", "D " + b64.substring(i, minOf(i + 3000, b64.length)))
                i += 3000
            }
            Log.i("VBSHT", "END $name")
        } catch (t: Throwable) {
            Log.w("VBSHT", "shot failed: ${t.message}")
        }
    }
}
