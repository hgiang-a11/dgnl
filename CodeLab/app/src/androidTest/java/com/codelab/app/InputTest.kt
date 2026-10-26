package com.codelab.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codelab.app.exec.ExecMode
import com.codelab.app.exec.InputDetector
import com.codelab.app.exec.InputDetector.OnlinePlan
import com.codelab.app.exec.InputProvider
import com.codelab.app.exec.JavaScriptExecutor
import com.codelab.app.exec.OnlineSession
import com.codelab.app.exec.PythonExecutor
import com.codelab.app.exec.WandboxApi
import com.codelab.app.exec.WandboxApi.Echo
import com.codelab.app.model.Language
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Kiểm thử tính năng nhập dữ liệu kiểu màn hình dòng lệnh (cin, scanf, input(), Scanner...). */
@RunWith(AndroidJUnit4::class)
class InputTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker = WandboxApi.NEED_INPUT_MARKER

    /** Người dùng giả: gõ lần lượt các dòng cho trước, ghi lại màn hình thấy gì mỗi lần được hỏi. */
    class FakeUser(vararg lines: String?) : InputProvider {
        private val queue = ArrayDeque(lines.toList())
        val screens = mutableListOf<String>()
        override fun requestLine(outputSoFar: String): String? {
            screens += outputSoFar
            return queue.removeFirstOrNull()
        }
    }

    // ---------- Nhận biết lệnh nhập ----------

    @Test
    fun detectsInputStatements() {
        assertTrue(InputDetector.needsInput(Language.CPP, "int a; cin >> a;"))
        assertTrue(InputDetector.needsInput(Language.C, "scanf(\"%d\", &a);"))
        assertTrue(InputDetector.needsInput(Language.PYTHON, "n = int(input('n: '))"))
        assertTrue(InputDetector.needsInput(Language.JAVA, "Scanner sc = new Scanner(System.in);"))
        assertTrue(InputDetector.needsInput(Language.JAVASCRIPT, "var n = prompt('n');"))
        assertFalse(InputDetector.needsInput(Language.CPP, "cout << \"hi\";"))
        assertFalse(InputDetector.needsInput(Language.CPP, "// cin >> a;\ncout << 1;"))
        assertFalse(InputDetector.needsInput(Language.PYTHON, "# input()\nprint(1)"))
        assertFalse(InputDetector.needsInput(Language.CPP, Language.CPP.template))
    }

    @Test
    fun choosesOnlinePlan() {
        assertEquals(OnlinePlan.CIN_ECHO, InputDetector.onlinePlan(Language.CPP, "cin >> a; cin >> b;"))
        assertEquals(OnlinePlan.STDIO_ECHO, InputDetector.onlinePlan(Language.CPP, "scanf(\"%d\", &a);"))
        assertEquals(OnlinePlan.STDIO_ECHO, InputDetector.onlinePlan(Language.C, "scanf(\"%d\", &a);"))
        assertEquals(OnlinePlan.UPFRONT, InputDetector.onlinePlan(Language.CPP, "ios::sync_with_stdio(false); cin >> a;"))
        assertEquals(OnlinePlan.UPFRONT, InputDetector.onlinePlan(Language.CPP, "cin >> a; scanf(\"%d\", &b);"))
        assertEquals(OnlinePlan.JAVA_SCANNER, InputDetector.onlinePlan(Language.JAVA, "new Scanner(System.in)"))
        assertEquals(OnlinePlan.UPFRONT, InputDetector.onlinePlan(Language.JAVA, "new BufferedReader(new InputStreamReader(System.in))"))
    }

    // ---------- Python / JavaScript: dừng thật để chờ nhập ----------

    @Test
    fun pythonAsksForEachInputLikeATerminal() {
        val user = FakeUser("3", "4")
        val code = "a = int(input('Nhập a: '))\nb = int(input('Nhập b: '))\nprint('Tổng =', a + b)"
        val r = PythonExecutor(context).execute(code, ExecMode.RUN, user)
        assertFalse(r.output, r.isError)
        assertEquals("Nhập a: 3\nNhập b: 4\nTổng = 7", r.output.trim())
        assertEquals(listOf("Nhập a: ", "Nhập a: 3\nNhập b: "), user.screens)
    }

    @Test
    fun pythonEndOfInputIsExplained() {
        val r = PythonExecutor(context).execute("x = input('Nhập: ')\nprint(x)", ExecMode.RUN, FakeUser())
        assertTrue(r.output, r.output.contains("Đã kết thúc nhập"))
    }

    @Test
    fun javascriptPromptAsksLikeATerminal() {
        val user = FakeUser("21")
        val r = JavaScriptExecutor().execute("const n = Number(prompt('n = ')); console.log(n * 2);", ExecMode.RUN, user)
        assertFalse(r.output, r.isError)
        assertEquals("n = 21\n42", r.output.trim())
        assertEquals(listOf("n = "), user.screens)
    }

    // ---------- Đọc kết quả Wandbox ----------

    @Test
    fun compileErrorIsNotReportedAsSuccess() {
        // Đúng tình huống trong ảnh: 'b' chưa khai báo, máy chủ vẫn gửi kèm các trường program_*
        val res = JSONObject(
            """{"status":"1","program_message":"","program_output":"",
                "compiler_error":"prog.cc: In function 'int main()':\nprog.cc:8:9: error: 'b' was not declared in this scope\n"}"""
        )
        val r = WandboxApi.toResult(res, ExecMode.BUILD_AND_RUN)
        assertTrue(r.isError)
        assertTrue(r.output, r.output.startsWith("Lỗi khi dịch:"))
        assertFalse(r.output, r.output.contains("Dịch thành công"))
    }

    @Test
    fun warningsAloneAreSuccess() {
        val res = JSONObject("""{"status":"0","compiler_error":"prog.cc:4:9: warning: unused variable 'error'\n","program_output":"ok\n"}""")
        assertFalse(WandboxApi.compileFailed(res))
        assertFalse(WandboxApi.toResult(res, ExecMode.RUN).isError)
    }

    @Test
    fun markerMeansProgramNeedsInput() {
        val res = JSONObject().put("status", "0").put("program_output", "Nhập a: $marker")
        val r = WandboxApi.toResult(res, ExecMode.RUN)
        assertTrue(r.needsInput)
        assertEquals("Nhập a: ", r.output)
    }

    @Test
    fun echoSnippetKeepsUserLinesAndStopFlag() {
        val code = "#include <iostream>\nusing namespace std;\nint main(){ int a; cin >> a; cout << a; }"
        val prepared = WandboxApi.prepareCode(Language.CPP, code, Echo.CIN, stopAtEof = true)
        assertEquals(code.lines(), prepared.lines().take(code.lines().size))
        assertTrue(prepared.contains("#define CODELAB_STOP_AT_EOF_ 1"))
        assertTrue(WandboxApi.prepareCode(Language.C, "x", Echo.STDIO, stopAtEof = false).contains("#define CODELAB_STOP_AT_EOF_ 0"))
        assertEquals(code, WandboxApi.prepareCode(Language.CPP, code))
        assertEquals("-D_GNU_SOURCE", WandboxApi.compilerOptions(Language.C, Echo.STDIO))
        assertEquals("", WandboxApi.compilerOptions(Language.CPP, Echo.STDIO))
    }

    @Test
    fun detectsErrorsInsideAddedCode() {
        val inSnippet = JSONObject("""{"status":"1","compiler_error":"prog.cc:8:1: error: expected '}'\n"}""")
        val inUserCode = JSONObject("""{"status":"1","compiler_error":"prog.cc:3:5: error: 'x' was not declared\n"}""")
        assertTrue(WandboxApi.failedInAddedCode(inSnippet, userLineCount = 4))
        assertFalse(WandboxApi.failedInAddedCode(inUserCode, userLineCount = 4))
    }

    // ---------- Vòng chạy online với "máy chủ giả" ----------

    private val cppTwoInputs = "#include <iostream>\nusing namespace std;\nint main(){ int a, b; cout << \"Nhập a: \"; cin >> a; " +
        "cout << \"Nhập b: \"; cin >> b; cout << \"Tổng = \" << a + b << endl; }"

    /** Máy chủ giả mô phỏng chương trình C++ trên khi có đoạn code in lại dữ liệu nhập. */
    private fun fakeCppServer(sent: MutableList<String>) = { prepared: String, _: String, stdin: String ->
        sent += stdin
        val stop = prepared.contains("#define CODELAB_STOP_AT_EOF_ 1")
        val lines = stdin.lines().filter { it.isNotEmpty() }
        val out = StringBuilder("Nhập a: ")
        if (lines.isEmpty()) {
            out.append(if (stop) marker else "\nTổng = 0\n")
        } else {
            out.append(lines[0]).append("\nNhập b: ")
            if (lines.size < 2) out.append(if (stop) marker else "\nTổng = 0\n")
            else out.append(lines[1]).append("\nTổng = ").append(lines[0].toInt() + lines[1].toInt()).append('\n')
        }
        JSONObject().put("status", "0").put("program_output", out.toString())
    }

    @Test
    fun cppAsksForEachCinAndRerunsWithTypedLines() {
        val sent = mutableListOf<String>()
        val user = FakeUser("5", "7")
        val r = OnlineSession(Language.CPP, cppTwoInputs, ExecMode.RUN, user, fakeCppServer(sent)).run()
        assertFalse(r.output, r.isError)
        assertEquals("Nhập a: 5\nNhập b: 7\nTổng = 12", r.output)
        assertEquals(listOf("Nhập a: ", "Nhập a: 5\nNhập b: "), user.screens)
        assertEquals(listOf("", "5\n", "5\n7\n"), sent)
    }

    @Test
    fun endOfInputRunsOnceMoreWithoutStopping() {
        val sent = mutableListOf<String>()
        val r = OnlineSession(Language.CPP, cppTwoInputs, ExecMode.RUN, FakeUser(null), fakeCppServer(sent)).run()
        assertEquals("Nhập a: \nTổng = 0", r.output)
        assertEquals(listOf("", ""), sent)
    }

    @Test
    fun compileErrorIsShownWithoutAskingForInput() {
        val user = FakeUser("5")
        val r = OnlineSession(Language.CPP, cppTwoInputs, ExecMode.RUN, user) { _, _, _ ->
            JSONObject("""{"status":"1","compiler_error":"prog.cc:3:5: error: 'b' was not declared\n"}""")
        }.run()
        assertTrue(r.isError)
        assertTrue(user.screens.isEmpty())
    }

    @Test
    fun javaScannerAsksForEachValueAndShowsTypedValues() {
        val code = "import java.util.Scanner;\npublic class Main { public static void main(String[] a) { " +
            "Scanner sc = new Scanner(System.in); System.out.print(\"Nhap a: \"); int x = sc.nextInt(); " +
            "System.out.print(\"Nhap b: \"); int y = sc.nextInt(); System.out.println(\"Tong = \" + (x + y)); } }"
        val user = FakeUser("3", "4")
        val r = OnlineSession(Language.JAVA, code, ExecMode.RUN, user) { prepared, _, stdin ->
            assertFalse(prepared.contains("public class Main"))
            val n = stdin.lines().count { it.isNotEmpty() }
            when (n) {
                0 -> JSONObject().put("status", "1").put("program_output", "Nhap a: ")
                    .put("program_error", "Exception in thread \"main\" java.util.NoSuchElementException")
                1 -> JSONObject().put("status", "1").put("program_output", "Nhap a: Nhap b: ")
                    .put("program_error", "Exception in thread \"main\" java.util.NoSuchElementException")
                else -> JSONObject().put("status", "0").put("program_output", "Nhap a: Nhap b: Tong = 7\n")
            }
        }.run()
        assertFalse(r.output, r.isError)
        assertEquals("Nhap a: 3\nNhap b: 4\nTong = 7", r.output)
        assertEquals(listOf("Nhap a: ", "Nhap a: 3\nNhap b: "), user.screens)
    }

    @Test
    fun upfrontModeCollectsAllLinesThenRunsOnce() {
        val code = "#include <bits/stdc++.h>\nusing namespace std;\nint main(){ ios::sync_with_stdio(false); int a, b; cin >> a >> b; cout << a * b; }"
        val sent = mutableListOf<String>()
        val r = OnlineSession(Language.CPP, code, ExecMode.RUN, FakeUser("6", "7", null)) { prepared, _, stdin ->
            assertFalse(prepared.contains("codelab_echo_"))
            sent += stdin
            JSONObject().put("status", "0").put("program_output", "42")
        }.run()
        assertEquals(listOf("6\n7\n"), sent)
        assertEquals("📥 Dữ liệu nhập:\n6\n7\n──── Kết quả chạy ────\n42", r.output)
    }
}
