package mystic.supplydrop.drop;

import mystic.supplydrop.config.DropSettings;
import mystic.supplydrop.config.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.util.Random;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DropListenerTest {
    private MockedStatic<Bukkit> bukkit;
    private DropManager manager;
    private DropListener listener;
    private ActiveDrop drop;
    private InventoryView view;
    private Player player;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(27);
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(27), any(Component.class)))
                .thenReturn(inventory);
        DropSettings settings = mock(DropSettings.class);
        when(settings.title()).thenReturn("Supply crate");
        World world = mock(World.class);
        Location location = new Location(world, 0, 65, 0);
        drop = new ActiveDrop(settings, mock(Messages.class), location, mock(Chunk.class), new Random(1));
        when(inventory.getHolder()).thenReturn(drop);
        manager = mock(DropManager.class);
        when(manager.current()).thenReturn(drop);
        listener = new DropListener(manager);
        player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    private InventoryClickEvent click(int slot, InventoryAction action) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        when(event.getAction()).thenReturn(action);
        listener.onClick(event);
        return event;
    }

    @ParameterizedTest
    @EnumSource(value = InventoryAction.class, names = {
            "PLACE_ALL", "PLACE_ONE", "PLACE_SOME", "SWAP_WITH_CURSOR", "HOTBAR_SWAP", "COLLECT_TO_CURSOR", "CLONE_STACK"
    })
    void cannotInsertSwapOrCloneItemsInTheCrate(InventoryAction action) {
        verify(click(0, action)).setCancelled(true);
    }

    @ParameterizedTest
    @EnumSource(value = InventoryAction.class, names = {"PICKUP_ALL", "PICKUP_HALF", "MOVE_TO_OTHER_INVENTORY"})
    void canTakeLootWithNormalAndShiftClicks(InventoryAction action) {
        verify(click(0, action), never()).setCancelled(true);
        verify(manager).checkEmpty(drop);
    }

    @Test
    void cannotShiftClickPlayerItemsIntoTheCrate() {
        verify(click(30, InventoryAction.MOVE_TO_OTHER_INVENTORY)).setCancelled(true);
    }

    @Test
    void cannotCollectMatchingStacksThroughThePlayerInventory() {
        verify(click(30, InventoryAction.COLLECT_TO_CURSOR)).setCancelled(true);
    }

    @Test
    void staleViewsCannotTransferLoot() {
        when(manager.current()).thenReturn(null);
        verify(click(0, InventoryAction.PICKUP_ALL)).setCancelled(true);
        verify(manager, never()).checkEmpty(any());
    }

    @Test
    void movingOutOfReachPreventsFurtherLootTransfers() {
        when(player.getLocation()).thenReturn(drop.landing.clone().add(20, 0, 0));
        verify(click(0, InventoryAction.PICKUP_ALL)).setCancelled(true);
    }

    @Test
    void draggingCannotDepositItems() {
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getView()).thenReturn(view);
        listener.onDrag(event);
        verify(event).setCancelled(true);
    }
}
