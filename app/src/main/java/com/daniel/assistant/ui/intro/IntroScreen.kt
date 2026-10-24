package com.daniel.assistant.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.assistant.ui.components.NabraBackground
import com.daniel.assistant.ui.components.NabraLogo
import com.daniel.assistant.ui.theme.Neo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI

/** Animated intro: the logo springs in, the voice ring draws itself, bars pulse, then the name appears. */
@Composable
fun IntroScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.55f) }
    val ring = remember { Animatable(0f) }
    val phase = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val subtitle = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        launch { ring.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
        launch { phase.animateTo((4 * PI).toFloat(), tween(2200, easing = LinearEasing)) }
        delay(650)
        launch { title.animateTo(1f, tween(600)) }
        delay(300)
        subtitle.animateTo(1f, tween(500))
        delay(700)
        onFinished()
    }

    NabraBackground {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            NabraLogo(
                size = 150.dp,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                ringProgress = ring.value,
                wavePhase = phase.value + 0.001f,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "نَبْرة",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Neo.Text,
                modifier = Modifier
                    .alpha(title.value)
                    .graphicsLayer { translationY = (1f - title.value) * 40f },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "صوتك… أوامرك",
                style = MaterialTheme.typography.titleMedium,
                color = Neo.Cyan,
                modifier = Modifier
                    .alpha(subtitle.value)
                    .graphicsLayer { translationY = (1f - subtitle.value) * 24f },
            )
        }
    }
}
