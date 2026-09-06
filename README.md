# Supplydrops (Trial Project)

SupplyDrop is a small Paper plugin that drops a barrel from the sky and lets players race for its contents. It uses vanilla display entities, so there is no resource pack or client mod to install.

Only one drop can exist at a time, including while a location is being selected. The server announces the landing coordinates when the drop starts and again when it lands. Everyone opens the same inventory; reopening the crate does not generate more loot.

## Installation

1. Build with Java 21 and Maven 3.9 or newer using `mvn clean verify`.
2. Copy `target/supplydrop-1.0.0.jar` into your Paper server's `plugins` directory.
3. Start the server, edit `plugins/SupplyDrop/config.yml`, then run `/supplydrop reload`.
4. Run `/supplydrop start` to start a drop in the configured world.

The plugin targets the Paper 1.21 API and produces Java 21 bytecode. Later Paper versions are intended to work through the public API, without version-specific server internals. Use the Java version required by your server. See [Paper's Java requirements](https://docs.papermc.io/paper/getting-started/).

This is a Paper plugin, not a Folia plugin. See [verification notes](docs/verification.md) for the checks performed and the remaining in-game checks.

## Commands

All management commands require `supplydrop.admin`, which defaults to operators. Players need no permission to collect loot. `/sd` is an alias for `/supplydrop`.

| Command | Action |
| --- | --- |
| `/supplydrop start [world]` | Choose a random safe location and start a drop. Omitting the world uses the configuration. |
| `/supplydrop stop` | Cancel a pending search or remove the active crate and remaining loot. |
| `/supplydrop status` | Show whether a crate is being placed, falling or waiting to be looted. |
| `/supplydrop reload` | Validate and apply configuration changes. Invalid settings leave the previous configuration active. |

## Configuration

The default area is a square extending 500 blocks in each direction from X 0, Z 0. Choose an area meant for public events. Drops are restricted to normal Overworld-type worlds, stay inside the world border, and require a solid surface with open air above it. Water, leaves and obstructed columns are rejected. There is no claim-plugin integration.

Location searches load or generate one candidate chunk at a time through Paper's asynchronous chunk API. A search tries at most `area.search-attempts` candidates and times out after 30 seconds. The selected chunk stays loaded until its crate is removed. Pregenerate the event area if you want to avoid generating terrain during an event.

| Setting | Default | Meaning |
| --- | --- | --- |
| `world` | `world` | Default world for automatic and manual drops. |
| `area.center-x`, `area.center-z` | `0`, `0` | Center of the random selection area. |
| `area.radius` | `500` | Maximum X and Z offset from the center. |
| `area.search-attempts` | `40` | Maximum candidate locations per search. |
| `drop.height` | `35` | Starting height above the landing location, in blocks. |
| `drop.fall-speed` | `0.15` | Blocks descended per server tick. Allowed range is 0.05 to 1.0. |
| `drop.lifetime-seconds` | `300` | Time before an uncollected crate expires, measured from landing. |
| `drop.interval-seconds` | `900` | Time between automatic attempts. Set to 0 to disable. |
| `drop.particles`, `drop.sounds` | `true` | Toggle crate effects. |
| `loot.rolls` | `6` | Number of stacks generated, from 1 to 27. |

Automatic drops wait one interval after startup or reload. If a crate or search is active when the timer fires, that attempt is skipped. Timing follows server ticks and therefore slows down when the server lags.

Loot entries use material names, a relative weight and an inclusive amount range. Each roll chooses an entry independently, so an entry can appear more than once. With weights of 30 and 10, the first entry has a 75% chance on each roll. Amounts must fit in a single stack of that material.

```yaml
loot:
  title: '<dark_gray>Supply crate'
  rolls: 6
  entries:
    iron:
      material: IRON_INGOT
      weight: 30
      min: 4
      max: 12
    diamonds:
      material: DIAMOND
      weight: 10
      min: 1
      max: 3
```

Messages and the inventory title use [MiniMessage formatting](https://docs.advntr.dev/minimessage/format.html). Coordinate messages accept `<x>`, `<y>`, `<z>` and `<world>`. Set a message to an empty string to suppress its text. A crate already in progress keeps its original loot, messages and timing after a reload.

## Crate behavior

The barrel is a display entity with an interaction hitbox. It never replaces a terrain block. Players can take loot with normal clicks or shift-clicks; they cannot deposit items, drag items into the crate, or swap a hotbar item into it. Hoppers cannot access the virtual inventory.

An empty crate is removed after the inventory transaction completes. Expiry, `/supplydrop stop`, world unload and plugin shutdown close open views, remove the entities, discard remaining loot and release the chunk ticket. If the landing spot becomes obstructed or loses its floor, the crate expires.

Active crates are temporary and do not survive restarts. Their entities are not saved to disk. A server crash therefore loses any loot still in a crate, but leaves no permanent barrel block behind.

## Source layout

`SupplyDropPlugin` loads configuration and handles commands. `DropManager` owns location searches and the drop lifecycle. `DropListener` handles player interaction and inventory transfers. `ActiveDrop` holds a single crate's entities and shared inventory. `DropSettings` validates configuration, and `WeightedTable` handles weighted selection without depending on the server.

Tests use JUnit and Mockito. These dependencies are test-only; the plugin JAR contains the plugin classes and its two YAML resources.
