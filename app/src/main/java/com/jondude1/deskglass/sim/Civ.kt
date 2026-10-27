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

class Building(val type: String, val x: Int, val y: Int)

class Villager(val id: Int, val name: String, val trait: String, val colorIdx: Int) {
    var x = 0f
    var y = 0f
    var hunger = 20f
    var energy = 90f
    var hp = 100f
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
    val heard = ArrayList<String>()
}

/** Whatever answers the villagers' questions: the local model, or Claude through the saved key. */
class Brain(val label: String, val gapSeconds: Double, val ask: (system: String, user: String) -> String)

private class Decision(val villagerId: Int, val text: String?, val error: String?)

private val NAMES = listOf(
    "Mira", "Tobin", "Edda", "Rook", "Wren", "Bram", "Isla", "Finn", "Odette", "Cass", "Hugo", "Nell",
    "Pike", "Sorrel", "Dov", "Lark", "Ember", "Quill", "Tamsin", "Gideon", "Juno", "Alder", "Pip", "Vesna",
)
private val TRAITS = listOf(
    "a cautious planner who hates running out of food",
    "a restless explorer who gets bored easily",
    "a cheerful chatterbox who loves company",
    "a stubborn builder proud of every hut",
    "a dreamy poet who watches the sky",
    "a practical worker who says little",
    "a nervous worrier who stockpiles things",
    "a bold leader who bosses others around",
    "a kind caretaker who watches over the others",
    "a grumpy skeptic who doubts every plan",
)
private val SMALL_TALK = listOf(
    "Fine day for work.", "I could eat a whole bush of berries.", "Have you seen the tall trees to the east?",
    "We should build more huts.", "My feet hurt.", "Do you think the lake has fish?", "Let's not run out of wood.",
)
private val ACTIONS = setOf(
    "gather_wood", "gather_stone", "gather_food", "build_hut", "build_farm", "build_campfire",
    "eat", "sleep", "talk", "wander",
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
    var stone = 0
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
        stone = 0
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
        val v = Villager(nextId++, name, TRAITS[rnd.nextInt(TRAITS.size)], rnd.nextInt(8))
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
                    "build_hut" -> wood >= 6
                    "build_farm" -> wood >= 4
                    else -> stone >= 3
                }
                if (!ok) return false
                val site = findSite()
                if (site < 0) return false
                if (!go(v, action, site)) return false
                when (action) {
                    "build_hut" -> wood -= 6
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
                    ?: return false
                v.talkTo = other
                if (v.talkMsg.isBlank()) v.talkMsg = SMALL_TALK[rnd.nextInt(SMALL_TALK.size)]
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
            v.action == "talk" -> {
                val o = v.talkTo
                if (o != null) {
                    v.say = v.talkMsg.take(90)
                    v.sayLeft = 7f
                    o.heard.add("${v.name}: ${v.talkMsg}")
                    while (o.heard.size > 3) o.heard.removeAt(0)
                    addLog("${v.name} to ${o.name}: ${v.talkMsg}")
                    narrate("${v.name} to ${o.name}: ${v.talkMsg}")
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
                if (v.gathered >= 4 || i < 0 || amt[i] <= 0) finish(v) else v.workLeft = 3f
            }
            "build_hut", "build_farm", "build_campfire" -> {
                val i = v.targetTile
                reserved.remove(i)
                val type = v.action.removePrefix("build_")
                if (i >= 0 && !occupied[i] && tiles[i] == T.GRASS) {
                    addBuilding(type, i % GW, i / GW)
                    addLog("${v.name} built a $type.")
                    narrate("${v.name} finishes a $type")
                }
                finish(v)
            }
            "eat" -> {
                if (food > 0) {
                    food--
                    v.hunger = max(0f, v.hunger - 45f)
                }
                finish(v)
            }
            else -> finish(v)
        }
    }

    // ---- brains ----

    private fun ruleDecision(v: Villager): Pair<String, String> {
        val others = villagers.filter { it !== v }
        if (v.hunger >= 60f && food > 0) return "eat" to ""
        if (v.energy < 25f || (isNight() && v.energy < 92f)) return "sleep" to ""
        if (food < 4 + villagers.size / 2) return "gather_food" to ""
        if (count("hut") * 2 < villagers.size + 2 && wood >= 6) return "build_hut" to ""
        if (count("farm") < 1 + villagers.size / 3 && wood >= 4) return "build_farm" to ""
        if (wood < 12) return "gather_wood" to ""
        if (stone < 3 && count("campfire") < 2) return "gather_stone" to ""
        if (stone >= 3 && count("campfire") < 2) return "build_campfire" to ""
        val roll = rnd.nextInt(5)
        if (roll == 0 && others.isNotEmpty()) return "talk" to others[rnd.nextInt(others.size)].name
        if (roll == 1) return "gather_food" to ""
        if (roll == 2) return "wander" to ""
        return "gather_wood" to ""
    }

    private fun applyDecision(v: Villager, action: String, target: String, say: String, thought: String) {
        v.waiting = false
        if (thought.isNotBlank()) v.thought = thought.take(100)
        if (action == "talk") v.talkMsg = say.ifBlank { SMALL_TALK[rnd.nextInt(SMALL_TALK.size)] }.take(90)
        else if (say.isNotBlank()) {
            v.say = say.take(90)
            v.sayLeft = 7f
            addLog("${v.name}: ${say.take(90)}")
        }
        var ok = action in ACTIONS && startAction(v, action, target)
        if (!ok) {
            val (a, t) = ruleDecision(v)
            ok = startAction(v, a, t)
            if (!ok) ok = startAction(v, "wander", "")
        }
        if (!ok) {
            v.idleFor = -3f
        } else if (v.thought.isNotBlank()) {
            narrate("${v.name}: ${v.thought}")
        }
    }

    private fun buildPrompt(v: Villager): String {
        val sb = StringBuilder()
        sb.append("Day ${day()}, ${partOfDay()}.\n")
        sb.append("You are ${v.name}, ${v.trait}.\n")
        sb.append("Hunger ${v.hunger.toInt()}/100 (eat when above 60). Energy ${v.energy.toInt()}/100 (sleep when low or at night).\n")
        sb.append("Village stock: wood $wood, stone $stone, food $food. ")
        sb.append("Buildings: ${count("hut")} huts, ${count("farm")} farms, ${count("campfire")} campfires. ")
        sb.append("${villagers.size} villagers.\n")
        val others = villagers.filter { it !== v }
        if (others.isNotEmpty()) {
            sb.append("Others: " + others.joinToString(", ") {
                "${it.name} (${if (it.action.isEmpty()) "idle" else it.action.replace('_', ' ')})"
            } + "\n")
        }
        if (v.heard.isNotEmpty()) sb.append("You recently heard: " + v.heard.joinToString(" | ") + "\n")
        val recent = log.takeLast(4)
        if (recent.isNotEmpty()) sb.append("Recent news: " + recent.joinToString(" | ") + "\n")
        if (godMsg.isNotBlank() && time < godUntil) {
            sb.append("A booming voice from the sky said to everyone: \"$godMsg\" (consider it seriously).\n")
        }
        sb.append("What do you do next?")
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
        if (b == null) brain = "autopilot — add a model or API key in Settings"
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

        farmClock += dt
        if (farmClock > 20f) {
            farmClock = 0f
            if (villagers.isNotEmpty() && food < 40) food += count("farm")
        }

        if (time - lastBirth > 150.0 && villagers.size < 12 && food >= 5 &&
            count("hut") * 2 > villagers.size
        ) {
            lastBirth = time
            val n = spawnVillager()
            addLog("${n.name} arrives and joins the village.")
            narrate("${n.name} joins the village")
        }

        schedule()
    }

    private fun update(v: Villager, dt: Float) {
        val asleep = v.phase == "sleeping"
        v.hunger = min(100f, v.hunger + (if (asleep) 0.12f else 0.33f) * dt)
        if (!asleep) v.energy = max(0f, v.energy - 0.2f * dt)
        if (v.hunger >= 100f) v.hp -= 1.2f * dt else if (v.hunger < 70f && v.hp < 100f) v.hp = min(100f, v.hp + 0.4f * dt)
        if (v.hp <= 0f) {
            villagers.remove(v)
            addLog("${v.name} starved. The village mourns.")
            narrate("${v.name} has died of hunger")
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
                    JSONObject().put("id", it.id).put("name", it.name).put("trait", it.trait)
                        .put("c", it.colorIdx).put("x", it.x.toDouble()).put("y", it.y.toDouble())
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
                val v = Villager(j.getInt("id"), j.getString("name"), j.getString("trait"), j.getInt("c"))
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
        private const val SYSTEM = """You control one villager in a small pixel-art village that is trying to survive and grow. Think and act like that character. Reply with ONLY a JSON object, no other text:
{"thought": "<your private thought, first person, under 12 words>", "action": "<one action>", "target": "<villager name, only for talk>", "say": "<a short sentence you say aloud, or empty>"}
Actions: gather_wood, gather_stone, gather_food (berries), build_hut (costs 6 wood), build_farm (costs 4 wood, makes food over time), build_campfire (costs 3 stone), eat (needs food in stock), sleep, talk (set target and say), wander.
Stay alive: eat when hungry, sleep when tired or at night. The village needs food, wood for huts, and huts so newcomers can join. Coordinate with others. Do not repeat the same thing forever."""
    }
}
