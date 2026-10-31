# Blaze Rod Looting Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans and targeted RED/GREEN checks. Continue the authorized Nether portal work; do not commit or push.

**Goal:** Add `blaze-rod-looting-level=0`, integer 0–5, providing a virtual minimum player Looting level for vanilla blaze rod drops during RUNNING matches.

**Architecture:** On uncancelled player-caused blaze deaths, compare the configured minimum to the causing player's current main-hand Looting. If it is higher, replace only the ordinary vanilla blaze rod portion with the native distribution `nextInt(2) + round(nextFloat() * level)`. Use LOWEST so subsequent loot plugins retain control; preserve XP and other drops. No weapon mutation or entity/item spawning.

**Tech Stack:** Shared Paper Java; existing config/localization and JUnit; no client, protocol, SDK or dependency changes.

**Spec and constraints:** User requested 0–5 equivalent to Looting, analogous to ore fortune. 0 disables, default 0; max(existing,configured), no addition/downgrade. Preserve native player-credit and final damage-source conditions: only actual causing players (including projectiles), not environment or wolves borrowing an owner's equipment. Respect mob-drops gamerule and cancelled death. Higher existing Looting is left entirely native.

Ruling: Both versions' built-in blaze loot and enchantment functions were inspected. Public `LootContext.lootingModifier` is explicitly nonfunctional; no temporary weapon enchantment or whole-table reroll will be used. The formula preserves vanilla probability distribution, not its particular random sequence. Same-key datapack rewrites or indistinguishable ordinary rods from an earlier plugin cannot be fully identified through EntityDeathEvent; support is for the standard vanilla rod table, with other materials/metadata preserved.

Ruling: Do not claim a death-event loot-table key check protects custom tables: native `Mob.dropFromLootTable` clears the override before EntityDeathEvent. Only clearly non-vanilla ordinary-rod counts or multiple ordinary stacks can be skipped. Default native distribution and later plugin control are the supported boundary.

- [x] Add integer enum/default/bilingual name and validation/persistence tests.
- [x] Implement/test native distribution, max-level policy, ordinary-rod-only replacement, cancellation/gates and player/projectile attribution.
- [x] Register listener, document commands and scope, review both completed features.
- [x] Independently build both versions; verify deployment metadata/resources, unchanged Fabric/API and refresh dist/checksums. No full game test.

Final read-only review found no important errors. Both independent version builds passed; total 359 targeted checks, Paper 163 each, zero failure/error/skip. Four deployment JARs verified/copied to dist and ten SHA-256 entries refreshed. Fabric JARs and embedded API/SDK 1.0.0 classes are unchanged. New resources have 128 matching bilingual keys/placeholders. Report: `build/reports/portal-protection-blaze-looting-artifacts.json`. No game instance was run; compilation/event-model checks are not runtime gameplay verification.
