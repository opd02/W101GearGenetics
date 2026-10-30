package me.opd.Optimization;
import me.opd.DataReading.*;
import java.util.*;
public class WizardProfile {
    public School school;
    public int level;
    /** Null means look up the exact school/level in the catalog. Entries replace source values. */
    public StatBlock baseOverrides;
    public Set<String> ownedItemIds = new TreeSet<>();
    public Map<String,Integer> ownedQuantities = new TreeMap<>();
    public boolean ownedOnly;
    public int quantity(String id) { return ownedQuantities.getOrDefault(id,ownedItemIds.contains(id) ? 1 : 0); }
    public void validate() {
        if (school == null || school == School.UNIVERSAL || level < 1 || ownedItemIds == null || ownedQuantities == null)
            throw new IllegalArgumentException("Wizard needs one of seven schools, positive level and an inventory");
        if (baseOverrides != null) baseOverrides.validate();
        ownedQuantities.forEach((id,n) -> { if (id == null || id.isBlank() || n == null || n < 0) throw new IllegalArgumentException("Invalid owned quantity"); });
    }
}
