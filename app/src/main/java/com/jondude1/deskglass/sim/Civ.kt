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

object T {
    const val GRASS = 0
    const val FOREST = 1
    const val ROCK = 2
    const val WATER = 3
    const val BUSH = 4
}

class Building(val type: String, val x: Int, val y: Int) {
    var boostUntil = 0.0
    var ready = 0
    var timer = 0f
}

class Persona(val trait: String, val voice: String, val dream: String, val lines: List<String>)

val JOBS = listOf("Woodcutter", "Miner", "Forager", "Farmer", "Builder", "Scout", "Storyteller")

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
)

private val JOB_LINES = mapOf(
    "Woodcutter" to listOf("Another tree down.", "My axe needs a whetstone.", "Timber! ...sorry, habit.", "These oaks are stubborn."),
    "Miner" to listOf("Good solid rock here.", "My back aches, but the stone is fine.", "Stone never lies.", "Found a nice vein."),
    "Forager" to listOf("These berries are sweet today.", "The fish are biting.", "Never go hungry on my watch.", "More berries than yesterday."),
    "Farmer" to listOf("The crops look happy.", "A little water, a little patience.", "The soil here is rich.", "Harvest soon."),
    "Builder" to listOf("Another wall, another day.", "A hut needs a straight roof.", "We could fit one more house.", "Measure twice, hammer once."),
    "Scout" to listOf("I saw something beyond the hills.", "The wind is strange today.", "No wolves on the ridge. Yet.", "The world is big out there."),
    "Storyteller" to listOf("That reminds me of a tale...", "Gather round, friends.", "Every village needs its stories.", "Let me tell you a secret."),
)

private val STORIES = listOf(
    "Long ago a bear stole a whole winter of berries, and wept about it.",
    "There was once a tree so tall it scratched the moon.",
    "I once met a fish who swore it was a king.",
    "They say the old ruins sing when the wind is just right.",
    "A traveler once paid me in songs. I'm still rich.",
    "The first fire in this valley burned for a hundred days.",
)

class Villager(val id: Int, val name: String, val job: String, val personaIdx: Int) {
    val persona: Persona get() = PERSONAS[personaIdx % PERSONAS.size]
    val trait: String get() = persona.trait
    val colorIdx: Int get() = JOBS.indexOf(job).coerceAtLeast(0)
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

    fun remember(text: String) {
        memories.add(text)
        while (memories.size > 6) memories.removeAt(0)
    }
}

/** Whatever answers the villagers' questions: the local model, or Claude through the saved key. */
class Brain(val label: String, val gapSeconds: Double, val ask: (system: String, user: String) -> String)

private class Decision(val villagerId: Int, val text: String?, val error: String?)

private val NAMES = listOf(
    "Mira", "Tobin", "Edda", "Rook", "Wren", "Bram", "Isla", "Finn", "Odette", "Cass", "Hugo", "Nell",
    "Pike", "Sorrel", "Dov", "Lark", "Ember", "Quill", "Tamsin", "Gideon", "Juno", "Alder", "Pip", "Vesna",
)
private val ACTIONS = setOf(
    "gather_wood", "gather_stone", "gather_food", "build_hut", "build_farm", "build_campfire",
    "eat", "sleep", "talk", "wander", "fish", "tend_farm", "explore", "tell_story", "huddle",
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
    private var godUntil = 0.0
    private var lastNarrateAt = 0L
    private var lastErrorLog = 0L

    init {
        if (!load()) generate()
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
        time = DAY_LEN * 0.08
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

    private fun addBuilding(type: String, x: Int, y: Int) {
        buildings.add(Building(type, x, y))
        occupied[y * GW + x] = true
    }

    private fun spawnVillager(): Villager {
        val used = villagers.map { it.name }.toSet()
        val pool = NAMES.filter { it !in used }.ifEmpty { NAMES }
        val name = pool[rnd.nextInt(pool.size)]
        val first = listOf("Woodcutter", "Forager", "Builder", "Scout")
        val job = if (villagers.size < first.size) first[villagers.size] else {
            val counts = JOBS.associateWith { j -> villagers.count { it.job == j } }
            val low = counts.values.minOrNull() ?: 0
            val choices = JOBS.filter { counts[it] == low }
            choices[rnd.nextInt(choices.size)]
        }
        val usedP = villagers.map { it.personaIdx }.toSet()
        val freeP = PERSONAS.indices.filter { it !in usedP }.ifEmpty { PERSONAS.indices.toList() }
        val v = Villager(nextId++, name, job, freeP[rnd.nextInt(freeP.size)])
        val fire = buildings.firstOrNull { it.type == "campfire" }
        v.x = (fire?.x ?: GW / 2) + 0.5f + (rnd.nextFloat() - 0.5f) * 3f
        v.y = (fire?.y ?: GH / 2) + 0.5f + (rnd.nextFloat() - 0.5f) * 3f
        villagers.add(v)
        return v
    }

    fun restart() {
        seed = System.currentTimeMillis()
        nextId = 1
        generate()
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
    fun darkness(): Float {
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
            "gather_wood", "gather_stone", "gather_food" -> {
                val type = when (action) {
                    "gather_wood" -> T.FOREST
                    "gather_stone" -> T.ROCK
                    else -> T.BUSH
                }
                val i = nearestTile(v, type)
                if (i < 0) return false
                return go(v, action, i)
            }
            "build_hut", "build_farm", "build_campfire" -> {
                val ok = when (action) {
                    "build_hut" -> wood >= 6 && stone >= 2
                    "build_farm" -> wood >= 4
                    else -> stone >= 3 && count("campfire") < 3
                }
                if (!ok) return false
                val site = findSite()
                if (site < 0) return false
                if (!go(v, action, site)) return false
                when (action) {
                    "build_hut" -> { wood -= 6; stone -= 2 }
                    "build_farm" -> wood -= 4
                    else -> stone -= 3
                }
                reserved.add(site)
                return true
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
            v.action.startsWith("gather_") -> { v.phase = "working"; v.workLeft = 3f }
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
                val story = STORIES[rnd.nextInt(STORIES.size)]
                v.say = story.take(90)
                v.sayLeft = 8f
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
                    l.replyText = listOf("Ooh, tell it again!", "Ha! I never heard that one.", "Is that really true?", "Lovely tale.")[rnd.nextInt(4)]
                    l.remember("${v.name} told a story by the fire")
                }
                v.happiness = min(100f, v.happiness + 5f)
            }
            v.action == "talk" -> {
                val o = v.talkTo
                if (o != null) {
                    v.say = v.talkMsg.take(90)
                    v.sayLeft = 7f
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
                        o.replyText = o.persona.lines[rnd.nextInt(o.persona.lines.size)]
                        o.remember("${v.name} chatted with me")
                    }
                    o.replyIn = 2.5f
                    v.remember("talked with ${o.name}")
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
            "gather_wood", "gather_stone", "gather_food" -> {
                val i = v.targetTile
                if (i >= 0 && amt[i] > 0) {
                    amt[i]--
                    v.gathered++
                    when (v.action) {
                        "gather_wood" -> wood++
                        "gather_stone" -> stone++
                        else -> food += 2
                    }
                    if (amt[i] == 0 && tiles[i] != T.BUSH) tiles[i] = T.GRASS
                }
                if (v.gathered >= 4 || i < 0 || amt[i] <= 0) {
                    if (v.gathered >= 3) v.remember("brought back ${v.gathered} ${v.action.removePrefix("gather_")}")
                    finish(v)
                } else v.workLeft = 3f
            }
            "build_hut", "build_farm", "build_campfire" -> {
                val i = v.targetTile
                reserved.remove(i)
                val type = v.action.removePrefix("build_")
                if (i >= 0 && !occupied[i] && tiles[i] == T.GRASS) {
                    addBuilding(type, i % GW, i / GW)
                    addLog("${v.name} built a $type.")
                    narrate("${v.name} finishes a $type")
                    v.remember("built a $type")
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
            "huddle" -> {
                v.happiness = min(100f, v.happiness + 2f)
                finish(v)
            }
            "tell_story" -> finish(v)
            else -> finish(v)
        }
    }

    // ---- brains ----

    private fun alert() = time < alertUntil
    fun raining() = time < rainUntil
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

    private fun lineFor(v: Villager): String {
        if (alert() && rnd.nextBoolean()) {
            return listOf("Did you hear those wolves?", "Stay close to the fire.", "Something is out there...")[rnd.nextInt(3)]
        }
        if (raining() && rnd.nextInt(3) == 0) {
            return listOf("This rain is relentless.", "Good weather for the farms, at least.", "My boots are soaked.")[rnd.nextInt(3)]
        }
        val pool = if (rnd.nextBoolean()) (JOB_LINES[v.job] ?: v.persona.lines) else v.persona.lines
        return pool[rnd.nextInt(pool.size)]
    }

    private fun ruleDecision(v: Villager): Pair<String, String> {
        val others = villagers.filter { it !== v }
        val hasShore = nearestShore(v) >= 0
        if (v.hunger >= 60f && food > 0) return "eat" to ""
        if (v.energy < 25f || (isNight() && v.energy < 92f)) return "sleep" to ""
        if (alert() && v.job != "Scout") return "huddle" to ""
        if (festival()) {
            return if (rnd.nextInt(3) == 0 && others.isNotEmpty()) "talk" to (friendFor(v)?.name ?: "") else "huddle" to ""
        }
        if (food < 3) {
            return (if (v.job == "Farmer" && count("farm") > 0) "tend_farm" else if (hasShore && rnd.nextBoolean()) "fish" else "gather_food") to ""
        }
        val needHut = count("hut") * 2 < villagers.size + 2
        if (needHut && wood >= 6 && stone >= 2 && (v.job == "Builder" || rnd.nextInt(5) == 0)) return "build_hut" to ""
        if (count("farm") < 1 + villagers.size / 3 && wood >= 4 && (v.job == "Builder" || v.job == "Farmer" || rnd.nextInt(6) == 0)) return "build_farm" to ""
        if (stone >= 3 && count("campfire") < 2 && v.job != "Scout") return "build_campfire" to ""
        if (partOfDay() == "evening" && v.job == "Storyteller") return "tell_story" to ""
        if (partOfDay() == "evening" && others.isNotEmpty() && rnd.nextInt(3) == 0) return "talk" to (friendFor(v)?.name ?: "")
        val farms = count("farm") > 0
        val act = when (v.job) {
            "Woodcutter" -> pick("gather_wood" to 7, "talk" to 1, "wander" to 1, "gather_food" to (if (food < 8) 2 else 0))
            "Miner" -> pick("gather_stone" to (if (stone < 12) 7 else 2), "gather_wood" to (if (wood < 12) 3 else 1), "talk" to 1, "wander" to 1)
            "Forager" -> pick("gather_food" to 4, "fish" to (if (hasShore) 3 else 0), "talk" to 1, "wander" to 1)
            "Farmer" -> pick("tend_farm" to (if (farms) 6 else 0), "gather_food" to 2, "gather_wood" to (if (farms) 0 else 4), "talk" to 1)
            "Builder" -> pick("gather_wood" to 4, "gather_stone" to (if (stone < 4) 3 else 1), "talk" to 1, "wander" to 1)
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
        if (thought.isNotBlank()) v.thought = thought.take(100)
        if (action == "talk") v.talkMsg = say.ifBlank { lineFor(v) }.take(90)
        else if (say.isNotBlank()) {
            v.say = say.take(90)
            v.sayLeft = 7f
            addLog("${v.name}: ${say.take(90)}")
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

    private fun buildPrompt(v: Villager): String {
        val sb = StringBuilder()
        sb.append("Day ${day()}, ${partOfDay()}.")
        if (raining()) sb.append(" It is raining.")
        if (alert()) sb.append(" WOLVES are howling nearby, the village is on alert.")
        if (festival()) sb.append(" The village is holding a feast by the fire.")
        sb.append("\n")
        sb.append("You are ${v.name}, the village ${v.job}. ${JOB_BLURB[v.job] ?: ""}\n")
        sb.append("Personality: ${v.trait}. You ${v.persona.voice}. Your dream: ${v.persona.dream}.\n")
        sb.append("Mood: ${mood(v)}. Hunger ${v.hunger.toInt()}/100 (eat above 60). Energy ${v.energy.toInt()}/100 (sleep when low or at night).\n")
        sb.append("Village stock: wood $wood, stone $stone, food $food. ")
        sb.append("Buildings: ${count("hut")} huts, ${count("farm")} farms, ${count("campfire")} campfires. ")
        sb.append("${villagers.size} villagers.\n")
        val others = villagers.filter { it !== v }
        if (others.isNotEmpty()) {
            sb.append("Others: " + others.joinToString(", ") {
                "${it.name} the ${it.job} (${if (it.action.isEmpty()) "idle" else it.action.replace('_', ' ')})"
            } + "\n")
            val close = others.filter { (v.affinity[it.id] ?: 0) >= 2 }.joinToString(", ") { it.name }
            val rivals = others.filter { (v.affinity[it.id] ?: 0) <= -2 }.joinToString(", ") { it.name }
            if (close.isNotEmpty()) sb.append("Your friends: $close.\n")
            if (rivals.isNotEmpty()) sb.append("You don't get along with: $rivals.\n")
        }
        if (v.memories.isNotEmpty()) sb.append("Your recent memories: " + v.memories.joinToString("; ") + "\n")
        if (v.heard.isNotEmpty()) sb.append("You recently heard: " + v.heard.joinToString(" | ") + "\n")
        if (v.recent.isNotEmpty()) sb.append("Your last actions: " + v.recent.joinToString(", ") + " (vary what you do).\n")
        val recent = log.takeLast(4)
        if (recent.isNotEmpty()) sb.append("Recent village news: " + recent.joinToString(" | ") + "\n")
        if (godMsg.isNotBlank() && time < godUntil) {
            sb.append("A booming voice from the sky said to everyone: \"$godMsg\" (consider it seriously).\n")
        }
        sb.append("What do you do next? Stay in character.")
        return sb.toString()
    }

    private fun send(v: Villager) {
        val b = brainProvider() ?: return
        val prompt = buildPrompt(v)
        v.waiting = true
        inFlight = true
        brain = "thinking…"
        val id = v.id
        scope.launch(Dispatchers.IO) {
            try {
                results.add(Decision(id, b.ask(SYSTEM, prompt), null))
            } catch (e: Exception) {
                results.add(Decision(id, null, "${e.javaClass.simpleName}: ${e.message}"))
            }
        }
    }

    private fun drain() {
        while (true) {
            val r = results.poll() ?: break
            inFlight = false
            nextAskAt = time + (brainProvider()?.gapSeconds ?: 0.0)
            val v = villagers.firstOrNull { it.id == r.villagerId }
            if (r.error != null) {
                brain = "offline — using autopilot"
                retryAt = time + 45.0
                val now = System.currentTimeMillis()
                if (now - lastErrorLog > 120_000L) {
                    lastErrorLog = now
                    onError("Village model call failed: ${r.error}")
                }
                v?.waiting = false
                continue
            }
            try {
                val text = r.text.orEmpty()
                val a = text.indexOf('{')
                val b = text.lastIndexOf('}')
                require(a >= 0 && b > a) { "no JSON in: ${text.take(120)}" }
                val o = JSONObject(text.substring(a, b + 1))
                brain = (brainProvider()?.label ?: "model") + " online"
                if (v != null && v.phase == "idle") {
                    applyDecision(
                        v,
                        o.optString("action").trim().lowercase().replace(' ', '_'),
                        o.optString("target"),
                        o.optString("say"),
                        o.optString("thought"),
                    )
                } else if (v != null) {
                    v.waiting = false
                }
            } catch (e: Exception) {
                brain = "model answered oddly — using autopilot"
                v?.waiting = false
                val now = System.currentTimeMillis()
                if (now - lastErrorLog > 120_000L) {
                    lastErrorLog = now
                    onError("Village model reply not understood: ${e.message}")
                }
            }
        }
    }

    private fun schedule() {
        val b = brainProvider()
        val configured = b != null
        if (b == null) brain = "autopilot — set your local model in Settings"
        val idle = villagers.filter { it.phase == "idle" && it.idleFor >= 0.8f }.sortedByDescending { it.idleFor }
        for (v in idle) {
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
        nextEventAt = time + 150.0 + rnd.nextInt(150)
        when (rnd.nextInt(6)) {
            0 -> {
                for (i in tiles.indices) if (tiles[i] == T.BUSH) amt[i] = 3
                event("The bushes burst with ripe berries.")
            }
            1 -> {
                if (villagers.size < 12 && rnd.nextInt(3) == 0) {
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
                event("Wolves howl beyond the trees! Everyone stay near the fire.")
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

        bushClock += dt
        if (bushClock > 30f) {
            bushClock = 0f
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
            var interval = if (time < b.boostUntil) 12f else 40f
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

        if (time - lastBirth > 150.0 && villagers.size < 12 && food >= 5 &&
            count("hut") * 2 > villagers.size
        ) {
            lastBirth = time
            val n = spawnVillager()
            addLog("${n.name} the ${n.job.lowercase()} arrives and joins the village.")
            narrate("${n.name} joins the village")
        }

        eventTick()
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
            event("${v.name} the ${v.job.lowercase()} starved. The village mourns.")
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
                v.say = v.replyText.take(90)
                v.sayLeft = 6f
                v.replyText = ""
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
            o.put("nextId", nextId)
            o.put("tiles", String(CharArray(tiles.size) { i -> '0' + tiles[i] }))
            o.put("amt", String(CharArray(amt.size) { i -> '0' + min(9, amt[i]) }))
            val bs = JSONArray()
            buildings.forEach { bs.put(JSONObject().put("t", it.type).put("x", it.x).put("y", it.y)) }
            o.put("buildings", bs)
            val vs = JSONArray()
            villagers.forEach {
                vs.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("job", it.job).put("p", it.personaIdx)
                        .put("happy", it.happiness.toDouble()).put("x", it.x.toDouble()).put("y", it.y.toDouble())
                        .put("hunger", it.hunger.toDouble()).put("energy", it.energy.toDouble())
                        .put("hp", it.hp.toDouble())
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
                addBuilding(b.getString("t"), b.getInt("x"), b.getInt("y"))
            }
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
                villagers.add(v)
            }
            val lg = o.optJSONArray("log")
            if (lg != null) for (i in 0 until lg.length()) log.add(lg.getString(i))
            rnd = Random(seed xor time.toLong())
            villagers.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val SYSTEM = """You play one villager in a small pixel-art village, as a distinct character with a job, a personality, a way of speaking and a dream. Reply with ONLY a JSON object, no other text:
{"thought": "<your private thought, first person, under 14 words, in your voice>", "action": "<one action>", "target": "<villager name, only for talk>", "say": "<one short sentence you say aloud in your own voice, or empty>"}
Actions: gather_wood, gather_stone, gather_food (berries), fish (at the lake), tend_farm (needs a farm), explore (scouting far away), tell_story (at the fire, lifts everyone's spirits), huddle (stay by the fire), build_hut (6 wood + 2 stone), build_farm (4 wood), build_campfire (3 stone), eat (needs food in stock), sleep, talk (set target and say), wander.
Mostly do the work of your job, but react to your needs, the weather, wolves, news and what others say. Talk about what you have seen, feel or remember, with opinions and feelings. Never repeat a line or an action you just did, and vary your sentences. Stay alive: eat when hungry, sleep when tired or at night."""
    }
}
