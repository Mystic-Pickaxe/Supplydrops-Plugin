package mystic.supplydrop.loot;

import java.util.List;
import java.util.random.RandomGenerator;

public final class WeightedTable<T> {
    public record Entry<T>(T value, int weight) {
        public Entry {
            if (value == null || weight <= 0) {
                throw new IllegalArgumentException("Loot entries need a value and a positive weight");
            }
        }
    }

    private final List<Entry<T>> entries;
    private final long totalWeight;

    public WeightedTable(List<Entry<T>> entries) {
        this.entries = List.copyOf(entries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("The loot table cannot be empty");
        }

        // Keep this as a long. A large config can push the combined weights past an int.
        totalWeight = entries.stream().mapToLong(Entry::weight).sum();
    }

    public T pick(RandomGenerator random) {
        long roll = random.nextLong(totalWeight);
        for (Entry<T> entry : entries) {
            roll -= entry.weight();
            if (roll < 0) {
                return entry.value();
            }
        }
        throw new IllegalStateException("Loot roll exceeded table weight");
    }
}
