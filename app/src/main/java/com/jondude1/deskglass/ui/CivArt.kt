package com.jondude1.deskglass.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

/** All the pixel art for the village, drawn from small text grids when the app starts. */
class CivArt {
    private fun sprite(rows: List<String>, pal: Map<Char, Int>): ImageBitmap {
        val h = rows.size
        val w = rows[0].length
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) for (x in 0 until w) {
            val c = pal[rows[y].getOrElse(x) { '.' }]
            if (c != null) bmp.setPixel(x, y, c)
        }
        return bmp.asImageBitmap()
    }

    private fun speckled(seed: Int, base: Int, dark: Int, light: Int): ImageBitmap {
        val r = Random(seed.toLong())
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) for (x in 0 until 16) {
            bmp.setPixel(x, y, when (r.nextInt(13)) {
                0 -> dark
                1 -> light
                else -> base
            })
        }
        return bmp.asImageBitmap()
    }

    private fun grassTile(seed: Int): ImageBitmap {
        val r = Random(seed.toLong() * 31)
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) for (x in 0 until 16) {
            bmp.setPixel(x, y, when (r.nextInt(14)) {
                0 -> 0xFF4C9650.toInt()
                1 -> 0xFF67B55F.toInt()
                else -> 0xFF5AA854.toInt()
            })
        }
        // a couple of little grass blades
        for (k in 0 until 3) {
            val x = 1 + r.nextInt(13)
            val y = 2 + r.nextInt(12)
            bmp.setPixel(x, y, 0xFF3F8744.toInt())
            bmp.setPixel(x, y - 1, 0xFF3F8744.toInt())
        }
        return bmp.asImageBitmap()
    }

    private fun waterTile(frame: Int): ImageBitmap {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        for (y in 0 until 16) for (x in 0 until 16) {
            val ripple = ((x + frame * 4 + (y / 4) * 6) % 10) < 2 && y % 4 == 1
            val deep = ((x / 4 + y / 4) % 2) == 0
            bmp.setPixel(
                x, y,
                if (ripple) 0xFF8CBDE8.toInt() else if (deep) 0xFF2B68AE.toInt() else 0xFF3175BC.toInt()
            )
        }
        return bmp.asImageBitmap()
    }

    val grass = List(4) { grassTile(it + 1) }
    val sand = List(2) { speckled(50 + it, 0xFFE6D394.toInt(), 0xFFD4BE7C.toInt(), 0xFFF2E4B0.toInt()) }
    val water = List(2) { waterTile(it) }

    val oak = sprite(OAK, mapOf(
        'g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    ))
    val pine = sprite(PINE, mapOf(
        'p' to 0xFF2F7F52.toInt(), 'P' to 0xFF1F5A3B.toInt(), 'q' to 0xFF4E9E6A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    ))
    val stump = sprite(STUMP, mapOf('t' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(), 'c' to 0xFFD9B77E.toInt()))
    val rock = sprite(ROCK, mapOf('r' to 0xFF9AA0AA.toInt(), 'R' to 0xFFC9CED6.toInt(), 'd' to 0xFF6B707A.toInt()))
    private val bushPal = mapOf('g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(), 'r' to 0xFFE0405A.toInt())
    val bush = sprite(BUSH, bushPal)
    val bushEmpty = sprite(BUSH.map { it.replace('r', 'g') }, bushPal)
    val hut = sprite(HUT, mapOf(
        'r' to 0xFFB5533C.toInt(), 'R' to 0xFF8F3E2C.toInt(), 'S' to 0xFFD97A5C.toInt(),
        'w' to 0xFFDDBB82.toInt(), 'W' to 0xFFBF9C63.toInt(), 'd' to 0xFF4A2E1B.toInt(),
        'k' to 0xFF9FD3F0.toInt(), 'b' to 0xFF8A6A3E.toInt(), 'c' to 0xFF6E6E78.toInt(),
    ))
    val hutStone = sprite(HUT, mapOf(
        'r' to 0xFF6F7A8C.toInt(), 'R' to 0xFF4F5868.toInt(), 'S' to 0xFF8E99AB.toInt(),
        'w' to 0xFFB9B9BF.toInt(), 'W' to 0xFF95959D.toInt(), 'd' to 0xFF3E2A1B.toInt(),
        'k' to 0xFF9FD3F0.toInt(), 'b' to 0xFF6B6B74.toInt(), 'c' to 0xFF6E6E78.toInt(),
    ))
    val tent = sprite(TENT, mapOf(
        'p' to 0xFF6B4A2B.toInt(), 't' to 0xFFC9A26B.toInt(), 'T' to 0xFFA67E4B.toInt(), 'd' to 0xFF3B2A1A.toInt(),
    ))
    val oreRock = sprite(ORE, mapOf(
        'r' to 0xFF9AA0AA.toInt(), 'R' to 0xFFC9CED6.toInt(), 'd' to 0xFF6B707A.toInt(),
        'o' to 0xFFD98A3D.toInt(), 'O' to 0xFFF2D06B.toInt(),
    ))
    val forge = sprite(FORGE, mapOf(
        'c' to 0xFF4A4A52.toInt(), 'C' to 0xFF2E2E34.toInt(), 's' to 0xFF8A8A94.toInt(), 'S' to 0xFF62626C.toInt(),
        'O' to 0xFFFF7A21.toInt(), 'y' to 0xFFFFD84A.toInt(),
    ))
    val farm = sprite(FARM, mapOf(
        'd' to 0xFF6B4A2B.toInt(), 'D' to 0xFF573A20.toInt(), 'g' to 0xFF7BC043.toInt(),
        'G' to 0xFFE8C547.toInt(), 'f' to 0xFF8B6B45.toInt(),
    ))
    val well = sprite(WELL, mapOf(
        'R' to 0xFFB5533C.toInt(), 'b' to 0xFF7A4B24.toInt(), 's' to 0xFF9AA0AA.toInt(), 'S' to 0xFF2B68AE.toInt(), 'd' to 0xFF6B707A.toInt(),
    ))
    private val chickPal = mapOf('w' to 0xFFF4F0E6.toInt(), 'o' to 0xFFE89A2B.toInt(), 'r' to 0xFFD9373A.toInt(), 'd' to 0xFF222222.toInt(), 'g' to 0xFFC9C3B4.toInt())
    val chicken = listOf(sprite(CHICK1, chickPal), sprite(CHICK2, chickPal))
    private val firePal = mapOf('y' to 0xFFFFE070.toInt(), 'o' to 0xFFFF8A2B.toInt(), 'L' to 0xFF6B4A2B.toInt(), 'l' to 0xFF4A3220.toInt(), 's' to 0xFF8E8E96.toInt())
    val fire = listOf(sprite(FIRE1, firePal), sprite(FIRE2, firePal))

    private val shirts = intArrayOf(0xFFD94F4F.toInt(), 0xFF4F7FD9.toInt(), 0xFFE0B040.toInt(), 0xFF8E4FD9.toInt(), 0xFF4FB58E.toInt(), 0xFFE07A3A.toInt(), 0xFFD94F9B.toInt(), 0xFFEFEFEF.toInt(), 0xFF8B5A2B.toInt(), 0xFFCD7F32.toInt(), 0xFF4A4F5A.toInt())
    private val shirtShade = intArrayOf(0xFFA83A3A.toInt(), 0xFF3A62A8.toInt(), 0xFFB48C2E.toInt(), 0xFF6D3AA8.toInt(), 0xFF3A8D6E.toInt(), 0xFFB05E2A.toInt(), 0xFFA83A78.toInt(), 0xFFC0C0C0.toInt(), 0xFF6B4220.toInt(), 0xFF9A5F22.toInt(), 0xFF2F333B.toInt())
    private val hairs = intArrayOf(0xFF3B2A1A.toInt(), 0xFF8A5A2B.toInt(), 0xFF222222.toInt(), 0xFFD9B04A.toInt(), 0xFF7A2E1E.toInt(), 0xFFB0B0B0.toInt(), 0xFF5A3A6A.toInt(), 0xFF2E4A3A.toInt(), 0xFF3B2A1A.toInt(), 0xFF7A2E1E.toInt(), 0xFF222222.toInt())
    private val people = List(10) { i ->
        val pal = mapOf(
            'r' to hairs[i], 'h' to 0xFFF2CBA3.toInt(), 'H' to 0xFFD9A982.toInt(), 'd' to 0xFF222222.toInt(),
            's' to shirts[i], 'S' to shirtShade[i], 'p' to 0xFF3A3F5C.toInt(), 'b' to 0xFF3B2A1A.toInt(),
        )
        listOf(sprite(PERSON1, pal), sprite(PERSON2, pal))
    }

    fun person(colorIdx: Int, frame: Int): ImageBitmap = people[colorIdx.coerceIn(0, 9)][frame and 1]

    companion object {
        val OAK = listOf(
            "....hhhhhh....",
            "..hhhhgggggg..",
            ".hhhggggggGgg.",
            ".hhgggggggggg.",
            "hhgggGgggggggG",
            "hgggggggGggggg",
            "gggGgggggggGgg",
            ".ggggggGggggg.",
            ".GgggggggggGg.",
            "..GGgggggGGG..",
            "....GGGGGG....",
            "......tt......",
            "......tT......",
            "......tT......",
            ".....tttT.....",
            "....ttTTTt....",
        )
        val PINE = listOf(
            ".....qq.....",
            "....qppp....",
            "....ppPp....",
            "...qpppPp...",
            "...pppPpp...",
            "..qppppPpp..",
            "..pppPpppp..",
            ".qpppppPppp.",
            ".ppPpppppPp.",
            "qppppPpppppp",
            "pppPpppppPpp",
            "....tttt....",
            "....tTtt....",
            "....tTtt....",
        )
        val STUMP = listOf(
            "..cccc..",
            ".cttttc.",
            ".tttTtt.",
            ".tTttTt.",
            "..tTTt..",
        )
        val ROCK = listOf(
            "....rrrr....",
            "..rrRRRrrr..",
            ".rrRRRrrrrrd",
            "rrrRRrrrrrrd",
            "rrrrrrrrdrrd",
            "rrrdrrrrrrdd",
            ".rrrrrrrrddd",
            "..dddddddd..",
        )
        val BUSH = listOf(
            "...hhgg...",
            "..hhgGgg..",
            ".hgggrggGg",
            "hgrgggggGg",
            "ggggGgrggg",
            ".gggggggGg",
            "..GGGGGG..",
        )
        val HUT = listOf(
            "......RR......",
            "....SSrrRR....",
            "..SSrrrrrrRR..",
            ".SrrrrrrrrrrR.",
            "rrrrrrrrrrrrrR",
            ".wwwwwwwwwwww.",
            ".wkkwwwwwkkw..".replace("..", "W."),
            ".wkkwddddwkkw.",
            ".wwwwddddwwww.",
            ".WwwwddddwwwW.",
            ".bbbbbbbbbbbb.",
        )
        val TENT = listOf(
            "......pp......",
            ".....tttT.....",
            "....ttttTT....",
            "...tttttTTT...",
            "..tttttddTTT..",
            ".tttttdddTTTT.",
            "tttttttdddTTTT",
            "ttttttddddTTTT",
        )
        val ORE = listOf(
            "....rrrr....",
            "..rrRRRrrr..",
            ".rrRoRrrorrd",
            "rrrRRrrrOrrd",
            "rorrrrrrdrrd",
            "rrrdrorrrrdd",
            ".rrrrrrOrddd",
            "..dddddddd..",
        )
        val FORGE = listOf(
            "....cccc....",
            "....cCCc....",
            "....cCCc....",
            "...cccccc...",
            "..ssssssss..",
            ".ssssssssss.",
            ".ssOOOOOOss.",
            ".ssOyyyyOss.",
            ".ssOyyyyOss.",
            ".ssssssssss.",
            "SSSSSSSSSSSS",
        )
        val FARM = listOf(
            "ffffffffffffff",
            "dDdDdDdDdDdDdD",
            "dgdgdgdgdgdgdg",
            "dGdGdGdGdGdGdG",
            "dDdDdDdDdDdDdD",
            "dgdgdgdgdgdgdg",
            "dGdGdGdGdGdGdG",
            "dDdDdDdDdDdDdD",
            "dgdgdgdgdgdgdg",
            "ffffffffffffff",
        )
        val WELL = listOf(
            "..RRRR..",
            ".RRRRRR.",
            "..b..b..",
            "..b..b..",
            ".ssssss.",
            "ssSSSSss",
            "sdssssds",
            ".dddddd.",
        )
        val CHICK1 = listOf(
            "...r...",
            "..wwd..",
            ".wwwwo.",
            "wwwwg..",
            ".wwwg..",
            "..o.o..",
        )
        val CHICK2 = listOf(
            ".......",
            ".......",
            "..wwwd.",
            ".wwwwwo",
            "wwwwgr.",
            "..o.o..",
        )
        val FIRE1 = listOf(
            "....y...",
            "...yoy..",
            "..yooyy.",
            "..yooy..",
            "..LlLlL.",
            ".LLLLLL.",
            "..ssss..",
        )
        val FIRE2 = listOf(
            "...y....",
            "..yoy...",
            "..yooy..",
            ".yyooyy.",
            "..LlLlL.",
            ".LLLLLL.",
            "..ssss..",
        )
        val PERSON1 = listOf(
            "..rrrr..",
            ".rrrrrr.",
            ".rhhhhr.",
            ".hdhhdh.",
            "..hHHh..",
            ".ssssss.",
            "hsSssSsh",
            "hssssssh",
            ".sSssSs.",
            ".pppppp.",
            ".pp..pp.",
            ".bb..bb.",
        )
        val PERSON2 = listOf(
            "..rrrr..",
            ".rrrrrr.",
            ".rhhhhr.",
            ".hdhhdh.",
            "..hHHh..",
            ".ssssss.",
            "hsSssSsh",
            "hssssssh",
            ".sSssSs.",
            ".pppppp.",
            "..pppp..",
            "..bbbb..",
        )
    }
}
