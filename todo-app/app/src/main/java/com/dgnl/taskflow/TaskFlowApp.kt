package com.dgnl.taskflow

import android.app.Application
import com.dgnl.taskflow.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class TaskFlowApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var repository: TaskRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = TaskRepository(this, appScope)
    }
}
