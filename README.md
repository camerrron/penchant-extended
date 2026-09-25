# penchant-extended

Fabric mod, Minecraft 1.21.1. Keeps one specific player's own enchant levels consistent
across every item they enchant, built on top of
[Penchant](https://modrinth.com/mod/penchant) (per-item enchant grinding).

Internal package/mod id is still `maxenchant` — the repo carries the project's public
name; the code wasn't renamed across every file for a purely cosmetic reason.

## What it does

Not "always max." Tracks each enchantment's **current level** for one player, independent
of any single item:

- Grind an enchant up on any item (Penchant's normal usage-based leveling) — the level
  reached is recorded against the *player*, not the item.
- Enchant a **different** item afterward — it comes out at that recorded level directly,
  not back at level 1, regardless of what the table's bookshelf-limited offer would
  normally roll.
- Survives the original item breaking, since the level lives on the player, not the tool.
- Costs extra XP to skip the grind this way, configurable per enchantment and level (see
  below) — Sharpness 3 costs more to instantly apply than Sharpness 1.

Confirmed working end-to-end on a local test server: seeded Sharpness 3 + Unbreaking 5
(max) via a debug command, then enchanted three separate items (two swords, one axe), all
three came out at the recorded levels, not level 1.

## Config

`config/maxenchant/config.json`, auto-created with empty defaults on first launch:

```json
{
  "applyCosts": {
    "minecraft:sharpness": { "1": 1, "2": 3, "3": 10, "4": 20, "5": 35 },
    "minecraft:unbreaking": { "1": 1, "2": 3, "3": 8 }
  },
  "grindCosts": {
    "minecraft:sharpness": { "2": 50, "3": 120 }
  }
}
```

- **`applyCosts`** — XP-level cost to instantly apply a recorded level to a new item via
  the carry-over mechanic. Unconfigured enchant/level pairs default to costing exactly the
  level number (Sharpness 3 costs 3 unless you say otherwise).
- **`grindCosts`** — overrides Penchant's own formula for how much progress is needed to
  grind an enchant *up* to a given level in the first place. Keyed by the level being
  reached. This is global (affects every player's grind for that enchant/level, not just
  the target player) — a balance knob, not a personal perk. Unconfigured pairs leave
  Penchant's original formula completely untouched.

Edit the file, then run `/maxenchant reload` in-game — no server restart needed.

### Debug commands

Op-only (permission level 2):

- `/maxenchant seed` — sets Sharpness 3 and Unbreaking to its max on yourself, for quick
  testing without grinding first.
- `/maxenchant set <enchantment_id> <level>` — set any enchantment to any level, e.g.
  `/maxenchant set sharpness 4`.
- `/maxenchant reload` — reload `config.json` without restarting.

## The target player

`MaxEnchantMod.TARGET_PLAYER` is a hardcoded UUID — this mod is currently built for one
specific player, not a general-purpose "everyone gets this" feature. Change that constant
(and rebuild) to point it at someone else.

## The real bugs this took to find

Everything here was verified against real decompiled bytecode — Penchant's own jar, the
actual vanilla server jar (via both Mojang's official mappings and Fabric Loader's
intermediary-remapped copy), and Fabric API's real class list — not guessed from docs or
assumed Mixin conventions. Three real, non-obvious bugs surfaced this way, each only caught
by actually running the thing, not by a clean build:

1. **`ScreenHandlerEvents` doesn't exist** in Fabric API 0.116.17 — hooks
   `PlayerEntity`/`ServerPlayerEntity` directly instead.
2. **Mixin method-target strings need Yarn's real name when one exists.** Copying the
   intermediary name straight out of a *compiled* mod's bytecode (what Mixin's own
   build-time remapping leaves behind) is not the same as what that mod's own source uses.
3. **The big one**: with `penchant:table_rework` enabled (true on both local dev and the
   real production server), the enchanting table isn't vanilla's `EnchantmentScreenHandler`
   at all. Penchant ships a fully independent `PenchantmentMenu` class, and the actual
   level-applying call is a hardcoded literal `1`, buried two lambdas deep in a private
   *static* synthetic method with no `this`. Every mixin originally written against
   vanilla's screen handler was structurally incapable of ever firing — confirmed by
   decompiling with lambda-inlining disabled (`cfr --decodelambdas false`) to see the real
   compiled structure, not the source-like reconstruction a normal decompile gives you.

Full detail in each mixin class's own doc comment.

## Toolchain

Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Yarn 1.21.1+build.3,
Penchant 0.3.7+mc1.21.1 — pinned to exactly what the real production server runs
(`gradle.properties`), not independently chosen.

## Building and testing locally

```
./gradlew build
```

Needs JDK 21. For a real integration test: a plain Fabric server (not Docker) with just
Fabric API + Penchant + this jar, `online-mode=true` so you connect with your real account
and get your real UUID — no offline-mode UUID math needed.

## Releases

No CI. The repo owner's self-hosted GitHub Actions runner is registered to a different
repo specifically (`minecraft-expanded-server`) — on a personal GitHub account, a
self-hosted runner is bound to the one repo it registered against, not shared across every
repo the account owns, and a runner-group workaround needs an organization account. Rather
than stand up a second always-on runner just for this, releases are built locally and
published by hand:

```
./gradlew build
git tag vX.Y.Z
git push origin vX.Y.Z
gh release create vX.Y.Z build/libs/maxenchant-X.Y.Z.jar --title "vX.Y.Z" --generate-notes
```

Bump `mod_version` in `gradle.properties` to match before tagging.
