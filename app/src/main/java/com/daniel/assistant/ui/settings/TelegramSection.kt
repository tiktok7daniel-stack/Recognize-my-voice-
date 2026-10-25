package com.daniel.assistant.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.daniel.assistant.data.AppSettings
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.NeoTextField
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.components.SettingSwitch
import com.daniel.assistant.ui.rememberContainer
import com.daniel.assistant.ui.theme.Neo
import kotlinx.coroutines.launch

/** Remote control from your own Telegram bot. */
@Composable
fun TelegramSection(settings: AppSettings) {
    val c = rememberContainer()
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf("") }
    val hasToken = settings.telegramTokenEnc.isNotBlank()
    val paired = settings.telegramChatId.isNotBlank()

    GlassCard {
        SettingSwitch(
            "التحكم من تيليجرام",
            "ابعت أوامر لموبايلك من أي مكان (لازم Nabra يكون شغال)",
            settings.telegramEnabled && hasToken,
        ) { v -> scope.launch { c.settings.setTelegramEnabled(v) } }
        NeoTextField(
            token, { token = it },
            if (hasToken) "توكن البوت (محفوظ — اكتب جديد للتغيير)" else "توكن البوت من @BotFather",
            visualTransformation = PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(8.dp))
        PillButton("حفظ التوكن", {
            scope.launch {
                c.settings.setTelegramToken(token)
                c.settings.setTelegramEnabled(token.isNotBlank())
                token = ""
            }
        }, enabled = token.isNotBlank())
        if (hasToken) {
            Text(
                if (paired) "✅ مربوط بحسابك على تيليجرام"
                else "ابعت للبوت الكود ده عشان تربطه: ${settings.telegramPairCode}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (paired) Neo.Success else Neo.Cyan,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (paired) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("فك الربط", { scope.launch { c.settings.setTelegramChatId("") } }, primary = false)
                }
            }
        }
        Text(
            "إزاي: افتح @BotFather في تيليجرام ← /newbot ← خد التوكن وحطه هنا ← ابعت الكود للبوت. " +
                "بعدها أي رسالة تبعتها للبوت بتتنفذ كأنك قلتها، و/stop بيوقف أي حاجة شغالة.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
