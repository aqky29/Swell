package com.music.swell.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Thick-stroke, round-capped icons in the spirit of Telegram's modern icon set.
 * Drawn as strokes so the 2.2px weight + round joins read as a single polished
 * family. Tint is applied by [androidx.compose.material3.Icon].
 *
 * [Home] is the exception and says why it is one.
 */
object BitChordIcons {

    private const val STROKE = 2.2f
    private val stroke = SolidColor(Color.Black)

    /** Compact list and quotation-bubble shapes used by the player controls. */
    val Queue: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_queue", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
                for (y in listOf(6f, 12f, 18f)) {
                    moveTo(8f, y)
                    lineTo(21f, y)
                }
            }
            path(fill = stroke) {
                for (y in listOf(6f, 12f, 18f)) {
                    moveTo(4.35f, y)
                    arcToRelative(1.25f, 1.25f, 0f, true, true, -2.5f, 0f)
                    arcToRelative(1.25f, 1.25f, 0f, true, true, 2.5f, 0f)
                    close()
                }
            }
        }.build()
    }

    val LyricsQuote: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_lyrics_quote", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke, strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6f, 3.5f)
                lineTo(18f, 3.5f)
                quadTo(21f, 3.5f, 21f, 6.5f)
                lineTo(21f, 15f)
                quadTo(21f, 18f, 18f, 18f)
                lineTo(10f, 18f)
                lineTo(6f, 21f)
                lineTo(6f, 18f)
                quadTo(3f, 18f, 3f, 15f)
                lineTo(3f, 6.5f)
                quadTo(3f, 3.5f, 6f, 3.5f)
                close()
            }
            path(fill = stroke) {
                for (x in listOf(7.6f, 12.8f)) {
                    moveTo(x, 8f)
                    lineTo(x + 3f, 8f)
                    lineTo(x + 3f, 11.1f)
                    quadTo(x + 3f, 13.2f, x + 0.5f, 13.7f)
                    lineTo(x + 0.5f, 12.3f)
                    quadTo(x + 1.7f, 12f, x + 1.7f, 11f)
                    lineTo(x, 11f)
                    close()
                }
            }
        }.build()
    }

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_play",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = stroke,
            ) {
                moveTo(6.8f, 4.8f)
                lineTo(19.2f, 12f)
                lineTo(6.8f, 19.2f)
                close()
            }
        }.build()
    }

    /**
     * The one solid icon in the set, and the tab bar is why.
     *
     * Selection here is carried by tint alone — see `GlassNavBar`, where the
     * unselected tabs are the same glyph at 65% — so the home tab is the anchor
     * of the bar rather than a state of it, and a filled silhouette reads as
     * that anchor at 24dp where a four-stroke outline reads as detail.
     *
     * Filled *and* stroked, with the same path. The outline of a house is all
     * corners — an apex, two shoulders, two door jambs — and stroking the fill
     * with a round-joined pen is what rounds every one of them at once, for a
     * geometry that stays nine straight lines. The numbers below are therefore
     * inset by half the stroke: the pen puts back what they give up, and the
     * door notch is drawn oversized for the same reason, since the pen closes
     * in on it from both jambs.
     */
    val Home: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_home",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                fill = stroke,
                stroke = stroke,
                strokeLineWidth = 1.7f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Roof, flush with the walls rather than overhanging them: an
                // eave narrower than the pen it is drawn with comes out as a
                // blob on the corner instead of a ledge.
                moveTo(12f, 4.05f)
                lineTo(19.75f, 11.35f)
                // Right wall, down to the floor
                lineTo(19.75f, 19.65f)
                // Door, cut up out of the bottom edge
                lineTo(14.95f, 19.65f)
                lineTo(14.95f, 15.6f)
                lineTo(9.05f, 15.6f)
                lineTo(9.05f, 19.65f)
                // Left wall, back up to the apex
                lineTo(4.25f, 19.65f)
                lineTo(4.25f, 11.35f)
                close()
            }
        }.build()
    }

    /**
     * The phone tab bar's Explore, Library and Search glyphs: solid-weight
     * iOS shapes rather than this file's stroked family, taken from the
     * outlines of Flutter's cupertino_icons font (MIT) — square_grid_2x2,
     * square_stack_fill and search.
     *
     * Each is the glyph laid out the way the font sets it: the 512-unit em
     * box is the viewport, the baseline sits at the font's ascent share of
     * it, and the advance is centred. So they keep the font's own optical
     * size and weight against one another at any icon size.
     */
    val TabExplore: ImageVector by lazy {
        fontGlyph(
            "bc_tab_explore",
            "M99.5 230.24Q58.5 230.24 58.5 188.24V93.24Q58.5 51.24 99.5 51.24H197.5Q238.5 51.24 238.5 93.24V188.24Q238.5 230.24 197.5 230.24Z" +
                "M314.5 230.24Q273.5 230.24 273.5 188.24V93.24Q273.5 51.24 314.5 51.24H412.5Q453.5 51.24 453.5 93.24V188.24Q453.5 230.24 412.5 230.24Z" +
                "M99.5 200.24H196.5Q207.5 200.24 207.5 188.24V93.24Q207.5 81.24 196.5 81.24H99.5Q88.5 81.24 88.5 93.24V188.24Q88.5 200.24 99.5 200.24Z" +
                "M314.5 200.24H411.5Q422.5 200.24 422.5 188.24V93.24Q422.5 81.24 411.5 81.24H314.5Q303.5 81.24 303.5 93.24V188.24Q303.5 200.24 314.5 200.24Z" +
                "M99.5 445.24Q58.5 445.24 58.5 403.24V307.24Q58.5 266.24 99.5 266.24H197.5Q238.5 266.24 238.5 307.24V403.24Q238.5 445.24 197.5 445.24Z" +
                "M314.5 445.24Q273.5 445.24 273.5 403.24V307.24Q273.5 266.24 314.5 266.24H412.5Q453.5 266.24 453.5 307.24V403.24Q453.5 445.24 412.5 445.24Z" +
                "M99.5 415.24H196.5Q207.5 415.24 207.5 403.24V308.24Q207.5 296.24 196.5 296.24H99.5Q88.5 296.24 88.5 308.24V403.24Q88.5 415.24 99.5 415.24Z" +
                "M314.5 415.24H411.5Q422.5 415.24 422.5 403.24V308.24Q422.5 296.24 411.5 296.24H314.5Q303.5 296.24 303.5 308.24V403.24Q303.5 415.24 314.5 415.24Z",
        )
    }

    /**
     * Two units lower than the font sets it: the back card's top edge rises
     * just past the em box, and the viewport would clip it flat.
     */
    val TabLibrary: ImageVector by lazy {
        fontGlyph(
            "bc_tab_library",
            "M154 27.24Q156 0.24 185 0.24H322Q352 0.24 353 27.24Z" +
                "M116 87.24Q121 56.24 150 56.24H356Q385 56.24 390 87.24Z" +
                "M133 500.24Q66 500.24 66 434.24V187.24Q66 121.24 133 121.24H378Q446 121.24 446 187.24V434.24Q446 500.24 385 500.24Z",
        )
    }

    val TabSearch: ImageVector by lazy {
        fontGlyph(
            "bc_tab_search",
            "M219 374.24Q151 374.24 102.5 325.74Q54 277.24 54 209.24Q54 141.24 102.5 92.74Q151 44.24 219 44.24Q287 44.24 335.5 92.74Q384 141.24 384 209.24Q384 264.24 350 309.24L451 410.24Q458 417.24 458 427.24Q458 438.24 451 445.24Q444 452.24 434 452.24Q423 452.24 416 445.24L315 343.24Q271 374.24 219 374.24Z" +
                "M128 300.24Q166 338.24 219 338.24Q272 338.24 310 300.24Q348 262.24 348 209.24Q348 156.24 310 118.24Q272 80.24 219 80.24Q166 80.24 128 118.24Q90 156.24 90 209.24Q90 262.24 128 300.24Z",
        )
    }

    /**
     * The phone tab bar's Home, Explore and Library glyphs, outline and filled:
     * house, compass and music_albums from Flutter's cupertino_icons font (MIT),
     * laid out in the em box the same way as [TabExplore]. The outline marks a
     * tab at rest and the fill the one under the selection lens, the way the
     * iOS tab bar swaps them.
     *
     * The album pair sits two units lower than the font sets it: the stack's
     * top edge rises just past the em box, and the viewport would clip it flat.
     */
    val TabHouse: ImageVector by lazy {
        fontGlyph(
            "bc_tab_house",
            "M115.5 473.24Q66.5 473.24 66.5 426.24V231.24L39.5 256.24Q32.5 263.24 23.5 263.24Q5.5 263.24 5.5 246.24Q5.5 238.24 11.5 232.24L230.5 33.24Q240.5 23.24 255.5 23.24Q270.5 23.24 281.5 33.24L366.5 111.24V38.24Q366.5 23.24 381.5 23.24H409.5Q425.5 23.24 425.5 38.24V164.24L499.5 232.24Q506.5 239.24 506.5 246.24Q506.5 253.24 501 258.24Q495.5 263.24 487.5 263.24Q481.5 263.24 472.5 256.24L444.5 230.24V426.24Q444.5 473.24 396.5 473.24Z" +
                "M317.5 297.24V439.24H389.5Q410.5 439.24 410.5 417.24V199.24L263.5 65.24Q260.5 62.24 255.5 62.24Q251.5 62.24 248.5 65.24L101.5 199.24V417.24Q101.5 439.24 122.5 439.24H193.5V297.24Q193.5 280.24 210.5 280.24H301.5Q317.5 280.24 317.5 297.24Z",
        )
    }

    val TabHouseFill: ImageVector by lazy {
        fontGlyph(
            "bc_tab_house_fill",
            "M5.5 246.24Q5.5 238.24 11.5 232.24L230.5 33.24Q241.5 23.24 255.5 23.24Q271.5 23.24 281.5 33.24L366.5 111.24V38.24Q366.5 23.24 382.5 23.24H410.5Q425.5 23.24 425.5 38.24V165.24L499.5 232.24Q506.5 239.24 506.5 246.24Q506.5 253.24 501 258.24Q495.5 263.24 487.5 263.24Q479.5 263.24 472.5 256.24L263.5 65.24Q259.5 62.24 255.5 62.24Q251.5 62.24 248.5 65.24L39.5 256.24Q32.5 263.24 23.5 263.24Q5.5 263.24 5.5 246.24Z" +
                "M66.5 426.24V270.24L248.5 106.24Q254.5 102.24 255.5 102.24Q259.5 102.24 262.5 105.24L444.5 269.24V426.24Q444.5 473.24 396.5 473.24H310.5V321.24Q310.5 305.24 293.5 305.24H218.5Q201.5 305.24 201.5 321.24V473.24H115.5Q92.5 473.24 79.5 460.74Q66.5 448.24 66.5 426.24Z",
        )
    }

    val TabCompass: ImageVector by lazy {
        fontGlyph(
            "bc_tab_compass",
            "M126.5 377.74Q179.5 431.24 255.5 431.24Q331.5 431.24 385 377.74Q438.5 324.24 438.5 248.24Q438.5 172.24 385 118.74Q331.5 65.24 255.5 65.24Q179.5 65.24 126.5 118.74Q73.5 172.24 73.5 248.24Q73.5 324.24 126.5 377.74Z" +
                "M411 403.24Q346.5 467.24 255.5 467.24Q164.5 467.24 100.5 403.24Q36.5 339.24 36.5 248.24Q36.5 157.24 100.5 93.24Q164.5 29.24 255.5 29.24Q346.5 29.24 411 93.24Q475.5 157.24 475.5 248.24Q475.5 339.24 411 403.24Z" +
                "M287.5 279.24 154.5 346.24 220.5 213.24 353.5 146.24Z" +
                "M196.5 303.24 268.5 259.24 240.5 231.24Z",
        )
    }

    val TabCompassFill: ImageVector by lazy {
        fontGlyph(
            "bc_tab_compass_fill",
            "M411 403.24Q346.5 467.24 255.5 467.24Q164.5 467.24 100.5 403.24Q36.5 339.24 36.5 248.24Q36.5 157.24 100.5 93.24Q164.5 29.24 255.5 29.24Q346.5 29.24 411 93.24Q475.5 157.24 475.5 248.24Q475.5 339.24 411 403.24Z" +
                "M289.5 281.24 355.5 148.24 222.5 215.24 156.5 348.24Z" +
                "M191.5 313.24 234.5 226.24 278.5 269.24Z",
        )
    }

    val TabAlbums: ImageVector by lazy {
        fontGlyph(
            "bc_tab_albums",
            "M150 27.24Q152 0.24 181 0.24H326Q356 0.24 357 27.24Z" +
                "M112 87.24Q117 56.24 146 56.24H360Q389 56.24 394 87.24Z" +
                "M133 500.24Q66 500.24 66 434.24V187.24Q66 121.24 133 121.24H378Q446 121.24 446 187.24V434.24Q446 500.24 385 500.24Z" +
                "M134 466.24H384Q396 466.24 403.5 457.24Q411 448.24 411 432.24V189.24Q411 155.24 378 155.24H134Q100 155.24 100 189.24V432.24Q100 466.24 134 466.24Z" +
                "M315 274.24 269 285.24Q263 286.24 263 295.24V362.24Q263 383.24 250.5 396.74Q238 410.24 217 410.24Q202 410.24 193.5 401.74Q185 393.24 185 381.24Q185 356.24 217 347.24L235 342.24Q243 340.24 244.5 338.24Q246 336.24 246 330.24V242.24Q246 228.24 257 226.24L314 212.24Q327 208.24 327 220.24V258.24Q327 270.24 315 274.24Z",
        )
    }

    val TabAlbumsFill: ImageVector by lazy {
        fontGlyph(
            "bc_tab_albums_fill",
            "M154 27.24Q156 0.24 185 0.24H322Q352 0.24 353 27.24Z" +
                "M116 87.24Q121 56.24 150 56.24H356Q385 56.24 390 87.24Z" +
                "M133 500.24Q66 500.24 66 434.24V187.24Q66 121.24 133 121.24H378Q446 121.24 446 187.24V434.24Q446 500.24 385 500.24Z" +
                "M315 274.24Q327 270.24 327 258.24V220.24Q327 208.24 314 212.24L257 226.24Q246 228.24 246 242.24V330.24Q246 336.24 244.5 338.24Q243 340.24 235 342.24L217 347.24Q185 356.24 185 381.24Q185 393.24 193.5 401.74Q202 410.24 217 410.24Q238 410.24 250.5 396.74Q263 383.24 263 362.24V295.24Q263 286.24 269 285.24Z",
        )
    }

    /** A filled glyph outline in the 512-unit em box — see [TabExplore]. */
    private fun fontGlyph(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 512f, viewportHeight = 512f,
        ).addPath(pathData = addPathNodes(pathData), fill = stroke).build()

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_search",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Lens (full circle from two arcs)
                moveTo(4.6f, 11f)
                arcToRelative(6.4f, 6.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.8f, 0f)
                arcToRelative(6.4f, 6.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, -12.8f, 0f)
                // Handle
                moveTo(15.9f, 15.9f)
                lineTo(20.4f, 20.4f)
            }
        }.build()
    }

    val Explore: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_explore",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Compass dial
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Needle
                moveTo(15.4f, 8.6f)
                lineTo(13.6f, 13.6f)
                lineTo(8.6f, 15.4f)
                lineTo(10.4f, 10.4f)
                close()
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_shuffle",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Strand that crosses downwards, with its arrow head
                moveTo(3.4f, 7.4f); lineTo(7f, 7.4f); lineTo(16.6f, 16.6f); lineTo(20.6f, 16.6f)
                moveTo(18.1f, 14.1f); lineTo(20.6f, 16.6f); lineTo(18.1f, 19.1f)
                // Strand that crosses upwards, broken around the intersection
                moveTo(3.4f, 16.6f); lineTo(7f, 16.6f); lineTo(9.8f, 13.9f)
                moveTo(13.9f, 10.1f); lineTo(16.6f, 7.4f); lineTo(20.6f, 7.4f)
                moveTo(18.1f, 4.9f); lineTo(20.6f, 7.4f); lineTo(18.1f, 9.9f)
            }
        }.build()
    }

    val Repeat: ImageVector by lazy { repeatLoop("bc_repeat") }

    /**
     * Two straight runs joined by semicircles, with the arrow heads lying flat
     * at the ends of the straights. Putting them on the curves instead — as a
     * first pass did — makes the glyph read as a refresh/sync symbol.
     */
    private fun repeatLoop(name: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.6f, 7.6f)
                lineTo(15.4f, 7.6f)
                arcToRelative(4.4f, 4.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8.8f)
                lineTo(8.6f, 16.4f)
                arcToRelative(4.4f, 4.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -8.8f)
                close()
                // Direction of travel: right along the top, left along the bottom.
                moveTo(13.5f, 5.7f); lineTo(15.4f, 7.6f); lineTo(13.5f, 9.5f)
                moveTo(10.5f, 14.5f); lineTo(8.6f, 16.4f); lineTo(10.5f, 18.3f)
            }
        }.build()

    /** AutoPlay's lemniscate. */
    val Infinity: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_infinity",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 12f)
                curveTo(10.1f, 9.1f, 8.7f, 8f, 7.1f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 8f)
                curveTo(8.7f, 16f, 10.1f, 14.9f, 12f, 12f)
                curveTo(13.9f, 9.1f, 15.3f, 8f, 16.9f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8f)
                curveTo(15.3f, 16f, 13.9f, 14.9f, 12f, 12f)
            }
        }.build()
    }

    /** Beamed pair of notes, for instrumental stretches in the lyrics. */
    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_music_note",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Heads are solid; stems and beam keep the family's stroke weight.
            path(fill = stroke) {
                moveTo(4.2f, 17.7f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
                moveTo(14.2f, 15.9f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
            }
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 17.7f); lineTo(10f, 6.7f)
                moveTo(20f, 15.9f); lineTo(20f, 4.9f)
                moveTo(10f, 6.7f); lineTo(20f, 4.9f)
            }
        }.build()
    }

    /** Speech bubble with two lines of words. */
    val Lyrics: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_lyrics",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.2f, 4.6f)
                lineTo(17.8f, 4.6f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, 2.8f)
                lineTo(20.6f, 13.6f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, 2.8f)
                lineTo(10.6f, 16.4f)
                lineTo(6.8f, 19.6f)
                lineTo(6.8f, 16.4f)
                lineTo(6.2f, 16.4f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, -2.8f)
                lineTo(3.4f, 7.4f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, -2.8f)
                close()
                moveTo(7.6f, 9f); lineTo(16.4f, 9f)
                moveTo(7.6f, 12.1f); lineTo(13.2f, 12.1f)
            }
        }.build()
    }

    /** Plain chevron — a disclosure hint, not a directional arrow. */
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_chevron_right",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9.5f, 6.2f)
                lineTo(15.3f, 12f)
                lineTo(9.5f, 17.8f)
            }
        }.build()
    }

    /**
     * The player's like control, in two weights.
     *
     * Filled rather than merely tinted when set: the player draws every glyph
     * white on artwork, where a colour change alone is the one signal the
     * backdrop can swallow. A shape change survives any album cover.
     */
    val Heart: ImageVector by lazy { heart("bc_heart", filled = false) }

    val HeartFilled: ImageVector by lazy { heart("bc_heart_filled", filled = true) }

    private fun heart(name: String, filled: Boolean): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = if (filled) stroke else null,
            ) {
                // Two lobes meeting at the top notch, falling to a single point.
                moveTo(12f, 20f)
                curveTo(12f, 20f, 3.2f, 14.6f, 3.2f, 8.9f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.8f, -1.5f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.8f, 1.5f)
                curveTo(20.8f, 14.6f, 12f, 20f, 12f, 20f)
                close()
            }
        }.build()

    /** Adding something — a new playlist, on the library shelf. */
    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_plus",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }.build()
    }

    /**
     * A tick — the other half of [Plus]. Saving something to the library swaps
     * one for the other in place, so the two are drawn on the same 14-unit span
     * and at the same weight; a tick sized to its own bounding box would jump.
     */
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_check",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 12.8f); lineTo(9.6f, 17.4f); lineTo(19f, 6.9f)
            }
        }.build()
    }

    /** Arrow pointing down into a tray — offline download. */
    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_download",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Vertical stem
                moveTo(12f, 4.5f); lineTo(12f, 15.5f)
                // Arrow head
                moveTo(7.5f, 11f); lineTo(12f, 15.5f); lineTo(16.5f, 11f)
                // Tray base
                moveTo(4.5f, 18f); lineTo(19.5f, 18f)
            }
        }.build()
    }

    /**
     * Clock face with two hands — a download asked for but not yet on disk.
     *
     * The dial is the same 8.6 radius as [Explore]'s, so the two sit at the same
     * optical weight when they appear in the same row of header buttons. The
     * hands are one polyline through the centre rather than two strokes: the
     * round join is then the pivot, which is what stops the middle reading as a
     * pair of lines that happen to cross.
     */
    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_clock",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Dial (full circle from two arcs)
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Minute hand up, hour hand down to the right
                moveTo(12f, 7.4f); lineTo(12f, 12f); lineTo(15.4f, 13.8f)
            }
        }.build()
    }

    /**
     * A pushpin, not a map marker: a round head with a straight needle
     * kicked out to one side, the way it sits once it's stuck into something.
     * A teardrop reads as "location" the moment it's this small — the two
     * disjoint strokes here are what keep it read as "pinned" instead.
     */
    val Pin: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_pin",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Round head.
                moveTo(9.6f, 8.4f)
                arcToRelative(3.4f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6.8f, 0f)
                arcToRelative(3.4f, 3.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, -6.8f, 0f)
                // Needle, kicked out from the underside of the head to a point.
                moveTo(10.8f, 10.8f)
                lineTo(5.6f, 19.6f)
            }
        }.build()
    }

    /** Speed gauge used by high-performance display settings. */
    val Performance: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_performance",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Open gauge, leaving room for the base line.
                moveTo(4.1f, 16.8f)
                arcToRelative(8.4f, 8.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 15.8f, 0f)
                // Needle and hub.
                moveTo(12f, 15.8f)
                lineTo(16.7f, 9.3f)
                moveTo(10.9f, 16.9f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, 2.2f, 0f)
                arcToRelative(1.1f, 1.1f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.2f, 0f)
                // Stable baseline.
                moveTo(5.4f, 19.5f)
                lineTo(18.6f, 19.5f)
            }
        }.build()
    }

    /** Four frame-time bars, used by refresh-rate selection. */
    val FrameRate: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_frame_rate",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(5f, 15.5f); lineTo(5f, 8.5f)
                moveTo(9.7f, 18.5f); lineTo(9.7f, 5.5f)
                moveTo(14.3f, 16.5f); lineTo(14.3f, 7.5f)
                moveTo(19f, 14.5f); lineTo(19f, 9.5f)
            }
        }.build()
    }

    val Library: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_library",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Three upright spines + one leaning — Apple Music's library glyph, thickened
                moveTo(4.6f, 4.8f); lineTo(4.6f, 19.2f)
                moveTo(9.2f, 4.8f); lineTo(9.2f, 19.2f)
                moveTo(13.8f, 4.8f); lineTo(13.8f, 19.2f)
                moveTo(17.2f, 5.6f); lineTo(20.6f, 18.9f)
            }
        }.build()
    }

    /** 2x2 grid icon (4 rounded squares) for switching layout to grid view. */
    val GridView: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_grid_view",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Top-left
                moveTo(6.5f, 4.5f)
                lineTo(8.5f, 4.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(10.5f, 8.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(6.5f, 10.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(4.5f, 6.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Top-right
                moveTo(15.5f, 4.5f)
                lineTo(17.5f, 4.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19.5f, 8.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(15.5f, 10.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(13.5f, 6.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Bottom-left
                moveTo(6.5f, 13.5f)
                lineTo(8.5f, 13.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(10.5f, 17.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(6.5f, 19.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(4.5f, 15.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()

                // Bottom-right
                moveTo(15.5f, 13.5f)
                lineTo(17.5f, 13.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19.5f, 17.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(15.5f, 19.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(13.5f, 15.5f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                close()
            }
        }.build()
    }

    /** List view icon (horizontal rows) for switching layout to list view. */
    val ListView: ImageVector by lazy {
        ImageVector.Builder(
            name = "bc_list_view",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Three horizontal lines with bullet markers
                moveTo(4.5f, 6.5f); lineTo(5.5f, 6.5f)
                moveTo(9.5f, 6.5f); lineTo(19.5f, 6.5f)

                moveTo(4.5f, 12f); lineTo(5.5f, 12f)
                moveTo(9.5f, 12f); lineTo(19.5f, 12f)

                moveTo(4.5f, 17.5f); lineTo(5.5f, 17.5f)
                moveTo(9.5f, 17.5f); lineTo(19.5f, 17.5f)
            }
        }.build()
    }
}
