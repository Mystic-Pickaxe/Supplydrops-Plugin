package mystic.supplydrop.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.random.RandomGenerator;

public record LootItem(Material material, int minimum, int maximum) {
    public ItemStack create(RandomGenerator random) {
        return new ItemStack(material, random.nextInt(minimum, maximum + 1));
    }
}
