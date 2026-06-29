# Cosmic Guard Heatmap

## Cosmic API

The client connects through the `cosmicapi:main` plugin channel with public client ID
`client_mqo12agnhaofbxsgdk` and registry mod ID `noobclient6-7`. It requests:

- `hooks.player.enchant_proc:read` for `player.enchant_proc`
- `server.guards:read` for `server.guards.snapshot.changed`

No backend app key or `csk_live_` secret is included in the mod. Players approve access
in game with `/api approve client_mqo12agnhaofbxsgdk`.

Client-side Fabric mod for Minecraft Java 1.21.11.

The mod automatically records stone and coal/iron/lapis/gold/diamond ore blocks beneath your feet using Cosmic Prisons' authoritative Guarded/Unguarded zone line. It intentionally ignores the unrelated Criminal Record status, including `Neutral`. Learned blocks persist across restarts and continuously update as you move.

## Commands

- `/guardheatmap`

The mathematical heatmap uses the highest-scoring model fitted against post-cutoff live Cosmic data with a 1-block safety net: 3D spherical radii of 28 blocks for Guards, 29 for Enforcers, and 25 for Wardens. Overlap priority is Warden, then Enforcer, then Guard. The overlay works on any solid floor block with air above it, not just stone or ores.

The mod loads its IGN whitelist from `https://pastebin.com/raw/MB3veEjr`. The raw Pastebin text should be comma-separated, such as `name1,name2,name3`. Non-whitelisted accounts cannot toggle or render the heatmap.

The heatmap recalculates every client tick for faster guard detection and faster guarded/unguarded updates.

Whitelisted players also get enchant-proc labels from Cosmic chat/game messages. Known enchants use rarity colors; unknown proc names still display in common white. A HUD fallback displays procs in first-person while the world label handles third-person/body visibility.

The bundled movable HUD overlay uses `=` to enter edit mode, then `\` to toggle the info/armor HUD visibility. Use `[` / `]` to resize the selected panel and mouse dragging to move panels.

For Enforcer LOS testing, forensic samples include entity yaw, pitch, head yaw, body yaw, and whether the player is behind the entity.

The heatmap now uses Cosmic's live sidebar state as a correction around your current block, so it cannot show green directly under you while the server reports `Guarded E/W/G`.
