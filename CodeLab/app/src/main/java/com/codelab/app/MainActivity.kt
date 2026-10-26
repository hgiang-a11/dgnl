package com.codelab.app

import android.os.Bundle
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
            .replace(R.id.container, EditorFragment.newInstance(projectId))
            .addToBackStack("editor")
            .commit()
    }
}
