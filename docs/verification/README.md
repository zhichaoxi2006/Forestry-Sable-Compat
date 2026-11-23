# Verification

`01`–`06` are the GameTest server logs behind the table in the top-level README. Each one is the
tail of `./gradlew runGameTestServer` with the configuration named in the file.

| File | Configuration | Result |
|---|---|---|
| `01-unpatched-sable.txt` | Sable 2.0.6 only, no fix | `inventorysurvivesphysicalization` and `socketsurvivesphysicalization` both fail: 5 diamonds and 1 circuit board dropped at the origin while the sub-level copy kept them |
| `02-generic-sable-fix-mod.txt` | Sable + Sable Fix Mod 1.1.0 | inventory test passes (the mod clears `Container` slots); socket test still fails, 1 circuit board dropped |
| `03-this-mod.txt` | Sable + this mod (`-Pwith_sable_fix=false`) | all 3 content tests pass |
| `04-this-mod-plus-generic.txt` | Sable + this mod + Sable Fix Mod | all 3 content tests pass; the two fixes compose without conflict |
| `05-this-mod-all-tests.txt` | Sable + this mod, current suite | all 6 tests pass, including the two alveary tests and the biome test |
| `06-this-mod-plus-generic-all-tests.txt` | Sable + this mod + Sable Fix Mod, current suite | all 6 tests pass |

The logs are chronological, not a matrix:

- `01`/`02` predate `contentsSurviveRoundTrip` and the alveary tests (2 tests), and `03`/`04` predate
  the alveary tests (3 tests).
- `05`/`06` are the current suite: `inventorySurvivesPhysicalization`, `socketSurvivesPhysicalization`,
  `contentsSurviveRoundTrip`, `alvearyContentsSurvivePhysicalization`, `alvearyContentsSurviveRoundTrip`
  and `apiaryUsesWorldBiomeOfSubLevel`.
- Re-running the "no fix" / "generic mod only" configurations against the current suite would need this
  mod temporarily removed from the runtime, which the project does not script. The alveary tests were
  observed failing that way during development (contents lost in the sub-level, nothing dropped),
  which is what motivated `SubLevelAssemblyHelperMixin`.

Search for `=========|LogTestReporter|required tests` in the logs for the summary lines.
