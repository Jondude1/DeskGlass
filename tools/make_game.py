#!/usr/bin/env python3
"""Asks Claude to write a Godot 4 survivor-style game, builds it for the web, and prepares a release.

Environment: ANTHROPIC_API_KEY, GAME_MODEL, SAMPLE ("1" uses the bundled sample instead of Claude),
GODOT (path to the godot binary). Output goes to ./out (release assets) and ./out/tag.txt.
"""
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent
WORK = Path("work")
OUT = Path("out")
GODOT = os.environ.get("GODOT", "godot")
ENGINE = "4.3"
MODEL = os.environ.get("GAME_MODEL") or "claude-sonnet-5-5"
MAX_TRIES = 4

PROJECT_GODOT = """config_version=5

[application]
config/name="{title}"
run/main_scene="res://main.tscn"
config/features=PackedStringArray("4.3", "GL Compatibility")

[display]
window/size/viewport_width=384
window/size/viewport_height=216
window/stretch/mode="viewport"
window/stretch/aspect="keep"

[rendering]
renderer/rendering_method="gl_compatibility"
renderer/rendering_method.mobile="gl_compatibility"
textures/canvas_textures/default_texture_filter=0
environment/defaults/default_clear_color=Color(0, 0, 0, 1)
2d/snap/snap_2d_transforms_to_pixel=true
"""

MAIN_TSCN = """[gd_scene load_steps=2 format=3]

[ext_resource type="Script" path="res://main.gd" id="1"]

[node name="Main" type="Node2D"]
script = ExtResource("1")
"""

EXPORT_PRESETS = """[preset.0]

name="Web"
platform="Web"
runnable=true
dedicated_server=false
custom_features=""
export_filter="all_resources"
include_filter=""
exclude_filter=""
export_path="export/index.html"
encryption_include_filters=""
encryption_exclude_filters=""
encrypt_pck=false
encrypt_directory=false

[preset.0.options]

custom_template/debug=""
custom_template/release=""
variant/extensions_support=false
variant/thread_support=false
vram_texture_compression/for_desktop=true
vram_texture_compression/for_mobile=false
html/export_icon=true
html/custom_html_shell=""
html/head_include=""
html/canvas_resize_policy=2
html/focus_canvas_on_start=true
html/experimental_virtual_keyboard=false
progressive_web_app/enabled=false
"""

SYSTEM = """You write small, finished games for Godot 4.3 in GDScript. They are exported to the web and run on a mid-range Android tablet.

THE GAME
A pixel-art survivor-style arena game in the spirit of Vampire Survivors, Brotato and Castlevania: a hero auto-attacks while the player only moves; swarms of enemies close in, wave after wave; defeated enemies drop gems that give XP; each level-up grants an upgrade (pick it automatically and flash its name briefly); a boss appears at around the three-minute mark; the run ends when the hero dies.
Every game needs its own theme, hero, enemies, weapon and one twist (for example haunted castle, deep sea, space junkyard, candy land, desert ruins, robot factory). Never repeat a theme from the list you are given.
Make it rich: at least 4 enemy types plus a boss, 3 or more weapon or upgrade kinds, a screen-shake or hit flash, a little particle burst on kills, a tiled background with detail.

PIXEL ART (all generated in code, no external files)
The project is 384x216 with viewport stretch and nearest filtering. Build every sprite from small pixel grids (strings, about 8x8 to 16x16, with a colour palette) turned into ImageTexture with Image.create(w, h, false, Image.FORMAT_RGBA8), set_pixel and ImageTexture.create_from_image. Give walking sprites 2 frames. The hero, every enemy type, the boss, projectiles, gems and pickups must be recognisable from each other at a glance. Use a limited, attractive palette. Keep the HUD tiny (a thin XP bar, a timer, the level) and draw it with draw_string and ThemeDB.fallback_font.

FILES
The project is generated around your files. project.godot, export presets and main.tscn already exist: the main scene is a Node2D with res://main.gd attached. You write main.gd (required, extends Node2D) and optionally more .gd files (for example classes for sprites or enemies, loaded with preload("res://name.gd")). Create all nodes, drawing and input handling from code. Draw with _draw() and queue_redraw(), or with Sprite2D nodes you create. Use no scenes other than main.tscn and no assets other than code-generated textures. No audio.

GODOT 4.3 RULES (the code is compiled and run headless before it is accepted)
- GDScript 4 only: use await, not yield; @export, not export; Vector2i vs Vector2 are different; randf_range, randi_range, clampf, maxf, minf, TAU; Array.filter takes a Callable; use typed declarations when you can.
- Do not use class_name for scripts that you preload. Avoid name clashes with built-in classes.
- Make sure every variable is declared before use and every called function exists. Avoid shadowing warnings treated as errors: do not name variables like built-in functions.
- Keep performance reasonable: at most about 120 live enemies, avoid allocating inside tight loops where easy, use simple distance checks (no physics engine needed).
- The game must also run headless without a window: never depend on a display, and guard anything web-only with OS.has_feature("web").

THE CONTRACT WITH THE APP (follow exactly)
Include these three helper functions verbatim in main.gd (the sample below shows them). They talk to the tablet app through the web page:
  _dg_mode() returns "bot" or "human" (the app sets it; when not on the web it returns "bot").
  _dg_narrate(text) shows a faint subtitle. _dg_ended(score, seconds) reports the end of a run.
They need a float variable named t that holds seconds since the run started, and the narrate helper uses a variable named last_say (float, initial -10.0).
1. HUMAN mode: drag anywhere = virtual joystick (handle InputEventMouseButton and InputEventMouseMotion: touch is delivered as mouse events; positions are already in the 384x216 viewport space), also arrow keys / WASD (Input.get_vector with ui_left, ui_right, ui_up, ui_down). Draw a faint stick while dragging. On death show GAME OVER and the score; a tap restarts after a second.
2. BOT mode: the game plays itself with no input. Write a function that picks the move direction. It must feel human, not robotic:
   - It decides only every 0.2 to 0.35 seconds (reaction time) and holds the direction in between.
   - It mostly kites: moves away from the densest cluster of enemies, strafes, slips through gaps, collects gems when it is safe, and drifts toward gems when calm.
   - It makes believable mistakes: about one decision in twelve it ignores danger or heads for a far gem, it sometimes gets greedy for a gem and takes a hit, it can get cornered against the arena edge, it sometimes hesitates, and its movement has a little wobble.
   - It is a decent player but not perfect: most runs should last between 1.5 and 5 minutes and it dies sometimes.
   - When the hero dies, show GAME OVER for about 5 seconds, call _dg_ended(score, t), then start a fresh run automatically.
3. NARRATION (bot mode only): call _dg_narrate(text) when something notable happens (the helper already limits it to one every 4 seconds). Text is 2 to 7 words, lower case, present tense, in the game's own terms, such as "kiting toward the bats", "grabbing a gem under fire", "cornered by skeletons", "level up: holy water", "the boss arrives".

OUTPUT FORMAT (exactly, nothing before the first marker, no markdown fences)
=== FILE: meta.txt ===
title: <the game's name, under 40 characters>
blurb: <one short sentence describing the game>
=== FILE: main.gd ===
<the code>
=== FILE: other.gd ===   (only if you want more files)
<the code>

Here is a tiny working example of main.gd, showing the helper functions and the structure. Your game must be far richer than this:

"""


def post_stream(messages, max_tokens=32000):
    body = json.dumps({
        "model": MODEL,
        "max_tokens": max_tokens,
        "system": SYSTEM + (ROOT / "sample" / "main.gd").read_text(),
        "messages": messages,
        "stream": True,
    }).encode()
    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=body,
        headers={
            "x-api-key": os.environ["ANTHROPIC_API_KEY"],
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        },
    )
    text = []
    stop = ""
    try:
        resp = urllib.request.urlopen(req, timeout=600)
    except urllib.error.HTTPError as e:
        raise SystemExit("Anthropic API error {}: {}".format(e.code, e.read().decode()[:1500]))
    with resp:
        for raw in resp:
            line = raw.decode("utf-8", "replace").strip()
            if not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if not data:
                continue
            ev = json.loads(data)
            kind = ev.get("type")
            if kind == "content_block_delta" and ev["delta"].get("type") == "text_delta":
                text.append(ev["delta"]["text"])
            elif kind == "message_delta":
                stop = ev.get("delta", {}).get("stop_reason") or stop
            elif kind == "error":
                raise SystemExit("Stream error: " + json.dumps(ev)[:800])
    return "".join(text), stop


def parse_files(answer):
    parts = re.split(r"^=== FILE: (.+?) ===\s*$", answer, flags=re.M)
    files = {}
    for i in range(1, len(parts) - 1, 2):
        name = parts[i].strip()
        body = parts[i + 1].strip("\n")
        body = re.sub(r"^```[a-z]*\n", "", body)
        body = re.sub(r"\n```\s*$", "", body)
        if "/" in name or ".." in name or not re.match(r"^[A-Za-z0-9_.-]+$", name):
            continue
        files[name] = body + "\n"
    return files


def meta_of(files):
    title, blurb = "Untitled game", ""
    for line in files.get("meta.txt", "").splitlines():
        if line.lower().startswith("title:"):
            title = line.split(":", 1)[1].strip()
        elif line.lower().startswith("blurb:"):
            blurb = line.split(":", 1)[1].strip()
    return title[:60], blurb[:200]


def run(cmd, timeout):
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


ERROR_LINE = re.compile(r"(SCRIPT ERROR|Parse Error|Failed to load script|Compile Error|Invalid (call|access|assignment|operands)|Cannot (call|infer|assign)|\.gd:\d+)")


def problems(output):
    return "\n".join(l for l in output.splitlines() if ERROR_LINE.search(l))[:3500]


def build(files, title):
    shutil.rmtree(WORK, ignore_errors=True)
    proj = WORK / "proj"
    proj.mkdir(parents=True)
    (proj / "project.godot").write_text(PROJECT_GODOT.format(title=title.replace('"', "")))
    (proj / "main.tscn").write_text(MAIN_TSCN)
    (proj / "export_presets.cfg").write_text(EXPORT_PRESETS)
    for name, body in files.items():
        if name.endswith(".gd"):
            (proj / name).write_text(body)
    if not (proj / "main.gd").exists():
        return "main.gd is missing."

    rc, out = run([GODOT, "--headless", "--path", str(proj), "--import"], 300)
    print(out[-2000:])
    bad = problems(out)
    if bad:
        return bad

    # Play it headless for two game minutes to catch runtime errors.
    try:
        rc, out = run([GODOT, "--headless", "--path", str(proj), "--fixed-fps", "60", "--quit-after", "7200"], 240)
    except subprocess.TimeoutExpired as e:
        out = (e.stdout or b"").decode() if isinstance(e.stdout, bytes) else (e.stdout or "")
    print(out[-2000:])
    bad = problems(out)
    if bad:
        return bad

    (proj / "export").mkdir()
    rc, out = run([GODOT, "--headless", "--path", str(proj), "--export-release", "Web", "export/index.html"], 600)
    print(out[-2000:])
    if not (proj / "export" / "index.pck").exists():
        return "Web export failed:\n" + out[-1500:]
    return None


def main():
    previous = os.environ.get("PREVIOUS_TITLES", "")
    sample = os.environ.get("SAMPLE") == "1"
    if sample:
        files = {"main.gd": (ROOT / "sample" / "main.gd").read_text(),
                 "meta.txt": (ROOT / "sample" / "meta.txt").read_text()}
        title, _ = meta_of(files)
        err = build(files, title)
        if err:
            raise SystemExit("Sample failed to build:\n" + err)
    else:
        ask = "Write today's game."
        if previous.strip():
            ask += " Games already made (make something clearly different in theme, hero and enemies): " + previous.strip() + "."
        messages = [{"role": "user", "content": ask}]
        files, err = {}, "never ran"
        for attempt in range(1, MAX_TRIES + 1):
            print("--- Claude attempt", attempt)
            answer, stop = post_stream(messages)
            print("answer characters:", len(answer), "stop:", stop)
            files = parse_files(answer)
            if "main.gd" not in files:
                err = "No main.gd was found in your answer (stop reason: {}). Use the exact output format.".format(stop)
            else:
                title, _ = meta_of(files)
                err = build(files, title)
            if not err:
                break
            print("--- problems:\n" + err)
            messages += [
                {"role": "assistant", "content": answer},
                {"role": "user", "content": "Godot reported these problems when compiling, running and exporting the game:\n\n" + err +
                 "\n\nFix them and send ALL files again in the same output format."},
            ]
        if err:
            raise SystemExit("Could not produce a working game:\n" + err)

    title, blurb = meta_of(files)
    OUT.mkdir(exist_ok=True)
    export = WORK / "proj" / "export"
    engine_name = "engine-{}.wasm".format(ENGINE)
    assets = []
    for f in sorted(export.iterdir()):
        target = engine_name if f.name == "index.wasm" else f.name
        shutil.copy(f, OUT / target)
        assets.append(target)
    meta = {"title": title, "blurb": blurb, "engine": ENGINE, "engineFile": engine_name,
            "files": assets, "model": "sample" if sample else MODEL, "made": int(time.time())}
    (OUT / "meta.json").write_text(json.dumps(meta))
    tag = "game-" + time.strftime("%Y%m%d-%H%M%S", time.gmtime())
    (OUT / "tag.txt").write_text(tag)
    (OUT / "title.txt").write_text(title)
    (OUT / "blurb.txt").write_text(blurb)
    print("Built", title, tag, assets)


if __name__ == "__main__":
    main()
