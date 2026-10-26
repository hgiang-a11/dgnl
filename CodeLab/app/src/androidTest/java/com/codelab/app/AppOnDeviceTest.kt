package com.codelab.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codelab.app.exec.ExecMode
import com.codelab.app.exec.JavaScriptExecutor
import com.codelab.app.exec.PythonExecutor
import com.codelab.app.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm thử chạy trên máy ảo/điện thoại Android thật:
 * app mở được và các bộ chạy offline (Python, JavaScript) cho kết quả đúng.
 */
@RunWith(AndroidJUnit4::class)
class AppOnDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun appOpensWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { }
    }

    @Test
    fun pythonRunsOffline() {
        val result = PythonExecutor(context).execute("print(1 + 2)", ExecMode.RUN)
        assertFalse(result.output, result.isError)
        assertEquals("3", result.output.trim())
    }

    @Test
    fun pythonTemplateRuns() {
        val result = PythonExecutor(context).execute(Language.PYTHON.template, ExecMode.RUN)
        assertTrue(result.output, result.output.contains("Tổng 1..5 = 15"))
    }

    @Test
    fun pythonBuildReportsSyntaxError() {
        val result = PythonExecutor(context).execute("print(", ExecMode.BUILD_ONLY)
        assertTrue(result.output, result.isError)
    }

    @Test
    fun javascriptRunsOfflineWithModernSyntax() {
        val code = "let a = [1, 2, 3]; const b = a.map(x => x * 2); console.log(b.join(','));"
        val result = JavaScriptExecutor().execute(code, ExecMode.RUN)
        assertFalse(result.output, result.isError)
        assertEquals("2,4,6", result.output.trim())
    }

    @Test
    fun javascriptTemplateRuns() {
        val result = JavaScriptExecutor().execute(Language.JAVASCRIPT.template, ExecMode.RUN)
        assertTrue(result.output, result.output.contains("Tổng 1..5 = 15"))
    }

    @Test
    fun javascriptBuildReportsSyntaxError() {
        val result = JavaScriptExecutor().execute("var = ;", ExecMode.BUILD_ONLY)
        assertTrue(result.output, result.isError)
    }
}
