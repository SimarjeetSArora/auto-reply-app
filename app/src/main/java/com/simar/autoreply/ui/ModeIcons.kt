package com.simar.autoreply.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Icons a mode can pick, by the key stored in [com.simar.autoreply.data.Mode.icon]. */
val ModeIcons: Map<String, ImageVector> = linkedMapOf(
    "car" to Icons.Rounded.DirectionsCar,
    "moon" to Icons.Rounded.Bedtime,
    "school" to Icons.Rounded.School,
    "work" to Icons.Rounded.Work,
    "gym" to Icons.Rounded.FitnessCenter,
    "food" to Icons.Rounded.Restaurant,
    "movie" to Icons.Rounded.Movie,
    "flight" to Icons.Rounded.Flight,
    "hospital" to Icons.Rounded.LocalHospital,
    "pray" to Icons.Rounded.SelfImprovement,
    "game" to Icons.Rounded.SportsEsports,
    "hourglass" to Icons.Rounded.HourglassTop,
    "silent" to Icons.Rounded.NotificationsOff,
    "chat" to Icons.Rounded.Chat,
    "music" to Icons.Rounded.Headphones,
    "sports" to Icons.Rounded.SportsSoccer,
)

fun modeIcon(key: String): ImageVector = ModeIcons[key] ?: Icons.Rounded.Chat

/** An icon centred in a soft tinted circle: the app's standard icon treatment. */
@Composable
fun IconBadge(icon: ImageVector, size: Int, tint: Color, background: Color = tint.copy(alpha = 0.14f)) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}
