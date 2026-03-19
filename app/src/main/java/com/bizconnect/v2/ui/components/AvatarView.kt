package com.bizconnect.v2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bizconnect.v2.ui.theme.SamsungBlue
import com.bizconnect.v2.ui.theme.SamsungGreen
import com.bizconnect.v2.ui.theme.SamsungOrange
import com.bizconnect.v2.ui.theme.SamsungRed
import kotlin.math.abs

@Composable
fun AvatarView(
    name: String,
    photoUrl: String? = null,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    if (!photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Avatar for $name",
            modifier = modifier
                .size(size)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        val initial = name.firstOrNull()?.uppercaseChar() ?: '?'
        val backgroundColor = getColorForInitial(initial)

        Box(
            modifier = modifier
                .size(size)
                .background(backgroundColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial.toString(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value / 2.5).sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

private fun getColorForInitial(initial: Char): Color {
    return when (initial.code % 4) {
        0 -> SamsungBlue
        1 -> SamsungGreen
        2 -> SamsungOrange
        else -> SamsungRed
    }
}
