package me.opd.Optimization;
import me.opd.DataReading.*;
import java.util.*;
public class WizardProfile {
    public School school;
    public int level;
    /** Optional combat level; null keeps raw-stat scoring. Gear eligibility always uses level. */
    public Integer scaledLevel;
    /** Optional user-supplied post-scaling ceilings; bundled proportional ratios are retained. */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public StatBlock scaledCapOverrides;
    /** Null means look up the exact school/level in the catalog. Entries replace source values. */
    public StatBlock baseOverrides;
    public Set<String> ownedItemIds = new TreeSet<>();
    public Map<String,Integer> ownedQuantities = new TreeMap<>();
    public boolean ownedOnly;
    public int quantity(String id) { return ownedQuantities.getOrDefault(id,ownedItemIds.contains(id) ? 1 : 0); }
    public void validate() {
        if (school == null || school == School.UNIVERSAL || level < 1 || ownedItemIds == null || ownedQuantities == null)
            throw new IllegalArgumentException("Wizard needs one of seven schools, positive level and an inventory");
        if (scaledLevel!=null && (scaledLevel<1 || scaledLevel>level))
            throw new IllegalArgumentException("Scaled level must be positive and no higher than the character level");
        if (baseOverrides != null) baseOverrides.validate();
        if (scaledCapOverrides!=null) {
            scaledCapOverrides.validate();
            if (!scaledCapOverrides.values.isEmpty() && scaledLevel==null)
                throw new IllegalArgumentException("scaledCapOverrides requires scaledLevel");
            if (scaledCapOverrides.values.values().stream().anyMatch(value -> value<=0))
                throw new IllegalArgumentException("Scaled cap overrides must be positive");
        }
        ownedQuantities.forEach((id,n) -> { if (id == null || id.isBlank() || n == null || n < 0) throw new IllegalArgumentException("Invalid owned quantity"); });
    }
}
