package kr.yeaan.bus

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ThemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectedThemeSurvivesActivityRecreation() {
        compose.onNodeWithText("사용자", useUnmergedTree = true).performClick()
        compose.onNodeWithText("테마 변경").performClick()
        compose.onNodeWithText("포레스트").performClick()
        compose.onNodeWithText("선택 완료").performScrollTo().performClick()
        compose.onNodeWithText("어둡게").performScrollTo().performClick()
        compose.onNodeWithText("어둡게").assertIsSelected()
        compose.onNodeWithText("나의 버스 생활").performScrollTo()
        screenshot("user-forest-dark")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("사용자", useUnmergedTree = true).performClick()
        compose.onNodeWithText("테마 변경").performClick()
        compose.onNodeWithText("포레스트").assertIsSelected()
        compose.onNodeWithText("선택 완료").performScrollTo().performClick()
        compose.onNodeWithText("어둡게").performScrollTo().assertIsSelected()
        compose.onNodeWithText("밝게").performClick()
        compose.onNodeWithText("테마 변경").performScrollTo().performClick()
        compose.onNodeWithText("오션 블루").performClick()
        compose.onNodeWithText("선택 완료").performScrollTo().performClick()
        compose.onNodeWithText("나의 버스 생활").performScrollTo()
        screenshot("user-ocean-light")
        compose.onNodeWithText("홈", useUnmergedTree = true).performClick()
        compose.onNodeWithText("예안버스").assertIsDisplayed()
        screenshot("home-ocean-light")
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
