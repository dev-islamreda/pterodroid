package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun OrcaPanelLogo(
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
    animated: Boolean = true
) {

    val infiniteTransition = rememberInfiniteTransition(label = "orca_logo_pulse")
    val pulse by if (animated) {
        infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
    } else {
        remember { mutableFloatStateOf(1.0f) }
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pulse),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_orca_transparent),
            contentDescription = "Orca Panel Logo",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun PteroPilotLogo(
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
    animated: Boolean = true
) = OrcaPanelLogo(modifier = modifier, size = size, animated = animated)

@Composable
fun PterodroidLogo(
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
    animated: Boolean = true
) = OrcaPanelLogo(modifier = modifier, size = size, animated = animated)
