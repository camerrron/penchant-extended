# penchant-extended

A Fabric mod (Minecraft 1.21.1) that keeps one specific player's own enchant levels
consistent across every item they enchant, built on top of
[Penchant](https://modrinth.com/mod/penchant) (per-item enchant grinding).

Internal mod id/package is still `maxenchant` - the repo carries the project's public name,
the code wasn't renamed (a rename across every package/file wasn't worth doing for a purely
cosmetic reason).

## What it does

Not "always max." Tracks each enchantment's **current level** for one player, independent
of any single item:

- Grind an enchant up on any item (Penchant's normal usage-based leveling) - the reached
  level is recorded against the *player*, not the item.
- Enchant a **different** item afterward - it comes out at that recorded level directly,
  not back at level 1, regardless of what the enchanting table's bookshelf-limited offer
  would normally roll.
- Survives the original item breaking, since the level lives on the player, not the tool.
- Costs extra XP to skip the grind this way, scaled to the gap between the recorded level
  and what would have normally rolled.

Confirmed working end-to-end on a local test server: seeded Sharpness 3 + Unbreaking 5 (max)
via a debug command, then enchanted three separate items (two swords, one axe) and all three
came out at the recorded levels, not level 1.

## The real bugs this took to find

Everything here was verified against real decompiled bytecode - Penchant's own jar, the
actual vanilla server jar (both obfuscated via Mojang's official mappings and
Fabric-Loader's intermediary-remapped copy), and Fabric API's real class list - not
guessed from docs or assumed Mixin conventions. Three real, non-obvious bugs surfaced this
way, each one only caught by actually running the thing:

1. **`ScreenHandlerEvents` doesn't exist** in Fabric API 0.116.17 - had to hook
   `PlayerEntity`/`ServerPlayerEntity` directly instead.
2. **Mixin method-target strings need Yarn's real name when one exists** - copying the
   intermediary name straight out of a *compiled* mod's bytecode (which is what Mixin's own
   build-time remapping leaves behind) is not the same as what that mod's own source uses.
3. **The big one**: with `penchant:table_rework` enabled (true on both this dev setup and
   the real production server), the enchanting table isn't vanilla's `EnchantmentScreenHandler`
   at all - Penchant ships a fully independent `PenchantmentMenu` class, and the actual
   level-applying call is a hardcoded literal `1`, buried two lambdas deep in a private
   *static* synthetic method with no `this` at all. Every one of the original mixins
   targeting vanilla's screen handler was structurally incapable of ever firing.

## Toolchain

Minecraft 1.21.1, Fabric Loader 0.19.5, Fabric API 0.116.17+1.21.1, Yarn 1.21.1+build.3,
Penchant 0.3.7+mc1.21.1 - all pinned to exactly what the real production server runs
(`gradle.properties`), not independently chosen.

## Building and testing locally

```
./gradlew build
```

Needs JDK 21. Test server setup: a plain Fabric server (not Docker) with just Fabric API +
Penchant + this jar, `online-mode=true` so you connect with your real account and get your
real UUID - no offline-mode UUID math needed. See `MaxEnchantMod.TARGET_PLAYER` for where
that UUID is set; there's no config for it yet (see below).

## In progress

Cost is currently a flat `recorded - rolled` XP deduction. Moving to:
- A per-enchantment, per-level config table for the apply-time cost (skip-the-grind tax).
- A config override for Penchant's own grind-to-level-up cost curve.

Neither is built yet as of this commit.
