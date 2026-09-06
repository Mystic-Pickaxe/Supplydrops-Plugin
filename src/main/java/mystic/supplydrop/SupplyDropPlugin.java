package mystic.supplydrop;

import mystic.supplydrop.config.DropSettings;
import mystic.supplydrop.config.Messages;
import mystic.supplydrop.drop.DropManager;
import mystic.supplydrop.drop.DropListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

public final class SupplyDropPlugin extends JavaPlugin {
    private DropManager drops;
    private DropSettings settings;
    private Messages messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            loadSettings();
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Invalid SupplyDrop configuration; plugin disabled", exception);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        drops = new DropManager(this, settings, messages);
        Bukkit.getPluginManager().registerEvents(new DropListener(drops), this);
        Objects.requireNonNull(getCommand("supplydrop")).setExecutor(this);
        Objects.requireNonNull(getCommand("supplydrop")).setTabCompleter(this);
    }

    private void loadSettings() throws Exception {
        YamlConfiguration candidate = new YamlConfiguration();
        candidate.load(new File(getDataFolder(), "config.yml"));

        // Parse everything first. A bad reload should not leave half of the old settings behind.
        DropSettings parsed = DropSettings.load(candidate);
        Messages parsedMessages = new Messages(candidate);
        settings = parsed;
        messages = parsedMessages;
    }

    @Override
    public void onDisable() {
        if (drops != null) {
            drops.close();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("supplydrop.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            messages.send(sender, "usage");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length > (action.equals("start") ? 2 : 1)) {
            messages.send(sender, "usage");
            return true;
        }
        switch (action) {
            case "start" -> drops.start(Bukkit.getWorld(args.length == 2 ? args[1] : settings.world()), sender);
            case "stop" -> messages.send(sender, drops.stop() ? "stopped" : "idle");
            case "status" -> drops.status(sender);
            case "reload" -> {
                try {
                    loadSettings();
                    drops.configure(settings, messages);
                    messages.send(sender, "reloaded");
                } catch (Exception exception) {
                    getLogger().log(Level.WARNING, "SupplyDrop configuration reload rejected", exception);
                    messages.send(sender, "reload-failed");
                }
            }
            default -> messages.send(sender, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("supplydrop.admin")) {
            return List.of();
        }
        List<String> candidates = switch (args.length) {
            case 1 -> List.of("start", "stop", "status", "reload");
            case 2 -> args[0].equalsIgnoreCase("start")
                    ? Bukkit.getWorlds().stream().map(org.bukkit.World::getName).toList() : List.of();
            default -> List.of();
        };
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
