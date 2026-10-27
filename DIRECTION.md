# DeskGlass direction (Jon's standing requirements)

Later rounds must follow these.

1. **WorldBox-style god-sim village.** Several races/kingdoms with coloured banners and drawn borders, cities that grow, war/alliance/trade/rebellion, multi-day world ages (Hope, Sun, Moon, Wonders, Dark, Tears, Chaos, Ice, Ash, Despair), and god powers (spawn, bless, rain, lightning, quake, meteor, plague). Build on what exists; never redo it.
2. **Better graphics every round.** Richer pixel art in `ui/CivArt.kt` (race sprites, per-faction buildings, animals, bridges, walls, ships, tiles, animated water/fire, particles, lighting) and smoother rendering. Gemini only if `GEMINI_API_KEY` already exists in the environment; never ask for a key, never commit one. No Godot conversion, no new setup for Jon, keep bundled assets small.
3. **UI polish every round**, on phones and tablets of all sizes (spacing, typography, glass effects, motion, tap feedback, empty states).
4. **Villager speech is grounded in real state.** Prompts carry only real facts (needs, stock, weather/season/time, nearby people and what they said, family, jobs, factions, events, chronicle, memories) and tell the model never to invent buildings, people, resources or events. Rule-based fallback lines are built from real facts too. Conversations are two-way: replies answer what was said and both sides remember it. Stories and gossip cite real chronicle/log events. Children speak simply.
5. **Dialogue must be robust.** Tolerant JSON repair, action validation, one retry with a shorter prompt, a request watchdog so a slow model never freezes the sim, near-duplicate suppression, length caps, think-tag/markdown stripping, a visible brain status, and a throttled Error log.
6. **Villagers use only the local model** (Ollama over Tailscale), with an offline rule fallback. No Claude-API brain for villagers.
7. **The app self-updates** from GitHub Releases. CI builds on every push to `main`; never leave `main` broken. Old saves must keep loading.
