package com.keepdev.routeemock

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 루티 안드 디자인 시스템(core/designsystem)의 색과 글꼴을 옮겨 옴 */
object Routee {
    val Mint200 = Color(0xFFA3FBFE)
    val Mint300 = Color(0xFF63F6FD)
    val Mint500 = Color(0xFF01DEEF)
    val Lime300 = Color(0xFFE5FF4F)
    val Grey200 = Color(0xFFAAACB1)
    val Grey300 = Color(0xFF83868E)
    val Grey500 = Color(0xFF464A56)
    val Grey600 = Color(0xFF40434E)
    val Grey800 = Color(0xFF27292F)
    val Grey900 = Color(0xFF1D1F24)
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF101113)
    val Black60 = Color(0x99101113)
    val Black80 = Color(0xCC101113)
    val SurfaceTinted = Color(0x1A34E5F2)
    val Error = Color(0xFFDC4949)

    val Pretendard = FontFamily(
        Font(R.font.pretendard_regular, FontWeight.Normal),
        Font(R.font.pretendard_medium, FontWeight.Medium),
        Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    )
    private val Archivo = FontFamily(
        Font(R.font.archivo_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
        Font(R.font.archivo_bold_italic, FontWeight.Bold, FontStyle.Italic),
    )

    private fun pretendard(weight: FontWeight, size: Int) =
        TextStyle(fontFamily = Pretendard, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.4).sp)

    private fun display(weight: FontWeight, size: Int) =
        TextStyle(fontFamily = Archivo, fontWeight = weight, fontStyle = FontStyle.Italic, fontSize = size.sp, lineHeight = (size * 1.3).sp)

    val displaySb28 = display(FontWeight.SemiBold, 28)
    val displaySb22 = display(FontWeight.SemiBold, 22)
    val titleSb20 = pretendard(FontWeight.SemiBold, 20)
    val titleSb18 = pretendard(FontWeight.SemiBold, 18)
    val bodyR16 = pretendard(FontWeight.Normal, 16)
    val labelSb16 = pretendard(FontWeight.SemiBold, 16)
    val labelSb14 = pretendard(FontWeight.SemiBold, 14)
    val labelM14 = pretendard(FontWeight.Medium, 14)
    val labelSb12 = pretendard(FontWeight.SemiBold, 12)
    val labelR12 = pretendard(FontWeight.Normal, 12)
}

@Composable
fun RouteeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Routee.Mint300,
            onPrimary = Routee.Black,
            background = Routee.Black,
            surface = Routee.Black,
            surfaceContainerHigh = Routee.Grey800,
            onSurface = Routee.White,
            onSurfaceVariant = Routee.Grey200,
            outline = Routee.Grey500,
        ),
        content = content,
    )
}
