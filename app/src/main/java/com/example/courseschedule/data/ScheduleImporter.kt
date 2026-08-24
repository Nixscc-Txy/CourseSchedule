package com.example.courseschedule.data

import android.content.Context
import com.example.courseschedule.model.ScheduleData
import java.io.File

/**
 * 课表导入器:
 *  - 识别文件格式 (教务系统 .xls / schedule.json)
 *  - 解析为 ScheduleData
 *  - 保存"导入的课表" (App 私有存储, 优先于内置 assets 课表)
 */
object ScheduleImporter {

    const val IMPORTED_FILE = "imported_schedule.json"

    private val OLE2_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    )

    private fun isOle2(bytes: ByteArray): Boolean {
        if (bytes.size < OLE2_MAGIC.size) return false
        for (i in OLE2_MAGIC.indices) {
            if (bytes[i] != OLE2_MAGIC[i]) return false
        }
        return true
    }

    /** 解析导入文件: 支持教务系统导出的 .xls 和 schedule.json */
    fun parse(bytes: ByteArray): ScheduleData {
        return if (isOle2(bytes)) {
            val grid = XlsReader.readSheetGrid(bytes)
            MatrixScheduleParser.parse(grid)
        } else {
            val text = String(bytes, Charsets.UTF_8).trim().removePrefix("\uFEFF")
            if (text.startsWith("{")) {
                JsonScheduleParser.parse(text)
            } else {
                throw IllegalArgumentException("无法识别的文件格式，请选择教务系统导出的 .xls 课表或 schedule.json")
            }
        }
    }

    private fun importedFile(context: Context): File = File(context.filesDir, IMPORTED_FILE)

    fun save(context: Context, data: ScheduleData) {
        importedFile(context).writeText(JsonScheduleParser.toJson(data))
    }

    fun readImported(context: Context): String? {
        val f = importedFile(context)
        return if (f.exists()) f.readText() else null
    }
}
