package com.codelab.app

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codelab.app.data.ProjectRepository
import com.codelab.app.model.Language
import com.codelab.app.model.Project
import com.codelab.app.ui.NewProjectDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Kiểm thử giao diện trên máy ảo:
 *  - Mở được màn hình chính, hộp thoại tạo dự án, màn hình soạn thảo (không bị sập).
 *  - Khi mở bàn phím, bố cục màn hình soạn thảo KHÔNG bị dời/co lại, và vùng gõ code
 *    vẫn nằm trên bàn phím.
 * Đồng thời chụp ảnh màn hình (lưu trong thư mục files/screens của app) để xem giao diện.
 */
@RunWith(AndroidJUnit4::class)
class UiScreensTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        Thread.sleep(800)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val dir = File(context.getExternalFilesDir(null), "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun topInWindow(view: View): Int {
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return location[1]
    }

    @Test
    fun screensRenderAndKeyboardDoesNotMoveLayout() {
        val repo = ProjectRepository.get(context)
        val samples = listOf(
            Triple(Language.PYTHON, "Tính tổng dãy số", "Vòng lặp for và hàm range"),
            Triple(Language.CPP, "Bài tập C++", "Nhập xuất cơ bản với cout"),
            Triple(Language.JAVA, "Hello Java", "Lớp Main và hàm main"),
            Triple(Language.JAVASCRIPT, "Mảng trong JS", "map, filter, reduce"),
            Triple(Language.C, "Con trỏ trong C", "")
        )
        val ids = samples.map { (lang, name, desc) ->
            Project(name = name, description = desc, languageId = lang.id, code = lang.template)
                .also { repo.add(it) }.id
        }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                screenshot("1_man_hinh_chinh")

                // Hộp thoại tạo dự án
                scenario.onActivity { NewProjectDialog().show(it.supportFragmentManager, NewProjectDialog.TAG) }
                screenshot("2_tao_du_an")
                scenario.onActivity {
                    val dialog = it.supportFragmentManager.findFragmentByTag(NewProjectDialog.TAG) as DialogFragment
                    assertTrue("Hộp thoại không hiện", dialog.dialog?.isShowing == true)
                    dialog.dismissNow()
                }

                // Màn hình soạn thảo (dự án C++)
                scenario.onActivity { it.openEditor(ids[1]) }
                screenshot("3_soan_thao")

                // Mở bàn phím
                var outputTopBefore = 0
                var editorHeightBefore = 0
                scenario.onActivity { activity ->
                    outputTopBefore = topInWindow(activity.findViewById(R.id.output_panel))
                    val editor = activity.findViewById<EditText>(R.id.editor)
                    editorHeightBefore = editor.height
                    editor.requestFocus()
                    editor.setSelection(editor.text.length)
                    WindowCompat.getInsetsController(activity.window, editor).show(WindowInsetsCompat.Type.ime())
                }
                Thread.sleep(3000)
                screenshot("4_soan_thao_ban_phim")

                scenario.onActivity { activity ->
                    val editor = activity.findViewById<EditText>(R.id.editor)
                    val insets = ViewCompat.getRootWindowInsets(editor)
                    val imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    val imeHeight = insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
                    Log.i(TAG, "Bàn phím hiện: $imeVisible, cao: $imeHeight px")

                    // Bố cục cố định: khung kết quả không bị dời, ô code không bị co lại
                    assertEquals(outputTopBefore, topInWindow(activity.findViewById(R.id.output_panel)))
                    assertEquals(editorHeightBefore, editor.height)

                    // Vùng gõ chữ của ô code phải nằm trên bàn phím
                    if (imeVisible && imeHeight > 0) {
                        val keyboardTop = editor.rootView.height - imeHeight
                        val textAreaBottom = topInWindow(editor) + editor.height - editor.paddingBottom
                        Log.i(TAG, "Đáy vùng gõ: $textAreaBottom, đỉnh bàn phím: $keyboardTop")
                        assertTrue("Vùng gõ chữ bị bàn phím che", textAreaBottom <= keyboardTop + 2)
                    }
                }
            }
        } finally {
            ids.forEach { repo.delete(it) }
        }
    }

    /**
     * Bấm Chạy với code có lệnh nhập: hộp nhập dữ liệu hiện ra, gõ "5", bấm Chạy,
     * kết quả phải in lại giá trị đã nhập ngay sau câu hỏi (giống màn hình dòng lệnh).
     */
    @Test
    fun inputDialogFeedsProgramAndEchoesValue() {
        val repo = ProjectRepository.get(context)
        val project = Project(
            name = "Bảng cửu chương",
            description = "Nhập một số rồi in bảng cửu chương",
            languageId = Language.PYTHON.id,
            code = "n = int(input(\"Nhập bản cửu chương bạn muốn: \"))\n" +
                "for i in range(1, 11):\n" +
                "    print(f\"{n} x {i} = {n * i}\")\n"
        )
        repo.add(project)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.openEditor(project.id) }
                instrumentation.waitForIdleSync()

                onView(withId(R.id.btn_run)).perform(click())
                onView(withId(R.id.input_stdin)).inRoot(isDialog()).perform(replaceText("5"))
                screenshot("5_nhap_du_lieu")
                onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

                // Chờ chạy xong (Python chạy ở luồng nền)
                var output = ""
                val deadline = System.currentTimeMillis() + 20_000
                while (System.currentTimeMillis() < deadline) {
                    scenario.onActivity { output = it.findViewById<TextView>(R.id.output).text.toString() }
                    if (output.contains("5 x 10 = 50")) break
                    Thread.sleep(300)
                }
                screenshot("6_ket_qua_nhap")
                assertTrue(output, output.startsWith("Nhập bản cửu chương bạn muốn: 5\n5 x 1 = 5"))
                assertTrue(output, output.contains("5 x 10 = 50"))
                assertEquals("5", repo.get(project.id)?.lastInput)
            }
        } finally {
            repo.delete(project.id)
        }
    }

    companion object {
        private const val TAG = "UiScreensTest"
    }
}
