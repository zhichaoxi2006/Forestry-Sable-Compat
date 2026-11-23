# libs/

Optional, **not redistributed**: the jar dropped here is only used by the GameTest development
runtime to reproduce the "generic fix mod" comparison in the README. It is never a dependency of the
built mod and `*.jar` is git-ignored.

To recreate the comparison run, download **Sable Fix Mod 1.1.0** for 1.21.1 / NeoForge (MIT) from
<https://modrinth.com/mod/sable-fix-mod/version/1.1.0> and save the jar as:

```
libs/sable_fix_mod-1.1.0.jar
```

The path is configurable via `sable_fix_mod_jar` in `gradle.properties`; when the file is missing the
build logs a warning and simply runs the GameTests without it. `-Pwith_sable_fix=false` skips it even
when present.
