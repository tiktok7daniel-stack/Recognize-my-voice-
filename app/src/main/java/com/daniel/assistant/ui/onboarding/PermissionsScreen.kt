package com.daniel.assistant.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.IconBubble
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.components.ScreenHeader
import com.daniel.assistant.ui.components.StatusChip
import com.daniel.assistant.ui.theme.Neo

private data class PermItem(
    val icon: ImageVector,
    val title: String,
    val why: String,
    val granted: Boolean,
    val essential: Boolean,
    val buttonText: String,
    val extraText: String? = null,
    val onExtra: (() -> Unit)? = null,
    val onClick: () -> Unit,
)

@Composable
fun PermissionsScreen(onContinue: () -> Unit, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val requestOne = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    fun open(intent: Intent) {
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Permissions.appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    @Suppress("UNUSED_VARIABLE") val t = tick // re-read statuses on every resume
    val items = listOf(
        PermItem(
            Icons.Filled.Mic, "الميكروفون",
            "عشان أسمع كلمة التنبيه والأوامر. التعرف على الصوت بيحصل على الجهاز نفسه ومفيش صوت بيطلع برّه.",
            Permissions.mic(context), true, "اسمح",
        ) { requestOne.launch(Manifest.permission.RECORD_AUDIO) },
        PermItem(
            Icons.Filled.Accessibility, "خدمة إمكانية الوصول",
            "دي \"الإيدين\": بيها أقدر أضغط وأكتب وأعمل سكرول وأرجع وأفتح التطبيقات من الخلفية. " +
                "لو الزرار رمادي (أندرويد 13+): ادخل معلومات التطبيق ← ⋮ ← \"السماح بالإعدادات المقيدة\".",
            Permissions.accessibility(context), true, "فعّل",
            extraText = "الزرار رمادي؟ افتح معلومات Nabra",
            onExtra = { open(Permissions.appDetails(context)) },
        ) { open(Permissions.accessibilityIntent()) },
        PermItem(
            Icons.Filled.Photo, "الصور",
            "عشان أوامر زي \"ابعت آخر صورتين لكلود\" — بقرأ أحدث الصور بس لما تطلب.",
            Permissions.images(context), false, "اسمح",
        ) { requestOne.launch(Permissions.imagesPermission) },
        PermItem(
            Icons.Filled.Notifications, "الإشعارات",
            "للإشعار الدائم وأزرار الإيقاف، ولما أقولك \"مفهمتش\" أو خطوة فشلت أو محتاج تأكيد.",
            Permissions.notifications(context), false, "اسمح",
        ) {
            if (Build.VERSION.SDK_INT >= 33) requestOne.launch(Manifest.permission.POST_NOTIFICATIONS)
            else open(Permissions.notificationSettings(context))
        },
        PermItem(
            Icons.Filled.Contacts, "جهات الاتصال",
            "عشان \"كلّم محمد\" و\"ابعت لأحمد على الواتس…\" — بدوّر على الاسم في الأسماء اللي عندك بس.",
            Permissions.contacts(context), false, "اسمح",
        ) { requestOne.launch(Manifest.permission.READ_CONTACTS) },
        PermItem(
            Icons.Filled.Call, "المكالمات",
            "عشان \"اتصل بماما\" تتصل على طول. من غيرها هفتحلك الطالب والرقم مكتوب.",
            Permissions.calls(context), false, "اسمح",
        ) { requestOne.launch(Manifest.permission.CALL_PHONE) },
        PermItem(
            Icons.Filled.BrightnessMedium, "تعديل إعدادات النظام",
            "عشان \"خلي الإضاءة ٥٠\".",
            Permissions.writeSettings(context), false, "اسمح",
        ) { open(Permissions.writeSettingsIntent(context)) },
        PermItem(
            Icons.Filled.BatteryChargingFull, "تجاهل توفير البطارية",
            "عشان النظام ميقفلش المساعد وهو بيسمعك في الخلفية.",
            Permissions.battery(context), false, "اسمح",
        ) { open(Permissions.batteryIntent(context)) },
        PermItem(
            Icons.Filled.Layers, "الظهور فوق التطبيقات",
            "احتياطي عشان أقدر أفتح تطبيقات من الخلفية لو خدمة إمكانية الوصول مش كفاية.",
            Permissions.overlay(context), false, "اسمح",
        ) { open(Permissions.overlayIntent(context)) },
    )
    val essentialOk = items.filter { it.essential }.all { it.granted }

    NabraBackground {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("الصلاحيات", "خطوة بخطوة — الأساسي أول اتنين", onBack)
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items) { p ->
                    GlassCard(glow = if (!p.granted && p.essential) Neo.Accent else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(p.icon, if (p.granted) Neo.Success else Neo.Cyan)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.title + if (p.essential) " •" else "", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(4.dp))
                                StatusChip(p.granted, "متاح", "ناقص")
                            }
                            if (!p.granted) PillButton(p.buttonText, p.onClick, primary = p.essential)
                        }
                        Text(p.why, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                        if (!p.granted && p.extraText != null && p.onExtra != null) {
                            TextButton(onClick = p.onExtra) { Text(p.extraText, color = Neo.Cyan) }
                            Text(
                                "في صفحة المعلومات: دوس ⋮ فوق ← \"Allow restricted settings\" ← وارجع فعّل.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                item {
                    val oem = Permissions.oemInfo()
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBubble(Icons.Filled.PhoneAndroid, Neo.Warning)
                            Spacer(Modifier.width(12.dp))
                            Text("قاتل البطارية بتاع الشركة", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            (oem?.tip ?: "بعض الشركات (شاومي، سامسونج، هواوي…) بتقفل التطبيقات اللي في الخلفية. ") +
                                " لو المساعد بيقف لوحده، شوف الإرشادات الخاصة بجهازك.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                        Row {
                            if (oem != null) {
                                TextButton(onClick = { Permissions.launchFirst(context, oem.intents) }) {
                                    Text("إعدادات ${oem.brand}", color = Neo.Cyan)
                                }
                            }
                            TextButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(Permissions.dontKillUrl()))) }) {
                                Text("dontkillmyapp.com", color = Neo.Cyan)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.padding(8.dp)) }
            }
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                PillButton(
                    if (essentialOk) "يلا نكمّل" else "كمّل (ممكن تكمل الصلاحيات بعدين)",
                    onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    primary = essentialOk,
                )
            }
        }
    }
}
