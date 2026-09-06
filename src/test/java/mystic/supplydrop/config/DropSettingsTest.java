package mystic.supplydrop.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DropSettingsTest {
    private MockedStatic<Material> materials;

    @BeforeEach
    void setUpMaterials() {
        // Paper resolves item properties through server registries, which are absent in unit tests.
        materials = mockStatic(Material.class);
        for (String name : new String[]{"IRON_INGOT", "GOLD_INGOT", "DIAMOND", "GOLDEN_APPLE", "ENDER_PEARL"}) {
            Material material = mock(Material.class);
            when(material.isItem()).thenReturn(true);
            when(material.getMaxStackSize()).thenReturn(name.equals("ENDER_PEARL") ? 16 : 64);
            materials.when(() -> Material.matchMaterial(name)).thenReturn(material);
        }
        Material air = mock(Material.class);
        when(air.isItem()).thenReturn(true);
        when(air.isAir()).thenReturn(true);
        materials.when(() -> Material.matchMaterial("AIR")).thenReturn(air);
        Material water = mock(Material.class);
        materials.when(() -> Material.matchMaterial("WATER")).thenReturn(water);
    }

    @AfterEach
    void tearDownMaterials() {
        materials.close();
    }

    private YamlConfiguration defaults() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/config.yml")), StandardCharsets.UTF_8));
    }

    @Test
    void bundledConfigurationIsValid() {
        DropSettings settings = DropSettings.load(defaults());
        assertEquals(6, settings.rolls());
        assertEquals("world", settings.world());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidFallSpeeds(double speed) {
        var config = defaults();
        config.set("drop.fall-speed", speed);
        assertThrows(IllegalArgumentException.class, () -> DropSettings.load(config));
    }

    @Test
    void rejectsOversizedStacksAndReversedAmounts() {
        var config = defaults();
        config.set("loot.entries.pearls.max", 17);
        assertThrows(IllegalArgumentException.class, () -> DropSettings.load(config));
        config.set("loot.entries.pearls.max", 1);
        assertThrows(IllegalArgumentException.class, () -> DropSettings.load(config));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AIR", "WATER", "NOT_A_MATERIAL"})
    void rejectsMaterialsThatCannotBeLoot(String material) {
        var config = defaults();
        config.set("loot.entries.iron.material", material);
        assertThrows(IllegalArgumentException.class, () -> DropSettings.load(config));
    }

    @Test
    void rejectsFractionalIntegerSettings() {
        var config = defaults();
        config.set("loot.rolls", 2.5);
        assertThrows(IllegalArgumentException.class, () -> DropSettings.load(config));
    }

    @Test
    void allowsDisablingTheTimer() {
        var config = defaults();
        config.set("drop.interval-seconds", 0);
        assertEquals(0, DropSettings.load(config).intervalSeconds());
    }
}
