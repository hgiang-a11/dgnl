# File Python này được nhúng vào app và chạy ngay trên điện thoại (offline).
# Nhiệm vụ: nhận chuỗi code của người dùng, chạy nó và thu lại mọi thứ nó in ra.

import sys
import io
import traceback


def run_code(source):
    """Chạy code và trả về toàn bộ nội dung in ra (kể cả báo lỗi nếu có)."""
    buffer = io.StringIO()
    old_stdout, old_stderr = sys.stdout, sys.stderr
    sys.stdout = buffer
    sys.stderr = buffer
    try:
        compiled = compile(source, "<main>", "exec")
        # Chạy trong một không gian biến riêng, giả lập như chạy file chính
        exec(compiled, {"__name__": "__main__"})
    except SystemExit:
        pass
    except BaseException:
        # In lỗi ra buffer để người dùng nhìn thấy
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
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
