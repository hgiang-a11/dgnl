# File Python này được nhúng vào app và chạy ngay trên điện thoại (offline).
# Nhiệm vụ: nhận chuỗi code của người dùng, chạy nó và thu lại mọi thứ nó in ra.

import builtins
import io
import sys
import traceback


def run_code(source, provider=None):
    """Chạy code và trả về toàn bộ nội dung in ra (kể cả báo lỗi nếu có).

    provider: nguồn dữ liệu nhập (đối tượng Java InputProvider). Khi chương trình gọi
    input(), hàm provider.requestLine(...) được gọi và CHỜ người dùng gõ một dòng ở màn hình.
    """
    buffer = io.StringIO()
    old_stdout, old_stderr, old_stdin = sys.stdout, sys.stderr, sys.stdin
    old_input = builtins.input
    state = {"eof": False}

    def read_line():
        # Hỏi người dùng một dòng; giá trị gõ vào được in lại (giống màn hình dòng lệnh)
        line = provider.requestLine(buffer.getvalue()) if provider is not None else None
        if line is None:
            state["eof"] = True
            return None
        line = str(line)
        buffer.write(line + "\n")
        return line

    def input_with_echo(prompt=""):
        buffer.write(str(prompt))
        line = read_line()
        if line is None:
            raise EOFError("EOF when reading a line")
        return line

    class InteractiveStdin(io.TextIOBase):
        """sys.stdin đọc từng dòng từ người dùng (cho sys.stdin.readline(), for line in sys.stdin...)."""

        def readable(self):
            return True

        def readline(self, size=-1):
            line = read_line()
            return "" if line is None else line + "\n"

        def read(self, size=-1):
            parts = []
            while True:
                line = self.readline()
                if line == "":
                    return "".join(parts)
                parts.append(line)

    builtins.input = input_with_echo
    sys.stdin = InteractiveStdin()
    sys.stdout = buffer
    sys.stderr = buffer
    try:
        compiled = compile(source, "<main>", "exec")
        # Chạy trong một không gian biến riêng, giả lập như chạy file chính
        exec(compiled, {"__name__": "__main__"})
    except SystemExit:
        pass
    except EOFError:
        if state["eof"]:
            buffer.write("\n⚠ Đã kết thúc nhập dữ liệu nhưng chương trình vẫn cần nhập thêm.\n")
        else:
            traceback.print_exc()
    except BaseException:
        # In lỗi ra buffer để người dùng nhìn thấy
        traceback.print_exc()
    finally:
        sys.stdout, sys.stderr, sys.stdin = old_stdout, old_stderr, old_stdin
        builtins.input = old_input
    text = buffer.getvalue()
    return text if text else "(chương trình không in ra gì)"


def check_syntax(source):
    """Chỉ kiểm tra lỗi cú pháp, KHÔNG chạy. Trả về chuỗi rỗng nếu không có lỗi."""
    try:
        compile(source, "<main>", "exec")
        return ""
    except SyntaxError as e:
        line = e.lineno if e.lineno is not None else "?"
        return "Lỗi cú pháp ở dòng {}: {}".format(line, e.msg)
    except Exception as e:
        return "Lỗi: {}".format(e)
