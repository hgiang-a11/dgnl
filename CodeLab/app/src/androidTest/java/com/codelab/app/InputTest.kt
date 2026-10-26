package com.codelab.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codelab.app.exec.ExecMode
import com.codelab.app.exec.InputDetector
import com.codelab.app.exec.JavaScriptExecutor
import com.codelab.app.exec.PythonExecutor
import com.codelab.app.exec.WandboxApi
import com.codelab.app.model.Language
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Kiểm thử tính năng nhập dữ liệu cho chương trình (cin, scanf, input(), prompt()...). */
@RunWith(AndroidJUnit4::class)
class InputTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun detectsInputStatements() {
        assertTrue(InputDetector.needsInput(Language.CPP, "int a; cin >> a;"))
        assertTrue(InputDetector.needsInput(Language.C, "scanf(\"%d\", &a);"))
        assertTrue(InputDetector.needsInput(Language.PYTHON, "n = int(input('n: '))"))
        assertTrue(InputDetector.needsInput(Language.JAVA, "Scanner sc = new Scanner(System.in);"))
        assertTrue(InputDetector.needsInput(Language.JAVASCRIPT, "var n = prompt('n');"))
        // Không có lệnh nhập, hoặc chỉ nằm trong chú thích
        assertFalse(InputDetector.needsInput(Language.CPP, "cout << \"hi\";"))
        assertFalse(InputDetector.needsInput(Language.CPP, "// cin >> a;\ncout << 1;"))
        assertFalse(InputDetector.needsInput(Language.PYTHON, "# input()\nprint(1)"))
        assertFalse(InputDetector.needsInput(Language.CPP, Language.CPP.template))
    }

    @Test
    fun decidesWhenOnlineEchoIsPossible() {
        assertTrue(InputDetector.canEchoOnline(Language.CPP, "cin >> a;"))
        assertTrue(InputDetector.canEchoOnline(Language.C, "scanf(\"%d\", &a);"))
        assertFalse(InputDetector.canEchoOnline(Language.CPP, "ios::sync_with_stdio(false); cin >> a;"))
        assertFalse(InputDetector.canEchoOnline(Language.CPP, "scanf(\"%d\", &a);"))
        assertFalse(InputDetector.canEchoOnline(Language.JAVA, "new Scanner(System.in)"))
    }

    @Test
    fun pythonReadsInputAndEchoesIt() {
        val code = "a = input('Tên: ')\nb = int(input('Tuổi: '))\nprint('Chào', a, b)"
        val r = PythonExecutor(context).execute(code, ExecMode.RUN, "An\n20")
        assertFalse(r.output, r.isError)
        assertEquals("Tên: An\nTuổi: 20\nChào An 20", r.output.trim())
    }

    @Test
    fun pythonExplainsMissingInput() {
        val r = PythonExecutor(context).execute("x = input('Nhập: ')\nprint(x)", ExecMode.RUN, "")
        assertTrue(r.output, r.output.contains("ô nhập đã hết"))
    }

    @Test
    fun javascriptPromptReadsInputAndEchoesIt() {
        val code = "const n = Number(prompt('n = ')); console.log(n * 2);"
        val r = JavaScriptExecutor().execute(code, ExecMode.RUN, "21\n")
        assertFalse(r.output, r.isError)
        assertEquals("n = 21\n42", r.output.trim())
    }

    @Test
    fun echoSnippetKeepsUserLinesIntact() {
        val code = "#include <iostream>\nusing namespace std;\nint main(){ int a; cin >> a; cout << a; }"
        val prepared = WandboxApi.prepareCode(Language.CPP, code, echoInput = true)
        assertEquals(code.lines(), prepared.lines().take(code.lines().size))
        assertTrue(prepared.contains("codelab_echo_"))
        assertEquals(code, WandboxApi.prepareCode(Language.CPP, code, echoInput = false))
        assertEquals("-D_GNU_SOURCE", WandboxApi.compilerOptions(Language.C, echoInput = true))
        assertEquals("", WandboxApi.compilerOptions(Language.CPP, echoInput = true))
    }

    @Test
    fun detectsErrorsInsideAddedCode() {
        val inSnippet = JSONObject("""{"status":"1","compiler_error":"prog.cc:8:1: error: expected '}'\n"}""")
        val inUserCode = JSONObject("""{"status":"1","compiler_error":"prog.cc:3:5: error: 'x' was not declared\n"}""")
        assertTrue(WandboxApi.failedInAddedCode(inSnippet, userLineCount = 4))
        assertFalse(WandboxApi.failedInAddedCode(inUserCode, userLineCount = 4))
    }

    @Test
    fun showsInputBlockWhenNotEchoed() {
        val res = JSONObject("""{"status":"0","program_output":"Tong = 15\n"}""")
        val r = WandboxApi.toResult(res, ExecMode.RUN, shownInput = "7 8\n")
        assertEquals("📥 Dữ liệu nhập:\n7 8\n──── Kết quả chạy ────\nTong = 15", r.output)
    }
}
