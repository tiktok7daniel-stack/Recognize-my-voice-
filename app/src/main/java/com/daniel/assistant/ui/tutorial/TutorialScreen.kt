package com.daniel.assistant.ui.tutorial

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.assistant.ui.components.AnimatedLogo
import com.daniel.assistant.ui.components.GlassCard
import com.daniel.assistant.ui.components.GlowOrb
import com.daniel.assistant.ui.components.IconBubble
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.OrbState
import com.daniel.assistant.ui.components.PillButton
import com.daniel.assistant.ui.theme.Neo
import kotlinx.coroutines.launch

/** One page of the guided tutorial. */
private data class TutorialPage(
    val tag: String,
    val title: String,
    val lines: List<String>,
    val tip: String? = null,
    val illustration: @Composable () -> Unit,
)

/**
 * Step-by-step guide explaining how Nabra works, in order: what it is, how it listens,
 * the accessibility "hands", the voice model, starting it, ready commands, opening apps,
 * building your first command, troubleshooting and AI mode.
 */
@Composable
fun TutorialScreen(
    onFinish: () -> Unit,
    onCreateFirstCommand: () -> Unit,
    onClose: (() -> Unit)? = null,
) {
    val pages = rememberPages()
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    NabraBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // top bar: step counter + skip
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "الدرس ${pager.currentPage + 1} من ${pages.size}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClose ?: onFinish) {
                    Text(if (onClose != null) "إغلاق" else "تخطي", color = Neo.TextDim)
                }
            }
            ProgressBar(pager.currentPage, pages.size)

            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                PageContent(pages[index])
            }

            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Dots(pager.currentPage, pages.size)
                if (last) {
                    PillButton(
                        "اعمل أول أمر ليك دلوقتي",
                        onCreateFirstCommand,
                        Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Add,
                    )
                    PillButton(
                        if (onClose != null) "رجوع للتطبيق" else "يلا نبدأ",
                        onFinish,
                        Modifier.fillMaxWidth(),
                        primary = false,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (pager.currentPage > 0) {
                            PillButton(
                                "السابق",
                                { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                                Modifier.weight(1f),
                                primary = false,
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                            )
                        }
                        PillButton(
                            "التالي",
                            { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                            Modifier.weight(1f),
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageContent(page: TutorialPage) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
            page.illustration()
        }
        Spacer(Modifier.height(12.dp))
        Text(
            page.tag,
            color = Neo.Cyan,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .clip(CircleShape)
                .background(Neo.Cyan.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        GlassCard {
            page.lines.forEachIndexed { i, line ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(Neo.Accent),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = MaterialTheme.typography.bodySmall, color = Neo.Text) }
                    Spacer(Modifier.width(12.dp))
                    Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
        }
        page.tip?.let {
            Spacer(Modifier.height(12.dp))
            GlassCard(glow = Neo.Warning) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("💡", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ProgressBar(current: Int, total: Int) {
    val fraction = (current + 1f) / total
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).clip(CircleShape).background(Neo.Bg2)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(Neo.Accent, Neo.Cyan)))
        )
    }
}

@Composable
private fun Dots(current: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(total) { i ->
            val w by animateDpAsState(if (i == current) 22.dp else 7.dp, label = "dot")
            val c by animateColorAsState(if (i == current) Neo.Cyan else Neo.Stroke, label = "dotc")
            Box(Modifier.padding(3.dp).height(7.dp).width(w).clip(CircleShape).background(c))
        }
    }
}

// ---------------- illustrations ----------------

@Composable
private fun Bubble(text: String, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = if (highlight) Neo.Text else Neo.PillText,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) Neo.Accent else Neo.Pill)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Arrow() {
    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Neo.Cyan, modifier = Modifier.size(22.dp))
}

@Composable
private fun Pulse(content: @Composable () -> Unit) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    Box(Modifier.graphicsLayer { scaleX = s; scaleY = s }) { content() }
}

@Composable
private fun MiniStep(n: Int, icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Neo.Glass)
            .border(1.dp, Neo.Stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Neo.Accent), contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.bodySmall, color = Neo.Text)
        }
        Spacer(Modifier.width(8.dp))
        Icon(icon, null, tint = Neo.Cyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.DragHandle, null, tint = Neo.TextDim, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipCloud(items: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items.forEachIndexed { i, s -> Bubble(s, highlight = i % 3 == 0) }
    }
}

@Composable
private fun rememberPages(): List<TutorialPage> = remember { buildPages() }

private fun buildPages(): List<TutorialPage> = listOf(
    TutorialPage(
        tag = "أهلاً بيك",
        title = "Nabra = صوتك بيتحكم في موبايلك",
        lines = listOf(
            "Nabra مساعد صوتي بيسمعك وينفّذ أوامرك على الموبايل كأنه بيلمس الشاشة بداله.",
            "بيشتغل أوفلاين: فهم الكلام بيحصل جوه موبايلك، ومفيش صوت بيطلع برّه.",
            "تقدر تستخدم أوامر جاهزة (افتح يوتيوب، رجوع، انزل…) أو تعمل أوامرك الخاصة.",
            "أهم ميزة: كلمة واحدة منك ← سلسلة خطوات كاملة. مثلاً \"واحد\" ← يبعت صورتين لكلود ويكتب \"حلهم بالترتيب بالإيطالي\" ويدوس إرسال.",
        ),
        illustration = { AnimatedLogo(150.dp) },
    ),
    TutorialPage(
        tag = "إزاي بيسمعك",
        title = "كلمة التنبيه وبعدها الأمر",
        lines = listOf(
            "قول كلمة التنبيه: \"يا مساعد\" (تقدر تغيّرها من الإعدادات).",
            "الموبايل هيهتز أو يعمل صوت صغير، يعني: أنا سامعك.",
            "قول الأمر على طول، مثلاً \"افتح يوتيوب\". الأمر بيتفهم بمحرك Google الدقيق (وبيرجع للأوفلاين لو مفيش نت).",
            "هيرد عليك \"حاضر\" وينفّذ. وتقدر تقولهم مع بعض: \"يا مساعد افتح يوتيوب\".",
            "أوامرك الخاصة زي \"واحد\" بتشتغل كمان لوحدها من غير \"يا مساعد\" (تقدر تقفل ده من الإعدادات).",
        ),
        tip = "التطبيق مش بينفّذ أي حاجة إلا لو سمع كلمة التنبيه الأول، عشان ميشتغلش بالغلط من كلام عادي.",
        illustration = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bubble("يا مساعد", true); Arrow(); Pulse { IconBubble(Icons.Filled.Vibration, Neo.Cyan, 52) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bubble("افتح يوتيوب"); Arrow(); IconBubble(Icons.Filled.CheckCircle, Neo.Success, 52)
                }
            }
        },
    ),
    TutorialPage(
        tag = "الإيدين",
        title = "خدمة إمكانية الوصول",
        lines = listOf(
            "دي اللي بتخلي Nabra \"يلمس\" الشاشة: يدوس، يكتب، يعمل سكرول، يرجع، ويفتح التطبيقات.",
            "من غيرها التطبيق يقدر يسمعك بس، لكن مش هيقدر يتحكم في الموبايل.",
            "فعّلها من شاشة الصلاحيات ← زرار \"فعّل\" ← اختار \"Nabra\" ← شغّلها.",
            "لو المفتاح رمادي: إعدادات الموبايل ← التطبيقات ← Nabra ← ⋮ فوق ← \"السماح بالإعدادات المقيدة\" وبعدين فعّلها تاني.",
        ),
        tip = "دي أهم صلاحية في التطبيق كله. لو حسيت إن الأوامر مش بتتنفذ، أول حاجة اتأكد إنها لسه شغالة.",
        illustration = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBubble(Icons.Filled.Accessibility, Neo.Cyan, 72)
                Arrow()
                Pulse { IconBubble(Icons.Filled.TouchApp, Neo.Accent, 72) }
            }
        },
    ),
    TutorialPage(
        tag = "موديل الصوت",
        title = "مرة واحدة وخلاص",
        lines = listOf(
            "عشان يفهم كلامك من غير نت، محتاج ينزّل \"موديل صوت\" عربي (حوالي ٣٠٠ ميجا).",
            "بيتنزل مرة واحدة بس، والأفضل على واي فاي.",
            "لو النت قطع، دوس \"إعادة المحاولة\" وهيكمّل من مكانه.",
            "بعد كده كل حاجة شغالة أوفلاين تمامًا.",
        ),
        illustration = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pulse { IconBubble(Icons.Filled.CloudDownload, Neo.Cyan, 72) }
                Arrow()
                IconBubble(Icons.Filled.WifiOff, Neo.Success, 72)
            }
        },
    ),
    TutorialPage(
        tag = "التشغيل",
        title = "الدايرة المنورة",
        lines = listOf(
            "في الشاشة الرئيسية دوس على الدايرة الكبيرة أو \"ابدأ الاستماع\".",
            "الدايرة بتلف = بيسمع كلمة التنبيه. بتنبض بالسماوي = بيسمع الأمر دلوقتي.",
            "هيظهر إشعار ثابت فوق فيه \"إيقاف مؤقت\" و\"إيقاف\" عشان تتحكم فيه من أي مكان.",
            "وأنت بتعمل حاجة تانية سيبه شغال في الخلفية عادي.",
        ),
        tip = "عشان يوفر بطارية، مش بيسمع والشاشة مقفولة. تقدر تغيّر ده من الإعدادات.",
        illustration = { GlowOrb(OrbState.AWAITING, size = 190.dp) {} },
    ),
    TutorialPage(
        tag = "طرق أسرع",
        title = "من غير ما تقول كلمة التنبيه",
        lines = listOf(
            "زرار \"قول أمر دلوقتي\" في الرئيسية: دوسة واحدة وقول الأمر.",
            "الفقاعة العائمة: فعّلها من الرئيسية أو الإعدادات، هتلاقي دايرة صغيرة فوق كل التطبيقات، دوس عليها وقول الأمر. تقدر تسحبها لأي مكان.",
            "مربع الإعدادات السريعة: اسحب الإشعارات ← ✎ تعديل ← ضيف مربع \"Nabra\". دوسة عليه = قول أمر.",
            "زرار الصوت: من الإعدادات اختار مثلًا \"ضغطة طويلة على وطّي الصوت\" تبدأ الاستماع، ومن تعديل أي أمر تقدر تربطه بـ \"دوستين على علّي الصوت\".",
            "اضغط مطولًا على أيقونة Nabra ← \"قول أمر\".",
        ),
        tip = "الطرق دي مفيدة جدًا وأنت في مكان فيه دوشة، أو لما مش عايز تقول كلمة التنبيه بصوت عالي.",
        illustration = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pulse { IconBubble(Icons.Filled.Mic, Neo.Cyan, 64) }
                IconBubble(Icons.Filled.TouchApp, Neo.Accent, 64)
                IconBubble(Icons.Filled.AutoAwesome, Neo.Success, 64)
            }
        },
    ),
    TutorialPage(
        tag = "أوامر جاهزة",
        title = "تقدر تقولها على طول",
        lines = listOf(
            "تنقل: رجوع • الرئيسية • التطبيقات الأخيرة • الإشعارات • الإعدادات السريعة.",
            "الشاشة: انزل • اطلع • سكرين شوت • اقفل الشاشة.",
            "التحكم: اضغط على <كلمة> • اكتب <كلام>.",
            "الجهاز: علّي الصوت • وطّي الصوت • شغّل الكشاف • اطفي الكشاف • واي فاي • بلوتوث • قايمة الباور • قسم الشاشة.",
            "الأغاني: شغل الاغنية • اللي بعدها • اللي قبلها. والسحب: اسحب يمين • اسحب شمال.",
        ),
        tip = "لو قلت \"اكتب\" أو \"اضغط على\" لوحدها، هيسألك: تكتب إيه؟ أو تضغط على إيه؟",
        illustration = { ChipCloud(listOf("رجوع", "انزل", "سكرين شوت", "شغّل الكشاف", "علّي الصوت", "اضغط على بحث", "الإشعارات")) },
    ),
    TutorialPage(
        tag = "فتح التطبيقات",
        title = "\"افتح\" + اسم أي تطبيق",
        lines = listOf(
            "قول \"افتح\" وبعدها اسم التطبيق: افتح يوتيوب، افتح واتساب، افتح كلود.",
            "التطبيق عارف أسماء مصرية لأشهر التطبيقات، وبيفهم الاسم بالعربي أو الإنجليزي.",
            "لو تطبيق مش بيفتح باسمه: ادخل تبويب \"التطبيقات\" تحت ودوس + أو 🎙 وضيفله اسم بصوتك.",
            "مثلاً تسمّي سبوتيفاي \"الأغاني\" وبعدها تقول \"افتح الأغاني\".",
        ),
        illustration = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Bubble("افتح يوتيوب", true)
                Bubble("افتح الواتس")
                Bubble("افتح كلود", true)
            }
        },
    ),
    TutorialPage(
        tag = "أول أمر ليك ١/٢",
        title = "سجّل كلمة التشغيل",
        lines = listOf(
            "ادخل تبويب \"أوامري\" تحت ← دوس \"أمر جديد\".",
            "دوس \"سجّل بصوتك\" وقول الكلمة اللي عايزها، مثلاً \"اتنين\".",
            "هيكتبلك اللي سمعه: لو صح دوس \"تأكيد\"، لو غلط دوس \"إعادة\" أو عدّل الكلام.",
            "قولها ٢-٣ مرات عشان يتعرف عليها أحسن، وبعدين دوس \"خلاص\".",
        ),
        tip = "اختار كلمة قصيرة وواضحة ومش بتقولها كتير في الكلام العادي.",
        illustration = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Pulse { IconBubble(Icons.Filled.Mic, Neo.Cyan, 64) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bubble("اتنين", true); Bubble("اتنين"); Bubble("إتنين", true)
                }
            }
        },
    ),
    TutorialPage(
        tag = "أول أمر ليك ٢/٢",
        title = "ضيف الخطوات واحفظ",
        lines = listOf(
            "دوس \"أضف خطوة\" واختار نوعها: افتح تطبيق، ابعت صور، اكتب، دوس إرسال، اضغط على، استنى…",
            "كل خطوة هتسألك على تفاصيلها: أنهي تطبيق؟ كام صورة؟ تكتب إيه؟",
            "رتّب الخطوات بالسحب من علامة ☰، وامسح أي خطوة من 🗑.",
            "جرّبه بزرار ▶ فوق، ولو تمام دوس \"حفظ الأمر\". خلاص! قول \"يا مساعد… اتنين\".",
        ),
        tip = "لو خطوة بتفشل لأن التطبيق بطيء، ضيف قبلها خطوة \"استنى\" ثانية أو اتنين.",
        illustration = {
            Column(Modifier.width(280.dp)) {
                MiniStep(1, Icons.Filled.PhotoLibrary, "ابعت آخر ٢ صور لـ Claude")
                MiniStep(2, Icons.Filled.Timer, "استنى 800 ms")
                MiniStep(3, Icons.Filled.Keyboard, "اكتب \"حلهم بالترتيب بالإيطالي\"")
                MiniStep(4, Icons.AutoMirrored.Filled.Send, "دوس إرسال")
            }
        },
    ),
    TutorialPage(
        tag = "مثال جاهز",
        title = "أمر \"واحد\" مستنيك",
        lines = listOf(
            "عملتلك أمر جاهز اسمه \"واحد\" تلاقيه في \"أوامري\".",
            "بيبعت آخر صورتين في الاستوديو لتطبيق كلود، يستنى لحظة، يكتب \"حلهم بالترتيب بالإيطالي\"، ويدوس إرسال.",
            "قول \"يا مساعد… واحد\" وشوفه بيشتغل، أو دوس \"تجربة\" جنب الأمر.",
            "افتحه بـ \"تعديل\" عشان تشوف إزاي معمول وتعمل زيه.",
        ),
        illustration = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Bubble("واحد", true); Arrow(); IconBubble(Icons.Filled.PhotoLibrary, Neo.Cyan, 52); Arrow()
                IconBubble(Icons.AutoMirrored.Filled.Send, Neo.Success, 52)
            }
        },
    ),
    TutorialPage(
        tag = "مميزات متقدمة",
        title = "خلّي أوامرك أذكى",
        lines = listOf(
            "متغيرات: كلمة تشغيل زي \"ابعت لـ {اسم} {رسالة}\"، ولما تقول \"ابعت لأحمد أنا جاي\" التطبيق يستخدم {اسم} و{رسالة} في الخطوات.",
            "تشغيل تلقائي: من آخر شاشة تعديل الأمر، خليه يشتغل كل يوم في ساعة معينة، أو لما تفتح تطبيق معيّن.",
            "لوحة التنفيذ: وهو بينفّذ، هيظهر شريط صغير فوق فيه الخطوة اللي هو فيها وزرار \"وقف\" (محتاج صلاحية الظهور فوق التطبيقات).",
            "قفل الصوت: من الإعدادات ← الأمان، سجّل بصمة صوتك عشان التطبيق ميشتغلش غير بصوتك أنت.",
        ),
        tip = "الذكاء الاصطناعي كمان ممكن يشتغل جوه الموبايل من غير نت: الإعدادات ← الذكاء الاصطناعي ← \"موديل جوه الموبايل\".",
        illustration = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Bubble("ابعت لـ {اسم} {رسالة}", true)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBubble(Icons.Filled.Timer, Neo.Cyan, 52)
                    IconBubble(Icons.Filled.TouchApp, Neo.Accent, 52)
                    IconBubble(Icons.Filled.Mic, Neo.Success, 52)
                }
            }
        },
    ),
    TutorialPage(
        tag = "لو حاجة مش ماشية",
        title = "إزاي تعرف المشكلة",
        lines = listOf(
            "لو قال \"مفهمتش\": هيبعتلك إشعار فيه اللي سمعه بالظبط، فتعرف الغلط فين.",
            "جرّب أي أمر بالكتابة من خانة \"جرّب أمر بالكتابة\" في الرئيسية، من غير صوت.",
            "تحت \"آخر حاجة سمعتها\" هتلاقي ⏱ زمن الاستجابة من ساعة ما خلصت كلام لحد ما بدأ التنفيذ.",
            "الإعدادات ← السجل: فيه كل أمر اتنفذ وأي خطوة فشلت وليه.",
            "لو المساعد بيقف لوحده: من شاشة الصلاحيات اعمل إعدادات البطارية الخاصة بموبايلك.",
        ),
        tip = "وأنت بتنفذ أمر طويل، قول \"وقف\" في أي وقت عشان تلغيه.",
        illustration = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconBubble(Icons.Filled.Hearing, Neo.Warning, 64)
                IconBubble(Icons.Filled.Keyboard, Neo.Cyan, 64)
                IconBubble(Icons.AutoMirrored.Filled.Help, Neo.Accent, 64)
            }
        },
    ),
    TutorialPage(
        tag = "اختياري",
        title = "الذكاء الاصطناعي (بعدين)",
        lines = listOf(
            "التطبيق شغال كامل بالأوامر من غير ذكاء اصطناعي.",
            "لو حبيت بعدين: الإعدادات ← الذكاء الاصطناعي ← حط مفتاح API (Claude أو أي API تاني) أو موديل محلي.",
            "ساعتها لو قلت حاجة مش متسجلة، هو يقرأ الشاشة ويقرر يعمل إيه لوحده.",
            "قبل أي إرسال أو دفع أو مسح هيسألك \"أأكد؟\"، ومش بيشتغل جوه تطبيقات البنوك.",
        ),
        illustration = { Pulse { IconBubble(Icons.Filled.AutoAwesome, Neo.Cyan, 84) } },
    ),
    TutorialPage(
        tag = "جاهز",
        title = "يلا نبدأ!",
        lines = listOf(
            "الخطوة الجاية: الصلاحيات (أهم حاجة الميكروفون وإمكانية الوصول).",
            "بعدها نزّل موديل الصوت.",
            "شغّل الدايرة وقول \"يا مساعد… افتح يوتيوب\" كأول تجربة.",
            "وتقدر ترجع للشرح ده في أي وقت من زرار \"الشرح\" في الرئيسية أو من الإعدادات.",
        ),
        illustration = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedLogo(110.dp)
                Spacer(Modifier.height(10.dp))
                Text("صوتك… أوامرك", color = Neo.Cyan, fontWeight = FontWeight.SemiBold)
            }
        },
    ),
)
