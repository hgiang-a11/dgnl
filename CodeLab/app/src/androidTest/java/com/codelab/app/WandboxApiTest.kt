package com.codelab.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.codelab.app.exec.ExecMode
import com.codelab.app.exec.WandboxApi
import com.codelab.app.model.Language
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kiểm thử phần xử lý dữ liệu của trình dịch online Wandbox
 * bằng dữ liệu mẫu (không gọi mạng).
 */
@RunWith(AndroidJUnit4::class)
class WandboxApiTest {

    private val list = JSONArray(
        """
        [
          {"name": "gcc-head-pp", "language": "CPP"},
          {"name": "gcc-13.2.0", "language": "C++"},
          {"name": "gcc-head", "language": "C++"},
          {"name": "clang-head", "language": "C++"},
          {"name": "gcc-head-c", "language": "C"},
          {"name": "openjdk-jdk-21+35", "language": "Java"},
          {"name": "openjdk-jdk-22+36", "language": "Java"},
          {"name": "openjdk-jdk-15.0.3+2", "language": "Java"}
        ]
        """
    )

    @Test
    fun picksPreferredCompilers() {
        assertEquals("gcc-head", WandboxApi.pickCompiler(list, Language.CPP))
        assertEquals("gcc-head-c", WandboxApi.pickCompiler(list, Language.C))
    }

    @Test
    fun picksNewestJava() {
        assertEquals("openjdk-jdk-22+36", WandboxApi.pickCompiler(list, Language.JAVA))
    }

    @Test
    fun fallsBackToNewestWhenPreferredMissing() {
        val onlyVersions = JSONArray(
            """[{"name":"gcc-12.1.0","language":"C++"},{"name":"gcc-13.2.0","language":"C++"},
                {"name":"gcc-head-pp","language":"CPP"}]"""
        )
        assertEquals("gcc-13.2.0", WandboxApi.pickCompiler(onlyVersions, Language.CPP))
        assertNull(WandboxApi.pickCompiler(onlyVersions, Language.JAVA))
    }

    @Test
    fun javaPublicClassIsMadeNonPublic() {
        val prepared = WandboxApi.prepareCode(Language.JAVA, Language.JAVA.template)
        assertFalse(prepared, prepared.contains("public class Main"))
        assertTrue(prepared, prepared.contains("class Main"))
        // Hàm main vẫn phải là public
        assertTrue(prepared, prepared.contains("public static void main"))
        // Không thêm/bớt dòng -> báo lỗi vẫn đúng số dòng
        assertEquals(Language.JAVA.template.lines().size, prepared.lines().size)
    }

    @Test
    fun cppCodeIsUnchanged() {
        assertEquals(Language.CPP.template, WandboxApi.prepareCode(Language.CPP, Language.CPP.template))
    }

    @Test
    fun successfulRunShowsOutput() {
        val res = JSONObject("""{"status":"0","program_message":"Hi\n","program_output":"Hi\n"}""")
        val r = WandboxApi.toResult(res, ExecMode.RUN)
        assertFalse(r.output, r.isError)
        assertEquals("Hi", r.output)
    }

    @Test
    fun compileErrorIsReported() {
        val res = JSONObject(
            """{"status":"1","compiler_error":"prog.cc:3:5: error: 'x' was not declared in this scope\n",
                "compiler_message":"prog.cc:3:5: error: 'x' was not declared in this scope\n"}"""
        )
        val run = WandboxApi.toResult(res, ExecMode.RUN)
        assertTrue(run.isError)
        assertTrue(run.output, run.output.startsWith("Lỗi khi dịch") && run.output.contains("prog.cc:3:5"))
        assertTrue(WandboxApi.toResult(res, ExecMode.BUILD_ONLY).isError)
    }

    @Test
    fun buildOnlyWithWarningIsNotAnError() {
        val res = JSONObject(
            """{"status":"0","compiler_error":"prog.cc:4:9: warning: unused variable 'y'\n",
                "program_output":"ok\n"}"""
        )
        val r = WandboxApi.toResult(res, ExecMode.BUILD_ONLY)
        assertFalse(r.output, r.isError)
        assertTrue(r.output, r.output.contains("cảnh báo"))
    }

    @Test
    fun buildAndRunShowsBothParts() {
        val res = JSONObject("""{"status":"0","program_output":"15\n"}""")
        val r = WandboxApi.toResult(res, ExecMode.BUILD_AND_RUN)
        assertTrue(r.output, r.output.contains("Dịch thành công") && r.output.contains("15"))
    }

    @Test
    fun runtimeErrorAndCrashAreReported() {
        val exit = WandboxApi.toResult(JSONObject("""{"status":"3","program_error":"oops\n"}"""), ExecMode.RUN)
        assertTrue(exit.isError)
        assertTrue(exit.output, exit.output.contains("oops") && exit.output.contains("3"))

        val crash = WandboxApi.toResult(
            JSONObject("""{"status":"","signal":"Segmentation fault","program_output":"a\n"}"""), ExecMode.RUN
        )
        assertTrue(crash.isError)
        assertTrue(crash.output, crash.output.contains("Segmentation fault"))
    }
}
