package com.jondude1.deskglass.sim

/** One kingdom on the map. Id 0 is the player's own village, the rest are neighbours with their own banners. */
class Kingdom(val id: Int, var name: String, val race: String, val color: Long, val cx: Int, val cy: Int) {
    var pop = 6
    var might = 10f
    var fallen = false
    var fallenAt = 0.0
    var radius = 4f
    val homes = ArrayList<IntArray>()
}

/** How two kingdoms stand: war, alliance or trade. Peace is the absence of one. */
class Rel(val a: Int, val b: Int, var kind: String, var until: Double)

/** A short visual effect from a god power or a battle. */
class Fx(val type: String, val x: Float, val y: Float, val born: Double, val life: Float)

/** A multi-day age of the whole world, in the WorldBox spirit. mood is the change per twelve seconds. */
class WorldAgeInfo(
    val name: String,
    val blurb: String,
    val tint: Long,
    val tintA: Float,
    val particle: String,
    val mood: Float,
    val farm: Float,
    val war: Float,
    val darkFloor: Float,
)

val WORLD_AGES = listOf(
    WorldAgeInfo("Age of Hope", "Calm skies. Crops and spirits thrive.", 0xFFFFF0B0, 0.05f, "motes", 0.6f, 1.15f, 0.6f, 0f),
    WorldAgeInfo("Age of Sun", "Blazing days. Fields ripen fast.", 0xFFFFD25A, 0.10f, "rays", 0.4f, 1.4f, 0.9f, 0f),
    WorldAgeInfo("Age of Moon", "Silver nights and quiet magic.", 0xFF8FA8FF, 0.10f, "silver", 0.1f, 1.0f, 0.8f, 0.2f),
    WorldAgeInfo("Age of Wonders", "Strange gifts fall from the sky.", 0xFFFF9AF0, 0.06f, "sparkle", 0.8f, 1.1f, 0.7f, 0f),
    WorldAgeInfo("Age of Dark", "Long shadows. Fear spreads.", 0xFF1A1030, 0.18f, "wisps", -0.9f, 0.85f, 1.4f, 0.3f),
    WorldAgeInfo("Age of Tears", "Endless rain. Hearts grow heavy.", 0xFF4A6A9A, 0.12f, "none", -0.6f, 1.0f, 0.9f, 0.08f),
    WorldAgeInfo("Age of Chaos", "Omens, quarrels and wild events.", 0xFFFF4A3A, 0.07f, "embers", -0.3f, 0.9f, 1.8f, 0.05f),
    WorldAgeInfo("Age of Ice", "Snow and frost. Crops crawl.", 0xFFBFE6FF, 0.14f, "none", -0.4f, 0.6f, 0.8f, 0.05f),
    WorldAgeInfo("Age of Ash", "Grey skies and falling cinders.", 0xFF6A6A70, 0.16f, "ash", -0.6f, 0.75f, 1.3f, 0.12f),
    WorldAgeInfo("Age of Despair", "Hunger, doubt and endless war.", 0xFF30303A, 0.20f, "fog", -1.4f, 0.7f, 1.7f, 0.18f),
)

fun predatorMaxHp(kind: String): Float = when (kind) { "bear" -> 90f; "boar" -> 60f; else -> 40f }

/** A wolf, boar or bear hunting near the village. */
class Predator(val kind: String, var x: Float, var y: Float) {
    var hp = predatorMaxHp(kind)
    var cool = 0f
    var life = 0f
    var angle = 0f
}


val RACE_NAMES = mapOf(
    "Elf" to listOf("Sylvaran Court", "Aelthorn", "Lorien Glade", "Moonwhisper"),
    "Orc" to listOf("Grukhar Horde", "Blackmaw Clan", "Skullgate Warband", "Redtusk"),
    "Dwarf" to listOf("Ironhearth", "Deepforge Hold", "Stonebeard Clan", "Anvilgate"),
)
