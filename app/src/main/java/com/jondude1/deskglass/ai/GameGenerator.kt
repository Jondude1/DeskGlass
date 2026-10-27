package com.jondude1.deskglass.ai

import com.jondude1.deskglass.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeneratedGame(val title: String, val blurb: String, val html: String)

/** Asks the smart model to write a whole small game plus the bot that plays it. */
class GameGenerator(private val prefs: Prefs) {

    suspend fun generate(previousTitles: List<String>): GeneratedGame = withContext(Dispatchers.IO) {
        val key = prefs.apiKey.trim()
        if (key.isEmpty()) throw IllegalStateException("Add your API key in Settings first.")
        val avoid = if (previousTitles.isEmpty()) "" else
            "\nGames already made (make something clearly different in theme, hero and enemies): " +
                previousTitles.takeLast(12).joinToString("; ") + "."
        val body = JSONObject()
            .put("model", prefs.chatModel.trim())
            .put("max_tokens", 24000)
            .put("system", SYSTEM)
            .put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("content", "Write today's game.$avoid")
                )
            )
        val res = ClaudeApi.stream(key, body)
        val raw = res.text
        val html = try {
            extractHtml(raw)
        } catch (e: IllegalArgumentException) {
            val head = raw.trim().replace("\n", " ").take(300)
            throw IllegalArgumentException(
                "No game in Claude's answer (${raw.length} characters, stop reason: ${res.stopReason.ifEmpty { "none" }}). " +
                    "It began: \"$head\""
            )
        }
        require(html.contains("DG_MODE") && html.contains("</script>", ignoreCase = true)) {
            "The game came back incomplete (stop reason: ${res.stopReason.ifEmpty { "none" }}, ${html.length} characters)."
        }
        val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
            ?.ifEmpty { null } ?: "Untitled game"
        val blurb = Regex("<meta\\s+name=\"description\"\\s+content=\"(.*?)\"", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.trim() ?: ""
        GeneratedGame(title.take(60), blurb.take(200), html)
    }

    private fun extractHtml(raw: String): String {
        val fence = Regex("```(?:html)?\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL).find(raw)
        val text = fence?.groupValues?.get(1) ?: raw
        val start = text.indexOf("<!DOCTYPE", ignoreCase = true).let { if (it >= 0) it else text.indexOf("<html", ignoreCase = true) }
        if (start >= 0) return text.substring(start).trim()
        // Some answers skip the doctype: accept anything that clearly is a page.
        val loose = listOf("<head", "<canvas", "<script", "<body").map { text.indexOf(it, ignoreCase = true) }
            .filter { it >= 0 }.minOrNull()
        require(loose != null) { "no html" }
        return "<!DOCTYPE html><html>" + text.substring(loose).trim() + if (text.contains("</html>", true)) "" else "</html>"
    }

    companion object {
        private val SYSTEM = """
You write small, finished browser games. Reply with ONE complete, self-contained HTML file inside a single ```html code block and nothing else.

THE GAME
A pixel-art survivor-style arena game in the spirit of Vampire Survivors, Brotato and Castlevania: a hero auto-attacks while the player only moves; swarms of enemies close in, wave after wave; defeated enemies drop gems that give XP; each level-up grants an upgrade (pick one automatically and flash its name briefly); a boss shows up around the three-minute mark; the run ends when the hero dies.
Every game needs its own theme, hero, enemies, weapon and one twist (for example haunted castle, deep-sea, space junkyard, candy land, desert ruins, robot factory). Never repeat a theme from the list you are given.

PIXEL ART
No external files, libraries, fonts or network requests. Draw everything with canvas code. Internal resolution 384x216, scaled to fill the window with CSS (image-rendering: pixelated; the canvas fills the viewport, keeping aspect ratio, black bars are fine) and ctx.imageSmoothingEnabled = false. Define sprites as small pixel grids (strings or arrays, about 12x12 to 16x16, with a palette) and pre-render them to offscreen canvases. Use 2-frame walk animation, a hit flash, simple shadows, and a tiled background with a little detail. The hero, each enemy type (at least 4, plus the boss), projectiles, gems and pickups must be instantly recognisable from each other at a glance. Use a limited, attractive palette. Keep the HUD tiny: a thin XP bar, a timer, a level number.

PERFORMANCE
Runs on a mid-range Android tablet inside a WebView. Fixed-timestep update, requestAnimationFrame render, at most about 120 live enemies, no per-frame allocations in hot loops where easy. Pause updates when document.hidden. No alert/prompt/confirm. No localStorage.

THE CONTRACT WITH THE APP (follow exactly)
1. The app runs this line before your script: window.DG_MODE = "bot" or "human". Read window.DG_MODE (your code must also contain the text DG_MODE) and treat anything other than "human" as "bot".
2. The app may provide window.DeskGlass with methods narrate(text) and ended(score, seconds). Always guard calls: if (window.DeskGlass) DeskGlass.narrate("...").
3. HUMAN mode: drag anywhere on the screen = virtual joystick (use pointer events, draw a faint stick while dragging); also WASD and arrow keys. When the hero dies show GAME OVER and the score, and tap restarts.
4. BOT mode: the game plays itself and no input is needed. Write a function that decides the movement direction. It must feel human, not robotic:
   - It decides only every 200 to 350 ms (reaction time), not every frame, and holds that direction in between.
   - It mostly kites: moves away from the densest cluster of enemies, strafes around them, slips through gaps, collects gems when it is safe, and drifts toward gems when it is calm.
   - It makes believable mistakes: about one decision in twelve it ignores danger or heads for a far gem, it sometimes gets greedy for a gem and takes a hit, it occasionally gets cornered against the arena edge, sometimes it hesitates for a moment, and its movement has a little wobble.
   - It is a decent player but not perfect: most runs should last between 1.5 and 5 minutes, and it dies sometimes.
   - When the hero dies, show GAME OVER for about 5 seconds, call DeskGlass.ended(score, secondsSurvived), then automatically start a fresh run.
5. NARRATION (bot mode only): call DeskGlass.narrate(text) when something notable happens, never more than once every 4 seconds. Text is 2 to 7 words, lower case, present tense, describing what the bot is doing in the game's own terms, such as "kiting toward the bats", "grabbing a gem under fire", "cornered by skeletons", "level up: holy water", "the boss arrives". Make it fit the game's theme.
6. The page must have <title> with the game's name and <meta name="description" content="one short sentence describing the game">.
7. Test your logic mentally for runtime errors: the file must run on first load without errors. Make the page body background black, margin 0, overflow hidden, and no scrollbars.
""".trimIndent()
    }
}
