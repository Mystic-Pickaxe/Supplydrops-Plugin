package mystic.supplydrop.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public final class Messages {
    private final Map<String, String> templates = new HashMap<>();
    private final String prefix;

    public Messages(ConfigurationSection config) {
        ConfigurationSection section = config.getConfigurationSection("messages");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                templates.put(key, section.getString(key, ""));
            }
        }
        prefix = templates.getOrDefault("prefix", "");
    }

    public Component render(String key, Location location) {
        String template = templates.getOrDefault(key, key);
        if (template.isBlank()) {
            return Component.empty();
        }
        if (location == null) {
            return MiniMessage.miniMessage().deserialize(prefix + template);
        }

        // Coordinates use unparsed placeholders so a strange world name cannot turn into formatting.
        return MiniMessage.miniMessage().deserialize(prefix + template,
                Placeholder.unparsed("x", Integer.toString(location.getBlockX())),
                Placeholder.unparsed("y", Integer.toString(location.getBlockY())),
                Placeholder.unparsed("z", Integer.toString(location.getBlockZ())),
                Placeholder.unparsed("world", location.getWorld().getName()));
    }

    public void send(CommandSender recipient, String key) {
        recipient.sendMessage(render(key, null));
    }
}
