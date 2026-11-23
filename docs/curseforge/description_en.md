# Forestry Sable Compat

**Sable** turns a building into a moving physics structure. **Forestry: Community Edition** machines do not survive the trip: their inventories duplicate or vanish, multiblock controllers lose everything, and bee houses stay stuck in the biome they were assembled in. This mod fixes all three — server-side, no config, no new blocks or items.

| | |
|---|---|
| Minecraft | 1.21.1 |
| Loader | NeoForge 21.1.230+ |
| Requires | Forestry: Community Edition 3.0.0-alpha9+ · Sable 2.0.x |
| Side | Server-side (single-player works out of the box) |
| License | LGPL-3.0 |

Safe to drop into an existing world and safe to remove again: the mod is pure mixin patching with no saved data of its own.

## The problems it fixes

### 1. Contents are duplicated — or lost
Before Sable moves a block into a sub-level it saves the block entity's NBT, asks the block entity to empty itself (`Clearable.tryClear`), and only then removes the block from the world. Forestry's machines expose `Clearable` through `Container`, but their implementation is an empty method — so the removal step drops whatever the machine holds (and its circuit boards), while the NBT saved a moment earlier already carries the same items into the sub-level. The result is one inventory on the ground and one inside the moved machine. De-physicalizing runs the same code path, so the duplication works in both directions.

Circuit boards are a second, separate inventory that no `Container` view exposes, and are dropped by Forestry's own cleanup — which is why generic fixes miss them.

### 2. Multiblock contents vanish
In an Alveary or Managed Farm the inventory does not live on the member blocks; it lives in a `MultiblockController` hosted by the lowest member and travels as a payload. Sable's plot shares the same `Level` and multiblock index as the world, and the copied NBT still points at the world-side anchor — so when the sub-level version validates itself it finds the very controller being moved away, reuses it, and discards the payload it had just read. The structure arrives empty.

### 3. Bee housing never leaves its assembly biome
Sable stores sub-level blocks in a plot far from the world and stamps the anchor biome into the plot chunk at assembly time. Forestry asks the level at the block's plot position, so an apiary, bee house, alveary or hive keeps reporting its assembly biome after the structure drives, rotates or teleports elsewhere — wrong temperature, wrong humidity, wrong bee behaviour.

## What the mod does

- **Empties machines at the right moment.** Forestry block entities now really clear their inventory, their circuit board sockets, and (for multiblocks) the shared controller inventory when Sable asks them to. The contents survive in the NBT Sable copies, and nothing is left behind to drop. Idempotent and side-effect free.
- **Rebuilds multiblock controllers on the sub-level side.** The anchor position is stripped from the *copied* tag only, so the plot-side structure re-validates itself and seeds a fresh controller from its own payload instead of hijacking the one being moved. The world-side block entity is untouched.
- **Reads the biome where the structure actually is.** Bee housing and hive biome lookups — including the temperature/humidity cache and the climate API used by add-ons — project the plot position to world coordinates through Sable before asking for a biome. Structures that span a biome border give each housing its own correct climate; blocks outside a sub-level behave exactly as before.
- **Also fixes `/setblock ... replace`** on Forestry machines, which had the same "contents not cleared" deviation.

No new blocks, items, configs or commands; no per-tick overhead beyond the biome lookups Forestry already performs.

## Why not an existing fix?

The generic fixes do not reach these cases:

- **Sable Assembly Fix** patches block entities that do *not* implement `Clearable`. Forestry does implement it — with an empty body — so it is skipped.
- **Sable Fix Mod** clears `Container` / `IItemHandler`, which covers the main inventory but cannot see Forestry's circuit board sockets or the multiblock controller payload.

This mod uses the same `Clearable` extension point Sable itself uses for vanilla block entities, then adds the multiblock anchor fix. It works together with Sable Fix Mod (tested with 1.1.0 installed) and does not require it.

## Verified

Six in-game GameTests run against real blocks on a dedicated test server:

| Scenario | No fix | Sable Fix Mod 1.1.0 | **This mod** |
|---|---|---|---|
| Centrifuge inventory survives physicalization | ❌ drops 5 diamonds | ✅ | ✅ |
| Circuit board survives physicalization | ❌ drops the board | ❌ still drops | ✅ |
| Physicalize → move back round trip | ❌ | ✅ | ✅ |
| 3×3×3 Alveary controller contents | ❌ lost in sub-level | ❌ | ✅ |
| Alveary round trip | ❌ | ❌ | ✅ |
| Apiary biome follows the structure | ❌ assembly biome | ❌ | ✅ |

## Requirements

- **Minecraft 1.21.1**
- **NeoForge 21.1.230 or newer**
- **Forestry: Community Edition 3.0.0-alpha9 or newer** (plus its own dependency, Patchouli)
- **Sable 2.0.x** (tested with 2.0.6)

Install on the **server**; clients do not need it, and installing it on both sides is harmless.

## Notes and limitations

- Built against Sable 2.0.x. It hooks Sable's assembly routine and deliberately fails loudly rather than silently doing nothing if that routine changes shape, so a Sable update that breaks it is obvious instead of corrupting inventories.
- Managed Farm uses the same code paths as the Alveary but has no dedicated GameTest yet.
- Only Forestry's bee housing, hive and climate lookups are biome-corrected; other biome-dependent Forestry logic (worldgen, trees, butterflies) is untouched.
- Sable's plot chunk palette (grass/water tint) still shows the assembly biome even though the housing now reports the real one.
- Weather and sky visibility still come from Sable's host weather / plot sky light, so `isRaining()` and `canBlockSeeTheSky()` are not yet position-corrected.
- Does not retroactively repair a structure that was already duplicated before the mod was installed.

## License

LGPL-3.0, matching Forestry: Community Edition. Source code and issue tracker: **{{REPO_URL}}**
