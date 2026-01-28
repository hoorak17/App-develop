package com.example.plannermvp.data.persistence

import com.example.plannermvp.data.Category
import com.example.plannermvp.data.TimeBlock
import java.time.LocalDate

/**
 * ✅ 외부 JSON/serialization 없이 동작하는 간단 문자열 직렬화
 * - 충돌 줄이려고 유니코드 구분자 사용
 * - 스키마 버전별 기본값/복구 정책 포함
 */
internal object SnapshotCodec {

    private const val SEP_SECTION = "␜"
    private const val SEP_ROW = "␞"
    private const val SEP_BLOCK = "␝"
    private const val SEP_FIELD = "␟"

    fun encode(s: ScheduleSnapshot): String {
        return buildString {
            append("v=").append(s.schemaVersion).append(SEP_SECTION)

            append("today=").append(encodeBlocks(s.today)).append(SEP_SECTION)
            append("yesterday=").append(encodeBlocks(s.yesterday)).append(SEP_SECTION)
            append("tomorrow=").append(encodeBlocks(s.tomorrow)).append(SEP_SECTION)

            // v4 meta
            append("lastActiveDateIso=").append(escape(s.lastActiveDateIso)).append(SEP_SECTION)
            append("pendingRollover=").append(if (s.pendingRollover) "1" else "0").append(SEP_SECTION)
            append("pendingFromDateIso=").append(escape(s.pendingFromDateIso)).append(SEP_SECTION)

            // history: dateIso ␟ blocksEncoded
            append("history=")
            append(
                s.history.joinToString(SEP_ROW) { day ->
                    escape(day.dateIso) + SEP_FIELD + encodeBlocks(day.blocks)
                }
            )
        }
    }

    fun decode(raw: String): ScheduleSnapshot {
        val parts = raw.split(SEP_SECTION)

        var version = 4
        var today: List<TimeBlock> = emptyList()
        var yesterday: List<TimeBlock> = emptyList()
        var tomorrow: List<TimeBlock> = emptyList()
        var history: List<ScheduleSnapshot.HistoryDay> = emptyList()

        var lastActiveDateIso = ""
        var pendingRollover = false
        var pendingFromDateIso = ""

        for (p in parts) {
            if (p.isBlank()) continue
            val idx = p.indexOf("=")
            if (idx <= 0) continue
            val key = p.substring(0, idx)
            val value = p.substring(idx + 1)

            when (key) {
                "v" -> version = value.toIntOrNull() ?: 4
                "today" -> today = decodeBlocks(value)
                "yesterday" -> yesterday = decodeBlocks(value)
                "tomorrow" -> tomorrow = decodeBlocks(value)

                "lastActiveDateIso" -> lastActiveDateIso = unescape(value)
                "pendingRollover" -> pendingRollover = value == "1"
                "pendingFromDateIso" -> pendingFromDateIso = unescape(value)

                "history" -> {
                    history = if (value.isBlank()) emptyList() else {
                        value.split(SEP_ROW).mapNotNull { row ->
                            val cut = row.indexOf(SEP_FIELD)
                            if (cut <= 0) return@mapNotNull null
                            val dateIso = unescape(row.substring(0, cut))
                            val blocksRaw = row.substring(cut + 1)
                            ScheduleSnapshot.HistoryDay(
                                dateIso = dateIso,
                                blocks = decodeBlocks(blocksRaw)
                            )
                        }
                    }
                }
            }
        }

        val safeToday = today.filter { it.endMinute > it.startMinute }
        val safeYesterday = yesterday.filter { it.endMinute > it.startMinute }
        val safeTomorrow = tomorrow.filter { it.endMinute > it.startMinute }
        val safeHistory = history.map { day ->
            day.copy(blocks = day.blocks.filter { it.endMinute > it.startMinute })
        }

        // v3 이하 호환: lastActiveDateIso 없으면 오늘로 잡아주고 pending false
        if (lastActiveDateIso.isBlank()) {
            lastActiveDateIso = LocalDate.now().toString()
            pendingRollover = false
            pendingFromDateIso = ""
        }

        return ScheduleSnapshot(
            schemaVersion = version,
            today = safeToday,
            yesterday = safeYesterday,
            tomorrow = safeTomorrow,
            history = safeHistory,
            lastActiveDateIso = lastActiveDateIso,
            pendingRollover = pendingRollover,
            pendingFromDateIso = pendingFromDateIso
        )
    }

    /**
     * block format:
     * id ␟ title ␟ start ␟ end ␟ categoryName ␟ tagsCsv ␟ memo
     */
    private fun encodeBlocks(list: List<TimeBlock>): String {
        return list.joinToString(SEP_BLOCK) { b ->
            val tagsCsv = b.feedbackTags.joinToString(",") { escape(it) }
            listOf(
                escape(b.id),
                escape(b.title),
                b.startMinute.toString(),
                b.endMinute.toString(),
                escape(b.category.name),
                tagsCsv,
                escape(b.feedbackMemo)
            ).joinToString(SEP_FIELD)
        }
    }

    private fun decodeBlocks(raw: String): List<TimeBlock> {
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP_BLOCK).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val f = line.split(SEP_FIELD)
            if (f.size < 7) return@mapNotNull null

            val id = unescape(f[0])
            val title = unescape(f[1])
            val start = f[2].toIntOrNull() ?: 0
            val end = f[3].toIntOrNull() ?: 0
            val catName = unescape(f[4]).ifBlank { Category.ETC.name }
            val cat = runCatching { Category.valueOf(catName) }.getOrElse { Category.ETC }

            val tags = if (f[5].isBlank()) emptySet() else f[5].split(",").map { unescape(it) }.toSet()
            val memo = unescape(f[6])

            TimeBlock(
                id = id,
                title = title,
                startMinute = start,
                endMinute = end,
                category = cat,
                feedbackTags = tags,
                feedbackMemo = memo
            )
        }
    }

    private fun escape(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("␜", "\\u241c")
            .replace("␞", "\\u241e")
            .replace("␝", "\\u241d")
            .replace("␟", "\\u241f")
    }

    private fun unescape(s: String): String {
        return s.replace("\\u241c", "␜")
            .replace("\\u241e", "␞")
            .replace("\\u241d", "␝")
            .replace("\\u241f", "␟")
            .replace("\\\\", "\\")
    }
}
