package mystic.supplydrop.drop;

import mystic.supplydrop.config.DropSettings;
import mystic.supplydrop.config.Messages;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class ActiveDrop implements InventoryHolder {
    final DropSettings settings;
    final Messages messages;
    final Location landing;
    final Chunk chunk;
    final Inventory inventory;
    BlockDisplay display;
    Interaction hitbox;
    int age;
    boolean landed;

    ActiveDrop(DropSettings settings, Messages messages, Location landing, Chunk chunk, Random random) {
        this.settings = settings;
        this.messages = messages;
        this.landing = landing.clone();
        this.chunk = chunk;
        inventory = Bukkit.createInventory(this, 27, MiniMessage.miniMessage().deserialize(settings.title()));
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }

        // Scattered stacks look much nicer than a row packed into the top-left corner.
        Collections.shuffle(slots, random);
        for (int roll = 0; roll < settings.rolls(); roll++) {
            inventory.setItem(slots.get(roll), settings.loot().pick(random).create(random));
        }
    }

    void spawn() {
        Location start = landing.clone().add(0, settings.height(), 0);
        display = landing.getWorld().spawn(start, BlockDisplay.class, entity -> {
            entity.setBlock(Bukkit.createBlockData(Material.BARREL));

            // A stopped server should come back clean instead of saving a crate nobody can open.
            entity.setPersistent(false);
            entity.setInvulnerable(true);
            entity.setGravity(false);
            entity.setTeleportDuration(2);
            entity.setViewRange(2.0f);
        });
    }

    void land() {
        display.teleport(landing);
        hitbox = landing.getWorld().spawn(landing.clone().add(0.5, 0, 0.5), Interaction.class, entity -> {
            entity.setInteractionWidth(1.05f);
            entity.setInteractionHeight(1.05f);
            entity.setResponsive(true);
            entity.setPersistent(false);
            entity.setInvulnerable(true);
        });
        landed = true;
        age = 0;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
