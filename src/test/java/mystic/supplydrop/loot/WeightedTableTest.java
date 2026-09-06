package mystic.supplydrop.loot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WeightedTableTest {
    @Test
    void eachWeightOwnsExactlyItsShareOfTheRollRange() {
        WeightedTable<String> table = new WeightedTable<>(List.of(
                new WeightedTable.Entry<>("iron", 3),
                new WeightedTable.Entry<>("gold", 2),
                new WeightedTable.Entry<>("diamond", 1)));
        List<String> results = new ArrayList<>();
        for (long roll = 0; roll < 6; roll++) {
            long fixedRoll = roll;
            results.add(table.pick(new Random() {
                @Override
                public long nextLong(long bound) {
                    assertEquals(6, bound);
                    return fixedRoll;
                }
            }));
        }
        assertEquals(List.of("iron", "iron", "iron", "gold", "gold", "diamond"), results);
    }

    @Test
    void largeWeightsDoNotOverflowAnInteger() {
        WeightedTable<String> table = new WeightedTable<>(List.of(
                new WeightedTable.Entry<>("first", Integer.MAX_VALUE),
                new WeightedTable.Entry<>("last", Integer.MAX_VALUE)));
        assertEquals("last", table.pick(new Random() {
            @Override
            public long nextLong(long bound) {
                assertEquals(4_294_967_294L, bound);
                return bound - 1;
            }
        }));
    }

    @Test
    void tableKeepsItsOwnCopyOfEntries() {
        var entries = new ArrayList<>(List.of(new WeightedTable.Entry<>("iron", 1)));
        var table = new WeightedTable<>(entries);
        entries.clear();
        assertEquals("iron", table.pick(new Random(1)));
    }

    @Test
    void rejectsUnusableTables() {
        assertThrows(IllegalArgumentException.class, () -> new WeightedTable<>(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WeightedTable.Entry<>("iron", 0));
        assertThrows(IllegalArgumentException.class, () -> new WeightedTable.Entry<>(null, 1));
    }
}
