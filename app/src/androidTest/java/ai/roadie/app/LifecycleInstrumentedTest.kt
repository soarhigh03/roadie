package ai.roadie.app

import ai.roadie.app.data.SettingsStore
import ai.roadie.core.Mode
import ai.roadie.core.Preferences
import ai.roadie.core.Theme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class LifecycleInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private fun awaitVisible(text: String) {
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithText(text).isDisplayed() }.getOrDefault(false) }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun recreationPreservesSessionButLeavingAppEndsIt() = runBlocking<Unit> {
        val store = SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
        val original = store.preferences.first()
        store.save(Preferences(onboarded = true, mode = Mode.DEMO, theme = Theme.NIGHT))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                awaitVisible("함께 출발하기")
                compose.onNodeWithText("함께 출발하기").performClick()
                awaitVisible("마이크 끄기")
                activity.recreate()
                awaitVisible("마이크 끄기")
                activity.moveToState(Lifecycle.State.CREATED)
                activity.moveToState(Lifecycle.State.RESUMED)
                awaitVisible("함께 출발하기")
            }
        } finally { store.save(original) }
    }
}
