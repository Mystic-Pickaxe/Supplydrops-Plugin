package mystic.supplydrop.drop;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class DropListener implements Listener {
    private final DropManager manager;

    public DropListener(DropManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        ActiveDrop drop = manager.current();
        if (drop == null || !drop.landed || !event.getRightClicked().equals(drop.hitbox)) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND || !inReach(event.getPlayer(), drop)) {
            return;
        }
        event.getPlayer().openInventory(drop.inventory);
        if (drop.settings.sounds()) {
            event.getPlayer().playSound(drop.landing, Sound.BLOCK_BARREL_OPEN, 0.7f, 1.0f);
        }
    }

    private boolean inReach(Player player, ActiveDrop drop) {
        return player.getWorld().equals(drop.landing.getWorld())
                && player.getLocation().distanceSquared(drop.landing.clone().add(0.5, 0.5, 0.5)) <= 36;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ActiveDrop drop)) {
            return;
        }
        if (drop != manager.current() || !(event.getWhoClicked() instanceof Player player) || !inReach(player, drop)) {
            event.setCancelled(true);
            return;
        }
        boolean top = event.getRawSlot() >= 0 && event.getRawSlot() < drop.inventory.getSize();

        // Players may take loot, but every route that could put an item back in stays blocked.
        boolean allowed = switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, DROP_ALL_SLOT, DROP_ONE_SLOT,
                 DROP_ALL_CURSOR, DROP_ONE_CURSOR, NOTHING -> true;
            case MOVE_TO_OTHER_INVENTORY -> top;
            case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR, HOTBAR_SWAP -> !top;
            default -> false;
        };
        if (!allowed) {
            event.setCancelled(true);
        }
        manager.checkEmpty(drop);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ActiveDrop) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ActiveDrop drop) {
            manager.checkEmpty(drop);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        ActiveDrop drop = manager.current();
        if (drop != null && (event.getEntity().equals(drop.display) || event.getEntity().equals(drop.hitbox))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        manager.worldUnloaded(event.getWorld());
    }
}
