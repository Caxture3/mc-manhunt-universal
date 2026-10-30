# Manhunt Universal extension API

Java 21 contracts for extension plugins. This module has no platform dependencies. API artifacts use their own version (`1.0.0`) independently of the core plugin version.

Use `compileOnly("com.caxture.manhuntuniversal:manhunt-universal-api:1.0.0")` for the platform-independent contracts. For a Paper extension, use the sibling `manhunt-universal-api-paper` artifact instead; it exposes these contracts transitively. Do not shade either SDK into an extension plugin because the core provides their runtime classes.

The module creates the binary, sources, and Javadoc JARs under `api/build/libs`. A Maven publication is configured but no remote repository or automatic publication is enabled. For local development, `./gradlew :api:publishToMavenLocal :api-paper:publishToMavenLocal` installs both SDKs locally; a consuming build must enable `mavenLocal()`.

All live service queries and mutations require the server main thread. `matches().current()` and registration `close()` throw `IllegalStateException` off-thread; registration and mutation requests return `WRONG_THREAD`. Immutable snapshots can be retained. Use the session identifier to avoid touching a later match and supply a revision when stale-state rejection is required; `-1` skips only the revision check.

Registrations own exclusive control domains. A policy requires its corresponding domain; an empty domain set is suitable for observers. Closing a registration releases its domains without changing roles, elimination, or the active match. A role assignment contains the entire roster, preserves eliminated roles, and follows normal availability and final-team checks. No extension automatically changes the default fixed-role game.
