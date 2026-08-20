package com.evsearch.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple 계열 디자인 토큰 — dark-tile 표현.
 *
 * 원칙
 * - 인터랙션 색은 단 하나(SkyBlue #2997ff). 상태 의미색은 상태 라벨/도트에만 쓴다.
 * - 그림자 금지. 깊이는 '타일 표면색 변화'와 1px 헤어라인으로만 만든다.
 * - 디스플레이 크기(17sp 이상)에는 음수 자간, 본문은 17sp/1.47.
 * - 굵기 사다리는 300 / 400 / 600 / 700. 500은 쓰지 않는다.
 */
object Apple {

    // ── Color ────────────────────────────────────────────────────────────────
    object C {
        /** 글로벌 내비 / 진짜 void */
        val Black = Color(0xFF000000)
        /** 화면 캔버스 */
        val Canvas = Color(0xFF1D1D1F)
        /** 카드 타일 1·2·3 (미세 단계차로 인접 타일을 분리) */
        val Tile1 = Color(0xFF272729)
        val Tile2 = Color(0xFF2A2A2C)
        val Tile3 = Color(0xFF252527)

        /** 단일 액센트 (dark surface 용) */
        val Accent = Color(0xFF2997FF)
        /** 포커스 링 */
        val AccentFocus = Color(0xFF0071E3)

        val Text = Color(0xFFFFFFFF)
        val TextMuted = Color(0xFFCCCCCC)
        val TextFaint = Color(0xFF98989D)
        val TextDisabled = Color(0xFF7A7A7A)

        val Hairline = Color(0x14FFFFFF)      // rgba(255,255,255,0.08)
        val HairlineStrong = Color(0x24FFFFFF)
        val ChipTranslucent = Color(0x52D2D2D7)

        // 상태 의미색 (Apple system colors, dark 변형) — 상태 표기에만 사용
        val StatusAvailable = Color(0xFF30D158)
        val StatusCharging = Color(0xFF0A84FF)
        val StatusCommError = Color(0xFFFF9F0A)
        val StatusMaintenance = Color(0xFFFFD60A)
        val StatusSuspended = Color(0xFFFF453A)
        val StatusReserved = Color(0xFFBF5AF0)
        val StatusUnknown = Color(0xFF98989D)
    }

    // ── Type ─────────────────────────────────────────────────────────────────
    object T {
        /** 히어로 헤드라인 (폰 기준 28sp) */
        val HeroDisplay = TextStyle(
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 30.sp, letterSpacing = (-0.5).sp
        )
        /** 타일 헤드라인 */
        val DisplayLg = TextStyle(
            fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 26.sp, letterSpacing = (-0.4).sp
        )
        /** 섹션 헤드 */
        val DisplayMd = TextStyle(
            fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp, letterSpacing = (-0.374).sp
        )
        /** 서브 타일 태그라인 / 카테고리명 */
        val Tagline = TextStyle(
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp, letterSpacing = 0.2.sp
        )
        /** 에어리한 리드 문단 (rare weight 300) */
        val LeadAiry = TextStyle(
            fontSize = 19.sp, fontWeight = FontWeight.Light,
            lineHeight = 28.sp
        )
        val Body = TextStyle(
            fontSize = 17.sp, fontWeight = FontWeight.Normal,
            lineHeight = 25.sp, letterSpacing = (-0.374).sp
        )
        val BodyStrong = TextStyle(
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp, letterSpacing = (-0.374).sp
        )
        val Caption = TextStyle(
            fontSize = 14.sp, fontWeight = FontWeight.Normal,
            lineHeight = 20.sp, letterSpacing = (-0.224).sp
        )
        val CaptionStrong = TextStyle(
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp, letterSpacing = (-0.224).sp
        )
        val FinePrint = TextStyle(
            fontSize = 12.sp, fontWeight = FontWeight.Normal,
            lineHeight = 16.sp, letterSpacing = (-0.12).sp
        )
        val MicroLegal = TextStyle(
            fontSize = 10.sp, fontWeight = FontWeight.Normal,
            lineHeight = 13.sp, letterSpacing = (-0.08).sp
        )
        /** 내비 링크 */
        val NavLink = TextStyle(
            fontSize = 12.sp, fontWeight = FontWeight.Normal,
            lineHeight = 14.sp, letterSpacing = (-0.12).sp
        )
    }

    // ── Shape ────────────────────────────────────────────────────────────────
    object S {
        val Xs = RoundedCornerShape(5.dp)
        val Sm = RoundedCornerShape(8.dp)
        val Md = RoundedCornerShape(11.dp)
        val Lg = RoundedCornerShape(18.dp)
        val Pill = RoundedCornerShape(percent = 50)
    }

    // ── Spacing ──────────────────────────────────────────────────────────────
    object Sp {
        val xxs = 4.dp
        val xs = 8.dp
        val sm = 12.dp
        val md = 17.dp
        val lg = 24.dp
        val xl = 32.dp
        val xxl = 48.dp
        val section = 64.dp
    }
}
