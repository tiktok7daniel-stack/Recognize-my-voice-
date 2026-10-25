package com.daniel.assistant

import com.daniel.assistant.ai.AiAction
import com.daniel.assistant.commands.AppEntry
import com.daniel.assistant.commands.CommandJson
import com.daniel.assistant.commands.CommandResolver
import com.daniel.assistant.commands.CustomCommand
import com.daniel.assistant.commands.Resolution
import com.daniel.assistant.commands.Step
import com.daniel.assistant.commands.SysAction
import com.daniel.assistant.commands.withVars
import com.daniel.assistant.voice.ArabicNormalizer
import com.daniel.assistant.voice.FuzzyMatcher
import com.daniel.assistant.voice.GrammarBuilder
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    private val apps = listOf(
        AppEntry("YouTube", "com.google.android.youtube"),
        AppEntry("Claude", "com.anthropic.claude"),
        AppEntry("WhatsApp", "com.whatsapp"),
        AppEntry("Impostazioni", "com.android.settings"),
        AppEntry("Spotify", "com.spotify.music", userAliases = listOf("الأغاني بتاعتي")),
    )

    private val demo = CustomCommand(
        id = 1, name = "واحد", triggers = listOf("واحد"),
        steps = listOf(
            Step.AttachPhotos(2, "com.anthropic.claude", "Claude"),
            Step.Wait(1500), Step.TypeText("حلهم"), Step.PressSend,
        ),
    )

    private val resolver = CommandResolver()

    @Test fun normalizerStripsDiacriticsAndUnifiesLetters() {
        assertEquals("افتح يوتيوب", ArabicNormalizer.normalize("افْتَحْ يُوتيوب"))
        assertEquals("اضغط علي الصوره", ArabicNormalizer.normalize("إضغط على الصورة"))
        assertEquals("ا", ArabicNormalizer.normalize("آ"))
        assertEquals("123", ArabicNormalizer.normalize("١٢٣"))
    }

    @Test fun fuzzyScores() {
        assertEquals(1.0, FuzzyMatcher.score("يوتيوب", "يُوتيوب"), 0.0001)
        assertTrue(FuzzyMatcher.score("واتس اب", "واتساب") > 0.8)
        assertTrue(FuzzyMatcher.score("كلود", "فيسبوك") < 0.5)
    }

    @Test fun customCommandWinsFirst() {
        val r = resolver.resolve("واحد", listOf(demo), apps, aiAvailable = true)
        assertTrue(r is Resolution.Custom)
        val r2 = resolver.resolve("واحده", listOf(demo), apps, aiAvailable = false)
        assertTrue(r2 is Resolution.Custom)
    }

    @Test fun disabledCustomIsIgnored() {
        val r = resolver.resolve("واحد", listOf(demo.copy(enabled = false)), apps, aiAvailable = false)
        assertTrue(r is Resolution.Unknown)
    }

    @Test fun systemCommands() {
        assertEquals(SysAction.BACK, (resolver.resolve("ارجع", emptyList(), apps, false) as Resolution.System).action)
        assertEquals(SysAction.TORCH_ON, (resolver.resolve("شغّل الكشاف", emptyList(), apps, false) as Resolution.System).action)
        assertEquals(SysAction.NOTIFICATIONS, (resolver.resolve("افتح الإشعارات", emptyList(), apps, false) as Resolution.System).action)
        val click = resolver.resolve("اضغط على تسجيل الدخول", emptyList(), apps, false) as Resolution.System
        assertEquals(SysAction.CLICK, click.action)
        assertEquals("تسجيل الدخول", click.arg)
        assertTrue(resolver.resolve("اكتب", emptyList(), apps, false) is Resolution.NeedsArg)
    }

    @Test fun openApps() {
        val yt = resolver.resolve("افتح يوتيوب", emptyList(), apps, false) as Resolution.OpenApp
        assertEquals("com.google.android.youtube", yt.app.packageName)
        val cl = resolver.resolve("افتحلي كلود", emptyList(), apps, false) as Resolution.OpenApp
        assertEquals("com.anthropic.claude", cl.app.packageName)
        val sp = resolver.resolve("شغل الاغاني بتاعتي", emptyList(), apps, false) as Resolution.OpenApp
        assertEquals("com.spotify.music", sp.app.packageName)
        val en = resolver.resolve("افتح whatsapp", emptyList(), apps, false) as Resolution.OpenApp
        assertEquals("com.whatsapp", en.app.packageName)
        assertTrue(resolver.resolve("افتح زرزور", emptyList(), apps, false) is Resolution.OpenAppUnknown)
    }

    @Test fun aiFallbackAndCancel() {
        assertTrue(resolver.resolve("ابعت لماما اني جاي", emptyList(), apps, true) is Resolution.Ai)
        assertTrue(resolver.resolve("ابعت لماما اني جاي", emptyList(), apps, false) is Resolution.Unknown)
        assertTrue(resolver.resolve("وقف", emptyList(), apps, true) is Resolution.Cancel)
    }

    @Test fun grammarContainsEverything() {
        val phrases = GrammarBuilder.phrases("يا مساعد", listOf(demo), apps)
        assertTrue("يا مساعد" in phrases)
        assertTrue("واحد" in phrases)
        assertTrue("افتح يوتيوب" in phrases)
        assertTrue("رجوع" in phrases)
        val json = GrammarBuilder.toJson(phrases)
        assertTrue(json.startsWith("[") && json.endsWith("\"[unk]\"]"))
    }

    @Test fun stepsRoundTripJson() {
        val s = CommandJson.json.encodeToString(ListSerializer(Step.serializer()), demo.steps)
        assertTrue(s.contains("\"type\":\"attach_photos\""))
        val back = CommandJson.json.decodeFromString(ListSerializer(Step.serializer()), s)
        assertEquals(demo.steps, back)
    }

    @Test fun parsesAiActions() {
        assertEquals(AiAction.Click(5), AiAction.parse("""{"action":"click","index":5}"""))
        assertEquals(AiAction.Type(3, "مرحبا {x}", true),
            AiAction.parse("""Sure: {"action":"type","index":3,"text":"مرحبا {x}","sensitive":true} ok"""))
        assertEquals(AiAction.Scroll(false), AiAction.parse("""{"action":"scroll","direction":"up"}"""))
        assertEquals(AiAction.Done("تمام"), AiAction.parse("""{"action":"done","say":"تمام"}"""))
        assertEquals(null, AiAction.parse("no json"))
    }
}

class ImportExportTest {
    @Test fun roundTrip() {
        val cmd = CustomCommand(name = "x", triggers = listOf("واحد", "وحد"), steps = listOf(Step.Home, Step.Scroll(false)))
        val text = com.daniel.assistant.commands.ImportExport.encode(
            listOf(cmd), listOf(com.daniel.assistant.commands.ExportAlias("com.x", "X", "اكس")),
        )
        val back = com.daniel.assistant.commands.ImportExport.decode(text)
        assertEquals(cmd.steps, back.commands.single().steps)
        assertEquals(cmd.triggers, back.commands.single().triggers)
        assertEquals("اكس", back.aliases.single().alias)
    }
}

class RecognitionUpgradeTest {
    private val resolver = CommandResolver()
    private val apps = listOf(AppEntry("Claude", "com.anthropic.claude"), AppEntry("YouTube", "com.google.android.youtube"))
    private val one = CustomCommand(id = 1, name = "واحد", triggers = listOf("واحد"), steps = listOf(Step.Home))

    @Test fun shortTriggerInsideShortUtterance() {
        assertTrue(resolver.resolve("يا واحد", listOf(one), apps, false) is Resolution.Custom)
        assertTrue(resolver.resolve("واحد بس", listOf(one), apps, false) is Resolution.Custom)
        assertTrue(resolver.resolve("انا عايز واحد شاي كبير دلوقتي", listOf(one), apps, false) !is Resolution.Custom)
    }

    @Test fun picksBestAlternative() {
        val (text, r) = resolver.resolveBest(listOf("وحيد", "واحد", "وأحد"), listOf(one), apps, aiAvailable = true)
        assertEquals("واحد", text)
        assertTrue(r is Resolution.Custom)
        val (t2, r2) = resolver.resolveBest(listOf("افتح يوتيوب", "افتح يو تيوب"), listOf(one), apps, false)
        assertEquals("افتح يوتيوب", t2)
        assertTrue(r2 is Resolution.OpenApp)
        val (_, r3) = resolver.resolveBest(listOf("ابعت رسالة لاحمد"), emptyList(), apps, aiAvailable = true)
        assertTrue(r3 is Resolution.Ai)
    }

    @Test fun buckwalterOutputIsConverted() {
        assertEquals("واحد", ArabicNormalizer.fromBuckwalterIfNeeded("wAHd"))
        assertEquals("يا مساعد", ArabicNormalizer.fromBuckwalterIfNeeded("yA msAEd"))
        assertEquals("واحد", ArabicNormalizer.fromBuckwalterIfNeeded("واحد"))
        assertEquals("[unk]", ArabicNormalizer.fromBuckwalterIfNeeded("[unk]"))
    }
}

class VariablesTest {
    private val send = CustomCommand(
        id = 5, name = "رسالة", triggers = listOf("ابعت لـ {اسم} {رسالة}"),
        steps = listOf(Step.ClickText("{اسم}"), Step.TypeText("{رسالة}"), Step.PressSend),
    )

    @Test fun extractsVariables() {
        val v = com.daniel.assistant.commands.Variables.match("ابعت لـ {اسم} {رسالة}", "ابعت لأحمد أنا جاي في الطريق")
        assertEquals(mapOf("اسم" to "أحمد", "رسالة" to "أنا جاي في الطريق"), v)
        val v2 = com.daniel.assistant.commands.Variables.match("ابحث عن {حاجة}", "إبحث عن مطاعم قريبة")
        assertEquals(mapOf("حاجة" to "مطاعم قريبة"), v2)
        assertEquals(null, com.daniel.assistant.commands.Variables.match("ابحث عن {حاجة}", "افتح يوتيوب"))
    }

    @Test fun resolverFillsSteps() {
        val r = CommandResolver().resolve("ابعت لماما تعالي", listOf(send), emptyList(), false) as Resolution.Custom
        assertEquals("ماما", r.vars["اسم"])
        val steps = r.command.steps.map { it.withVars(r.vars) }
        assertEquals(Step.TypeText("تعالي"), steps[1])
        assertEquals(Step.ClickText("ماما"), steps[0])
    }
}

class SpeedAccuracyTest {
    private val resolver = CommandResolver()
    private val one = CustomCommand(id = 1, name = "واحد", triggers = listOf("واحد"), steps = listOf(Step.Home))
    private val two = CustomCommand(id = 2, name = "اتنين", triggers = listOf("اتنين"), steps = listOf(Step.Back))

    @Test fun digitsMatchNumberTriggers() {
        assertTrue(resolver.resolve("1", listOf(one, two), emptyList(), false).let { it is Resolution.Custom && it.command.id == 1L })
        assertTrue(resolver.resolve("٢", listOf(one, two), emptyList(), false).let { it is Resolution.Custom && it.command.id == 2L })
    }

    @Test fun msaSpellingMatchesEgyptian() {
        val r = resolver.resolve("اثنين", listOf(one, two), emptyList(), false)
        assertTrue(r is Resolution.Custom && r.command.id == 2L)
    }

    @Test fun bestAlternativeByScore() {
        val (text, r) = resolver.resolveBest(listOf("واحده", "واحد"), listOf(one), emptyList(), false)
        assertEquals("واحد", text)
        assertTrue(r is Resolution.Custom)
    }

    @Test fun earlyConfidence() {
        assertTrue(resolver.isConfidentEarly("واحد", listOf(one)))
        assertTrue(resolver.isConfidentEarly("رجوع", emptyList()))
        assertTrue(!resolver.isConfidentEarly("افتح يو", listOf(one)))
        assertTrue(!resolver.isConfidentEarly("واحد كمان", listOf(one)))
    }
}
