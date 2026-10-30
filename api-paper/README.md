# Paper extension bridge

Java 21 Paper registration and synchronous events for the Manhunt Universal extension API. Both supported server versions provide these classes through the core plugin.

Use `compileOnly("com.caxture.manhuntuniversal:manhunt-universal-api-paper:1.0.0")` and the normal Paper API dependency. Add `depend: [ManhuntUniversal]` to the extension's `plugin.yml`. Obtain `PaperManhuntUniversalApi` through Bukkit `ServicesManager`; the same service is also registered as `ManhuntUniversalApi`. Register using your plugin instance, retain the returned token, and close it when finished. Plugin disable closes its registrations automatically.

The bridge compiles against Paper 1.21.11 with Java 21. Paper itself is a compile-only dependency of the published SDK. All 11 concrete events have independent handler lists and run synchronously. Pre-change events support cancellation; role candidates and match snapshots are immutable. `MatchFinishingEvent` additionally allows replacement of its candidate `Victory`. API writes from event or policy callbacks return `BUSY` rather than nesting mutations.

Binary, sources, and Javadoc JARs appear under `api-paper/build/libs`. The Maven publication is configured without an automatic remote publication destination. See the [pure API module](../api/README.md) for local publication and concurrency details.
