package mystic.supplydrop.drop;

import mystic.supplydrop.config.DropSettings;
import mystic.supplydrop.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DropManagerTest {
    private MockedStatic<Bukkit> bukkit;
    private DropManager manager;
    private JavaPlugin plugin;
    private World world;
    private Messages messages;
    private CommandSender sender;
    private CompletableFuture<Chunk> loading;
    private final Queue<Runnable> scheduled = new ArrayDeque<>();
    private Runnable timeout;

    @BeforeEach
    void setUp() {
        bukkit = mockStatic(Bukkit.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(BukkitTask.class));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            scheduled.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(600L))).thenAnswer(call -> {
            timeout = call.getArgument(1);
            return mock(BukkitTask.class);
        });
        world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any(Location.class))).thenReturn(true);
        loading = new CompletableFuture<>();
        when(world.getChunkAtAsync(anyInt(), anyInt(), eq(true))).thenReturn(loading);
        DropSettings settings = mock(DropSettings.class);
        when(settings.radius()).thenReturn(20);
        when(settings.attempts()).thenReturn(5);
        messages = mock(Messages.class);
        sender = mock(CommandSender.class);
        manager = new DropManager(plugin, settings, messages);
    }

    @AfterEach
    void tearDown() {
        manager.close();
        bukkit.close();
    }

    @Test
    void pendingSearchCountsAsAnActiveDrop() {
        manager.start(world, sender);
        manager.start(world, sender);
        verify(messages).send(sender, "busy");
        verify(world, times(1)).getChunkAtAsync(anyInt(), anyInt(), eq(true));
    }

    @Test
    void stoppingSearchPreventsLateChunkCompletionFromCreatingADrop() {
        manager.start(world, sender);
        assertTrue(manager.stop());
        Chunk chunk = mock(Chunk.class);
        loading.complete(chunk);
        scheduled.remove().run();
        verifyNoInteractions(chunk);
        assertFalse(manager.stop());
    }

    @Test
    void timedOutSearchCanBeStartedAgain() {
        manager.start(world, sender);
        timeout.run();
        verify(messages).send(sender, "no-location");
        manager.start(world, sender);
        verify(world, times(2)).getChunkAtAsync(anyInt(), anyInt(), eq(true));
    }

    @Test
    void completionAfterShutdownDoesNotAcquireAChunkTicket() {
        manager.start(world, sender);
        manager.close();
        when(plugin.isEnabled()).thenReturn(false);
        Chunk chunk = mock(Chunk.class);
        loading.complete(chunk);
        assertTrue(scheduled.isEmpty());
        verifyNoInteractions(chunk);
    }
}
