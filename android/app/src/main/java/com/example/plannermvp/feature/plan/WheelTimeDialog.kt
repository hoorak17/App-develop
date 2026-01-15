package com.example.plannermvp.feature.plan

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

private const val DAY_MIN = 24 * 60

@Composable
fun WheelTimeDialog(
    title: String,
    titleDefault: String,
    startDefaultMinute: Int,
    endDefaultMinute: Int,
    onDismiss: () -> Unit,
    /**
     * 저장을 실제로 수행하고 결과를 반환해야 함.
     * - true: 저장 성공(다이얼 닫기)
     * - false: 저장 실패(이유는 호출자가 보여주기 위해 errorMessage에 넣거나, 아래 default 메시지 사용)
     */
    onTrySave: (title: String, startMin: Int, endMin: Int) -> Boolean,
    /**
     * 겹침 등 외부 검증 실패 시, 다이얼 안에 표시할 메시지(선택)
     */
    externalErrorMessage: String? = null
) {
    var name by remember { mutableStateOf(titleDefault) }

    var startHour by remember { mutableStateOf(((startDefaultMinute % DAY_MIN) + DAY_MIN) % DAY_MIN / 60) }
    var startMin by remember { mutableStateOf(((startDefaultMinute % DAY_MIN) + DAY_MIN) % DAY_MIN % 60) }

    var endHour by remember { mutableStateOf(((endDefaultMinute % DAY_MIN) + DAY_MIN) % DAY_MIN / 60) }
    var endMin by remember { mutableStateOf(((endDefaultMinute % DAY_MIN) + DAY_MIN) % DAY_MIN % 60) }

    // 다이얼 내부 에러(시간/이름/겹침)
    var localError by remember { mutableStateOf("") }

    val rawStart = startHour * 60 + startMin
    val rawEnd = endHour * 60 + endMin

    val nameOk = name.trim().isNotEmpty()

    // 자정 넘김 처리
    val computedEnd = when {
        rawEnd == rawStart -> rawEnd
        rawEnd < rawStart -> rawEnd + DAY_MIN
        else -> rawEnd
    }

    val crossesMidnight = rawEnd < rawStart && rawEnd != rawStart
    val hint = if (crossesMidnight) "종료 시간이 시작보다 이르면 다음날 종료(+1일)로 처리됩니다." else ""

    val baseError = when {
        !nameOk -> "일정 이름을 입력하세요."
        rawEnd == rawStart -> "시작과 종료가 같으면 저장할 수 없습니다."
        computedEnd <= rawStart -> "종료 시간은 시작 시간보다 늦어야 합니다."
        else -> ""
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title)

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        localError = ""
                    },
                    label = { Text("일정 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text("시작")
                WheelTimePicker(
                    hour = startHour,
                    minute = startMin,
                    onHourChanged = { startHour = it; localError = "" },
                    onMinuteChanged = { startMin = it; localError = "" }
                )

                Text("종료")
                WheelTimePicker(
                    hour = endHour,
                    minute = endMin,
                    onHourChanged = { endHour = it; localError = "" },
                    onMinuteChanged = { endMin = it; localError = "" }
                )

                if (hint.isNotBlank()) Text(hint)

                // ✅ 에러 표시 우선순위: baseError -> localError -> externalErrorMessage
                val showError = when {
                    baseError.isNotBlank() -> baseError
                    localError.isNotBlank() -> localError
                    !externalErrorMessage.isNullOrBlank() -> externalErrorMessage
                    else -> ""
                }
                if (showError.isNotBlank()) Text(showError)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("취소") }
                    Button(
                        enabled = baseError.isBlank(),
                        onClick = {
                            if (baseError.isNotBlank()) return@Button
                            val ok = onTrySave(name.trim(), rawStart, computedEnd)
                            if (!ok) {
                                // 호출자가 false를 준 경우 = 대부분 겹침
                                localError = "저장 실패: 다른 일정과 시간이 겹칩니다."
                            } else {
                                onDismiss() // 성공이면 닫기
                            }
                        }
                    ) { Text("저장") }
                }
            }
        }
    }
}

@Composable
private fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit
) {
    val context = LocalContext.current

    AndroidView(
        factory = {
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER

                val hourPicker = NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 23
                    value = hour
                    setOnValueChangedListener { _, _, newVal -> onHourChanged(newVal) }
                }

                val minutePicker = NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 59
                    value = minute
                    setFormatter { v -> "%02d".format(v) }
                    setOnValueChangedListener { _, _, newVal -> onMinuteChanged(newVal) }
                }

                addView(hourPicker)
                addView(minutePicker)
            }
        },
        update = { layout ->
            val hourPicker = layout.getChildAt(0) as NumberPicker
            val minutePicker = layout.getChildAt(1) as NumberPicker
            if (hourPicker.value != hour) hourPicker.value = hour
            if (minutePicker.value != minute) minutePicker.value = minute
        }
    )
}
