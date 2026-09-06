# Verification

## Automated checks

Run `mvn clean verify` with Java 21. All 35 tests pass. Unit tests cover weighted selection boundaries, integer overflow, configuration limits, simultaneous start requests, search timeout, cancellation, delayed chunk completion after shutdown, inventory insertion attempts, hotbar swaps, stale views and out-of-reach transfers. Bukkit-dependent tests use mocks; they do not simulate a Minecraft client or prove in-game rendering.

The release JAR is compiled against Paper 1.21 with `api-version: '1.21'`. Compiling against another API can identify removed methods, but does not establish runtime or client compatibility.

Compilation was checked against these Paper APIs on September 5, 2026:

| Paper API | Compilation |
| --- | --- |
| `1.21-R0.1-SNAPSHOT` | Passed, release baseline |
| `1.21.1-R0.1-SNAPSHOT` | Passed |
| `1.21.4-R0.1-SNAPSHOT` | Passed |
| `1.21.8-R0.1-SNAPSHOT` | Passed |
| `1.21.11-R0.1-SNAPSHOT` | Passed |
| `26.1.2.build.74-stable` | Passed |
| `26.2.build.112-stable` | Passed |

To repeat a compilation check, use `mvn clean compile -Dmaven.test.skip=true -Dpaper.version=VERSION` with a JDK new enough to read that API. Build the distributable afterward with the default Paper version and Java 21.

## In-game acceptance checks

These checks require a Paper server and Minecraft clients. They have not been completed for this release.

1. Start a drop in a small, known event area. Confirm the first broadcast matches the location, the barrel falls smoothly, effects are audible and visible, and it cannot be opened before landing.
2. Run `start` twice while the location is loading and again while the barrel falls. Confirm only one crate appears.
3. Have two players open the landed crate together. Take the same stack from both views and confirm it is only awarded once. Reopen the crate and confirm the loot has not rerolled.
4. Try shift-clicking player items into the crate, number-key swaps, offhand swaps, double-click collection and inventory dragging. Confirm no items enter the crate and no items duplicate. Confirm ordinary clicks and shift-clicks can take loot out.
5. Empty the crate and confirm it disappears. Let a separate crate expire with a viewer still inside; the view should close and remaining loot should be discarded.
6. Stop a drop during search, descent and after landing. Start another immediately. Check that no late callback creates an old crate and that chunks can unload after cleanup.
7. Reload valid and invalid configuration while idle and with a crate active. Active crates should keep their previous settings. Invalid configuration should preserve the previous settings and log a useful error.
8. Test an area outside the world border, an ocean, dense trees and a near-build-limit platform. Unsuitable locations should be rejected without replacing terrain.
9. Place an obstruction at the landing spot or remove its floor. The crate should expire. Restart the server during a drop and confirm no entities or terrain changes remain.
10. Check commands as an operator, a player with `supplydrop.admin`, a player without the permission and the console. Test `/sd` and tab completion.

Repeat the player checks on the oldest and newest Paper versions you plan to advertise. Claim and protection plugins may cancel entity interaction; test them separately if the server uses them.
