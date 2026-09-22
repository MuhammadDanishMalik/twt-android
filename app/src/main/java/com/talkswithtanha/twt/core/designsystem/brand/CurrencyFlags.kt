package com.talkswithtanha.twt.core.designsystem.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Flags, drawn rather than shipped.
 *
 * A direct port of `CurrencyFlagView.swift`. Eighty-odd flag images would be
 * eighty-odd assets to license, localise and keep in step with the iOS app;
 * these are a few dozen lines of geometry that render identically on both
 * platforms and scale to any size. They are stylised on purpose — a 30dp circle
 * cannot hold a real flag's detail, and pretending otherwise gives you mud.
 */
sealed interface FlagDesign {
    data class Horizontal(val colors: List<Color>) : FlagDesign
    data class Vertical(val colors: List<Color>) : FlagDesign
    data class NordicCross(val field: Color, val cross: Color) : FlagDesign
    data class CentredCross(val field: Color, val cross: Color) : FlagDesign
    data class Disc(val field: Color, val disc: Color, val radius: Float) : FlagDesign
    data class StarRing(val field: Color, val star: Color) : FlagDesign
    data class CornerStars(val field: Color, val star: Color) : FlagDesign
    data class StripesWithCanton(
        val stripe: Color,
        val alt: Color,
        val canton: Color
    ) : FlagDesign
    data object UnionJack : FlagDesign
    data class UnionCanton(val field: Color) : FlagDesign
    data class Symbol(val text: String, val field: Color, val tint: Color) : FlagDesign
    data class FlagWithLabel(val base: FlagBase, val label: String) : FlagDesign
}

enum class FlagBase { USA, GERMANY, UK, FRANCE, EU, SPAIN, ITALY, JAPAN, HONG_KONG, AUSTRALIA, CHINA, SINGAPORE }

private val Navy = Color(0xFF002466)
private val BritRed = Color(0xFFCA0F30)

private fun baseDesign(base: FlagBase): FlagDesign = when (base) {
    FlagBase.USA -> FlagDesign.StripesWithCanton(BritRed, Color.White, Navy)
    FlagBase.GERMANY -> FlagDesign.Horizontal(
        listOf(Color.Black, Color(0xFFDB1417), Color(0xFFFFCF00))
    )
    FlagBase.UK -> FlagDesign.UnionJack
    FlagBase.FRANCE -> FlagDesign.Vertical(
        listOf(Color(0xFF002494), Color.White, Color(0xFFED2938))
    )
    FlagBase.EU -> FlagDesign.StarRing(Color(0xFF003399), Color(0xFFFFCC00))
    FlagBase.SPAIN -> FlagDesign.Horizontal(
        listOf(Color(0xFFAB0F21), Color(0xFFFFC400), Color(0xFFAB0F21))
    )
    FlagBase.ITALY -> FlagDesign.Vertical(
        listOf(Color(0xFF008C45), Color.White, Color(0xFFCF1C33))
    )
    FlagBase.JAPAN -> FlagDesign.Disc(Color.White, Color(0xFFBF0F33), 0.22f)
    FlagBase.HONG_KONG -> FlagDesign.Disc(Color(0xFFDE1A29), Color.White, 0.18f)
    FlagBase.AUSTRALIA -> FlagDesign.UnionCanton(Navy)
    FlagBase.CHINA -> FlagDesign.CornerStars(Color(0xFFED1C24), Color(0xFFFFDE00))
    FlagBase.SINGAPORE -> FlagDesign.Horizontal(listOf(Color(0xFFED1C2E), Color.White))
}

/** The currency-code lookup, ported from `Currency.flagDesign`. */
fun flagDesignFor(code: String): FlagDesign = when (code.uppercase()) {
    "USD" -> FlagDesign.StripesWithCanton(BritRed, Color.White, Navy)
    "EUR" -> FlagDesign.StarRing(Color(0xFF003399), Color(0xFFFFCC00))
    "GBP" -> FlagDesign.UnionJack
    "JPY" -> FlagDesign.Disc(Color.White, Color(0xFFBF0F33), 0.22f)
    "CHF" -> FlagDesign.CentredCross(Color(0xFFD90000), Color.White)
    "AUD" -> FlagDesign.UnionCanton(Navy)
    "NZD" -> FlagDesign.UnionCanton(Color(0xFF001A5C))
    "CAD" -> FlagDesign.Vertical(listOf(BritRed, Color.White, BritRed))
    "CNY" -> FlagDesign.CornerStars(Color(0xFFED1C24), Color(0xFFFFDE00))
    "RUB" -> FlagDesign.Horizontal(
        listOf(Color.White, Color(0xFF0038A6), Color(0xFFD92121))
    )
    "SEK" -> FlagDesign.NordicCross(Color(0xFF00529A), Color(0xFFFFCC00))
    "NOK" -> FlagDesign.NordicCross(BritRed, Color.White)
    "DKK" -> FlagDesign.NordicCross(Color(0xFFC70F2E), Color.White)
    "PLN" -> FlagDesign.Horizontal(listOf(Color.White, Color(0xFFDB213D)))
    "TRY" -> FlagDesign.Disc(Color(0xFFE30F24), Color.White, 0.20f)
    "ZAR" -> FlagDesign.Horizontal(
        listOf(Color(0xFF007859), Color.White, Color(0xFFDE2129))
    )
    "MXN" -> FlagDesign.Vertical(
        listOf(Color(0xFF006B3D), Color.White, Color(0xFFCF1226))
    )
    "SGD" -> FlagDesign.Horizontal(listOf(Color(0xFFED1C2E), Color.White))
    "HKD" -> FlagDesign.Disc(Color(0xFFDE1A29), Color.White, 0.18f)
    "INR" -> FlagDesign.Horizontal(
        listOf(Color(0xFFFF9933), Color.White, Color(0xFF128708))
    )
    "PKR" -> FlagDesign.Vertical(listOf(Color.White, Color(0xFF00663D)))

    "XAU" -> FlagDesign.Symbol("Au", Color(0xFFB88C1A), Color.White)
    "XAG" -> FlagDesign.Symbol("Ag", Color(0xFF8C9199), Color.White)
    "XPT" -> FlagDesign.Symbol("Pt", Color(0xFF73808C), Color.White)
    "XPD" -> FlagDesign.Symbol("Pd", Color(0xFF5C6B78), Color.White)

    "BTC" -> FlagDesign.Symbol("₿", Color(0xFFF29417), Color.White)
    "ETH" -> FlagDesign.Symbol("Ξ", Color(0xFF616BBF), Color.White)
    "SOL" -> FlagDesign.Symbol("S", Color(0xFF994CE6), Color.White)
    "XRP" -> FlagDesign.Symbol("X", Color(0xFF212933), Color.White)
    "USDT" -> FlagDesign.Symbol("₮", Color(0xFF26A687), Color.White)

    "USOIL" -> FlagDesign.Symbol("WTI", Color(0xFF242629), Color.White)
    "UKOIL" -> FlagDesign.Symbol("BR", Color(0xFF333833), Color.White)
    "NGAS" -> FlagDesign.Symbol("NG", Color(0xFF337AB8), Color.White)

    "NAS100" -> FlagDesign.FlagWithLabel(FlagBase.USA, "100")
    "US30" -> FlagDesign.FlagWithLabel(FlagBase.USA, "30")
    "US500" -> FlagDesign.FlagWithLabel(FlagBase.USA, "500")
    "US2000" -> FlagDesign.FlagWithLabel(FlagBase.USA, "2K")
    "VIX" -> FlagDesign.Symbol("VIX", Color(0xFF8C2633), Color.White)
    "GER40" -> FlagDesign.FlagWithLabel(FlagBase.GERMANY, "40")
    "UK100" -> FlagDesign.FlagWithLabel(FlagBase.UK, "100")
    "FRA40" -> FlagDesign.FlagWithLabel(FlagBase.FRANCE, "40")
    "EU50" -> FlagDesign.FlagWithLabel(FlagBase.EU, "50")
    "ESP35" -> FlagDesign.FlagWithLabel(FlagBase.SPAIN, "35")
    "ITA40" -> FlagDesign.FlagWithLabel(FlagBase.ITALY, "40")
    "JP225" -> FlagDesign.FlagWithLabel(FlagBase.JAPAN, "225")
    "HK50" -> FlagDesign.FlagWithLabel(FlagBase.HONG_KONG, "50")
    "AUS200" -> FlagDesign.FlagWithLabel(FlagBase.AUSTRALIA, "200")
    "CHINA50" -> FlagDesign.FlagWithLabel(FlagBase.CHINA, "50")
    "SG30" -> FlagDesign.FlagWithLabel(FlagBase.SINGAPORE, "30")

    else -> FlagDesign.Symbol(
        code.take(1).ifEmpty { "?" },
        Color(0xFF474747),
        Color.White
    )
}

/**
 * A pair, split into its two currencies.
 *
 * Ported from `CurrencyPair.parse`: a separator if there is one, otherwise six
 * letters split down the middle, otherwise a single-sided instrument like
 * `NAS100` that has no quote currency at all.
 */
data class ParsedPair(val base: String, val quote: String?) {
    val isSingleSided: Boolean get() = quote.isNullOrEmpty()

    companion object {
        fun parse(symbol: String): ParsedPair {
            val cleaned = symbol.uppercase().trim()
            if (cleaned.isEmpty()) return ParsedPair("", null)

            val parts = cleaned.split('/', '-', '_').filter { it.isNotBlank() }
            if (parts.size == 2) return ParsedPair(parts[0], parts[1])
            if (parts.size == 1 && parts[0].length == 6 && parts[0].all { it.isLetter() }) {
                return ParsedPair(parts[0].take(3), parts[0].takeLast(3))
            }
            return ParsedPair(parts.firstOrNull() ?: cleaned, null)
        }
    }
}

/** One flag, in a circle with a hairline rim. */
@Composable
fun CurrencyFlag(
    code: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val design = flagDesignFor(code)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .size(size)
            .clip(CircleShape)
    ) {
        drawFlag(design, measurer)
        drawCircle(
            color = Color.White.copy(alpha = 0.22f),
            radius = this.size.minDimension / 2 - 0.4f,
            style = Stroke(width = 0.8f * density)
        )
    }
}

/**
 * Both sides of a pair, overlapped — the base in front, the quote behind it,
 * with a dark separator ring so the two read as distinct coins.
 */
@Composable
fun PairFlagBadge(
    pair: String,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    separatorColor: Color = Color.Black.copy(alpha = 0.55f)
) {
    val parsed = remember(pair) { ParsedPair.parse(pair) }
    val overlap = size * 0.38f
    val separator = (size.value * 0.06f).coerceAtLeast(1.5f).dp

    if (parsed.isSingleSided) {
        CurrencyFlag(parsed.base, modifier, size)
        return
    }

    Box(modifier.size(width = size * 2 - overlap, height = size)) {
        CurrencyFlag(
            code = parsed.quote.orEmpty(),
            modifier = Modifier.offset(x = size - overlap),
            size = size
        )
        Box(
            Modifier
                .offset(x = -separator, y = -separator)
                .size(size + separator * 2)
                .clip(CircleShape)
                .background(separatorColor)
        )
        CurrencyFlag(parsed.base, size = size)
    }
}

// ── Drawing ──────────────────────────────────────────────────────────────────

private fun DrawScope.drawFlag(design: FlagDesign, measurer: TextMeasurer) {
    val rect = Rect(Offset.Zero, size)
    when (design) {
        is FlagDesign.Horizontal -> bands(design.colors, vertical = false)
        is FlagDesign.Vertical -> bands(design.colors, vertical = true)

        is FlagDesign.NordicCross -> {
            drawRect(design.field)
            val thickness = size.width * 0.20f
            val x = size.width * 0.34f
            drawRect(design.cross, Offset(0f, size.height / 2 - thickness / 2), Size(size.width, thickness))
            drawRect(design.cross, Offset(x - thickness / 2, 0f), Size(thickness, size.height))
        }

        is FlagDesign.CentredCross -> {
            drawRect(design.field)
            val arm = size.width * 0.22f
            val length = size.width * 0.62f
            drawRect(design.cross, Offset(rect.center.x - length / 2, rect.center.y - arm / 2), Size(length, arm))
            drawRect(design.cross, Offset(rect.center.x - arm / 2, rect.center.y - length / 2), Size(arm, length))
        }

        is FlagDesign.Disc -> {
            drawRect(design.field)
            drawCircle(design.disc, size.width * design.radius, rect.center)
        }

        is FlagDesign.StarRing -> {
            drawRect(design.field)
            val ringRadius = size.width * 0.30f
            repeat(12) { i ->
                val angle = i / 12.0 * 2 * PI - PI / 2
                drawPath(
                    star(
                        Offset(
                            rect.center.x + (cos(angle) * ringRadius).toFloat(),
                            rect.center.y + (sin(angle) * ringRadius).toFloat()
                        ),
                        size.width * 0.055f
                    ),
                    design.star
                )
            }
        }

        is FlagDesign.CornerStars -> {
            drawRect(design.field)
            drawPath(
                star(Offset(size.width * 0.24f, size.height * 0.30f), size.width * 0.13f),
                design.star
            )
            listOf(0.44f to 0.16f, 0.52f to 0.30f, 0.50f to 0.46f, 0.40f to 0.56f).forEach { (dx, dy) ->
                drawPath(
                    star(Offset(size.width * dx, size.height * dy), size.width * 0.05f),
                    design.star
                )
            }
        }

        is FlagDesign.StripesWithCanton -> {
            val count = 7
            val h = size.height / count
            repeat(count) { i ->
                drawRect(
                    if (i % 2 == 0) design.stripe else design.alt,
                    Offset(0f, i * h),
                    Size(size.width, h)
                )
            }
            val cantonSize = Size(size.width * 0.46f, size.height * 0.54f)
            drawRect(design.canton, Offset.Zero, cantonSize)
            for (row in 0 until 3) {
                for (col in 0 until 4) {
                    drawPath(
                        star(
                            Offset(
                                cantonSize.width * (0.16f + col * 0.23f),
                                cantonSize.height * (0.22f + row * 0.29f)
                            ),
                            size.width * 0.035f
                        ),
                        Color.White
                    )
                }
            }
        }

        FlagDesign.UnionJack -> drawUnionJack(size)

        is FlagDesign.UnionCanton -> {
            drawRect(design.field)
            val canton = Size(size.width * 0.5f, size.height * 0.5f)
            clipRect(right = canton.width, bottom = canton.height) {
                drawUnionJack(canton)
            }
            listOf(0.74f to 0.30f, 0.86f to 0.52f, 0.70f to 0.62f, 0.78f to 0.82f).forEach { (dx, dy) ->
                drawPath(
                    star(Offset(size.width * dx, size.height * dy), size.width * 0.055f),
                    Color.White
                )
            }
        }

        is FlagDesign.FlagWithLabel -> {
            drawFlag(baseDesign(design.base), measurer)
            drawRect(Color.Black.copy(alpha = 0.45f))
            centredText(design.label, size.width * 0.34f, Color.White, measurer)
        }

        is FlagDesign.Symbol -> {
            drawRect(design.field)
            centredText(design.text, size.width * 0.55f, design.tint, measurer)
        }
    }
}

private fun DrawScope.bands(colors: List<Color>, vertical: Boolean) {
    if (colors.isEmpty()) return
    val extent = (if (vertical) size.width else size.height) / colors.size
    colors.forEachIndexed { i, colour ->
        if (vertical) {
            drawRect(colour, Offset(i * extent, 0f), Size(extent, size.height))
        } else {
            drawRect(colour, Offset(0f, i * extent), Size(size.width, extent))
        }
    }
}

private fun DrawScope.drawUnionJack(area: Size) {
    drawRect(Navy, Offset.Zero, area)

    val diagonals = Path().apply {
        moveTo(0f, 0f); lineTo(area.width, area.height)
        moveTo(area.width, 0f); lineTo(0f, area.height)
    }
    drawPath(diagonals, Color.White, style = Stroke(area.width * 0.20f))
    drawPath(diagonals, BritRed, style = Stroke(area.width * 0.09f))

    val whiteArm = area.width * 0.30f
    val redArm = area.width * 0.17f
    drawRect(Color.White, Offset(0f, area.height / 2 - whiteArm / 2), Size(area.width, whiteArm))
    drawRect(Color.White, Offset(area.width / 2 - whiteArm / 2, 0f), Size(whiteArm, area.height))
    drawRect(BritRed, Offset(0f, area.height / 2 - redArm / 2), Size(area.width, redArm))
    drawRect(BritRed, Offset(area.width / 2 - redArm / 2, 0f), Size(redArm, area.height))
}

private fun star(centre: Offset, radius: Float): Path = Path().apply {
    val inner = radius * 0.42f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else inner
        val angle = i / 10.0 * 2 * PI - PI / 2
        val point = Offset(
            centre.x + (cos(angle) * r).toFloat(),
            centre.y + (sin(angle) * r).toFloat()
        )
        if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
    }
    close()
}

private fun DrawScope.centredText(
    text: String,
    fontSizePx: Float,
    colour: Color,
    measurer: TextMeasurer
) {
    val layout = measurer.measure(
        text = androidx.compose.ui.text.AnnotatedString(text),
        style = TextStyle(
            fontSize = (fontSizePx / density).sp,
            fontWeight = FontWeight.Bold,
            color = colour
        )
    )
    translate(
        left = (size.width - layout.size.width) / 2f,
        top = (size.height - layout.size.height) / 2f
    ) {
        drawText(layout)
    }
}
