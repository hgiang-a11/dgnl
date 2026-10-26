package com.codelab.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.codelab.app.ui.EditorFragment
import com.codelab.app.ui.HomeFragment

/**
 * Màn hình gốc của app. Chứa một khung (container) để hiển thị lần lượt:
 *  - HomeFragment: danh sách project.
 *  - EditorFragment: màn hình soạn thảo một project.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Vẽ tràn cả màn hình (sau thanh trạng thái/điều hướng trong suốt, biểu tượng màu sáng).
        // Nhờ vậy màn hình KHÔNG bị thu nhỏ khi mở bàn phím: mỗi màn hình tự quyết định phần
        // nào bị che (xem EditorFragment.applyInsets).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Lần đầu mở app thì hiển thị màn hình chính
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, HomeFragment())
                .commit()
        }
    }

    /** Mở màn hình soạn thảo cho một project. */
    fun openEditor(projectId: String) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                android.R.anim.fade_in, android.R.anim.fade_out,
                android.R.anim.fade_in, android.R.anim.fade_out
            )
            .replace(R.id.container, EditorFragment.newInstance(projectId))
            .addToBackStack("editor")
            .commit()
    }
}
