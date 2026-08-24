package com.example.courseschedule.data

import kotlin.math.min

/**
 * 极简 OLE2 复合文档 + BIFF8 读取器。
 *
 * 只实现读取"第一个工作表"的文本单元格 —— 这正是教务系统导出的 .xls 课表
 * (行=时间段、列=星期、单元格=课程字符串) 所需的最小功能集。
 * 纯 JVM 代码，不依赖 Android API，方便单元测试。
 *
 * 支持的 BIFF 记录: BOF/EOF, SST(+CONTINUE), LABELSST, LABEL, NUMBER, RK, MULRK, BOOLERR, BLANK
 */
object XlsReader {

    private const val ENDOFCHAIN: Int = -2   // 0xFFFFFFFE
    private const val FREESECT: Int = -1     // 0xFFFFFFFF
    private const val MINI_CUTOFF = 4096

    /** 读取第一个工作表的文本网格 (行 x 列)，空单元格为 "" */
    fun readSheetGrid(bytes: ByteArray): List<List<String>> {
        val cfb = Cfb(bytes)
        val workbook = cfb.readStream("Workbook") ?: cfb.readStream("Book")
            ?: throw IllegalArgumentException("文件中找不到课表数据（Workbook 流）")
        val cells = readFirstSheetCells(workbook)
        if (cells.isEmpty()) throw IllegalArgumentException("文件中没有可读取的单元格")
        val maxRow = cells.keys.maxOrNull() ?: 0
        val maxCol = cells.values.flatMap { it.keys }.maxOrNull() ?: 0
        return (0..maxRow).map { r ->
            val rowMap = cells[r]
            (0..maxCol).map { c -> rowMap?.get(c) ?: "" }
        }
    }

    // ------------------------------------------------------------------ BIFF8

    private fun readFirstSheetCells(workbook: ByteArray): HashMap<Int, HashMap<Int, String>> {
        val cells = HashMap<Int, HashMap<Int, String>>()
        val n = workbook.size
        var pos = 0
        var inSheet = false
        var sst: List<String>? = null

        while (pos + 4 <= n) {
            val type = leUShort(workbook, pos)
            val len = leUShort(workbook, pos + 2)
            val recStart = pos + 4
            val recEnd = recStart + len
            if (recEnd > n) break

            when (type) {
                0x0809 -> { // BOF
                    val dt = if (recEnd - recStart >= 4) leUShort(workbook, recStart + 2) else 0
                    inSheet = dt == 0x0010 // 0x0010 = worksheet, 0x0005 = workbook globals
                }
                0x000A -> { // EOF: 只关心第一个工作表
                    if (inSheet) break
                }
                0x00FC -> { // SST (共享字符串表)
                    val chunks = ArrayList<ByteArray>()
                    chunks.add(workbook.copyOfRange(recStart, recEnd))
                    var p = recEnd
                    while (p + 4 <= n && leUShort(workbook, p) == 0x003C) { // CONTINUE
                        val clen = leUShort(workbook, p + 2)
                        if (p + 4 + clen > n) break
                        chunks.add(workbook.copyOfRange(p + 4, p + 4 + clen))
                        p += 4 + clen
                    }
                    sst = parseSst(chunks)
                    pos = p
                    continue
                }
                0x00FD -> if (inSheet && sst != null) { // LABELSST
                    val row = leUShort(workbook, recStart)
                    val col = leUShort(workbook, recStart + 2)
                    val isst = leInt(workbook, recStart + 6)
                    putCell(cells, row, col, sst.getOrNull(isst) ?: "")
                }
                0x0204 -> if (inSheet) { // LABEL (BIFF5 风格字符串单元格)
                    val row = leUShort(workbook, recStart)
                    val col = leUShort(workbook, recStart + 2)
                    putCell(cells, row, col, parseUnicodeString(workbook, recStart + 8, recEnd))
                }
                0x0203 -> if (inSheet) { // NUMBER
                    val row = leUShort(workbook, recStart)
                    val col = leUShort(workbook, recStart + 2)
                    putCell(cells, row, col, formatNumber(leDouble(workbook, recStart + 6)))
                }
                0x027E -> if (inSheet) { // RK
                    val row = leUShort(workbook, recStart)
                    val col = leUShort(workbook, recStart + 2)
                    putCell(cells, row, col, formatNumber(decodeRk(leInt(workbook, recStart + 6))))
                }
                0x00BD -> if (inSheet) { // MULRK
                    val row = leUShort(workbook, recStart)
                    var col = leUShort(workbook, recStart + 2)
                    var p = recStart + 4
                    while (p + 6 <= recEnd - 2) {
                        putCell(cells, row, col, formatNumber(decodeRk(leInt(workbook, p + 2))))
                        col++
                        p += 6
                    }
                }
                0x0205 -> if (inSheet) { // BOOLERR
                    val row = leUShort(workbook, recStart)
                    val col = leUShort(workbook, recStart + 2)
                    val v = workbook[recStart + 6].toInt() and 0xFF
                    val isErr = workbook[recStart + 7].toInt() and 0xFF
                    putCell(cells, row, col, if (isErr != 0) "#ERR" else if (v != 0) "TRUE" else "FALSE")
                }
            }
            pos = recEnd
        }
        return cells
    }

    private fun putCell(cells: HashMap<Int, HashMap<Int, String>>, row: Int, col: Int, text: String) {
        if (text.isEmpty()) return
        cells.getOrPut(row) { HashMap() }.putIfAbsent(col, text)
    }

    private fun parseSst(chunks: List<ByteArray>): List<String> {
        if (chunks.isEmpty() || chunks[0].size < 8) return emptyList()
        val cstUnique = leInt(chunks[0], 4)
        val reader = SstReader(chunks)
        val result = ArrayList<String>(cstUnique.coerceAtLeast(0))
        repeat(cstUnique.coerceAtLeast(0)) {
            result.add(reader.readString())
        }
        return result
    }

    /** 跨记录(含 CONTINUE)读取 SST 字符串；字符串数据跨记录时按规范跳过选项字节 */
    private class SstReader(private val chunks: List<ByteArray>) {
        private var ci = 0
        private var pos = if (chunks[0].size >= 8) 8 else chunks[0].size // 跳过 cstTotal/cstUnique
        private var inString = false

        private fun hasRoom(need: Int): Boolean {
            while (ci < chunks.size && pos + need > chunks[ci].size) {
                ci++
                pos = 0
                if (ci < chunks.size && inString) pos = 1 // 跨记录的字符串数据前有 1 字节选项
            }
            return ci < chunks.size && pos + need <= chunks[ci].size
        }

        private fun u16(): Int {
            if (!hasRoom(2)) return 0
            val v = leUShort(chunks[ci], pos)
            pos += 2
            return v
        }

        private fun u8(): Int {
            if (!hasRoom(1)) return 0
            val v = chunks[ci][pos].toInt() and 0xFF
            pos += 1
            return v
        }

        private fun u32(): Int {
            if (!hasRoom(4)) return 0
            val v = leInt(chunks[ci], pos)
            pos += 4
            return v
        }

        private fun skipBytes(n: Int) {
            var left = n
            while (left > 0) {
                if (!hasRoom(1)) return
                val take = min(left, chunks[ci].size - pos)
                pos += take
                left -= take
            }
        }

        fun readString(): String {
            var cch = u16()
            val grbit = u8()
            // 本课表导出器的字符串标志约定 (与 xlrd 一致):
            //   bit0 (0x01) = 双字节 UTF-16LE (而非 MS-XLS 的 bit3)
            //   bit2 (0x04) = 带扩展/拼音数据 (字符前有 4 字节长度)
            //   bit3 (0x08) = 带富文本 (字符前有 2 字节 run 数, 字符后有 4*run 字节数据)
            var richRuns = 0
            var phoneticSize = 0
            if (grbit and 0x08 != 0) richRuns = u16()
            if (grbit and 0x04 != 0) phoneticSize = u32()
            val unicode = (grbit and 0x01) != 0
            val byteLen = if (unicode) cch * 2 else cch

            val sb = StringBuilder(cch)
            var remaining = byteLen
            inString = true
            while (remaining > 0) {
                if (ci >= chunks.size) break
                if (pos >= chunks[ci].size) {
                    ci++
                    pos = 0
                    if (ci < chunks.size) pos = 1
                    continue
                }
                val take = min(remaining, chunks[ci].size - pos)
                if (take <= 0) continue
                if (unicode) {
                    var i = 0
                    while (i + 1 < take) {
                        val lo = chunks[ci][pos + i].toInt() and 0xFF
                        val hi = chunks[ci][pos + i + 1].toInt() and 0xFF
                        sb.append(((hi shl 8) or lo).toChar())
                        i += 2
                    }
                } else {
                    for (i in 0 until take) sb.append(Char(chunks[ci][pos + i].toInt() and 0xFF))
                }
                pos += take
                remaining -= take
            }
            inString = false

            if (richRuns > 0) skipBytes(richRuns * 4)
            if (phoneticSize > 0) skipBytes(phoneticSize)
            return sb.toString()
        }
    }

    private fun parseUnicodeString(b: ByteArray, start: Int, end: Int): String {
        if (start + 3 > end) return ""
        var p = start
        val cch = leUShort(b, p)
        p += 2
        val grbit = b[p].toInt() and 0xFF
        p += 1
        val unicode = (grbit and 0x01) != 0 // 与 SST 相同的约定: bit0 = 双字节 UTF-16LE
        val byteLen = if (unicode) cch * 2 else cch
        if (p + byteLen > end) return ""
        val sb = StringBuilder(cch)
        if (unicode) {
            var i = 0
            while (i + 1 < byteLen) {
                val lo = b[p + i].toInt() and 0xFF
                val hi = b[p + i + 1].toInt() and 0xFF
                sb.append(((hi shl 8) or lo).toChar())
                i += 2
            }
        } else {
            for (i in 0 until byteLen) sb.append(Char(b[p + i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    // ------------------------------------------------------------- OLE2 (CFB)

    private class Cfb(private val data: ByteArray) {
        val sectorSize: Int
        private val miniSectorSize = 64
        private val fat: IntArray
        private val miniFat: IntArray
        private val miniStream: ByteArray
        private val dirEntries: List<DirEntry>

        init {
            require(data.size >= 512) { "文件太小，不是有效的 .xls 文件" }
            require(isOle2Magic(data)) { "不是有效的 .xls 文件（OLE2 格式）" }

            sectorSize = 1 shl leUShort(data, 0x1E)
            val firstDirSector = leInt(data, 0x30)
            val firstMiniFatSector = leInt(data, 0x3C)
            val numMiniFatSectors = leInt(data, 0x40)
            val firstDifSector = leInt(data, 0x44)
            val numDifSectors = leInt(data, 0x48)
            val numFatSectors = leInt(data, 0x2C)

            // 完整 DIFAT: 头部 109 项 + 后续 DIFAT 扇区
            val difat = ArrayList<Int>(109 + numDifSectors.coerceAtLeast(0) * (sectorSize / 4 - 1))
            for (i in 0 until 109) difat.add(leInt(data, 0x4C + i * 4))
            var difSector = firstDifSector
            repeat(numDifSectors.coerceAtLeast(0)) {
                if (difSector < 0) return@repeat
                val s = readSector(difSector)
                for (i in 0 until sectorSize / 4 - 1) difat.add(leInt(s, i * 4))
                difSector = leInt(s, sectorSize - 4)
            }

            // FAT
            val fatList = ArrayList<Int>(numFatSectors.coerceAtLeast(0) * sectorSize / 4)
            for (i in 0 until numFatSectors.coerceAtLeast(0)) {
                val sec = difat.getOrNull(i) ?: continue
                val s = readSector(sec)
                for (j in 0 until sectorSize / 4) fatList.add(leInt(s, j * 4))
            }
            fat = fatList.toIntArray()

            // 目录
            val dirBytes = readChain(firstDirSector, fat, null)
            dirEntries = (0 until dirBytes.size / 128).map { parseDirEntry(dirBytes, it * 128) }

            // 根目录流 = mini stream
            val root = dirEntries.firstOrNull { it.type == 5 }
                ?: throw IllegalArgumentException("OLE2 文件缺少根目录")
            miniStream = readChain(root.startSector, fat, root.size)

            // Mini FAT
            val miniFatBytes = readChain(firstMiniFatSector, fat, numMiniFatSectors.toLong() * sectorSize)
            miniFat = IntArray(miniFatBytes.size / 4) { leInt(miniFatBytes, it * 4) }
        }

        fun readStream(name: String): ByteArray? {
            val entry = dirEntries.firstOrNull { it.type == 2 && it.name.equals(name, ignoreCase = true) }
                ?: return null
            return if (entry.size >= MINI_CUTOFF) readChain(entry.startSector, fat, entry.size)
            else readMiniChain(entry.startSector, entry.size)
        }

        private fun readChain(startSector: Int, fat: IntArray, size: Long?): ByteArray {
            if (startSector == ENDOFCHAIN || startSector == FREESECT || startSector < 0) return ByteArray(0)
            val sectorList = ArrayList<Int>()
            var sector = startSector
            while (sector != ENDOFCHAIN && sector != FREESECT && sector >= 0 && sector < fat.size) {
                sectorList.add(sector)
                sector = fat[sector]
            }
            val total = if (size != null) min(size.toInt(), sectorList.size * sectorSize)
            else sectorList.size * sectorSize
            val out = ByteArray(total)
            var written = 0
            for (s in sectorList) {
                if (written >= total) break
                val src = readSector(s)
                val take = min(sectorSize, total - written)
                System.arraycopy(src, 0, out, written, take)
                written += take
            }
            return out
        }

        private fun readMiniChain(startSector: Int, size: Long): ByteArray {
            val out = ByteArray(size.toInt())
            var sector = startSector
            var written = 0
            while (sector != ENDOFCHAIN && sector != FREESECT && sector >= 0 && sector < miniFat.size) {
                if (written >= out.size) break
                val offset = sector * miniSectorSize
                val take = min(miniSectorSize, out.size - written)
                if (offset + take > miniStream.size) break
                System.arraycopy(miniStream, offset, out, written, take)
                written += take
                sector = miniFat[sector]
            }
            return out
        }

        private fun readSector(n: Int): ByteArray {
            if (n < 0) return ByteArray(sectorSize)
            val off = 512L + n.toLong() * sectorSize
            if (off + sectorSize > data.size) return ByteArray(sectorSize)
            return data.copyOfRange(off.toInt(), (off + sectorSize).toInt())
        }
    }

    private class DirEntry(val name: String, val type: Int, val startSector: Int, val size: Long)

    private fun parseDirEntry(b: ByteArray, off: Int): DirEntry {
        val nameLen = leUShort(b, off + 64)
        val name = decodeUtf16(b, off, off + nameLen).trimEnd('\u0000')
        val type = b[off + 66].toInt() and 0xFF
        val startSector = leInt(b, off + 116)
        val size = leLong(b, off + 120)
        return DirEntry(name, type, startSector, size)
    }

    private fun isOle2Magic(b: ByteArray): Boolean =
        b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte() && b[2] == 0x11.toByte() && b[3] == 0xE0.toByte() &&
            b[4] == 0xA1.toByte() && b[5] == 0xB1.toByte() && b[6] == 0x1A.toByte() && b[7] == 0xE1.toByte()

    // ------------------------------------------------------------------ utils

    private fun decodeUtf16(b: ByteArray, start: Int, end: Int): String {
        val sb = StringBuilder((end - start) / 2)
        var i = start
        while (i + 1 < end) {
            val lo = b[i].toInt() and 0xFF
            val hi = b[i + 1].toInt() and 0xFF
            sb.append(((hi shl 8) or lo).toChar())
            i += 2
        }
        return sb.toString()
    }

    private fun leUShort(b: ByteArray, off: Int): Int {
        if (off < 0 || off + 2 > b.size) return 0
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)
    }

    private fun leInt(b: ByteArray, off: Int): Int {
        if (off < 0 || off + 4 > b.size) return 0
        return (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or (b[off + 3].toInt() shl 24)
    }

    private fun leLong(b: ByteArray, off: Int): Long {
        if (off < 0 || off + 8 > b.size) return 0
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    private fun leDouble(b: ByteArray, off: Int): Double {
        if (off < 0 || off + 8 > b.size) return 0.0
        var bits = 0L
        for (i in 7 downTo 0) bits = (bits shl 8) or (b[off + i].toLong() and 0xFF)
        return Double.fromBits(bits)
    }

    private fun decodeRk(rk: Int): Double {
        val fX100 = (rk and 0x01) != 0
        val fInt = (rk and 0x02) != 0
        val value = if (fInt) (rk shr 2).toDouble()
        else Double.fromBits((rk.toLong() and 0xFFFFFFFCL) shl 32)
        return if (fX100) value / 100.0 else value
    }

    private fun formatNumber(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) d.toLong().toString()
        else d.toString()
}
