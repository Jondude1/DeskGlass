package com.jondude1.deskglass.sim

import java.io.File
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

const val GW = 48
const val GH = 27
const val DAY_LEN = 420.0
private const val SPEED = 2.3f
private const val YEAR = DAY_LEN * 2

object T {
    const val GRASS = 0
    const val FOREST = 1
    const val ROCK = 2
    const val WATER = 3
    const val BUSH = 4
    const val ORE = 5
}

class Building(val type: String, val x: Int, val y: Int, val era: Int = 0) {
    var boostUntil = 0.0
    var ready = 0
    var timer = 0f
}

class Persona(val trait: String, val voice: String, val dream: String, val lines: List<String>)

val JOBS = listOf(
    "Woodcutter", "Miner", "Forager", "Farmer", "Builder", "Scout", "Storyteller",
    "Hunter", "Bronzesmith", "Blacksmith",
)
private val JOB_MIN_AGE = mapOf("Farmer" to 1, "Bronzesmith" to 2, "Blacksmith" to 3)
private val JOB_MAX_AGE = mapOf("Hunter" to 1, "Bronzesmith" to 2)
val AGE_NAMES = listOf("Hunter-Gatherer Age", "Farming Age", "Bronze Age", "Iron Age")

private val PERSONAS = listOf(
    Persona("a cautious planner who hates running out of food", "speaks carefully in short, anxious sentences and is always counting supplies", "a full storehouse that lasts the whole winter", listOf("Let's count the berries again.", "Better safe than hungry.")),
    Persona("a restless explorer who gets bored easily", "speaks in excited fragments and is always pointing somewhere", "to see what lies past the far mountains", listOf("I wonder what's over that ridge.", "I can't sit still today.")),
    Persona("a cheerful chatterbox who loves company", "is chatty, warm and full of exclamations", "a feast where the whole village sings", listOf("Isn't this the best village ever?", "Oh, I have to tell you something!")),
    Persona("a stubborn builder proud of every plank", "is gruff, proud and blunt", "a great hall with a proper roof", listOf("Built to last, that's how I do it.", "Nobody builds straighter than me.")),
    Persona("a dreamy poet who watches the sky", "is poetic and a little distant, always noticing the sky", "to write a song about the village", listOf("The clouds look like sleeping sheep.", "The light on the lake... lovely.")),
    Persona("a practical worker who says little", "is plain and brief and says only what matters", "a village that simply works", listOf("Work won't do itself.", "Fine.")),
    Persona("a nervous worrier who stockpiles things", "is jittery and always imagines what could go wrong", "to finally feel safe at night", listOf("Did you hear that?", "What if the berries run out?")),
    Persona("a bold leader who bosses others around", "is commanding and confident and gives orders", "to lead the village to greatness", listOf("Follow me, we have work to do.", "This village will be remembered.")),
    Persona("a kind caretaker who watches over the others", "is gentle and encouraging and asks how others feel", "that no one in the village goes hungry or lonely", listOf("Have you eaten today?", "You look tired, friend.")),
    Persona("a grumpy skeptic who doubts every plan", "is dry, sarcastic and complaining", "one day where absolutely nothing goes wrong", listOf("Hmph. We'll see about that.", "Of course it's raining.")),
)

private val JOB_BLURB = mapOf(
    "Woodcutter" to "You fell trees and haul timber. Wood is your pride, and you want the village to never run short of it.",
    "Miner" to "You dig stone from the rocks. Stone makes campfires and strengthens huts, and you take pride in it.",
    "Forager" to "You pick berries and fish the lake. You keep the village fed.",
    "Farmer" to "You tend the farms and the harvest. You know crops grow faster when someone looks after them.",
    "Builder" to "You raise huts, farms and campfires. The village grows only because you build.",
    "Scout" to "You roam far beyond the village, find caches and warn of wolves, and bring back news.",
    "Storyteller" to "You keep spirits up: you tell stories by the fire in the evening and chat with everyone.",
    "Hunter" to "You track and hunt game in the forest. Meat keeps the tribe fed before anyone knows farming.",
    "Bronzesmith" to "You mine ore and smelt bronze at the forge. Metal tools are the future of the village.",
    "Blacksmith" to "You mine ore and forge iron. Your tools are stronger than anything the village has had.",
    "Child" to "You are a small child. You play, ask questions and follow the grown-ups around.",
)

private val JOB_LINES = mapOf(
    "Woodcutter" to listOf("Another tree down.", "My axe needs a whetstone.", "Timber! ...sorry, habit.", "These oaks are stubborn."),
    "Miner" to listOf("Good solid rock here.", "My back aches, but the stone is fine.", "Stone never lies.", "Found a nice vein."),
    "Forager" to listOf("These berries are sweet today.", "The fish are biting.", "Never go hungry on my watch.", "More berries than yesterday."),
    "Farmer" to listOf("The crops look happy.", "A little water, a little patience.", "The soil here is rich.", "Harvest soon."),
    "Builder" to listOf("Another wall, another day.", "A hut needs a straight roof.", "We could fit one more house.", "Measure twice, hammer once."),
    "Scout" to listOf("I saw something beyond the hills.", "The wind is strange today.", "No wolves on the ridge. Yet.", "The world is big out there."),
    "Storyteller" to listOf("That reminds me of a tale...", "Gather round, friends.", "Every village needs its stories.", "Let me tell you a secret."),
    "Hunter" to listOf("I tracked a deer all morning.", "Quiet now, game ahead.", "Fresh tracks by the trees.", "Tonight we eat well."),
    "Bronzesmith" to listOf("The forge is hot today.", "Bronze rings when it's good.", "Copper and tin, that's the secret.", "Soon every tool will be metal."),
    "Blacksmith" to listOf("Iron beats bronze every time.", "Hammer, heat, hammer.", "This blade will outlast us all.", "The anvil sings."),
    "Child" to listOf("Play with me!", "Look, a beetle!", "I'm not tired!", "Are we there yet?", "Why is the sky blue?"),
)

private val STORIES = listOf(
    "Long ago a bear stole a whole winter of berries, and wept about it.",
    "There was once a tree so tall it scratched the moon.",
    "I once met a fish who swore it was a king.",
    "They say the old ruins sing when the wind is just right.",
    "A traveler once paid me in songs. I'm still rich.",
    "The first fire in this valley burned for a hundred days.",
)

class Villager(val id: Int, val name: String, var job: String, val personaIdx: Int) {
    val persona: Persona get() = PERSONAS[personaIdx % PERSONAS.size]
    val trait: String get() = persona.trait
    val colorIdx: Int get() = if (job == "Child") 6 else JOBS.indexOf(job).coerceAtLeast(0)
    var born = 0.0
    var lifespan = 60
    var parents = ""
    var harmedBy = ""
    var x = 0f
    var y = 0f
    var hunger = 20f
    var energy = 90f
    var hp = 100f
    var happiness = 60f
    var action = ""
    var phase = "idle" // idle | walking | working | sleeping
    var path = IntArray(0)
    var pathIdx = 0
    var targetTile = -1
    var workLeft = 0f
    var gathered = 0
    var talkTo: Villager? = null
    var talkMsg = ""
    var thought = ""
    var say = ""
    var sayLeft = 0f
    var idleFor = 0f
    var waiting = false
    var facing = 1
    var replyIn = 0f
    var replyText = ""
    val heard = ArrayList<String>()
    val memories = ArrayList<String>()
    val recent = ArrayList<String>()
    val affinity = HashMap<Int, Int>()
    val said = ArrayList<String>()
    var replyTo: Villager? = null

    fun remember(text: String) {
        memories.add(text)
        while (memories.size > 6) memories.removeAt(0)
    }
}

/** Whatever answers the villagers' questions: the local model, or Claude through the saved key. */
class Brain(val label: String, val gapSeconds: Double, val ask: (system: String, user: String) -> String)

private class Decision(val villagerId: Int, val text: String?, val error: String?, val reqId: Int = 0, val repaired: Boolean = false)

private val NAMES = listOf(
    "Mira", "Tobin", "Edda", "Rook", "Wren", "Bram", "Isla", "Finn", "Odette", "Cass", "Hugo", "Nell",
    "Pike", "Sorrel", "Dov", "Lark", "Ember", "Quill", "Tamsin", "Gideon", "Juno", "Alder", "Pip", "Vesna",
    "Maren", "Oswin", "Thea", "Corin", "Lio", "Yara", "Bastian", "Fenna", "Rhys", "Saga", "Orla", "Kit",
)
private val ACTIONS = setOf(
    "gather_wood", "gather_stone", "gather_food", "build_hut", "build_farm", "build_campfire",
    "eat", "sleep", "talk", "wander", "fish", "tend_farm", "explore", "tell_story", "huddle",
    "hunt", "mine_ore", "smelt", "build_smelter",
)

private val DX = intArrayOf(1, -1, 0, 0)
private val DY = intArrayOf(0, 0, 1, -1)

/** A tiny village that runs itself. A local language model picks what each villager does next. */
class CivSim(
    private val saveFile: File,
    private val scope: CoroutineScope,
    private val brainProvider: () -> Brain?,
) {
    val tiles = IntArray(GW * GH)
    val amt = IntArray(GW * GH)
    val buildings = ArrayList<Building>()
    val villagers = ArrayList<Villager>()
    val log = ArrayList<String>()
    var wood = 4
    var stone = 2
    var food = 6
    var ore = 0
    var metal = 0
    var totalSmelted = 0
    var explored = 0
    var age = 0
    var outsideRain = false
    var outsideSnow = false
    val chronicle = ArrayList<String>()
    var time = DAY_LEN * 0.08
    var brain = "starting…"
        private set

    var onNarrate: (String) -> Unit = {}
    var onError: (String) -> Unit = {}

    private val occupied = BooleanArray(GW * GH)
    private val reserved = HashSet<Int>()
    private var seed = System.currentTimeMillis()
    private var rnd = Random(seed)
    private val results = ConcurrentLinkedQueue<Decision>()
    private var inFlight = false
    private var retryAt = 0.0
    private var nextAskAt = 0.0
    private var lastBirth = 0.0
    private var farmClock = 0f
    private var bushClock = 0f
    private var nextId = 1
    private var godMsg = ""
    private var reqSeq = 0
    private var curReq = 0
    private var sentAt = 0.0
    private var okCount = 0
    private var fixCount = 0
    private var failCount = 0
    private var plagueUntil = 0.0
    private val problemAt = HashMap<String, Long>()
    private val problemSkipped = HashMap<String, Int>()
    private var godUntil = 0.0
    private var lastNarrateAt = 0L
    private var lastErrorLog = 0L

    // kingdoms, world ages and god powers (declared before init so loading can use them)
    val kingdoms = ArrayList<Kingdom>()
    val rels = HashMap<String, Rel>()
    val biome = IntArray(GW * GH)
    val owner = IntArray(GW * GH) { -1 }
    val fx = ArrayList<Fx>()
    val predators = ArrayList<Predator>()
    private val fleeAt = HashMap<Int, Double>()
    var worldAge = 0
        private set
    private var worldAgeStart = 0.0
    private var pendingKings: JSONArray? = null
    private var pendingRels: JSONArray? = null
    private var worldClock = 0f
    private var ownerClock = 0f
    private var nextWorldEventAt = 0.0
    private var shakeUntil = 0.0

    init {
        if (!load()) generate()
        setupWorld()
    }

    // ---- world ----

    private fun noise(r: Random, passes: Int): FloatArray {
        var a = FloatArray(GW * GH) { r.nextFloat() }
        repeat(passes) {
            val b = FloatArray(GW * GH)
            for (y in 0 until GH) for (x in 0 until GW) {
                var s = 0f
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx in 0 until GW && yy in 0 until GH) {
                        s += a[yy * GW + xx]
                        n++
                    }
                }
                b[y * GW + x] = s / n
            }
            a = b
        }
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        for (v in a) {
            mn = min(mn, v)
            mx = max(mx, v)
        }
        val span = max(0.0001f, mx - mn)
        for (i in a.indices) a[i] = (a[i] - mn) / span
        return a
    }

    private fun generate() {
        rnd = Random(seed)
        buildings.clear()
        villagers.clear()
        log.clear()
        occupied.fill(false)
        reserved.clear()
        wood = 4
        stone = 2
        food = 6
        ore = 0
        metal = 0
        totalSmelted = 0
        explored = 0
        age = 0
        chronicle.clear()
        predators.clear()
        time = DAY_LEN * 0.08
        lastBirth = time
        worldAge = 0
        worldAgeStart = time
        pendingKings = null
        pendingRels = null
        fx.clear()
        val e = noise(rnd, 4)
        val f = noise(rnd, 3)
        val g = noise(rnd, 3)
        val cx = GW / 2
        val cy = GH / 2
        for (y in 0 until GH) for (x in 0 until GW) {
            val i = y * GW + x
            val d = hypot((x - cx).toFloat(), (y - cy).toFloat())
            tiles[i] = T.GRASS
            amt[i] = 0
            if (d < 6f) continue
            when {
                e[i] < 0.22f -> tiles[i] = T.WATER
                f[i] > 0.62f -> { tiles[i] = T.FOREST; amt[i] = 5 }
                g[i] > 0.80f -> { tiles[i] = T.ROCK; amt[i] = 6 }
                rnd.nextFloat() < 0.035f -> { tiles[i] = T.BUSH; amt[i] = 3 }
            }
        }
        for (k in 0 until 4) {
            val a = rnd.nextFloat() * 6.28f
            val i = (cy + (Math.sin(a.toDouble()) * 7).toInt()) * GW + cx + (Math.cos(a.toDouble()) * 7).toInt()
            tiles[i] = T.BUSH
            amt[i] = 3
        }
        ensure(T.FOREST, 5, 40)
        ensure(T.ROCK, 6, 14)
        ensure(T.BUSH, 3, 14)
        addBuilding("campfire", cx, cy)
        for (k in 0 until 4) spawnVillager()
        addLog("A small band of villagers settles by a fire.")
        mark("A small band of villagers settled by a fire.")
    }

    private fun ensure(type: Int, amount: Int, wanted: Int) {
        var count = tiles.count { it == type }
        var guard = 0
        while (count < wanted && guard++ < 3000) {
            val x = rnd.nextInt(GW)
            val y = rnd.nextInt(GH)
            val i = y * GW + x
            if (tiles[i] == T.GRASS && hypot((x - GW / 2).toFloat(), (y - GH / 2).toFloat()) > 7f) {
                tiles[i] = type
                amt[i] = amount
                count++
            }
        }
    }

    private fun addBuilding(type: String, x: Int, y: Int, era: Int = age) {
        buildings.add(Building(type, x, y, era))
        occupied[y * GW + x] = true
    }

    private fun spawnVillager(parent: Villager? = null, other: Villager? = null): Villager {
        val used = villagers.map { it.name }.toSet()
        val pool = NAMES.filter { it !in used }.ifEmpty { NAMES }
        val name = pool[rnd.nextInt(pool.size)]
        val baby = parent != null
        val first = listOf("Woodcutter", "Hunter", "Forager", "Builder")
        val job = when {
            baby -> "Child"
            else -> first.getOrNull(villagers.size)?.takeIf { jobOk(it) } ?: chooseJob()
        }
        val usedP = villagers.map { it.personaIdx }.toSet()
        val freeP = PERSONAS.indices.filter { it !in usedP }.ifEmpty { PERSONAS.indices.toList() }
        val persona = if (baby && rnd.nextBoolean()) (if (rnd.nextBoolean()) parent!!.personaIdx else (other ?: parent!!).personaIdx)
        else freeP[rnd.nextInt(freeP.size)]
        val v = Villager(nextId++, name, job, persona)
        v.lifespan = 55 + rnd.nextInt(15)
        v.born = if (baby) time else time - (16 + rnd.nextInt(15) + rnd.nextFloat()) * YEAR
        if (baby) v.parents = "${parent!!.name} & ${(other ?: parent).name}"
        val fire = buildings.firstOrNull { it.type == "campfire" }
        v.x = (parent?.x ?: ((fire?.x ?: GW / 2) + 0.5f)) + (rnd.nextFloat() - 0.5f) * 3f
        v.y = (parent?.y ?: ((fire?.y ?: GH / 2) + 0.5f)) + (rnd.nextFloat() - 0.5f) * 3f
        villagers.add(v)
        return v
    }

    fun restart() {
        seed = System.currentTimeMillis()
        nextId = 1
        generate()
        setupWorld()
        save()
    }

    // ---- time ----

    fun day(): Int = (time / DAY_LEN).toInt() + 1
    private fun frac(): Double = (time % DAY_LEN) / DAY_LEN
    fun isNight(): Boolean = frac().let { it >= 0.72 || it < 0.04 }

    fun partOfDay(): String {
        val f = frac()
        return when {
            f < 0.04 -> "night"
            f < 0.35 -> "morning"
            f < 0.60 -> "afternoon"
            f < 0.72 -> "evening"
            else -> "night"
        }
    }

    /** 0 = bright day, up to 0.55 = deep night. */
    fun darkness(): Float = max(baseDarkness(), ageInfo().darkFloor)

    private fun baseDarkness(): Float {
        val f = frac()
        return when {
            f < 0.04 -> 0.55f
            f < 0.10 -> (0.55 * (1 - (f - 0.04) / 0.06)).toFloat()
            f < 0.62 -> 0f
            f < 0.72 -> (0.55 * (f - 0.62) / 0.10).toFloat()
            else -> 0.55f
        }
    }

    // ---- log ----

    private fun addLog(text: String) {
        log.add(text)
        while (log.size > 40) log.removeAt(0)
    }

    private fun narrate(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastNarrateAt < 5_000L) return
        lastNarrateAt = now
        onNarrate(text)
    }

    fun whisper(text: String) {
        val t = text.trim().take(200)
        if (t.isEmpty()) return
        godMsg = t
        godUntil = time + 240.0
        addLog("A voice from the sky: \"$t\"")
        lastNarrateAt = 0L
        narrate("A voice from the sky: $t")
    }

    // ---- paths ----

    private fun blocked(i: Int): Boolean = tiles[i] == T.WATER || occupied[i]

    private fun cellOf(v: Villager): Int =
        v.y.toInt().coerceIn(0, GH - 1) * GW + v.x.toInt().coerceIn(0, GW - 1)

    private fun findPath(start: Int, goal: Int): IntArray? {
        if (start == goal) return IntArray(0)
        if (tiles[goal] == T.WATER) return null
        val prev = IntArray(GW * GH) { -1 }
        val queue = IntArray(GW * GH)
        var head = 0
        var tail = 0
        prev[start] = start
        queue[tail++] = start
        while (head < tail) {
            val c = queue[head++]
            val cx = c % GW
            val cy = c / GW
            for (d in 0..3) {
                val nx = cx + DX[d]
                val ny = cy + DY[d]
                if (nx < 0 || ny < 0 || nx >= GW || ny >= GH) continue
                val n = ny * GW + nx
                if (prev[n] != -1) continue
                if (n != goal && blocked(n)) continue
                prev[n] = c
                if (n == goal) {
                    val out = ArrayList<Int>()
                    var cur = goal
                    while (cur != start) {
                        out.add(cur)
                        cur = prev[cur]
                    }
                    out.reverse()
                    return out.toIntArray()
                }
                queue[tail++] = n
            }
        }
        return null
    }

    private fun nearestTile(v: Villager, type: Int): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in tiles.indices) {
            if (tiles[i] != type || amt[i] <= 0) continue
            val d = hypot(i % GW + 0.5f - v.x, i / GW + 0.5f - v.y)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return best
    }

    private fun nearestBuilding(v: Villager, type: String?): Building? =
        buildings.filter { type == null || it.type == type }
            .minByOrNull { hypot(it.x + 0.5f - v.x, it.y + 0.5f - v.y) }

    private fun count(type: String) = buildings.count { it.type == type }

    // ---- actions ----

    private fun go(v: Villager, action: String, goal: Int, target: Int = goal): Boolean {
        val p = findPath(cellOf(v), goal) ?: return false
        v.action = action
        v.phase = "walking"
        v.path = p
        v.pathIdx = 0
        v.targetTile = target
        v.gathered = 0
        if (p.isEmpty()) arrive(v)
        return true
    }

    private fun findSite(): Int {
        var cx = GW / 2f
        var cy = GH / 2f
        if (buildings.isNotEmpty()) {
            cx = buildings.map { it.x }.average().toFloat()
            cy = buildings.map { it.y }.average().toFloat()
        }
        var best = -1
        var bs = Float.MAX_VALUE
        for (y in 1 until GH - 1) for (x in 1 until GW - 1) {
            val i = y * GW + x
            if (tiles[i] != T.GRASS || occupied[i] || i in reserved) continue
            if (buildings.any { abs(it.x - x) <= 1 && abs(it.y - y) <= 1 }) continue
            val s = hypot(x - cx, y - cy) + rnd.nextFloat() * 2.5f
            if (s < bs) {
                bs = s
                best = i
            }
        }
        return best
    }

    private fun nearestShore(v: Villager): Int {
        var best = -1
        var bd = Float.MAX_VALUE
        for (y in 0 until GH) for (x in 0 until GW) {
            val i = y * GW + x
            if (tiles[i] == T.WATER || occupied[i]) continue
            val nearWater = (x > 0 && tiles[i - 1] == T.WATER) || (x < GW - 1 && tiles[i + 1] == T.WATER) ||
                (y > 0 && tiles[i - GW] == T.WATER) || (y < GH - 1 && tiles[i + GW] == T.WATER)
            if (!nearWater) continue
            val d = hypot(x + 0.5f - v.x, y + 0.5f - v.y)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        return best
    }

    private fun startAction(v: Villager, action: String, target: String): Boolean {
        when (action) {
            "gather_wood", "gather_stone", "gather_food", "mine_ore" -> {
                if (action == "mine_ore" && age < 2) return false
                val type = when (action) {
                    "gather_wood" -> T.FOREST
                    "gather_stone" -> T.ROCK
                    "mine_ore" -> T.ORE
                    else -> T.BUSH
                }
                val i = nearestTile(v, type)
                if (i < 0) return false
                return go(v, action, i)
            }
            "build_hut", "build_farm", "build_campfire", "build_smelter" -> {
                val ok = when (action) {
                    "build_hut" -> wood >= 6 && stone >= 2
                    "build_farm" -> age >= 1 && wood >= 4
                    "build_smelter" -> age >= 2 && wood >= 4 && stone >= 6 && count("smelter") < 3
                    else -> stone >= 3 && count("campfire") < 3
                }
                if (!ok) return false
                val site = findSite()
                if (site < 0) return false
                if (!go(v, action, site)) return false
                when (action) {
                    "build_hut" -> { wood -= 6; stone -= 2 }
                    "build_farm" -> wood -= 4
                    "build_smelter" -> { wood -= 4; stone -= 6 }
                    else -> stone -= 3
                }
                reserved.add(site)
                return true
            }
            "hunt" -> {
                val i = nearestTile(v, T.FOREST)
                if (i < 0) return false
                return go(v, "hunt", i)
            }
            "smelt" -> {
                if (age < 2 || ore < 2 || wood < 1) return false
                val b = nearestBuilding(v, "smelter") ?: return false
                return go(v, "smelt", b.y * GW + b.x)
            }
            "eat" -> {
                if (food <= 0) return false
                val b = nearestBuilding(v, "campfire") ?: nearestBuilding(v, null) ?: return false
                return go(v, "eat", b.y * GW + b.x)
            }
            "sleep" -> {
                val b = nearestBuilding(v, "hut") ?: nearestBuilding(v, "campfire")
                if (b == null) {
                    v.action = "sleep"
                    v.phase = "sleeping"
                    return true
                }
                return go(v, "sleep", b.y * GW + b.x)
            }
            "talk" -> {
                val other = villagers.firstOrNull { it !== v && it.name.equals(target.trim(), ignoreCase = true) }
                    ?: friendFor(v) ?: return false
                v.talkTo = other
                if (v.talkMsg.isBlank()) v.talkMsg = lineFor(v)
                return go(v, "talk", cellOf(other))
            }
            "wander" -> {
                for (k in 0 until 12) {
                    val x = (v.x.toInt() + rnd.nextInt(13) - 6).coerceIn(1, GW - 2)
                    val y = (v.y.toInt() + rnd.nextInt(9) - 4).coerceIn(1, GH - 2)
                    val i = y * GW + x
                    if (!blocked(i) && go(v, "wander", i)) return true
                }
                return false
            }
            "fish" -> {
                val i = nearestShore(v)
                if (i < 0) return false
                return go(v, "fish", i)
            }
            "tend_farm" -> {
                if (age < 1) return false
                val b = nearestBuilding(v, "farm") ?: return false
                return go(v, "tend_farm", b.y * GW + b.x)
            }
            "explore" -> {
                for (k in 0 until 14) {
                    val x = rnd.nextInt(GW - 2) + 1
                    val y = rnd.nextInt(GH - 2) + 1
                    val i = y * GW + x
                    if (blocked(i) || hypot(x + 0.5f - v.x, y + 0.5f - v.y) < 12f) continue
                    if (go(v, "explore", i)) return true
                }
                return false
            }
            "tell_story", "huddle" -> {
                val b = nearestBuilding(v, "campfire") ?: return false
                return go(v, action, b.y * GW + b.x)
            }
        }
        return false
    }

    private fun finish(v: Villager) {
        v.action = ""
        v.phase = "idle"
        v.idleFor = 0f
        v.waiting = false
        v.gathered = 0
        v.path = IntArray(0)
        v.talkTo = null
        v.talkMsg = ""
    }

    private fun arrive(v: Villager) {
        when {
            v.action.startsWith("gather_") || v.action == "mine_ore" -> { v.phase = "working"; v.workLeft = 3f }
            v.action == "hunt" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "smelt" -> { v.phase = "working"; v.workLeft = 6f }
            v.action.startsWith("build_") -> { v.phase = "working"; v.workLeft = 6f }
            v.action == "eat" -> { v.phase = "working"; v.workLeft = 2f }
            v.action == "sleep" -> v.phase = "sleeping"
            v.action == "fish" -> { v.phase = "working"; v.workLeft = 4f }
            v.action == "tend_farm" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "explore" -> { v.phase = "working"; v.workLeft = 5f }
            v.action == "huddle" -> { v.phase = "working"; v.workLeft = 7f }
            v.action == "tell_story" -> {
                v.phase = "working"
                v.workLeft = 9f
                val story = Speech.clean(storyFor(), 90)
                v.say = if (isChild(v)) Speech.kidify(story) else story
                v.sayLeft = 8f
                noteSaid(v, story)
                addLog("${v.name} tells a story by the fire.")
                narrate("${v.name} tells a story by the fire")
                val listeners = villagers.filter {
                    it !== v && it.phase != "sleeping" && hypot(it.x - v.x, it.y - v.y) < 9f
                }
                for (l in listeners) {
                    l.happiness = min(100f, l.happiness + 8f)
                    l.affinity[v.id] = (l.affinity[v.id] ?: 0) + 1
                    v.affinity[l.id] = (v.affinity[l.id] ?: 0) + 1
                }
                if (listeners.isNotEmpty()) {
                    val l = listeners[rnd.nextInt(listeners.size)]
                    l.replyIn = 3f
                    l.replyTo = v
                    l.replyText = replyTo(l, v, story)
                    l.remember("${v.name} told a story: ${story.take(50)}")
                    v.remember("told ${l.name} a story")
                }
                v.happiness = min(100f, v.happiness + 5f)
            }
            v.action == "talk" -> {
                val o = v.talkTo
                if (o != null) {
                    v.say = v.talkMsg.take(90)
                    v.sayLeft = 7f
                    noteSaid(v, v.talkMsg)
                    o.replyTo = v
                    o.heard.add("${v.name}: ${v.talkMsg}")
                    while (o.heard.size > 3) o.heard.removeAt(0)
                    addLog("${v.name} to ${o.name}: ${v.talkMsg}")
                    narrate("${v.name} to ${o.name}: ${v.talkMsg}")
                    val argue = v.persona.trait.contains("grumpy") && rnd.nextInt(3) == 0
                    if (argue) {
                        v.affinity[o.id] = (v.affinity[o.id] ?: 0) - 2
                        o.affinity[v.id] = (o.affinity[v.id] ?: 0) - 2
                        o.happiness = max(0f, o.happiness - 6f)
                        o.replyText = "Oh, give it a rest, ${v.name}."
                        addLog("${v.name} and ${o.name} bicker.")
                        o.remember("${v.name} was rude to me")
                    } else {
                        v.affinity[o.id] = (v.affinity[o.id] ?: 0) + 1
                        o.affinity[v.id] = (o.affinity[v.id] ?: 0) + 1
                        v.happiness = min(100f, v.happiness + 4f)
                        o.happiness = min(100f, o.happiness + 4f)
                        o.replyText = replyTo(o, v, v.talkMsg)
                        o.remember("${v.name} said: ${v.talkMsg.take(50)}")
                    }
                    o.replyIn = 2.5f
                    v.remember("told ${o.name}: ${v.talkMsg.take(40)}")
                }
                finish(v)
            }
            else -> finish(v)
        }
    }

    private fun work(v: Villager, dt: Float) {
        v.workLeft -= dt
        if (v.workLeft > 0f) return
        when (v.action) {
            "gather_wood", "gather_stone", "gather_food", "mine_ore" -> {
                val i = v.targetTile
                if (i >= 0 && amt[i] > 0) {
                    amt[i]--
                    v.gathered++
                    val y = 1 + toolBonus()
                    when (v.action) {
                        "gather_wood" -> wood += y
                        "gather_stone" -> stone += y
                        "mine_ore" -> ore += y
                        else -> food += 2 + toolBonus()
                    }
                    if (amt[i] == 0 && tiles[i] != T.BUSH) tiles[i] = T.GRASS
                }
                if (v.gathered >= 4 || i < 0 || amt[i] <= 0) {
                    if (v.gathered >= 3) v.remember("brought back ${v.gathered} ${v.action.removePrefix("gather_")}")
                    finish(v)
                } else v.workLeft = 3f
            }
            "build_hut", "build_farm", "build_campfire", "build_smelter" -> {
                val i = v.targetTile
                reserved.remove(i)
                val type = v.action.removePrefix("build_")
                val label = if (type == "hut" && age == 0) "tent" else if (type == "smelter") "forge" else type
                if (i >= 0 && !occupied[i] && tiles[i] == T.GRASS) {
                    addBuilding(type, i % GW, i / GW)
                    addLog("${v.name} built a $label.")
                    narrate("${v.name} finishes a $label")
                    v.remember("built a $label")
                    v.happiness = min(100f, v.happiness + 8f)
                }
                finish(v)
            }
            "eat" -> {
                if (food > 0) {
                    food--
                    if (v.hunger > 55f) v.happiness = min(100f, v.happiness + 4f)
                    v.hunger = max(0f, v.hunger - 45f)
                }
                finish(v)
            }
            "fish" -> {
                v.gathered++
                if (rnd.nextInt(4) != 0) food++
                if (v.gathered >= 3) {
                    v.remember("fished at the lake")
                    finish(v)
                } else v.workLeft = 4f
            }
            "tend_farm" -> {
                val b = buildings.firstOrNull { it.type == "farm" && it.y * GW + it.x == v.targetTile }
                if (b != null) {
                    val got = b.ready * 2 + 1
                    food += got
                    b.ready = 0
                    b.boostUntil = time + 120.0
                    addLog("${v.name} tended the farm (+$got food).")
                    v.remember("tended the farm")
                    v.happiness = min(100f, v.happiness + 3f)
                }
                finish(v)
            }
            "explore" -> {
                explored++
                val roll = rnd.nextInt(100)
                when {
                    roll < 45 -> {
                        val kind = rnd.nextInt(3)
                        val n = 3 + rnd.nextInt(2)
                        val what = when (kind) {
                            0 -> { wood += n; "wood" }
                            1 -> { stone += n; "stone" }
                            else -> { food += n; "food" }
                        }
                        addLog("${v.name} found an abandoned cache beyond the hills (+$n $what).")
                        narrate("${v.name} found a cache of $what")
                        v.remember("found a cache of $what out in the hills")
                        v.happiness = min(100f, v.happiness + 6f)
                    }
                    roll < 60 -> {
                        alertUntil = max(alertUntil, time + 70.0)
                        addLog("${v.name} spotted wolf tracks near the village!")
                        narrate("${v.name} spotted wolf tracks")
                        v.remember("saw wolf tracks and warned everyone")
                    }
                    roll < 75 -> {
                        food += 3
                        addLog("${v.name} found a hidden berry grove (+3 food).")
                        v.remember("found a hidden berry grove")
                    }
                    else -> {
                        addLog("${v.name} scouted the hills but found only wind.")
                        v.remember("scouted the hills, nothing to report")
                    }
                }
                finish(v)
            }
            "hunt" -> {
                val got = if (rnd.nextInt(100) < 65) 3 + toolBonus() else 1
                food += got
                v.remember(if (got >= 3) "brought home game (+$got food)" else "had a poor hunt")
                v.happiness = min(100f, v.happiness + 1f)
                if (rnd.nextInt(5) == 0) addLog("${v.name} returns from the hunt (+$got food).")
                finish(v)
            }
            "smelt" -> {
                if (ore >= 2 && wood >= 1) {
                    ore -= 2
                    wood -= 1
                    metal++
                    totalSmelted++
                    v.remember("smelted metal at the forge")
                    if (totalSmelted % 5 == 1) addLog("${v.name} smelts fresh ${if (age >= 3) "iron" else "bronze"}.")
                }
                finish(v)
            }
            "huddle" -> {
                v.happiness = min(100f, v.happiness + 2f)
                finish(v)
            }
            "tell_story" -> finish(v)
            else -> finish(v)
        }
    }

    // ---- brains ----

    // ---- ages and generations ----

    /** A short history of the big moments, kept in the save file. */
    fun mark(text: String) {
        chronicle.add("Day ${day()}: $text")
        while (chronicle.size > 40) chronicle.removeAt(0)
    }

    /** Called by the screen when a frame of the sim throws, so one bug never takes the app down. */
    fun guard(e: Exception) {
        val now = System.currentTimeMillis()
        if (now - lastErrorLog > 60_000L) {
            lastErrorLog = now
            onError("Village hiccup: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun isChild(v: Villager) = v.job == "Child"
    fun ageYears(v: Villager): Int = max(0.0, (time - v.born) / YEAR).toInt()
    fun ageName(): String = AGE_NAMES[age.coerceIn(0, AGE_NAMES.size - 1)]
    fun nextAgeName(): String? = AGE_NAMES.getOrNull(age + 1)
    fun popCap(): Int = when (age) { 0 -> 10; 1 -> 14; 2 -> 20; else -> 26 }
    fun adults(): Int = villagers.count { !isChild(it) }
    fun toolBonus(): Int = when {
        age >= 3 && totalSmelted >= 30 -> 2
        age >= 2 && totalSmelted >= 8 -> 1
        else -> 0
    }

    /** How close the village is to the next age, 0..100. */
    fun progress(): Int {
        val ad = adults().toFloat()
        val parts = when (age) {
            0 -> listOf(day() / 80f, ad / 6f, count("hut") / 3f)
            1 -> listOf(day() / 300f, ad / 9f, count("hut") / 6f, count("farm") / 2f, explored / 6f)
            2 -> listOf(day() / 650f, ad / 12f, count("smelter") / 2f, totalSmelted / 20f)
            else -> return 100
        }
        return (parts.map { min(1f, it) }.average() * 100).toInt().coerceIn(0, 99)
    }

    private fun jobOk(j: String) = age >= (JOB_MIN_AGE[j] ?: 0) && age <= (JOB_MAX_AGE[j] ?: 99)

    private fun chooseJob(): String {
        val ok = JOBS.filter { jobOk(it) }
        val counts = ok.associateWith { j -> villagers.count { it.job == j } }
        val newest = ok.filter { age > 0 && (JOB_MIN_AGE[it] ?: 0) == age }
        if (newest.isNotEmpty() && rnd.nextInt(100) < 65) {
            val low = newest.minOf { counts[it] ?: 0 }
            if (low < 3) {
                val c = newest.filter { counts[it] == low }
                return c[rnd.nextInt(c.size)]
            }
        }
        val low = counts.values.minOrNull() ?: 0
        val c = ok.filter { counts[it] == low }
        return c[rnd.nextInt(c.size)]
    }

    private fun setJob(v: Villager, j: String) {
        addLog("${v.name} takes up a new trade: ${j.lowercase()}.")
        v.remember("became a ${j.lowercase()}")
        v.job = j
    }

    private fun retrain() {
        val grown = villagers.filter { !isChild(it) }
        fun n(j: String) = villagers.count { it.job == j }
        fun swap(from: List<String>, to: String, want: Int) {
            for (v in grown) {
                if (n(to) >= want) break
                if (v.job in from) setJob(v, to)
            }
        }
        when (age) {
            1 -> swap(listOf("Hunter", "Forager", "Woodcutter"), "Farmer", 2)
            2 -> {
                for (v in grown.filter { it.job == "Hunter" }) setJob(v, "Farmer")
                swap(listOf("Miner", "Builder", "Woodcutter", "Forager"), "Bronzesmith", 2)
            }
            3 -> {
                for (v in grown.filter { it.job == "Bronzesmith" }) setJob(v, "Blacksmith")
                swap(listOf("Miner", "Builder", "Woodcutter"), "Blacksmith", 2)
            }
        }
    }

    private fun advanceAge() {
        age++
        if (age >= 2 && tiles.none { it == T.ORE }) ensure(T.ORE, 5, 8)
        event(
            when (age) {
                1 -> "The villagers learn to plant seeds. The Farming Age begins!"
                2 -> "Glittering ore is found in the hills. The Bronze Age begins!"
                else -> "Stronger metal comes from the forge. The Iron Age begins!"
            }
        )
        mark(AGE_NAMES[age.coerceIn(0, AGE_NAMES.size - 1)] + " begins.")
        retrain()
        for (v in villagers) v.happiness = min(100f, v.happiness + 15f)
        festivalUntil = time + 40.0
    }

    private var ageClock = 0f

    private fun ageTick(dt: Float) {
        ageClock += dt
        if (ageClock < 2f) return
        ageClock = 0f
        val ad = adults()
        val ready = when (age) {
            0 -> day() >= 80 && ad >= 6 && count("hut") >= 3
            1 -> day() >= 300 && ad >= 9 && count("hut") >= 6 && count("farm") >= 2 && explored >= 6
            2 -> day() >= 650 && ad >= 12 && count("smelter") >= 2 && totalSmelted >= 20
            else -> false
        }
        if (ready) advanceAge()
    }

    private fun growUp(v: Villager) {
        val j = chooseJob()
        v.job = j
        v.happiness = min(100f, v.happiness + 15f)
        v.remember("grew up and became a ${j.lowercase()}")
        event("${v.name} has grown up and becomes a ${j.lowercase()}.")
        mark("${v.name} grew up and became a ${j.lowercase()}.")
    }

    private fun techNote(): String = when (age) {
        0 -> "Age: Hunter-Gatherer. You live in tents and survive by hunting, fishing and foraging. Nobody knows farming yet."
        1 -> "Age: Farming. You build timber huts and farms. The village grows crops and stores food."
        2 -> "Age: Bronze. Ore has been found in the hills. Forges smelt bronze, and metal tools make work faster."
        else -> "Age: Iron. Blacksmiths forge iron tools. The village is a real town now."
    }

    // ---- brains ----

    private fun alert() = time < alertUntil || predators.isNotEmpty()
    fun raining() = time < rainUntil || outsideRain || worldAge == 5
    private fun festival() = time < festivalUntil

    fun mood(v: Villager): String = when {
        v.hunger > 85f -> "starving"
        v.energy < 20f -> "exhausted"
        alert() && v.happiness < 60f -> "scared"
        v.happiness > 75f -> "joyful"
        v.happiness > 55f -> "content"
        v.happiness > 35f -> "restless"
        else -> "gloomy"
    }

    private fun pick(vararg w: Pair<String, Int>): String {
        var total = 0
        for (p in w) total += p.second
        var roll = rnd.nextInt(max(1, total))
        for (p in w) {
            if (roll < p.second) return p.first
            roll -= p.second
        }
        return w[0].first
    }

    private fun friendFor(v: Villager): Villager? {
        val others = villagers.filter { it !== v }
        if (others.isEmpty()) return null
        if (rnd.nextInt(3) == 0) return others[rnd.nextInt(others.size)]
        return others.maxByOrNull { (v.affinity[it.id] ?: 0) * 2 + rnd.nextInt(5) }
    }

    private fun factLines(v: Villager): List<String> {
        val out = ArrayList<String>()
        val n = villagers.size
        if (food <= n / 2) out.add("Only $food food left for $n of us.")
        else if (food >= n * 2) out.add("The stores hold $food food. We'll manage.")
        if (wood < 4) out.add("We're down to $wood wood.")
        if (stone < 2) out.add("Barely any stone, just $stone.")
        if (v.hunger > 70f) out.add("My stomach is growling. I need to eat.")
        if (v.energy < 30f) out.add("I could sleep on my feet.")
        if (alert()) out.add("Those wolves have everyone on edge.")
        if (raining()) out.add("This rain slows the ${v.job.lowercase()} work.")
        if (weatherNow().startsWith("cold")) out.add("The cold gets into my bones.")
        if (partOfDay() == "evening") out.add("The light is fading. Time to wind down.")
        if (isNight()) out.add("Hard to see anything at night.")
        wars().firstOrNull()?.let { out.add("Word is ${it.first.name} is at war with ${it.second.name}.") }
        if (time < plagueUntil) out.add("People are falling sick. Keep your distance.")
        history(5).lastOrNull()?.let { out.add("I keep thinking about it: ${it.take(60)}") }
        childrenOf(v).firstOrNull()?.let { out.add("${it.name} is ${ageYears(it)} already. They grow fast.") }
        v.memories.lastOrNull()?.let { out.add("I keep remembering that ${it.take(50)}.") }
        nearby(v).firstOrNull()?.let { out.add("${it.name} is busy ${doing(it)}.") }
        if (worldAge != 0) out.add("The ${ageInfo().name.lowercase()} is on everyone's mind.")
        if (age > 0) out.add("${count("hut")} huts and ${count("farm")} farms. We've come far.")
        if (rnd.nextInt(4) == 0) out.addAll(JOB_LINES[v.job] ?: v.persona.lines)
        if (out.isEmpty()) out.addAll(v.persona.lines)
        return out
    }

    private fun kidLines(v: Villager): List<String> {
        val out = ArrayList<String>()
        if (v.hunger > 60f) out.add("I'm hungry!")
        if (v.energy < 35f || isNight()) out.add("I'm sleepy.")
        if (raining()) out.add("Rain! Splash!")
        if (alert()) out.add("I don't like the wolves.")
        if (weatherNow().startsWith("cold")) out.add("Brr, it's cold!")
        v.parents.split(" & ").firstOrNull()?.takeIf { it.isNotBlank() }?.let { out.add("Where is $it?") }
        nearby(v).firstOrNull()?.let { out.add("Look, ${it.name} is ${doing(it)}!") }
        if (food > 0) out.add("We have $food food!")
        out.add("Play with me!")
        return out
    }

    private fun lineFor(v: Villager): String {
        val pool = if (isChild(v)) kidLines(v) else factLines(v)
        val fresh = pool.filter { l -> v.said.none { Speech.similar(it, l) } }
        val use = fresh.ifEmpty { pool }
        return use[rnd.nextInt(use.size)]
    }

    /** A reply that answers what was actually said, using real state. */
    private fun replyTo(o: Villager, from: Villager, msg: String): String {
        val m = msg.lowercase()
        val w = wars().firstOrNull()
        val r = when {
            m.contains("food") || m.contains("hungry") || m.contains("berr") || m.contains("stomach") || m.contains("eat") ->
                if (food > villagers.size) "Aye, ${from.name}. $food food in the stores, we're fine."
                else "Aye, ${from.name}. Only $food left. We should gather more."
            m.contains("wolf") -> if (alert()) "I heard them too, ${from.name}. Stay near the fire." else "The wolves have gone quiet, ${from.name}. For now."
            m.contains("rain") || m.contains("wet") ->
                if (raining()) "It won't stop, ${from.name}." else "At least the rain has stopped."
            m.contains("wood") || m.contains("timber") -> "We have $wood wood right now, ${from.name}."
            m.contains("stone") || m.contains("rock") -> "Stone stands at $stone."
            m.contains("tired") || m.contains("sleep") ->
                if (o.energy < 40f) "Me too. My energy is low." else "Rest well, ${from.name}."
            m.contains("baby") || m.contains("born") || m.contains("child") -> "Good news for the village, ${from.name}."
            (m.contains("war") || m.contains("raid") || m.contains("kingdom")) && w != null ->
                "Keep the village safe, ${from.name}. ${w.first.name} is fighting ${w.second.name}."
            m.contains("?") -> "I'm not sure, ${from.name}. I'm busy ${doing(o)}."
            else -> "I hear you, ${from.name}. " + lineFor(o)
        }
        val out = if (Speech.similar(r, msg)) lineFor(o) else r
        return if (isChild(o)) Speech.kidify(out) else out
    }

    /** Stories and gossip come from things that really happened. */
    private fun storyFor(): String {
        val h = history(8)
        if (h.isNotEmpty()) {
            val t = h[rnd.nextInt(h.size)].take(70)
            return listOf("Gather round. $t", "Remember when: $t", "They'll tell this one for years: $t")[rnd.nextInt(3)]
        }
        val l = log.takeLast(6)
        if (l.isNotEmpty()) return "Did you hear? " + l[rnd.nextInt(l.size)].take(70)
        return STORIES[rnd.nextInt(STORIES.size)]
    }

    private fun ruleDecision(v: Villager): Pair<String, String> {
        val others = villagers.filter { it !== v }
        val hasShore = nearestShore(v) >= 0
        if (v.hunger >= 60f && food > 0) return "eat" to ""
        if (v.energy < 25f || (isNight() && v.energy < 92f)) return "sleep" to ""
        if (isChild(v)) {
            if (alert()) return "huddle" to ""
            return when (pick("wander" to 4, "talk" to 3, "huddle" to 2)) {
                "talk" -> "talk" to (friendFor(v)?.name ?: "")
                "huddle" -> "huddle" to ""
                else -> "wander" to ""
            }
        }
        if (alert() && v.job != "Scout") return "huddle" to ""
        if (festival()) {
            return if (rnd.nextInt(3) == 0 && others.isNotEmpty()) "talk" to (friendFor(v)?.name ?: "") else "huddle" to ""
        }
        if (food < 3) {
            val a = when {
                v.job == "Farmer" && age >= 1 && count("farm") > 0 -> "tend_farm"
                v.job == "Hunter" -> "hunt"
                hasShore && rnd.nextBoolean() -> "fish"
                else -> "gather_food"
            }
            return a to ""
        }
        val ad = adults()
        val needHut = count("hut") * 2 < villagers.size + 2
        if (needHut && wood >= 6 && stone >= 2 && (v.job == "Builder" || rnd.nextInt(5) == 0)) return "build_hut" to ""
        if (age >= 1 && count("farm") < 1 + ad / 3 && wood >= 4 && (v.job == "Builder" || v.job == "Farmer" || rnd.nextInt(6) == 0)) return "build_farm" to ""
        if (stone >= 3 && count("campfire") < 2 && v.job != "Scout") return "build_campfire" to ""
        if (age >= 2 && count("smelter") < min(3, 1 + ad / 5) && stone >= 6 && wood >= 4 &&
            (v.job == "Builder" || v.job == "Bronzesmith" || v.job == "Blacksmith" || rnd.nextInt(6) == 0)
        ) return "build_smelter" to ""
        if (partOfDay() == "evening" && v.job == "Storyteller") return "tell_story" to ""
        if (partOfDay() == "evening" && others.isNotEmpty() && rnd.nextInt(3) == 0) return "talk" to (friendFor(v)?.name ?: "")
        val farms = age >= 1 && count("farm") > 0
        val act = when (v.job) {
            "Woodcutter" -> pick("gather_wood" to 7, "talk" to 1, "wander" to 1, "gather_food" to (if (food < 8) 2 else 0))
            "Miner" -> pick(
                "gather_stone" to (if (stone < 12) 7 else 2),
                "mine_ore" to (if (age >= 2 && ore < 10) 4 else 0),
                "gather_wood" to (if (wood < 12) 3 else 1), "talk" to 1, "wander" to 1,
            )
            "Forager" -> pick("gather_food" to 4, "fish" to (if (hasShore) 3 else 0), "talk" to 1, "wander" to 1)
            "Hunter" -> pick("hunt" to 7, "gather_food" to 1, "fish" to (if (hasShore) 1 else 0), "talk" to 1, "wander" to 1)
            "Farmer" -> pick("tend_farm" to (if (farms) 6 else 0), "gather_food" to 2, "gather_wood" to (if (farms) 0 else 4), "talk" to 1)
            "Builder" -> pick("gather_wood" to 4, "gather_stone" to (if (stone < 4) 3 else 1), "talk" to 1, "wander" to 1)
            "Bronzesmith", "Blacksmith" ->
                if (count("smelter") > 0) pick(
                    "smelt" to (if (ore >= 2 && wood >= 1) 7 else 0),
                    "mine_ore" to (if (ore < 6) 6 else 1),
                    "gather_wood" to (if (wood < 6) 3 else 1), "talk" to 1,
                ) else pick("mine_ore" to 4, "gather_stone" to 3, "gather_wood" to 2, "talk" to 1)
            "Scout" -> pick("explore" to 4, "gather_food" to 2, "talk" to 2, "wander" to 1)
            else -> pick("talk" to 4, "gather_food" to 2, "tell_story" to (if (v.energy > 40f) 1 else 0), "wander" to 1)
        }
        return act to (if (act == "talk") (friendFor(v)?.name ?: "") else "")
    }

    private fun applyDecision(v: Villager, actionIn: String, targetIn: String, say: String, thought: String) {
        v.waiting = false
        var action = actionIn
        var target = targetIn
        // don't do the exact same thing four times in a row
        val survival = action == "eat" || action == "sleep"
        if (!survival && v.recent.size >= 3 && v.recent.takeLast(3).all { it == action }) {
            if (others().isNotEmpty() && rnd.nextBoolean()) {
                action = "talk"
                target = friendFor(v)?.name ?: ""
            } else {
                action = "wander"
            }
        }
        val thoughtClean = Speech.clean(thought, 100)
        if (thoughtClean.isNotBlank()) v.thought = thoughtClean
        var sayClean = Speech.clean(say, 90)
        if (isChild(v)) sayClean = Speech.kidify(sayClean)
        if (sayClean.isNotBlank() && v.said.any { Speech.similar(it, sayClean) }) sayClean = ""
        if (action == "talk") v.talkMsg = sayClean.ifBlank { lineFor(v) }
        else if (sayClean.isNotBlank()) {
            v.say = sayClean
            v.sayLeft = 7f
            noteSaid(v, sayClean)
            addLog("${v.name}: $sayClean")
        }
        var ok = action in ACTIONS && startAction(v, action, target)
        if (!ok) {
            val (a, t) = ruleDecision(v)
            action = a
            ok = startAction(v, a, t)
            if (!ok) {
                action = "wander"
                ok = startAction(v, "wander", "")
            }
        }
        if (!ok) {
            v.idleFor = -3f
        } else {
            v.recent.add(action)
            while (v.recent.size > 5) v.recent.removeAt(0)
            if (v.thought.isNotBlank()) narrate("${v.name}: ${v.thought}")
        }
    }

    private fun others(): List<Villager> = villagers

    private fun seasonName(): String = when (java.time.LocalDate.now().monthValue) {
        12, 1, 2 -> "winter"
        3, 4, 5 -> "spring"
        6, 7, 8 -> "summer"
        else -> "autumn"
    }

    private fun weatherNow(): String = when {
        raining() -> "raining"
        outsideSnow || worldAge == 7 || seasonName() == "winter" -> "cold, with snow about"
        else -> "dry"
    }

    private fun doing(o: Villager) = if (o.action.isEmpty()) "idle" else o.action.replace('_', ' ')
    private fun childrenOf(v: Villager): List<Villager> = villagers.filter { it !== v && it.parents.contains(v.name) }
    private fun nearby(v: Villager, r: Float = 6f): List<Villager> =
        villagers.filter { it !== v && hypot(it.x - v.x, it.y - v.y) <= r }

    private fun noteSaid(v: Villager, text: String) {
        v.said.add(text)
        while (v.said.size > 8) v.said.removeAt(0)
    }

    private fun history(n: Int): List<String> = chronicle.takeLast(n).map { it.substringAfter(": ") }

    private fun allowedActions(): List<String> {
        val a = arrayListOf("gather_wood", "gather_stone", "gather_food", "fish", "hunt", "explore", "tell_story", "huddle", "sleep", "talk", "wander")
        if (food > 0) a.add("eat")
        if (wood >= 6 && stone >= 2) a.add("build_hut")
        if (stone >= 3 && count("campfire") < 3) a.add("build_campfire")
        if (age >= 1 && wood >= 4) a.add("build_farm")
        if (age >= 1 && count("farm") > 0) a.add("tend_farm")
        if (age >= 2) {
            a.add("mine_ore")
            if (ore >= 2 && wood >= 1 && count("smelter") > 0) a.add("smelt")
            if (wood >= 4 && stone >= 6 && count("smelter") < 3) a.add("build_smelter")
        }
        return a
    }

    private fun buildPrompt(v: Villager): String {
        val kid = isChild(v)
        val sb = StringBuilder()
        sb.append("FACTS. Mention only things listed here. Never invent buildings, people, resources or events.\n")
        sb.append("Time: day ${day()}, ${partOfDay()}, ${seasonName()}. Weather: ${weatherNow()}.\n")
        val ev = ArrayList<String>()
        if (alert()) ev.add("wolves are howling near the village")
        if (festival()) ev.add("a feast is being held by the fire")
        if (time < plagueUntil) ev.add("a sickness is spreading")
        wars().firstOrNull()?.let { ev.add("${it.first.name} is at war with ${it.second.name}") }
        sb.append("Happening now: " + (if (ev.isEmpty()) "nothing unusual" else ev.joinToString("; ")) + ". ${ageInfo().name}: ${ageInfo().blurb}\n")
        sb.append("You are ${v.name}, ${if (kid) "a small child" else "the ${v.job.lowercase()}"} of Hearthvale, ${ageYears(v)} years old. ${JOB_BLURB[v.job] ?: ""}\n")
        sb.append(techNote() + "\n")
        sb.append("Personality: ${v.trait}. You ${v.persona.voice}. Your dream: ${v.persona.dream}.\n")
        sb.append("Body and mood: hunger ${v.hunger.toInt()}/100 (eat above 60), energy ${v.energy.toInt()}/100, health ${v.hp.toInt()}/100, feeling ${mood(v)}.\n")
        v.recent.lastOrNull()?.let { sb.append("You just did: ${it.replace('_', ' ')}.\n") }
        val kids = childrenOf(v)
        val fam = ArrayList<String>()
        if (v.parents.isNotBlank()) fam.add("your parents are ${v.parents}")
        if (kids.isNotEmpty()) fam.add("your children: " + kids.joinToString(", ") { "${it.name} (${ageYears(it)})" })
        if (fam.isNotEmpty()) sb.append("Family: " + fam.joinToString("; ") + ".\n")
        sb.append("Village stock: wood $wood, stone $stone, food $food")
        if (age >= 2) sb.append(", ore $ore, metal $metal")
        sb.append(". Buildings: ${count("hut")} ${if (age == 0) "tents" else "huts"}, ")
        if (age >= 1) sb.append("${count("farm")} farms, ")
        if (age >= 2) sb.append("${count("smelter")} forges, ")
        sb.append("${count("campfire")} campfires. ${villagers.size} villagers (${villagers.count { isChild(it) }} children).\n")
        val near = nearby(v)
        if (near.isNotEmpty()) {
            sb.append("Nearby: " + near.joinToString("; ") {
                "${it.name} the ${it.job.lowercase()} (${doing(it)}${if (it.say.isNotBlank()) ", just said \"${it.say.take(50)}\"" else ""})"
            } + "\n")
        } else {
            sb.append("Nobody is close to you right now.\n")
        }
        val far = villagers.filter { it !== v && it !in near }
        if (far.isNotEmpty()) sb.append("Elsewhere: " + far.joinToString(", ") { "${it.name} (${doing(it)})" } + "\n")
        val close = villagers.filter { it !== v && (v.affinity[it.id] ?: 0) >= 2 }.joinToString(", ") { it.name }
        val rivals = villagers.filter { it !== v && (v.affinity[it.id] ?: 0) <= -2 }.joinToString(", ") { it.name }
        if (close.isNotEmpty()) sb.append("Your friends: $close.\n")
        if (rivals.isNotEmpty()) sb.append("You don't get along with: $rivals.\n")
        if (v.memories.isNotEmpty()) sb.append("Your recent memories: " + v.memories.joinToString("; ") + "\n")
        if (v.heard.isNotEmpty()) sb.append("You recently heard: " + v.heard.joinToString(" | ") + "\n")
        if (v.recent.isNotEmpty()) sb.append("Your last actions: " + v.recent.joinToString(", ") + " (vary what you do).\n")
        if (v.said.isNotEmpty()) sb.append("Things you already said (do not repeat): " + v.said.takeLast(3).joinToString(" | ") + "\n")
        val h = history(3)
        if (h.isNotEmpty()) sb.append("Village history: " + h.joinToString(" | ") + "\n")
        val recent = log.takeLast(4)
        if (recent.isNotEmpty()) sb.append("Recent news: " + recent.joinToString(" | ") + "\n")
        sb.append(worldNote())
        if (godMsg.isNotBlank() && time < godUntil) {
            sb.append("A booming voice from the sky said to everyone: \"$godMsg\" (consider it seriously).\n")
        }
        sb.append("Actions you can use now: ${allowedActions().joinToString(", ")}.\n")
        if (kid) sb.append("You are a small child: use very short, simple words, at most 8 words, about play, food, family or what you see.\n")
        sb.append("What do you do next? Reply with one JSON object only. Your 'say' must rely only on the facts above.")
        return sb.toString()
    }

    private fun shortPrompt(v: Villager): String =
        "You are ${v.name}, ${if (isChild(v)) "a child" else "a ${v.job.lowercase()}"}. ${partOfDay()}. Hunger ${v.hunger.toInt()}, energy ${v.energy.toInt()}. " +
            "Stock: wood $wood, stone $stone, food $food. Pick one action from: ${allowedActions().joinToString(", ")}. JSON only."

    private fun logProblem(key: String, msg: String) {
        val now = System.currentTimeMillis()
        if (now - (problemAt[key] ?: 0L) < 120_000L) {
            problemSkipped[key] = (problemSkipped[key] ?: 0) + 1
            return
        }
        problemAt[key] = now
        val skipped = problemSkipped.remove(key) ?: 0
        onError(if (skipped > 0) "$msg (+$skipped similar)" else msg)
    }

    private fun brainStatus(base: String) {
        brain = base + " · $okCount ok" + (if (fixCount > 0) ", $fixCount repaired" else "") + (if (failCount > 0) ", $failCount failed" else "")
    }

    private fun send(v: Villager) {
        val b = brainProvider() ?: return
        val prompt = buildPrompt(v)
        val short = shortPrompt(v)
        v.waiting = true
        inFlight = true
        sentAt = time
        val req = ++reqSeq
        curReq = req
        brain = "thinking…"
        val id = v.id
        scope.launch(Dispatchers.IO) {
            var text: String? = null
            var err: String? = null
            var repaired = false
            for (attempt in 0..1) {
                try {
                    val t = b.ask(if (attempt == 0) SYSTEM else SYSTEM_SHORT, if (attempt == 0) prompt else short)
                    val p = Speech.parse(t)
                    if (p != null) {
                        text = t
                        repaired = p.repaired || attempt > 0
                        break
                    }
                    err = "unparsable reply: ${t.take(100)}"
                } catch (e: Exception) {
                    err = "${e.javaClass.simpleName}: ${e.message}"
                }
            }
            results.add(Decision(id, text, err, req, repaired))
        }
    }

    private fun drain() {
        if (inFlight && time - sentAt > 100.0) {
            inFlight = false
            curReq = ++reqSeq
            for (x in villagers) x.waiting = false
            failCount++
            retryAt = time + 30.0
            brainStatus("model too slow — autopilot")
            logProblem("slow", "Village model took too long; villagers use autopilot for a moment.")
        }
        while (true) {
            val r = results.poll() ?: break
            if (r.reqId != curReq) continue
            inFlight = false
            nextAskAt = time + (brainProvider()?.gapSeconds ?: 0.0)
            val v = villagers.firstOrNull { it.id == r.villagerId }
            if (r.text == null) {
                failCount++
                retryAt = time + 45.0
                brainStatus("offline — using autopilot")
                logProblem("call", "Village model call failed: ${r.error}")
                v?.waiting = false
                continue
            }
            try {
                val p = Speech.parse(r.text) ?: throw IllegalStateException("no usable JSON")
                okCount++
                if (r.repaired) fixCount++
                brainStatus((brainProvider()?.label ?: "model") + " online")
                if (v != null && v.phase == "idle") {
                    var act = p.action.trim().lowercase().replace(' ', '_').replace('-', '_')
                    if (act !in ACTIONS) act = ACTIONS.firstOrNull { act.length > 3 && (act.contains(it) || it.contains(act)) } ?: act
                    applyDecision(v, act, p.target, p.say, p.thought)
                } else if (v != null) {
                    v.waiting = false
                }
            } catch (e: Exception) {
                failCount++
                brainStatus("model answered oddly — autopilot")
                v?.waiting = false
                logProblem("parse", "Village model reply not understood: ${e.message}")
            }
        }
    }

    private fun schedule() {
        val b = brainProvider()
        val configured = b != null
        if (b == null) brain = "autopilot — set your local model in Settings"
        val idle = villagers.filter { it.phase == "idle" && it.idleFor >= 0.8f }.sortedByDescending { it.idleFor }
        for (v in idle) {
            if (isChild(v)) {
                val (a, t) = ruleDecision(v)
                applyDecision(v, a, t, "", "")
                continue
            }
            if (v.waiting) {
                if (v.idleFor > 25f) {
                    val (a, t) = ruleDecision(v)
                    applyDecision(v, a, t, "", "")
                }
                continue
            }
            if (configured && time >= retryAt) {
                if (!inFlight && time >= nextAskAt) send(v)
                else if (v.idleFor > 8f) {
                    val (a, t) = ruleDecision(v)
                    applyDecision(v, a, t, "", "")
                }
            } else {
                val (a, t) = ruleDecision(v)
                applyDecision(v, a, t, "", "")
            }
        }
    }

    // ---- kingdoms, world ages and god powers ----

    fun ageInfo(): WorldAgeInfo = WORLD_AGES[worldAge.coerceIn(0, WORLD_AGES.size - 1)]

    fun shake(): Float = if (time < shakeUntil) ((shakeUntil - time) / 1.5).toFloat().coerceIn(0f, 1f) else 0f

    private fun relKey(a: Int, b: Int) = "${min(a, b)}-${max(a, b)}"

    fun relOf(a: Int, b: Int): String = rels[relKey(a, b)]?.kind ?: "peace"

    fun wars(): List<Pair<Kingdom, Kingdom>> {
        val out = ArrayList<Pair<Kingdom, Kingdom>>()
        for (r in rels.values) {
            if (r.kind != "war") continue
            val a = kingdoms.firstOrNull { it.id == r.a } ?: continue
            val b = kingdoms.firstOrNull { it.id == r.b } ?: continue
            out.add(Pair(a, b))
        }
        return out
    }

    fun kx(k: Kingdom): Float =
        (if (k.id == 0) (buildings.firstOrNull { it.type == "campfire" }?.x ?: k.cx) else k.cx) + 0.5f

    fun ky(k: Kingdom): Float =
        (if (k.id == 0) (buildings.firstOrNull { it.type == "campfire" }?.y ?: k.cy) else k.cy) + 0.5f

    fun homeCount(k: Kingdom): Int = (2 + k.pop / 4).coerceIn(2, max(2, k.homes.size))

    private fun strength(k: Kingdom): Float =
        if (k.id == 0) adults() * 1.5f + count("hut") + age * 3f + toolBonus() * 2f + 4f
        else k.pop * 1.5f + k.might

    private fun setupWorld() {
        val nb = noise(Random(seed * 17 + 3), 4)
        for (i in 0 until GW * GH) {
            val x = i % GW
            val y = i / GW
            val d = hypot((x - GW / 2).toFloat(), (y - GH / 2).toFloat())
            biome[i] = when {
                d < 9f -> 0
                nb[i] > 0.80f -> 1
                nb[i] < 0.13f -> 2
                else -> 0
            }
        }
        val r = Random(seed * 31 + 17)
        kingdoms.clear()
        rels.clear()
        kingdoms.add(Kingdom(0, "Hearthvale", "Human", 0xFF4F9BFF, GW / 2, GH / 2))
        val spots = listOf(
            intArrayOf(6 + r.nextInt(4), 5 + r.nextInt(3)),
            intArrayOf(GW - 8 - r.nextInt(3), GH - 6 - r.nextInt(3)),
            intArrayOf(GW - 8 - r.nextInt(3), 5 + r.nextInt(3)),
        )
        val races = listOf("Elf", "Orc", "Dwarf")
        val colors = listOf(0xFFC77DFF, 0xFFE8503A, 0xFFF0B13C)
        val offs = listOf(
            intArrayOf(-2, -1), intArrayOf(2, -1), intArrayOf(-2, 2), intArrayOf(2, 2),
            intArrayOf(0, -3), intArrayOf(0, 3), intArrayOf(-4, 1), intArrayOf(4, 1),
        )
        for (k in 0 until 3) {
            val names = RACE_NAMES[races[k]] ?: listOf("Rivals")
            val kg = Kingdom(k + 1, names[r.nextInt(names.size)], races[k], colors[k], spots[k][0], spots[k][1])
            kg.pop = 5 + r.nextInt(4)
            kg.might = 8f + r.nextInt(8)
            kingdoms.add(kg)
            for (dy in -3..3) for (dx in -3..3) {
                val x = kg.cx + dx
                val y = kg.cy + dy
                if (x !in 1 until GW - 1 || y !in 1 until GH - 1) continue
                val i = y * GW + x
                if (occupied[i] || hypot(dx.toFloat(), dy.toFloat()) > 3.4f) continue
                if (tiles[i] != T.GRASS) {
                    tiles[i] = T.GRASS
                    amt[i] = 0
                }
            }
            occupied[kg.cy * GW + kg.cx] = true
            for (o in offs) {
                val x = kg.cx + o[0]
                val y = kg.cy + o[1]
                if (x !in 1 until GW - 1 || y !in 1 until GH - 1) continue
                val i = y * GW + x
                if (occupied[i]) continue
                tiles[i] = T.GRASS
                amt[i] = 0
                occupied[i] = true
                kg.homes.add(intArrayOf(x, y))
            }
        }
        val pk = pendingKings
        if (pk != null) {
            for (i in 0 until pk.length()) {
                val j = pk.getJSONObject(i)
                val kg = kingdoms.firstOrNull { it.id == j.optInt("id", -1) } ?: continue
                if (kg.id == 0) continue
                kg.pop = j.optInt("pop", kg.pop)
                kg.might = j.optDouble("might", kg.might.toDouble()).toFloat()
                kg.fallen = j.optBoolean("fallen", false)
                kg.fallenAt = j.optDouble("fallenAt", 0.0)
                val nm = j.optString("name", "")
                if (nm.isNotBlank()) kg.name = nm
            }
        }
        pendingKings = null
        val pr = pendingRels
        if (pr != null) {
            for (i in 0 until pr.length()) {
                val j = pr.getJSONObject(i)
                val a = j.optInt("a", -1)
                val b = j.optInt("b", -1)
                if (a < 0 || b < 0 || a == b) continue
                rels[relKey(a, b)] = Rel(a, b, j.optString("kind", "trade"), j.optDouble("until", time + 100.0))
            }
        }
        pendingRels = null
        nextWorldEventAt = time + 60.0
        refreshBorders()
    }

    private fun refreshBorders() {
        val me = kingdoms.firstOrNull() ?: return
        me.pop = villagers.size
        me.radius = min(10f, 3.5f + kotlin.math.sqrt(villagers.size.toFloat()) * 1.2f + buildings.size * 0.12f)
        for (k in kingdoms) {
            if (k.id != 0) k.radius = if (k.fallen) 0f else min(8.5f, 3.2f + kotlin.math.sqrt(k.pop.toFloat()) * 0.9f)
        }
        for (i in 0 until GW * GH) {
            if (tiles[i] == T.WATER) {
                owner[i] = -1
                continue
            }
            val x = i % GW + 0.5f
            val y = i / GW + 0.5f
            var best = -1
            var bestV = 1f
            for (k in kingdoms) {
                if (k.fallen || k.radius <= 0f) continue
                val v = hypot(x - kx(k), y - ky(k)) / k.radius
                if (v < bestV) {
                    bestV = v
                    best = k.id
                }
            }
            owner[i] = best
        }
    }

    private fun worldNote(): String {
        val inf = ageInfo()
        val sb = StringBuilder("World age: ${inf.name} (${inf.blurb}) ")
        if (predators.isNotEmpty()) {
            val kinds = predators.groupBy { it.kind }.entries.joinToString(", ") { "${it.value.size} ${it.key}${if (it.value.size == 1) "" else "s"}" }
            sb.append("DANGER: $kinds hunting near the village. Fighters should defend; others should stay by the fire. ")
        }
        val others = kingdoms.filter { it.id != 0 && !it.fallen }
        if (others.isNotEmpty()) {
            sb.append("Neighbouring kingdoms: " + others.joinToString(", ") { "${it.name} (${it.race}s, ${relOf(0, it.id)} with you)" } + ". ")
        }
        val w = wars().firstOrNull()
        if (w != null) sb.append("${w.first.name} is at war with ${w.second.name}. ")
        return sb.toString() + "\n"
    }

    private fun nextWorldAge() {
        val n = WORLD_AGES.size
        var pick = if (worldAge != 0 && rnd.nextInt(5) < 2) 0 else rnd.nextInt(n)
        if (pick == worldAge) pick = (pick + 1) % n
        worldAge = pick
        worldAgeStart = time
        val inf = ageInfo()
        event("${inf.name} begins. ${inf.blurb}")
        mark("${inf.name} begins.")
    }

    private fun worldTick(dt: Float) {
        ownerClock += dt
        if (ownerClock > 1.5f) {
            ownerClock = 0f
            refreshBorders()
        }
        fx.removeAll { time - it.born > it.life }
        if (time - worldAgeStart > DAY_LEN * 2.5) nextWorldAge()
        worldClock += dt
        if (worldClock < 12f) return
        worldClock = 0f
        val info = ageInfo()
        for (v in villagers) v.happiness = (v.happiness + info.mood).coerceIn(0f, 100f)
        if (worldAge == 3 && rnd.nextInt(8) == 0) {
            food += 2
            wood += 2
            event("A shimmering gift from the sky lands in the village (+2 food, +2 wood).")
        }
        for (k in kingdoms) {
            if (k.id == 0) continue
            if (k.fallen) {
                if (time - k.fallenAt > 260.0) reborn(k)
                continue
            }
            if (k.pop < 30 && rnd.nextFloat() < (if (info.farm >= 1f) 0.4f else 0.2f)) k.pop++
            k.might = min(40f, k.might + 0.15f)
        }
        for (r in rels.values.toList()) {
            when (r.kind) {
                "war" -> warRound(r)
                else -> if (time > r.until) rels.remove(relKey(r.a, r.b))
            }
        }
        if (time >= nextWorldEventAt) {
            nextWorldEventAt = time + 70.0 + rnd.nextInt(90)
            diplomacy()
        }
    }

    private fun reborn(k: Kingdom) {
        val names = RACE_NAMES[k.race] ?: return
        k.fallen = false
        k.pop = 4
        k.might = 6f
        k.name = "Free " + names[rnd.nextInt(names.size)]
        event("Rebels rise from the ruins: the ${k.race.lowercase()}s raise a new banner as the ${k.name}.")
        mark("${k.name} is founded by rebels.")
    }

    private fun warRound(r: Rel) {
        val a = kingdoms.firstOrNull { it.id == r.a } ?: return
        val b = kingdoms.firstOrNull { it.id == r.b } ?: return
        if (a.fallen || b.fallen) {
            rels.remove(relKey(r.a, r.b))
            return
        }
        val pa = strength(a)
        val pb = strength(b)
        val aWins = rnd.nextFloat() * (pa + pb) < pa
        val win = if (aWins) a else b
        val lose = if (aWins) b else a
        fx.add(Fx("clash", (kx(a) + kx(b)) / 2f, (ky(a) + ky(b)) / 2f, time, 13f))
        if (lose.id == 0) {
            food -= min(food, 1 + rnd.nextInt(2))
            wood -= min(wood, rnd.nextInt(3))
            for (v in villagers) v.happiness = max(0f, v.happiness - 3f)
            if (rnd.nextInt(3) == 0) addLog("Raiders from ${win.name} plunder the village stores.")
        } else {
            lose.pop = max(0, lose.pop - 1)
            if (win.id == 0) {
                wood += 1 + rnd.nextInt(2)
                stone += 1
                for (v in villagers) v.happiness = min(100f, v.happiness + 2f)
                if (rnd.nextInt(3) == 0) addLog("Hearthvale's defenders drive back ${lose.name}.")
            } else {
                win.might = min(40f, win.might + 0.6f)
            }
        }
        if (lose.id != 0 && lose.pop <= 2) {
            lose.fallen = true
            lose.fallenAt = time
            lose.pop = 0
            rels.remove(relKey(r.a, r.b))
            if (win.id != 0) win.pop += 2
            event("${lose.name} has fallen to ${win.name}! Its banners come down.")
            mark("${lose.name} fell to ${win.name}.")
            return
        }
        if (time > r.until) {
            rels.remove(relKey(r.a, r.b))
            event("${a.name} and ${b.name} sign a truce. The war is over.")
            mark("${a.name} and ${b.name} made peace.")
        }
    }

    private fun diplomacy() {
        val alive = kingdoms.filter { !it.fallen }
        if (alive.size < 2) return
        val a = alive[rnd.nextInt(alive.size)]
        val rest = alive.filter { it !== a }
        val b = rest[rnd.nextInt(rest.size)]
        if (rels.containsKey(relKey(a.id, b.id))) return
        val warFactor = ageInfo().war
        val warsNow = rels.values.count { it.kind == "war" }
        val roll = rnd.nextFloat()
        val key = relKey(a.id, b.id)
        val me = a.id == 0 || b.id == 0
        when {
            roll < 0.28f * warFactor && warsNow < 1 -> {
                rels[key] = Rel(a.id, b.id, "war", time + 120.0 + rnd.nextInt(120))
                if (me) {
                    alertUntil = time + 60.0
                    for (v in villagers) v.happiness = max(0f, v.happiness - 6f)
                    event("${a.name} declares war on ${b.name}! Warriors march on the border.")
                } else {
                    event("${a.name} declares war on ${b.name}!")
                }
                mark("${a.name} went to war with ${b.name}.")
            }
            roll < 0.28f * warFactor + 0.30f -> {
                rels[key] = Rel(a.id, b.id, "alliance", time + 500.0)
                if (me) for (v in villagers) v.happiness = min(100f, v.happiness + 8f)
                event("${a.name} and ${b.name} swear an alliance.")
                mark("${a.name} and ${b.name} allied.")
            }
            else -> {
                rels[key] = Rel(a.id, b.id, "trade", time + 160.0)
                if (me) {
                    food += 3
                    wood += 3
                }
                if (a.id != 0) a.pop++
                if (b.id != 0) b.pop++
                event("Trade caravans roll between ${a.name} and ${b.name}.")
            }
        }
    }

    // ---- predators: wolves and bears hunt anyone caught away from the fire ----

    private fun spawnPredators(kind: String, n: Int) {
        val fire = buildings.firstOrNull { it.type == "campfire" }
        repeat(n) {
            for (attempt in 0 until 12) {
                val edgeX = if (rnd.nextBoolean()) 1.5f else GW - 2.5f
                val y = 2f + rnd.nextFloat() * (GH - 4f)
                if (tiles[y.toInt() * GW + edgeX.toInt()] == T.WATER) continue
                if (fire != null && hypot(edgeX - fire.x, y - fire.y) < 9f) continue
                predators.add(Predator(kind, edgeX, y).also { it.angle = rnd.nextFloat() * 6.28f })
                break
            }
        }
    }

    private fun stepPredator(p: Predator, dx: Float, dy: Float) {
        val nx = (p.x + dx).coerceIn(0.5f, GW - 0.6f)
        val ny = (p.y + dy).coerceIn(0.5f, GH - 0.6f)
        if (tiles[ny.toInt() * GW + nx.toInt()] == T.WATER) {
            p.angle += 1.7f
            return
        }
        p.x = nx
        p.y = ny
    }

    private fun isFighter(v: Villager) = !isChild(v) && v.hp > 35f &&
        (age >= 2 || v.job in setOf("Hunter", "Scout", "Builder", "Woodcutter", "Miner", "Bronzesmith", "Blacksmith"))

    private var nextTowerAt = 0.0

    /** When danger looms, the villagers raise a watchtower on their own. Towers shoot at nearby predators. */
    private fun defenceTick() {
        if (age < 1 || time < nextTowerAt || villagers.size < 3) return
        val danger = predators.isNotEmpty() || wars().any { it.first.id == 0 || it.second.id == 0 }
        if (!danger) return
        nextTowerAt = time + 90.0
        val have = count("tower")
        if (have >= 3 || wood < 8 || stone < 2) return
        val site = findSite()
        if (site < 0) return
        wood -= 8
        stone -= 2
        addBuilding("tower", site % GW, site / GW)
        val builder = villagers.firstOrNull { it.job == "Builder" } ?: villagers[rnd.nextInt(villagers.size)]
        builder.remember("helped raise a watchtower")
        event("The villagers raise a watchtower at the edge of the village. Archers will watch the woods.")
        mark("A watchtower was built.")
    }

    private fun predatorTick(dt: Float) {
        defenceTick()
        if (predators.isEmpty()) return
        val fires = buildings.filter { it.type == "campfire" || it.type == "tower" }
        val towers = buildings.filter { it.type == "tower" }
        for (p in predators.toList()) {
            p.life += dt
            if (p.cool > 0f) p.cool -= dt
            var prey: Villager? = null
            var best = 15f
            for (v in villagers) {
                val safe = fires.any { hypot(it.x + 0.5f - v.x, it.y + 0.5f - v.y) < 3.2f }
                if (safe) continue
                val d = hypot(v.x - p.x, v.y - p.y)
                if (d < best) {
                    best = d
                    prey = v
                }
            }
            val speed = (when (p.kind) { "bear" -> 1.5f; "boar" -> 2.4f; else -> 2.1f }) * dt
            if (p.life > 150f && prey == null) {
                // the hunt is over: slip away toward the nearest map edge
                val tx = if (p.x < GW / 2f) 0f else GW.toFloat()
                val d = max(0.1f, abs(tx - p.x))
                stepPredator(p, (tx - p.x) / d * speed, 0f)
                if (p.x < 0.8f || p.x > GW - 1.2f) {
                    predators.remove(p)
                    if (predators.isEmpty()) event("The ${p.kind}s slink back into the woods. The village breathes again.")
                }
                continue
            }
            if (prey != null) {
                val dx = prey.x - p.x
                val dy = prey.y - p.y
                val d = max(0.05f, hypot(dx, dy))
                if (d > 0.8f) stepPredator(p, dx / d * speed, dy / d * speed)
                else if (p.cool <= 0f) {
                    val dmg = (when (p.kind) { "bear" -> 22f; "boar" -> 16f; else -> 12f }) * (if (isChild(prey)) 1.5f else 1f)
                    prey.hp -= dmg
                    prey.harmedBy = p.kind
                    prey.happiness = max(0f, prey.happiness - 12f)
                    prey.say = "Aaah!"
                    prey.sayLeft = 2.5f
                    p.cool = 1.4f
                    fx.add(Fx("clash", prey.x, prey.y, time, 1.2f))
                }
            } else {
                if (rnd.nextInt(60) == 0) p.angle += (rnd.nextFloat() - 0.5f) * 2.4f
                stepPredator(p, kotlin.math.cos(p.angle) * speed * 0.5f, kotlin.math.sin(p.angle) * speed * 0.5f)
            }
            for (tw in towers) {
                if (hypot(tw.x + 0.5f - p.x, tw.y + 0.5f - p.y) < 6.5f) {
                    p.hp -= 6f * dt
                    if (rnd.nextInt(45) == 0) fx.add(Fx("arrow", p.x, p.y, time, 0.6f))
                }
            }
            val fighters = villagers.filter { isFighter(it) && hypot(it.x - p.x, it.y - p.y) < 2.6f }
            if (fighters.isNotEmpty()) {
                p.hp -= fighters.size * (6f + 3f * toolBonus()) * dt
                if (rnd.nextInt(40) == 0) fx.add(Fx("clash", p.x, p.y, time, 0.8f))
            }
            if (p.hp <= 0f) {
                predators.remove(p)
                val meat = when (p.kind) { "bear" -> 8; "boar" -> 6; else -> 4 }
                food += meat
                val hero = fighters.maxByOrNull { it.hp }
                if (hero != null) {
                    hero.happiness = min(100f, hero.happiness + 15f)
                    hero.remember("helped kill a ${p.kind}")
                    event("${hero.name} and the others bring down the ${p.kind}! (+$meat food)")
                    mark("${hero.name} helped slay a ${p.kind}.")
                } else {
                    event("The ${p.kind} falls (+$meat food).")
                }
            }
        }
        // everyone who is not a fighter runs for the fire when a predator gets close
        for (v in villagers) {
            if (v.phase == "sleeping") continue
            if (isFighter(v) && v.hp > 50f) continue
            val near = predators.any { hypot(it.x - v.x, it.y - v.y) < 4.5f }
            if (!near || time < (fleeAt[v.id] ?: 0.0)) continue
            fleeAt[v.id] = time + 4.0
            if (startAction(v, "huddle", "")) {
                v.say = "Run!"
                v.sayLeft = 2f
            }
        }
    }

    private fun godAct(text: String) {
        addLog(text)
        lastNarrateAt = 0L
        narrate(text)
        for (v in villagers) v.remember(text.take(70))
    }

    private fun hitKingdom(ci: Int, loss: Int, text: String) {
        val k = kingdoms.firstOrNull { it.id == owner[ci] } ?: return
        if (k.id == 0 || k.fallen) return
        k.pop = max(2, k.pop - loss)
        event("$text ${k.name} suffers.")
    }

    /** The player's god powers. Called when a power is chosen and the map is tapped. */
    fun usePower(name: String, x: Float, y: Float) {
        val tx = x.coerceIn(0f, GW - 0.01f)
        val ty = y.coerceIn(0f, GH - 0.01f)
        val ix = tx.toInt()
        val iy = ty.toInt()
        val ci = iy * GW + ix
        fun near(r: Float) = villagers.filter { hypot(it.x - tx, it.y - ty) <= r }
        when (name) {
            "Bless" -> {
                for (v in near(4.5f)) {
                    v.happiness = min(100f, v.happiness + 30f)
                    v.hp = min(100f, v.hp + 40f)
                    v.hunger = max(0f, v.hunger - 25f)
                }
                for (b in buildings) {
                    if (b.type == "farm" && hypot(b.x + 0.5f - tx, b.y + 0.5f - ty) <= 5f) b.boostUntil = time + 60.0
                }
                fx.add(Fx("bless", tx, ty, time, 2.6f))
                godAct("A golden light blesses the land.")
            }
            "Rain" -> {
                rainUntil = max(rainUntil, time + 70.0)
                for (i in tiles.indices) if (tiles[i] == T.BUSH && amt[i] < 3) amt[i]++
                godAct("The sky opens and rain falls.")
            }
            "Spawn" -> {
                if (tiles[ci] == T.WATER) return
                if (villagers.size >= popCap() + 4) {
                    addLog("The village is too crowded to welcome anyone else.")
                    return
                }
                val n = spawnVillager()
                n.x = tx
                n.y = ty
                fx.add(Fx("spawn", tx, ty, time, 1.8f))
                godAct("A stranger named ${n.name} appears in a flash of light.")
            }
            "Lightning" -> {
                fx.add(Fx("bolt", tx, ty, time, 0.9f))
                if (tiles[ci] == T.FOREST) {
                    amt[ci] = 2
                    fx.add(Fx("fire", tx, ty, time, 7f))
                }
                for (v in near(1.8f)) {
                    v.hp = max(5f, v.hp - 45f)
                    v.happiness = max(0f, v.happiness - 10f)
                }
                godAct("Lightning strikes!")
            }
            "Quake" -> {
                shakeUntil = time + 1.5
                fx.add(Fx("quake", tx, ty, time, 1.8f))
                repeat(8) {
                    val ex = ix + rnd.nextInt(7) - 3
                    val ey = iy + rnd.nextInt(7) - 3
                    if (ex in 1 until GW - 1 && ey in 1 until GH - 1) {
                        val i = ey * GW + ex
                        if (tiles[i] == T.GRASS && !occupied[i]) {
                            tiles[i] = T.ROCK
                            amt[i] = 4
                        }
                    }
                }
                for (v in near(7f)) v.happiness = max(0f, v.happiness - 6f)
                godAct("The ground shakes and splits.")
                hitKingdom(ci, 2, "An earthquake rattles the land.")
            }
            "Meteor" -> {
                shakeUntil = time + 1.8
                fx.add(Fx("meteor", tx, ty, time, 2.4f))
                for (v in near(2.5f)) v.hp = max(1f, v.hp - 60f)
                if (!occupied[ci] && tiles[ci] != T.WATER) {
                    tiles[ci] = if (age >= 2) T.ORE else T.ROCK
                    amt[ci] = 5
                }
                godAct("A meteor crashes down!")
                mark("A meteor fell from the sky.")
                hitKingdom(ci, 3, "A meteor strikes.")
            }
            "Plague" -> {
                plagueUntil = time + 150.0
                fx.add(Fx("plague", tx, ty, time, 5f))
                for (v in near(5.5f)) {
                    v.hp = max(5f, v.hp - 30f)
                    v.happiness = max(0f, v.happiness - 12f)
                }
                godAct("A sickly green cloud drifts over the land.")
                hitKingdom(ci, 3, "A plague spreads.")
            }
        }
    }

    // ---- tick ----

    private var nextEventAt = DAY_LEN * 0.3
    private var alertUntil = 0.0
    private var rainUntil = 0.0
    private var festivalUntil = 0.0

    private fun event(text: String) {
        addLog(text)
        lastNarrateAt = 0L
        narrate(text)
        for (v in villagers) v.remember(text.take(70))
    }

    private fun eventTick() {
        if (time < nextEventAt || villagers.size < 2) return
        nextEventAt = time + 150.0 + rnd.nextInt(150) - (if (worldAge == 6) 75.0 else 0.0)
        when (rnd.nextInt(6)) {
            0 -> {
                for (i in tiles.indices) if (tiles[i] == T.BUSH) amt[i] = 3
                event("The bushes burst with ripe berries.")
            }
            1 -> {
                if (villagers.size < popCap() && rnd.nextInt(3) == 0) {
                    val n = spawnVillager()
                    event("A wandering ${n.job.lowercase()} named ${n.name} asks to stay.")
                } else {
                    food += 3
                    wood += 3
                    event("A friendly traveler shares supplies and stories.")
                }
            }
            2 -> {
                alertUntil = time + 80.0
                for (v in villagers) v.happiness = max(0f, v.happiness - 6f)
                val bearLoose = (worldAge == 4 || worldAge == 6 || worldAge == 9) && rnd.nextInt(3) == 0
                if (!bearLoose && rnd.nextInt(3) == 0) {
                    val n = 1 + rnd.nextInt(2)
                    spawnPredators("boar", n)
                    event("Wild boars crash out of the undergrowth and charge toward the village!")
                } else if (bearLoose) {
                    spawnPredators("bear", 1)
                    event("A huge bear crashes out of the forest and heads for the village!")
                } else {
                    val n = 2 + rnd.nextInt(2)
                    spawnPredators("wolf", n)
                    event("A pack of $n wolves prowls out of the trees! Stay near the fire, fighters to the edge.")
                }
                mark("Predators attacked the village.")
            }
            3 -> {
                rainUntil = time + 90.0
                event("Rain begins to fall.")
            }
            4 -> {
                if (food >= 8) {
                    food -= 4
                    festivalUntil = time + 45.0
                    for (v in villagers) v.happiness = min(100f, v.happiness + 20f)
                    event("The villagers hold a small harvest feast around the fire.")
                } else {
                    stone += 3
                    event("Someone finds a pile of good stone by the stream.")
                }
            }
            else -> {
                val v = villagers[rnd.nextInt(villagers.size)]
                wood += 4
                event("${v.name} stumbles on a stash of seasoned timber (+4 wood).")
            }
        }
    }

    fun tick(dtIn: Float) {
        val dt = dtIn.coerceIn(0f, 0.1f)
        time += dt
        drain()

        for (v in villagers.toList()) update(v, dt)
        predatorTick(dt)

        bushClock += dt
        if (bushClock > 30f) {
            bushClock = 0f
            if (age >= 2 && rnd.nextBoolean()) ensure(T.ORE, 5, 6)
            for (i in tiles.indices) {
                if (tiles[i] == T.BUSH && amt[i] < 3) amt[i]++
            }
            if (rnd.nextInt(3) == 0) {
                val i = rnd.nextInt(GW * GH)
                if (tiles[i] == T.GRASS && !occupied[i] && i % GW > 2 && i / GW > 2) {
                    val x = i % GW
                    val y = i / GW
                    val hasTree = (-2..2).any { dy -> (-2..2).any { dx ->
                        val xx = x + dx
                        val yy = y + dy
                        xx in 0 until GW && yy in 0 until GH && tiles[yy * GW + xx] == T.FOREST
                    } }
                    if (hasTree) {
                        tiles[i] = T.FOREST
                        amt[i] = 3
                    }
                }
            }
        }

        for (b in buildings) {
            if (b.type != "farm") continue
            var interval = (if (time < b.boostUntil) 12f else 40f) / ageInfo().farm
            if (toolBonus() > 0) interval *= 0.8f
            if (raining()) interval *= 0.7f
            b.timer += dt
            if (b.timer >= interval) {
                b.timer = 0f
                b.ready++
                if (b.ready >= 4) {
                    food += b.ready
                    b.ready = 0
                }
            }
        }

        if (time - lastBirth > 200.0 && villagers.size < min(popCap(), count("hut") * 2 + 4) && food >= 6) {
            val ps = villagers.filter { !isChild(it) && ageYears(it) in 6..45 && it.hunger < 70f }
            if (ps.size >= 2) {
                lastBirth = time
                val a = ps[rnd.nextInt(ps.size)]
                val b = ps.filter { it !== a }.maxByOrNull { (a.affinity[it.id] ?: 0) * 2 + rnd.nextInt(4) }!!
                val n = spawnVillager(a, b)
                a.happiness = min(100f, a.happiness + 12f)
                b.happiness = min(100f, b.happiness + 12f)
                a.remember("welcomed baby ${n.name}")
                b.remember("welcomed baby ${n.name}")
                addLog("${a.name} and ${b.name} welcome a baby, ${n.name}!")
                mark("${n.name} was born to ${a.name} and ${b.name}.")
                narrate("A baby is born: ${n.name}")
            }
        }

        ageTick(dt)
        eventTick()
        worldTick(dt)
        schedule()
    }

    private fun update(v: Villager, dt: Float) {
        val asleep = v.phase == "sleeping"
        v.hunger = min(100f, v.hunger + (if (asleep) 0.12f else 0.33f) * dt)
        if (!asleep) v.energy = max(0f, v.energy - 0.2f * dt)
        if (v.hunger >= 100f) v.hp -= 1.2f * dt else if (v.hunger < 70f && v.hp < 100f) v.hp = min(100f, v.hp + 0.4f * dt)
        v.happiness += (55f - v.happiness) * 0.01f * dt
        if (v.hunger > 85f) v.happiness -= 0.5f * dt
        v.happiness = v.happiness.coerceIn(0f, 100f)
        if (v.hp <= 0f) {
            villagers.remove(v)
            if (v.harmedBy.isNotEmpty()) {
                event("${v.name} the ${v.job.lowercase()} was killed by a ${v.harmedBy}. The village mourns.")
                mark("${v.name} the ${v.job.lowercase()} was killed by a ${v.harmedBy}.")
            } else {
                event("${v.name} the ${v.job.lowercase()} starved. The village mourns.")
            }
            return
        }
        if (isChild(v) && ageYears(v) >= 3) growUp(v)
        if (ageYears(v) >= v.lifespan) {
            villagers.remove(v)
            for (o in villagers) o.happiness = max(0f, o.happiness - 8f)
            event("${v.name} the ${v.job.lowercase()} passed away peacefully at ${ageYears(v)}.")
            mark("${v.name} the ${v.job.lowercase()} passed away at ${ageYears(v)}.")
            return
        }
        if (v.energy <= 0f && !asleep) {
            v.action = "sleep"
            v.phase = "sleeping"
            v.thought = "so tired…"
        }
        if (v.sayLeft > 0f) {
            v.sayLeft -= dt
            if (v.sayLeft <= 0f) v.say = ""
        }
        if (v.replyIn > 0f) {
            v.replyIn -= dt
            if (v.replyIn <= 0f && v.replyText.isNotBlank()) {
                val txt = Speech.clean(v.replyText, 90)
                v.say = txt
                v.sayLeft = 6f
                noteSaid(v, txt)
                val to = v.replyTo
                if (to != null) {
                    to.heard.add("${v.name}: $txt")
                    while (to.heard.size > 3) to.heard.removeAt(0)
                    to.remember("${v.name} answered: ${txt.take(50)}")
                    v.remember("answered ${to.name}: ${txt.take(40)}")
                    addLog("${v.name} to ${to.name}: $txt")
                }
                v.replyText = ""
                v.replyTo = null
            }
        }
        when (v.phase) {
            "idle" -> v.idleFor += dt
            "walking" -> move(v, dt)
            "working" -> work(v, dt)
            "sleeping" -> {
                val c = cellOf(v)
                val inHut = buildings.any { it.type == "hut" && it.y * GW + it.x == c }
                v.energy += (if (inHut) 3.2f else 1.1f) * dt
                if (v.energy >= 100f || (v.energy >= 85f && !isNight())) {
                    v.energy = min(100f, v.energy)
                    v.thought = "feeling rested"
                    if (inHut) v.happiness = min(100f, v.happiness + 5f)
                    finish(v)
                }
            }
        }
    }

    private fun move(v: Villager, dt: Float) {
        if (v.pathIdx >= v.path.size) {
            arrive(v)
            return
        }
        val cell = v.path[v.pathIdx]
        val tx = cell % GW + 0.5f
        val ty = cell / GW + 0.5f
        val dx = tx - v.x
        val dy = ty - v.y
        val dist = hypot(dx, dy)
        val step = SPEED * dt
        if (abs(dx) > 0.01f) v.facing = if (dx > 0) 1 else -1
        if (dist <= step) {
            v.x = tx
            v.y = ty
            v.pathIdx++
            if (v.pathIdx >= v.path.size) arrive(v)
        } else {
            v.x += dx / dist * step
            v.y += dy / dist * step
        }
    }

    // ---- saving ----

    fun save() {
        try {
            val o = JSONObject()
            o.put("seed", seed)
            o.put("time", time)
            o.put("wood", wood).put("stone", stone).put("food", food)
            o.put("chron", JSONArray(chronicle.takeLast(40)))
            o.put("ore", ore).put("metal", metal).put("smelted", totalSmelted).put("explored", explored).put("age", age)
            o.put("nextId", nextId)
            o.put("wage", worldAge).put("wstart", worldAgeStart)
            val ks = JSONArray()
            kingdoms.forEach {
                ks.put(
                    JSONObject().put("id", it.id).put("pop", it.pop).put("might", it.might.toDouble())
                        .put("fallen", it.fallen).put("fallenAt", it.fallenAt).put("name", it.name)
                )
            }
            o.put("kings", ks)
            val rs = JSONArray()
            rels.values.forEach { rs.put(JSONObject().put("a", it.a).put("b", it.b).put("kind", it.kind).put("until", it.until)) }
            o.put("rels", rs)
            o.put("tiles", String(CharArray(tiles.size) { i -> '0' + tiles[i] }))
            o.put("amt", String(CharArray(amt.size) { i -> '0' + min(9, amt[i]) }))
            val bs = JSONArray()
            buildings.forEach { bs.put(JSONObject().put("t", it.type).put("x", it.x).put("y", it.y).put("e", it.era)) }
            o.put("buildings", bs)
            val vs = JSONArray()
            villagers.forEach {
                vs.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("job", it.job).put("p", it.personaIdx)
                        .put("happy", it.happiness.toDouble()).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                        .put("hunger", it.hunger.toDouble()).put("energy", it.energy.toDouble())
                        .put("hp", it.hp.toDouble())
                        .put("born", it.born).put("life", it.lifespan).put("par", it.parents)
                )
            }
            o.put("villagers", vs)
            o.put("log", JSONArray(log.takeLast(20)))
            val tmp = File(saveFile.parentFile, saveFile.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(saveFile)
        } catch (e: Exception) {
            // Saving is best effort.
        }
    }

    private fun load(): Boolean {
        return try {
            if (!saveFile.exists()) return false
            val o = JSONObject(saveFile.readText())
            seed = o.getLong("seed")
            time = o.getDouble("time")
            wood = o.getInt("wood")
            stone = o.getInt("stone")
            food = o.getInt("food")
            ore = o.optInt("ore", 0)
            metal = o.optInt("metal", 0)
            totalSmelted = o.optInt("smelted", 0)
            explored = o.optInt("explored", 0)
            nextId = o.optInt("nextId", 1)
            val t = o.getString("tiles")
            val a = o.getString("amt")
            if (t.length != tiles.size || a.length != amt.size) return false
            for (i in tiles.indices) {
                tiles[i] = t[i] - '0'
                amt[i] = a[i] - '0'
            }
            occupied.fill(false)
            buildings.clear()
            val bs = o.getJSONArray("buildings")
            for (i in 0 until bs.length()) {
                val b = bs.getJSONObject(i)
                addBuilding(b.getString("t"), b.getInt("x"), b.getInt("y"), b.optInt("e", 1))
            }
            age = o.optInt("age", if (buildings.any { it.type == "farm" }) 1 else 0)
            if (age >= 2 && tiles.none { it == T.ORE }) ensure(T.ORE, 5, 8)
            lastBirth = time
            chronicle.clear()
            val ch = o.optJSONArray("chron")
            if (ch != null) for (i in 0 until ch.length()) chronicle.add(ch.getString(i))
            villagers.clear()
            val vs = o.getJSONArray("villagers")
            for (i in 0 until vs.length()) {
                val j = vs.getJSONObject(i)
                val v = Villager(j.getInt("id"), j.getString("name"), j.optString("job", JOBS[i % JOBS.size]), j.optInt("p", i))
                v.happiness = j.optDouble("happy", 60.0).toFloat()
                v.x = j.getDouble("x").toFloat()
                v.y = j.getDouble("y").toFloat()
                v.hunger = j.getDouble("hunger").toFloat()
                v.energy = j.getDouble("energy").toFloat()
                v.hp = j.getDouble("hp").toFloat()
                v.lifespan = j.optInt("life", 55 + rnd.nextInt(15))
                v.born = j.optDouble("born", time - (16 + rnd.nextInt(15) + rnd.nextFloat()) * YEAR)
                v.parents = j.optString("par", "")
                villagers.add(v)
            }
            val lg = o.optJSONArray("log")
            if (lg != null) for (i in 0 until lg.length()) log.add(lg.getString(i))
            worldAge = o.optInt("wage", 0).coerceIn(0, WORLD_AGES.size - 1)
            worldAgeStart = o.optDouble("wstart", time)
            pendingKings = o.optJSONArray("kings")
            pendingRels = o.optJSONArray("rels")
            rnd = Random(seed xor time.toLong())
            villagers.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val SYSTEM = """You play one villager in a small pixel-art village, as a distinct character with a job, a personality, a way of speaking and a dream. Reply with ONLY a JSON object, no other text:
{"thought": "<your private thought, first person, under 14 words, in your voice>", "action": "<one action>", "target": "<villager name, only for talk>", "say": "<one short sentence you say aloud in your own voice, or empty>"}
Actions: gather_wood, gather_stone, gather_food (berries), fish (at the lake), hunt (game in the forest, hunter-gatherers' best food), tend_farm (needs a farm, Farming Age on), explore (scouting far away), tell_story (at the fire, lifts everyone's spirits), huddle (stay by the fire), build_hut (6 wood + 2 stone), build_farm (4 wood, Farming Age on), build_campfire (3 stone), mine_ore (Bronze Age on), smelt (2 ore + 1 wood at a forge, Bronze Age on), build_smelter (4 wood + 6 stone, Bronze Age on), eat (needs food in stock), sleep, talk (set target and say), wander. Use only the actions listed as available to you in the message.
Mostly do the work of your job, but react to your needs, the weather, wolves, news and what others say. Talk about what you have seen, feel or remember, with opinions and feelings. Never repeat a line or an action you just did, and vary your sentences. Stay alive: eat when hungry, sleep when tired or at night.
Speak only about facts given in the message: your needs, the stock, the weather, people nearby and what they said, family, village history and news. Never invent buildings, people, resources or events. Answer what others actually said to you. If you are a child, use very short, simple words."""
        private const val SYSTEM_SHORT = """Reply with ONLY a JSON object: {"thought": "", "action": "<one allowed action>", "target": "", "say": "<one short sentence using only the facts given, or empty>"}"""
    }
}
