package mystic.supplydrop.config;

import mystic.supplydrop.loot.LootItem;
import mystic.supplydrop.loot.WeightedTable;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record DropSettings(String world, int centerX, int centerZ, int radius, int attempts,
                           int height, double fallSpeed, int lifetimeSeconds, int intervalSeconds,
                           boolean particles, boolean sounds, String title, int rolls,
                           WeightedTable<LootItem> loot) {
    public static DropSettings load(ConfigurationSection config) {
        List<WeightedTable.Entry<LootItem>> entries = new ArrayList<>();
        ConfigurationSection loot = config.getConfigurationSection("loot.entries");
        if (loot == null) {
            throw new IllegalArgumentException("loot.entries is missing");
        }

        // The section names are just labels for server owners, so there is no fixed list here.
        for (String key : loot.getKeys(false)) {
            String path = "loot.entries." + key;
            Material material = Material.matchMaterial(config.getString(path + ".material", ""));
            if (material == null || !material.isItem() || material.isAir()) {
                throw new IllegalArgumentException(path + ".material must be an item material");
            }
            int min = integer(config, path + ".min", 1, material.getMaxStackSize());
            int max = integer(config, path + ".max", min, material.getMaxStackSize());
            int weight = integer(config, path + ".weight", 1, 1_000_000);
            entries.add(new WeightedTable.Entry<>(new LootItem(material, min, max), weight));
        }
        double speed = config.getDouble("drop.fall-speed", Double.NaN);
        if (!Double.isFinite(speed) || speed < 0.05 || speed > 1.0) {
            throw new IllegalArgumentException("drop.fall-speed must be between 0.05 and 1.0");
        }
        String world = config.getString("world", "").trim();
        if (world.isEmpty()) {
            throw new IllegalArgumentException("world must not be blank");
        }
        return new DropSettings(world,
                integer(config, "area.center-x", -29_000_000, 29_000_000),
                integer(config, "area.center-z", -29_000_000, 29_000_000),
                integer(config, "area.radius", 1, 100_000),
                integer(config, "area.search-attempts", 1, 200),
                integer(config, "drop.height", 5, 128), speed,
                integer(config, "drop.lifetime-seconds", 10, 86_400),
                integer(config, "drop.interval-seconds", 0, 604_800),
                config.getBoolean("drop.particles"), config.getBoolean("drop.sounds"),
                config.getString("loot.title", "Supply crate"),
                integer(config, "loot.rolls", 1, 27), new WeightedTable<>(entries));
    }

    private static int integer(ConfigurationSection config, String path, int min, int max) {
        if (!config.isInt(path)) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        int value = config.getInt(path);
        if (value < min || value > max) {
            throw new IllegalArgumentException(path + " must be between " + min + " and " + max);
        }
        return value;
    }
}
