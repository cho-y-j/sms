package com.bizconnect.v2.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val SamsungShapes = Shapes(
    // Extra small for small UI elements (badges, chips)
    extraSmall = RoundedCornerShape(4.dp),
    // Small for buttons, input fields
    small = RoundedCornerShape(8.dp),
    // Medium for cards, dialogs
    medium = RoundedCornerShape(12.dp),
    // Large for large rounded surfaces
    large = RoundedCornerShape(16.dp),
    // Extra large for very large surfaces
    extraLarge = RoundedCornerShape(28.dp)
)

// Message bubble specific shapes
val SentMessageBubbleShape = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 4.dp,
    bottomEnd = 4.dp,
    bottomStart = 18.dp
)

val ReceivedMessageBubbleShape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 18.dp,
    bottomEnd = 18.dp,
    bottomStart = 4.dp
)
