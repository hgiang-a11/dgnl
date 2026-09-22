package vn.hgiang.sochitieu;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Màn hình duy nhất của Sổ chi tiêu: một khung web hiện giao diện trong thư mục
 * assets, cộng một cầu nối để giao diện đọc ghi dữ liệu vào bộ nhớ riêng của app
 * và lưu file ra ngoài (sao lưu, xuất bảng tính).
 */
public class MainActivity extends Activity {

    private static final int MA_CHON_FILE = 1;
    private static final int MA_LUU_FILE = 2;

    private static final String TRANG_CHINH = "file:///android_asset/index.html";
    private static final String FILE_DU_LIEU = "so_chi_tieu.json";
    private static final String FILE_BAN_TRUOC = "so_chi_tieu.truoc.json";
    private static final String FILE_TAM = "so_chi_tieu.tam";

    private final Object khoaFile = new Object();

    private WebView web;
    private ValueCallback<Uri[]> choChonFile;
    private String noiDungChoLuu;

    @Override
    protected void onCreate(Bundle trangThai) {
        super.onCreate(trangThai);
        toMauThanhHeThong();

        web = new WebView(this);
        web.setBackgroundColor(0xFFEEF1F6);

        WebSettings caiDat = web.getSettings();
        caiDat.setJavaScriptEnabled(true);
        caiDat.setDomStorageEnabled(true);
        caiDat.setAllowContentAccess(false);
        caiDat.setTextZoom(tyLeChu());

        web.addJavascriptInterface(new CauNoi(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                if (url.startsWith("file:///android_asset/")) return false;
                // Liên kết ra ngoài thì mở bằng trình duyệt
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException e) {
                    // không có app nào mở được thì bỏ qua
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> traVe,
                                             FileChooserParams thamSo) {
                if (choChonFile != null) choChonFile.onReceiveValue(null);
                choChonFile = traVe;
                Intent chon = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                chon.addCategory(Intent.CATEGORY_OPENABLE);
                chon.setType("*/*");
                try {
                    startActivityForResult(chon, MA_CHON_FILE);
                } catch (ActivityNotFoundException e) {
                    choChonFile = null;
                    return false;
                }
                return true;
            }
        });

        setContentView(web);
        web.loadUrl(TRANG_CHINH);
    }

    /** Thanh trạng thái màu xanh đậm như dải tiêu đề, thanh điều hướng màu trắng */
    private void toMauThanhHeThong() {
        Window w = getWindow();
        w.setStatusBarColor(0xFF1F3864);
        if (Build.VERSION.SDK_INT >= 26) {
            w.setNavigationBarColor(0xFFFFFFFF);
            // 0x10 là SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR: nút điều hướng màu tối trên nền trắng
            w.getDecorView().setSystemUiVisibility(0x10);
        } else {
            w.setNavigationBarColor(0xFF1F3864);
        }
    }

    /** Theo cỡ chữ của máy nhưng giới hạn để bảng không vỡ khung */
    private int tyLeChu() {
        float coChu = getResources().getConfiguration().fontScale;
        return Math.round(Math.max(0.85f, Math.min(coChu, 1.2f)) * 100);
    }

    @Override
    public void onBackPressed() {
        // Để giao diện tự đóng bảng nhập hoặc quay về trang ngày trước khi thoát
        web.evaluateJavascript("window.xuLyQuayLai ? window.xuLyQuayLai() : false",
            new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String ketQua) {
                    if (!"true".equals(ketQua)) MainActivity.super.onBackPressed();
                }
            });
    }

    @Override
    protected void onActivityResult(int ma, int ketQua, Intent duLieu) {
        if (ma == MA_CHON_FILE) {
            if (choChonFile != null) {
                choChonFile.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(ketQua, duLieu));
                choChonFile = null;
            }
            return;
        }
        if (ma == MA_LUU_FILE) {
            String kq = "huy";
            if (ketQua == RESULT_OK && duLieu != null && duLieu.getData() != null
                    && noiDungChoLuu != null) {
                kq = ghiRaUri(duLieu.getData(), noiDungChoLuu) ? "ok" : "loi";
            }
            noiDungChoLuu = null;
            web.evaluateJavascript("window.daLuuFile && window.daLuuFile('" + kq + "')", null);
            return;
        }
        super.onActivityResult(ma, ketQua, duLieu);
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    private boolean ghiRaUri(Uri uri, String noiDung) {
        OutputStream ra = null;
        try {
            ra = getContentResolver().openOutputStream(uri, "w");
            if (ra == null) return false;
            ra.write(noiDung.getBytes(StandardCharsets.UTF_8));
            ra.flush();
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        } finally {
            dong(ra);
        }
    }

    private static String docHet(File f) throws IOException {
        try (InputStream vao = new FileInputStream(f)) {
            ByteArrayOutputStream gom = new ByteArrayOutputStream();
            byte[] dem = new byte[8192];
            int n;
            while ((n = vao.read(dem)) > 0) gom.write(dem, 0, n);
            return new String(gom.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void dong(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException e) {
            // đã ghi xong, lỗi khi đóng thì bỏ qua
        }
    }

    /** Các hàm giao diện gọi được qua window.Android */
    public class CauNoi {

        @JavascriptInterface
        public String docDuLieu() {
            synchronized (khoaFile) {
                File chinh = new File(getFilesDir(), FILE_DU_LIEU);
                File truoc = new File(getFilesDir(), FILE_BAN_TRUOC);
                try {
                    if (chinh.exists()) return docHet(chinh);
                    if (truoc.exists()) return docHet(truoc);
                } catch (IOException e) {
                    // đọc lỗi thì coi như chưa có dữ liệu
                }
                return null;
            }
        }

        /** Ghi ra file tạm rồi mới thay file chính, giữ lại một bản trước đó */
        @JavascriptInterface
        public boolean ghiDuLieu(String json) {
            synchronized (khoaFile) {
                File thuMuc = getFilesDir();
                File chinh = new File(thuMuc, FILE_DU_LIEU);
                File truoc = new File(thuMuc, FILE_BAN_TRUOC);
                File tam = new File(thuMuc, FILE_TAM);

                FileOutputStream ra = null;
                try {
                    ra = new FileOutputStream(tam);
                    ra.write(json.getBytes(StandardCharsets.UTF_8));
                    ra.getFD().sync();
                } catch (IOException e) {
                    dong(ra);
                    tam.delete();
                    return false;
                }
                dong(ra);

                if (chinh.exists()) {
                    truoc.delete();
                    if (!chinh.renameTo(truoc)) chinh.delete();
                }
                return tam.renameTo(chinh);
            }
        }

        /** Mở hộp chọn chỗ lưu của Android, lưu xong báo lại qua window.daLuuFile */
        @JavascriptInterface
        public void luuFile(final String ten, final String loai, final String noiDung) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    noiDungChoLuu = noiDung;
                    Intent luu = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    luu.addCategory(Intent.CATEGORY_OPENABLE);
                    luu.setType(loai);
                    luu.putExtra(Intent.EXTRA_TITLE, ten);
                    try {
                        startActivityForResult(luu, MA_LUU_FILE);
                    } catch (ActivityNotFoundException e) {
                        noiDungChoLuu = null;
                        web.evaluateJavascript("window.daLuuFile && window.daLuuFile('loi')", null);
                    }
                }
            });
        }
    }
}
