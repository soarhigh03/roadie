package ai.roadie.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import ai.roadie.app.data.PrivateVault
import ai.roadie.app.data.SettingsStore
import ai.roadie.app.ui.*
import ai.roadie.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test

class RoadieInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<RenderTestActivity>()
    private fun show(content: @Composable () -> Unit) {
        compose.runOnUiThread { RenderTestActivity.screen = content }
        compose.waitForIdle()
    }
    @After fun clearScreen() { compose.runOnUiThread { RenderTestActivity.screen = null } }
    private fun actions(start: () -> Unit = {}, mute: () -> Unit = {}, listen: () -> Unit = {}) = HomeActions(
        start, listen, mute, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )

    @Test fun compactLayoutKeepsListeningAndMuteControlsUsable() {
        var muted = false
        show { RoadieTheme(Theme.NIGHT) {
            Box(Modifier.fillMaxHeight().width(480.dp)) {
                HomeScreen(SessionState(phase = Phase.LISTENING, active = true), Preferences(), false, false,
                    actions(mute = { muted = true }))
            }
        } }
        compose.onNodeWithText("마이크 끄기").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(muted) }
        compose.onNodeWithText("조용히 가기").assertIsDisplayed()
        val bounds = compose.onNodeWithText("마이크 끄기").getUnclippedBoundsInRoot()
        assertTrue(bounds.bottom - bounds.top >= 72.dp)
    }

    @Test fun sourceAndDateAreVisibleOnlyWhenParked() {
        val allowed = mutableStateOf(true)
        val article = Article("1", "날짜가 있는 테스트 기사", "테스트 제목", "시험 출처", "https://example.org/news", "2026-01-02T00:00:00Z", true, "headline")
        show { RoadieTheme(Theme.DAY) { SourcesScreen(Briefing(Topic.TECH, "2026-01-03T00:00:00Z", listOf(article)), allowed.value) {} } }
        compose.onNodeWithText("시험 출처", substring = true).assertExists()
        compose.onNodeWithText("2026-01-02", substring = true).assertExists()
        compose.runOnIdle { allowed.value = false }
        compose.onNodeWithText("시험 출처", substring = true).assertDoesNotExist()
        compose.onNodeWithText("자세한 출처는 정차 후 확인해주세요.").assertIsDisplayed()
    }

    @Test fun settingsRestoreAcrossStoreInstances() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SettingsStore(context)
        val original = store.preferences.first()
        val value = Preferences(theme = Theme.NIGHT, interests = setOf(Topic.CULTURE), remember = true, proactive = true, onboarded = true)
        try {
            store.save(value)
            assertEquals(value, SettingsStore(context).preferences.first())
        } finally { store.save(original) }
    }

    @Test fun vaultEncryptsAndDeletesConsentedMemory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val vault = PrivateVault(context)
        val original = vault.load()
        try {
            val turns = listOf(Turn("user", "private-test-sentence"), Turn("assistant", "private-test-reply"))
            vault.save(turns)
            assertEquals(turns, PrivateVault(context).load())
            val raw = context.getSharedPreferences("private_vault", 0).getString("memory", "")!!
            assertFalse(raw.contains("private-test"))
            vault.clear(); assertTrue(PrivateVault(context).load().isEmpty())
        } finally { if (original.isEmpty()) vault.clear() else vault.save(original) }
    }

    @Test fun renderDayNightAndCompactStates() {
        val theme = mutableStateOf(Theme.DAY)
        val state = mutableStateOf(SessionState())
        val compact = mutableStateOf(false)
        show { RoadieTheme(theme.value) {
            Box(Modifier.fillMaxHeight().width(if (compact.value) 560.dp else 1200.dp)) {
                HomeScreen(state.value, Preferences(theme = theme.value, mode = Mode.DEMO), true, true, actions())
            }
        } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun save(name: String) {
            compose.waitForIdle()
            val directory = java.io.File(context.filesDir, "screenshots").apply { mkdirs() }
            java.io.File(directory, name).outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        save("day-ready.png")
        compose.runOnIdle { theme.value = Theme.NIGHT; state.value = SessionState(Phase.QUIET, true, "말없이 함께 가도 괜찮아요.") }
        save("night-quiet.png")
        compose.runOnIdle { compact.value = true; state.value = SessionState(Phase.MUTED, true, "마이크가 꺼져 있어요.") }
        compose.onNodeWithText("마이크 켜기").assertIsDisplayed()
        save("compact-muted.png")
    }
}
