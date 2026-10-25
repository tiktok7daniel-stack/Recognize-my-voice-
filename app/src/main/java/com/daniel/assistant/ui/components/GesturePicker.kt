package com.daniel.assistant.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daniel.assistant.accessibility.ButtonGesture
import com.daniel.assistant.ui.theme.Neo

/** Dialog to pick a volume-button gesture (or none). */
@Composable
fun GesturePickerDialog(onDismiss: () -> Unit, onPick: (ButtonGesture?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Neo.Bg1,
        title = { Text("اختار الزرار") },
        text = {
            Column {
                ButtonGesture.entries.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(g) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBubble(
                            if (g.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) Icons.AutoMirrored.Filled.VolumeUp
                            else Icons.AutoMirrored.Filled.VolumeDown,
                            size = 36,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(g.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    "الدوسة العادية لسه بتغيّر الصوت عادي. الأزرار بتشتغل والشاشة منورة (محتاج خدمة إمكانية الوصول).",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null) }) { Text("من غير زرار", color = Neo.Danger) }
                TextButton(onClick = onDismiss) { Text("إلغاء", color = Neo.TextDim) }
            }
        },
    )
}
