package me.opd.DataReading;
import java.util.*;
/** Raw additive bonuses. Effective totals retain their original universal contributions. */
public class StatBlock {
    public Map<String, Double> values = new TreeMap<>();
    public static String key(StatType stat, School school) {
        Objects.requireNonNull(stat); Objects.requireNonNull(school);
        if (!stat.schoolSpecific && school != School.UNIVERSAL)
            throw new IllegalArgumentException(stat + " is not school-specific");
        return stat.name() + ":" + school.name();
    }
    public StatBlock put(StatType stat, School school, double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Stat must be finite");
        values.put(key(stat, school), value); return this;
    }
    public double raw(StatType stat, School school) { return values.getOrDefault(key(stat, school), 0.0); }
    public double effective(StatType stat, School school) {
        return raw(stat, school) + (stat.schoolSpecific && school != School.UNIVERSAL ? raw(stat, School.UNIVERSAL) : 0);
    }
    public void add(StatBlock other) { other.values.forEach((key, value) -> values.merge(key, value, Double::sum)); }
    public StatBlock copy() { StatBlock copy = new StatBlock(); copy.values.putAll(values); return copy; }
    public void validate() {
        if (values == null) throw new IllegalArgumentException("Missing stat values");
        values.forEach((k,v) -> {
            String[] parts = k.split(":", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid stat key: " + k);
            key(StatType.valueOf(parts[0]), School.valueOf(parts[1]));
            if (v == null || !Double.isFinite(v)) throw new IllegalArgumentException("Invalid value for " + k);
        });
    }
    @Override public String toString() { return values.toString(); }
}
