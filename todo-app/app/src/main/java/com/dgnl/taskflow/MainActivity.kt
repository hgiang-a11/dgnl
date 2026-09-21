package com.dgnl.taskflow

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dgnl.taskflow.ui.TaskFlowRoot
import com.dgnl.taskflow.ui.theme.TaskFlowTheme

class MainActivity : ComponentActivity() {

    private val repository by lazy { (application as TaskFlowApp).repository }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            TaskFlowTheme {
                TaskFlowRoot(repository = repository)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Ghi ngay du lieu xuong may khi app chuyen sang chay nen.
        repository.flush()
    }
}
