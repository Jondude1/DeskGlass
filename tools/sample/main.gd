extends Node2D
# A tiny reference game. It shows the DeskGlass contract (mode, narration, game over)
# and a simple survivor loop. Real games should be far richer than this.

const W := 384
const H := 216

var mode := "bot"
var hero := Vector2(W / 2.0, H / 2.0)
var hero_hp := 5
var hurt_cd := 0.0
var enemies: Array = []   # {"p": Vector2, "hp": int}
var shots: Array = []     # {"p": Vector2, "v": Vector2, "t": float}
var gems: Array = []      # Vector2
var xp := 0
var level := 1
var score := 0
var t := 0.0
var shoot_cd := 0.0
var spawn_cd := 0.0
var think_cd := 0.0
var dir := Vector2.ZERO
var dead := false
var dead_t := 0.0
var last_say := -10.0
var dragging := false
var stick_origin := Vector2.ZERO
var stick_vec := Vector2.ZERO
var hero_tex: ImageTexture
var bat_tex: ImageTexture
var gem_tex: ImageTexture


# ---- DeskGlass contract helpers: keep these exactly ----
func _dg_mode() -> String:
	if OS.has_feature("web"):
		return str(JavaScriptBridge.eval("window.DG_MODE || 'bot'"))
	return "bot"

func _dg_narrate(text: String) -> void:
	if OS.has_feature("web") and t - last_say > 4.0:
		last_say = t
		JavaScriptBridge.eval("window.DeskGlass && DeskGlass.narrate(" + JSON.stringify(text) + ")")

func _dg_ended(final_score: int, seconds: float) -> void:
	if OS.has_feature("web"):
		JavaScriptBridge.eval("window.DeskGlass && DeskGlass.ended(%d, %d)" % [final_score, int(seconds)])
# ---- end helpers ----


func _make_tex(rows: Array, pal: Dictionary) -> ImageTexture:
	var h := rows.size()
	var w: int = rows[0].length()
	var img := Image.create(w, h, false, Image.FORMAT_RGBA8)
	for y in h:
		for x in w:
			var c: String = rows[y][x]
			if pal.has(c):
				img.set_pixel(x, y, pal[c])
	return ImageTexture.create_from_image(img)


func _ready() -> void:
	mode = _dg_mode()
	var pal := {
		"h": Color("e8c39e"), "c": Color("3b6fd1"), "d": Color("1d2b53"),
		"r": Color("c0392b"), "p": Color("7e57c2"), "k": Color("2b1b3d"),
		"y": Color("ffd54a"), "w": Color("ffffff"),
	}
	hero_tex = _make_tex([
		"..dddd..", ".dhhhhd.", ".dhwhwd.", "..hhhh..",
		".cccccc.", "cccccccc", ".cc..cc.", ".dd..dd.",
	], pal)
	bat_tex = _make_tex([
		"k......k", "kk.pp.kk", "kkpppp.k", ".kpwpwk.",
		"..pppp..", "...pp...", "........", "........",
	], pal)
	gem_tex = _make_tex([
		"...y....", "..yyy...", ".yywyy..", "..yyy...", "...y....",
	], pal)
	_reset()


func _reset() -> void:
	hero = Vector2(W / 2.0, H / 2.0)
	hero_hp = 5
	enemies.clear()
	shots.clear()
	gems.clear()
	xp = 0
	level = 1
	score = 0
	t = 0.0
	dead = false
	dead_t = 0.0
	spawn_cd = 0.5


func _input(event: InputEvent) -> void:
	if mode != "human":
		return
	if event is InputEventMouseButton:
		dragging = event.pressed
		stick_origin = event.position
		stick_vec = Vector2.ZERO
		if event.pressed and dead and dead_t > 1.0:
			_reset()
	elif event is InputEventMouseMotion and dragging:
		stick_vec = (event.position - stick_origin).limit_length(40.0) / 40.0


func _edge(v: float, m: float) -> float:
	return clampf((30.0 - v) / 30.0, 0.0, 1.0) - clampf((v - (m - 30.0)) / 30.0, 0.0, 1.0)


func _think() -> void:
	# A bot that is decent but human: slow reactions, wobble and the odd mistake.
	var push := Vector2.ZERO
	for e in enemies:
		var d: Vector2 = hero - e["p"]
		var l := d.length()
		if l < 70.0 and l > 0.1:
			push += d / (l * l) * 60.0
	push += Vector2(_edge(hero.x, W), _edge(hero.y, H)) * 0.8
	if push.length() < 0.15 and gems.size() > 0:
		var best: Vector2 = gems[0]
		for g in gems:
			if g.distance_to(hero) < best.distance_to(hero):
				best = g
		push = (best - hero).normalized() * 0.5
		_dg_narrate("collecting gems")
	elif push.length() >= 0.15:
		_dg_narrate("kiting the bats")
	if randf() < 0.08:
		push = Vector2.from_angle(randf() * TAU) * 0.6   # a human slip
	dir = push.limit_length(1.0) + Vector2.from_angle(randf() * TAU) * 0.08
	think_cd = randf_range(0.2, 0.35)


func _process(delta: float) -> void:
	if dead:
		dead_t += delta
		if mode == "bot" and dead_t > 5.0:
			_reset()
		queue_redraw()
		return
	t += delta
	hurt_cd = maxf(0.0, hurt_cd - delta)

	# movement
	if mode == "bot":
		think_cd -= delta
		if think_cd <= 0.0:
			_think()
	else:
		dir = stick_vec if dragging else Input.get_vector("ui_left", "ui_right", "ui_up", "ui_down")
	hero = (hero + dir * 70.0 * delta).clamp(Vector2(6, 6), Vector2(W - 6, H - 6))

	# spawn
	spawn_cd -= delta
	if spawn_cd <= 0.0:
		spawn_cd = maxf(0.25, 1.2 - t * 0.01)
		var a := randf() * TAU
		enemies.append({"p": hero + Vector2.from_angle(a) * 230.0, "hp": 1 + int(t / 60.0)})

	# enemies chase and hurt
	for e in enemies:
		e["p"] += (hero - e["p"]).normalized() * (26.0 + t * 0.1) * delta
		if hurt_cd <= 0.0 and e["p"].distance_to(hero) < 7.0:
			hero_hp -= 1
			hurt_cd = 0.8
	if hero_hp <= 0:
		dead = true
		_dg_ended(score, t)
		return

	# auto attack
	shoot_cd -= delta
	if shoot_cd <= 0.0 and enemies.size() > 0:
		var near: Dictionary = enemies[0]
		for e in enemies:
			if e["p"].distance_to(hero) < near["p"].distance_to(hero):
				near = e
		shots.append({"p": hero, "v": (near["p"] - hero).normalized() * 170.0, "t": 1.5})
		shoot_cd = maxf(0.15, 0.55 - level * 0.04)
	for s in shots:
		s["p"] += s["v"] * delta
		s["t"] -= delta
		for e in enemies:
			if e["hp"] > 0 and s["p"].distance_to(e["p"]) < 6.0:
				e["hp"] -= 1
				s["t"] = 0.0
				if e["hp"] <= 0:
					gems.append(e["p"])
					score += 10
				break
	shots = shots.filter(func(s): return s["t"] > 0.0)
	enemies = enemies.filter(func(e): return e["hp"] > 0)

	# gems
	for g in gems.duplicate():
		if g.distance_to(hero) < 9.0:
			gems.erase(g)
			xp += 1
			if xp >= level * 4:
				xp = 0
				level += 1
				_dg_narrate("level up: faster shots")
	queue_redraw()


func _draw() -> void:
	draw_rect(Rect2(0, 0, W, H), Color("1f2a1f"))
	for i in 40:
		var gx := (i * 97) % W
		var gy := (i * 53) % H
		draw_rect(Rect2(gx, gy, 2, 1), Color("2b3a2b"))
	for g in gems:
		draw_texture(gem_tex, g - Vector2(4, 3))
	for e in enemies:
		draw_texture(bat_tex, e["p"] - Vector2(4, 4))
	for s in shots:
		draw_rect(Rect2(s["p"] - Vector2(1, 1), Vector2(3, 3)), Color("fff3a0"))
	if not (hurt_cd > 0.0 and int(t * 20.0) % 2 == 0):
		draw_texture(hero_tex, hero - Vector2(4, 4))
	var font := ThemeDB.fallback_font
	draw_string(font, Vector2(4, 10), "LV %d   HP %d   %d" % [level, hero_hp, score], HORIZONTAL_ALIGNMENT_LEFT, -1, 8)
	if dead:
		draw_string(font, Vector2(0, H / 2.0), "GAME OVER", HORIZONTAL_ALIGNMENT_CENTER, W, 16)
