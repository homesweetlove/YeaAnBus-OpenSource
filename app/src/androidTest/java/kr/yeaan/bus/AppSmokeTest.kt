package kr.yeaan.bus

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var captured: Bitmap? = null
        repeat(3) {
            if (captured == null) {
                try { captured = compose.onRoot().captureToImage().asAndroidBitmap() }
                catch (error: AssertionError) {
                    if (error.message?.contains("PixelCopy") != true) throw error
                    SystemClock.sleep(500)
                }
            }
        }
        // PixelCopy can time out on overloaded preview emulators. Capture diagnostics
        // without turning an optional screenshot failure into a functional test failure.
        val bitmap = captured ?: instrumentation.uiAutomation.takeScreenshot()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "qa").apply { mkdirs() }
        val target = File(folder, "$name.png")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        instrumentation.uiAutomation.executeShellCommand("cp "+target.absolutePath+" /sdcard/Download/yeaan-qa-$name.png").use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
        }
    }
    @Test fun homeAndSearchNavigation() {
        compose.onNodeWithText("예안버스").assertIsDisplayed()
        compose.onNodeWithText("안동", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("예천", useUnmergedTree = true).assertDoesNotExist()
        screenshot("01-home")
        compose.onNodeWithText("검색", useUnmergedTree = true).performClick()
        compose.onNodeWithText("정류장 이름 또는 버스 번호").assertIsDisplayed()
        compose.onNodeWithText("안동 · 예천 통합").assertIsDisplayed()
        compose.onNodeWithText("어디로 가시나요?").assertIsDisplayed()
        screenshot("02-search")
    }
    @Test fun liveBisSearchArrivalFavoriteAndMap() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveBis") == "true")
        compose.onNodeWithText("검색", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("안동역")
        compose.waitUntil(60_000) {
            compose.onAllNodesWithText("안동역(안동터미널)").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasSetTextAction()).performImeAction()
        screenshot("03-search-results")
        compose.onAllNodesWithText("안동역(안동터미널)").onFirst().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("안동 BIS").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("즐겨찾기 변경").performClick()
        screenshot("04-arrivals")
        compose.onNodeWithText("지도 보기").performClick()
        compose.waitForIdle()
        SystemClock.sleep(5000)
        screenshot("05-map")
        compose.onNodeWithText("홈", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("안동역(안동터미널)").onFirst().assertIsDisplayed()
        screenshot("06-home-favorite")
    }
}
