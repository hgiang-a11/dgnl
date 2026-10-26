# File Python này được nhúng vào app và chạy ngay trên điện thoại (offline).
# Nhiệm vụ: nhận chuỗi code của người dùng, chạy nó và thu lại mọi thứ nó in ra.

import builtins
import io
import sys
import traceback


def run_code(source, stdin_text=""):
    """Chạy code và trả về toàn bộ nội dung in ra (kể cả báo lỗi nếu có).

    stdin_text: dữ liệu nhập cho input(), người dùng gõ trước khi chạy.
    """
    buffer = io.StringIO()
    old_stdout, old_stderr, old_stdin = sys.stdout, sys.stderr, sys.stdin
    old_input = builtins.input

    if stdin_text and not stdin_text.endswith("\n"):
        stdin_text += "\n"
    sys.stdin = io.StringIO(stdin_text or "")

    def input_with_echo(prompt=""):
        # In câu hỏi, đọc một dòng dữ liệu nhập và in lại giá trị đó
        # (giống khi gõ trên màn hình dòng lệnh)
        sys.stdout.write(str(prompt))
        line = sys.stdin.readline()
        if line == "":
            raise EOFError("EOF when reading a line")
        sys.stdout.write(line)
        return line.rstrip("\n")

    builtins.input = input_with_echo
    sys.stdout = buffer
    sys.stderr = buffer
    try:
        compiled = compile(source, "<main>", "exec")
        # Chạy trong một không gian biến riêng, giả lập như chạy file chính
        exec(compiled, {"__name__": "__main__"})
    except SystemExit:
        pass
    except EOFError:
        buffer.write("\n⚠ Chương trình cần nhập thêm dữ liệu nhưng ô nhập đã hết. "
                     "Hãy bấm Chạy lại và gõ thêm dữ liệu.\n")
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
