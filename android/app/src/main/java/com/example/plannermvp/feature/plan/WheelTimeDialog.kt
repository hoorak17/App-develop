package com.example.plannermvp.feature.plan

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog

/**
 * 공용 휠(스크롤) 시간 설정 다이얼
 * - 원형 시계 다이얼이 아니라 "위아래 휠" 방식(NumberPicker)
 * - 화면 깨짐 방지: Dialog + heightIn + verticalScroll
 */
@Composable
fun WheelTimeDialog(
    title: String,
    titleDefault: String,
    startDefaultMinute: Int,
    endDefaultMinute: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, startMin: Int, endMin: Int) -> Unit
) {
    var name by remember { mutableStateOf(titleDefault) }

    var startHour by remember { mutableStateOf(startDefaultMinute / 60) }
    var startMin by remember { mutableStateOf(startDefaultMinute % 60) }

    var endHour by remember { mutableStateOf(endDefaultMinute / 60) }
    var endMin by remember { mutableStateOf(endDefaultMinute % 60) }

    Dialog(onDismissRequest = onDismiss) {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("일정 이름") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("시작")
                WheelTimePicker(
                    hour = startHour,
                    minute = startMin,
                    onHourChanged = { startHour = it },
                    onMinuteChanged = { startMin = it }
                )

                Text("종료")
                WheelTimePicker(
                    hour = endHour,
                    minute = endMin,
                    onHourChanged = { endHour = it },
                    onMinuteChanged = { endMin = it }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("취소") }
                    Button(onClick = {
                        val s = startHour * 60 + startMin
                        val e = endHour * 60 + endMin
                        if (name.isBlank()) return@Button
                        if (e <= s) return@Button
                        onSave(name.trim(), s, e)
                    }) { Text("저장") }
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
                    setFormatter { v -> "%02d".format(v) } // ✅ formatter 에러 해결
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
