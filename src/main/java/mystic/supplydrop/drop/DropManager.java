package mystic.supplydrop.drop;

import mystic.supplydrop.config.DropSettings;
import mystic.supplydrop.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Random;

public final class DropManager {
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private DropSettings settings;
    private Messages messages;
    private ActiveDrop active;
    private boolean searching;
    private boolean closed;
    private long searchId;
    private World searchWorld;
    private BukkitTask timer;
    private BukkitTask searchTimeout;
    private final BukkitTask ticker;

    public DropManager(JavaPlugin plugin, DropSettings settings, Messages messages) {
        this.plugin = plugin;
        configure(settings, messages);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    public void configure(DropSettings settings, Messages messages) {
        this.settings = settings;
        this.messages = messages;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (settings.intervalSeconds() > 0) {
            long interval = settings.intervalSeconds() * 20L;
            timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (active == null && !searching) {
                    start(Bukkit.getWorld(this.settings.world()), Bukkit.getConsoleSender());
                }
            }, interval, interval);
        }
    }

    public void start(World world, CommandSender sender) {
        if (closed) {
            return;
        }
        if (active != null || searching) {
            messages.send(sender, "busy");
            return;
        }
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            messages.send(sender, world == null ? "unknown-world" : "no-location");
            return;
        }
        searching = true;
        searchWorld = world;
        messages.send(sender, "searching");

        // Old chunk callbacks may arrive late. The id makes them harmless after a stop or restart.
        long id = ++searchId;
        Messages text = messages;
        searchTimeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (searching && id == searchId) {
                stop();
                text.send(sender, "no-location");
            }
        }, 600L);
        search(world, sender, settings, text, id, 0);
    }

    private void search(World world, CommandSender sender, DropSettings options, Messages text,
                        long id, int attempt) {
        if (closed || id != searchId) {
            return;
        }
        if (attempt >= options.attempts()) {
            endSearch();
            text.send(sender, "no-location");
            return;
        }
        int x = options.centerX() + random.nextInt(-options.radius(), options.radius() + 1);
        int z = options.centerZ() + random.nextInt(-options.radius(), options.radius() + 1);
        Location column = new Location(world, x, 0, z);
        if (!insideBorder(column)) {
            retry(world, sender, options, text, id, attempt);
            return;
        }
        world.getChunkAtAsync(x >> 4, z >> 4, true).whenComplete((chunk, error) -> {
            // Paper may finish this off-thread, so hop back before touching the world.
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed || id != searchId) {
                    return;
                }
                if (error != null || chunk == null) {
                    retry(world, sender, options, text, id, attempt);
                    return;
                }

                // Keep the chosen chunk around until the crate is gone. Otherwise it can vanish mid-drop.
                chunk.addPluginChunkTicket(plugin);
                boolean retained = false;
                try {
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING) + 1;
                    Location landing = new Location(world, x, y, z);
                    if (safe(landing, options.height())) {
                        active = new ActiveDrop(options, text, landing, chunk, random);
                        active.spawn();
                        retained = true;
                        endSearch();
                        Bukkit.broadcast(text.render("started", landing));
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not create supply crate", exception);
                    removeActive();
                } finally {
                    if (!retained) {
                        chunk.removePluginChunkTicket(plugin);
                    }
                }
                if (!retained) {
                    retry(world, sender, options, text, id, attempt);
                }
            });
        });
    }

    private void retry(World world, CommandSender sender, DropSettings options, Messages text, long id, int attempt) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> search(world, sender, options, text, id, attempt + 1), 1L);
    }

    private boolean insideBorder(Location location) {
        return location.getWorld().getWorldBorder().isInside(location.clone().add(-1, 0, -1))
                && location.getWorld().getWorldBorder().isInside(location.clone().add(2, 0, 2));
    }

    private boolean safe(Location landing, int clearance) {
        World world = landing.getWorld();
        if (!insideBorder(landing) || landing.getBlockY() + clearance + 1 >= world.getMaxHeight()) {
            return false;
        }
        Block floor = landing.clone().subtract(0, 1, 0).getBlock();
        Material material = floor.getType();
        if (!material.isOccluding() || material == Material.MAGMA_BLOCK || material == Material.CACTUS) {
            return false;
        }
        for (int offset = 0; offset <= clearance; offset++) {
            if (!landing.clone().add(0, offset, 0).getBlock().getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private void tick() {
        ActiveDrop drop = active;
        if (drop == null) {
            return;
        }
        if (!drop.display.isValid() || (drop.landed && !drop.hitbox.isValid())) {
            finish("expired");
            return;
        }
        drop.age += 2;
        Location center = drop.display.getLocation().add(0.5, 0.5, 0.5);
        if (drop.settings.particles() && drop.age % 10 == 0) {
            center.getWorld().spawnParticle(drop.landed ? Particle.HAPPY_VILLAGER : Particle.CLOUD,
                    center, 5, 0.35, 0.15, 0.35, 0.01);
        }
        if (!drop.landed) {

            // This job runs every two ticks, hence the extra * 2 here.
            double y = Math.max(drop.landing.getY(), drop.display.getY() - drop.settings.fallSpeed() * 2);
            drop.display.teleport(new Location(drop.landing.getWorld(), drop.landing.getX(), y, drop.landing.getZ()));
            if (drop.settings.sounds() && drop.age % 40 == 0) {
                center.getWorld().playSound(center, Sound.BLOCK_WOOL_STEP, 1.0f, 0.5f);
            }
            if (y <= drop.landing.getY()) {
                if (!safe(drop.landing, 1)) {
                    finish("expired");
                    return;
                }
                drop.land();
                if (drop.settings.sounds()) {
                    center.getWorld().playSound(center, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
                }
                Bukkit.broadcast(drop.messages.render("landed", drop.landing));
            }
        } else if (drop.age >= drop.settings.lifetimeSeconds() * 20 || !safe(drop.landing, 1)) {
            finish("expired");
        }
    }

    public boolean stop() {
        boolean wasActive = searching || active != null;

        // Bumping this also cancels any location result that is still on its way back.
        ++searchId;
        endSearch();
        removeActive();
        return wasActive;
    }

    private void endSearch() {
        searching = false;
        searchWorld = null;
        if (searchTimeout != null) {
            searchTimeout.cancel();
            searchTimeout = null;
        }
    }

    public void close() {
        closed = true;
        stop();
        ticker.cancel();
        if (timer != null) {
            timer.cancel();
        }
    }

    public void status(CommandSender sender) {
        if (active != null) {
            sender.sendMessage(messages.render(active.landed ? "status-landed" : "status-falling", active.landing));
        } else {
            messages.send(sender, searching ? "status-searching" : "idle");
        }
    }

    private void finish(String reason) {
        if (active != null) {
            Bukkit.broadcast(active.messages.render(reason, active.landing));
            removeActive();
        }
    }

    private void removeActive() {
        ActiveDrop drop = active;
        active = null;
        if (drop == null) {
            return;
        }
        for (HumanEntity viewer : List.copyOf(drop.inventory.getViewers())) {
            viewer.closeInventory();
        }
        if (drop.display != null) {
            drop.display.remove();
        }
        if (drop.hitbox != null) {
            drop.hitbox.remove();
        }
        drop.inventory.clear();
        drop.chunk.removePluginChunkTicket(plugin);
    }

    void checkEmpty(ActiveDrop drop) {
        if (!closed) {

            // Bukkit updates the inventory after the click event, so check it on the next tick.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (active == drop && drop.inventory.isEmpty()) {
                    finish("emptied");
                }
            });
        }
    }

    ActiveDrop current() {
        return active;
    }

    void worldUnloaded(World world) {
        if (world.equals(searchWorld) || (active != null && world.equals(active.landing.getWorld()))) {
            stop();
        }
    }
}
