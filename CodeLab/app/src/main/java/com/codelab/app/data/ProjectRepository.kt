package com.codelab.app.data

import android.content.Context
import com.codelab.app.model.Project
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Nơi lưu và đọc danh sách project.
 *
 * Toàn bộ dữ liệu được ghi vào một file văn bản (projects.json) nằm trong
 * bộ nhớ riêng của app trên máy. Vì vậy hoạt động HOÀN TOÀN OFFLINE,
 * không cần Internet, và dữ liệu vẫn còn khi tắt mở lại app.
 */
class ProjectRepository private constructor(context: Context) {

    private val gson = Gson()
    // File lưu trữ nằm trong thư mục riêng của app (không ai khác đọc được)
    private val file = File(context.filesDir, "projects.json")

    // Giữ sẵn danh sách trong bộ nhớ để truy cập nhanh
    private val projects: MutableList<Project> = loadFromDisk()

    /** Đọc danh sách từ file trên máy. */
    private fun loadFromDisk(): MutableList<Project> {
        if (!file.exists()) return mutableListOf()
        return try {
            val json = file.readText()
            if (json.isBlank()) return mutableListOf()
            val type = object : TypeToken<MutableList<Project>>() {}.type
            gson.fromJson<MutableList<Project>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            // Nếu file hỏng, bắt đầu lại với danh sách rỗng thay vì làm sập app
            mutableListOf()
        }
    }

    /** Ghi danh sách hiện tại xuống file trên máy. */
    private fun saveToDisk() {
        try {
            file.writeText(gson.toJson(projects))
        } catch (e: Exception) {
            // Bỏ qua lỗi ghi file để không làm sập app
        }
    }

    /** Lấy tất cả project, sắp xếp project sửa gần đây nhất lên đầu. */
    fun getAll(): List<Project> = projects.sortedByDescending { it.updatedAt }

    /** Lấy một project theo mã id. */
    fun get(id: String): Project? = projects.firstOrNull { it.id == id }

    /** Thêm project mới. */
    fun add(project: Project) {
        projects.add(project)
        saveToDisk()
    }

    /** Cập nhật project đã có (thay thế theo id). */
    fun update(project: Project) {
        val index = projects.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            project.updatedAt = System.currentTimeMillis()
            projects[index] = project
            saveToDisk()
        }
    }

    /** Chỉ cập nhật phần code của một project (dùng khi bấm Lưu). */
    fun saveCode(id: String, code: String) {
        val p = get(id) ?: return
        p.code = code
        p.updatedAt = System.currentTimeMillis()
        saveToDisk()
    }

    /** Xóa một project. */
    fun delete(id: String) {
        projects.removeAll { it.id == id }
        saveToDisk()
    }

    companion object {
        @Volatile
        private var instance: ProjectRepository? = null

        /** Lấy đối tượng kho dữ liệu dùng chung cho toàn app. */
        fun get(context: Context): ProjectRepository {
            return instance ?: synchronized(this) {
                instance ?: ProjectRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
