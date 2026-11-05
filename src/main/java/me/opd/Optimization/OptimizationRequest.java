package me.opd.Optimization;
import me.opd.DataReading.*;
import me.opd.GA.GAConfig;
import java.util.*;
public class OptimizationRequest {
    public WizardProfile wizard = new WizardProfile();
    public List<StatTarget> targets = new ArrayList<>();
    public Map<GearSlot,String> lockedItems = new EnumMap<>(GearSlot.class);
    public Map<GearSlot,GearSelector> lockedGear = new EnumMap<>(GearSlot.class);
    public List<GearSelector> excludedGear = new ArrayList<>();
    /** Key is SLOT:zero-based-socket-index. Empty sockets are allowed unless locked. */
    public Map<String,String> lockedJewels = new TreeMap<>();
    public Set<String> excludedIds = new TreeSet<>(), excludedSources = new TreeSet<>(), excludedCategories = new TreeSet<>();
    public Set<GearSlot> requiredSlots = EnumSet.allOf(GearSlot.class);
    public boolean allowIncompleteItems;
    public GAConfig search = new GAConfig();
    public PetSearchConfig petSearch = new PetSearchConfig();
    public int topK = 10;
    public int minDifferentSlots = 1;
    public void validate() {
        if (wizard == null || targets == null || lockedItems == null || lockedJewels == null || excludedIds == null
                || excludedSources == null || excludedCategories == null || requiredSlots == null || search == null || lockedGear==null || excludedGear==null || petSearch==null)
            throw new IllegalArgumentException("Request sections cannot be null");
        wizard.validate(); search.validate();
        petSearch.validate(this);
        if (requiredSlots.isEmpty() || topK < 1 || topK > 1000 || minDifferentSlots < 1 || minDifferentSlots > GearSlot.values().length)
            throw new IllegalArgumentException("Choose slots, topK 1..1000 and minDifferentSlots 1..10");
        for (StatTarget target : targets) { if (target == null) throw new IllegalArgumentException("Null target"); target.validate(); }
        for (var entry : lockedGear.entrySet()) {
            if (entry.getValue()==null || !requiredSlots.contains(entry.getKey())) throw new IllegalArgumentException("Name lock needs a selected slot and gear selection");
            entry.getValue().validate();
            if (entry.getValue().slot!=null && entry.getValue().slot!=entry.getKey()) throw new IllegalArgumentException("Name lock slot qualifier conflicts with "+entry.getKey());
        }
        for (GearSelector selector : excludedGear) {
            if (selector==null) throw new IllegalArgumentException("Null excluded gear selection"); selector.validate();
        }
        for (StatTarget a : targets) for (StatTarget b : targets)
            if (a.mandatory && b.mandatory && a.stat == b.stat && a.school == b.school
                    && a.mode == StatTarget.Mode.MINIMUM && b.mode == StatTarget.Mode.MAXIMUM && a.value > b.value)
                throw new IllegalArgumentException("Contradictory minimum/maximum for " + a.stat + ":" + a.school);
        for (var entry : lockedItems.entrySet())
            if (!requiredSlots.contains(entry.getKey()) || excludedIds.contains(entry.getValue()))
                throw new IllegalArgumentException("Locked item is excluded or its slot is not selected: " + entry);
        for (var entry : lockedJewels.entrySet()) {
            String[] parts = entry.getKey().split(":");
            try {
                if (parts.length != 2 || Integer.parseInt(parts[1]) < 0 || !requiredSlots.contains(GearSlot.valueOf(parts[0])))
                    throw new IllegalArgumentException();
            } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid socket lock " + entry.getKey() + "; use SLOT:0"); }
            if (entry.getValue() == null || excludedIds.contains(entry.getValue()))
                throw new IllegalArgumentException("Locked jewel is missing or excluded: " + entry);
        }
    }
}
