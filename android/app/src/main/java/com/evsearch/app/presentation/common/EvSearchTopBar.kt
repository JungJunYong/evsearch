package com.evsearch.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 앱 아이콘과 동일한 번개(⚡) 벡터 아이콘
 * - One UI 9.0 스타일: 흰색 볼트 + 내부 하이라이트
 */
val EvSearchBoltIcon: ImageVector
    get() = ImageVector.Builder(
        name = "EvSearchBolt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Main bolt body (sharp, modern)
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(13f, 2f)
            lineTo(6f, 13f)
            lineTo(11f, 13f)
            lineTo(10f, 22f)
            lineTo(18f, 10f)
            lineTo(13f, 10f)
            close()
        }
        // Subtle inner highlight for depth (matches launcher icon)
        path(
            fill = SolidColor(Color(0xFFE8FDF7)),
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(13f, 2f)
            lineTo(6f, 13f)
            lineTo(9f, 13f)
            lineTo(12.5f, 5f)
            close()
        }
    }.build()

/**
 * Samsung One UI 9.0 공통 상단 앱바
 * - 지도/위젯 탭에서 완전히 동일한 디자인·간격·크기 사용
 * - 앱 아이콘과 일치하는 번개 아이콘 사용
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvSearchTopBar(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        actions = actions,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // App icon container: 38dp circle, same teal as launcher icon background
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = EvSearchBoltIcon,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
