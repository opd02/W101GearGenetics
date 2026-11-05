package me.opd;
import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import java.util.*;
public class Individual {
    public Map<GearSlot,Gear> gearSet = new EnumMap<>(GearSlot.class);
    /** Lists retain socket positions, with null representing an empty socket. */
    public Map<GearSlot,List<Jewel>> jewels = new EnumMap<>(GearSlot.class);
    public Individual copyGenome() {
        Individual copy = new Individual(); copy.gearSet.putAll(gearSet);
        jewels.forEach((slot,list) -> copy.jewels.put(slot,new ArrayList<>(list)));
        return copy; // Catalog records are never mutated by search operations.
    }
    public StatBlock evaluateStats() {
        StatBlock total = new StatBlock();
        for (var entry : gearSet.entrySet()) {
            Gear gear = entry.getValue(); total.add(gear.baseStats);
            if (gear.pet != null) total.add(me.opd.PetHandeling.PetCalculator.calculate(gear.pet,jewels.getOrDefault(entry.getKey(),List.of())));
            for (Jewel jewel : jewels.getOrDefault(entry.getKey(),List.of()))
                if (jewel != null) total.add(jewel.statBonus);
        }
        return total;
    }
    /** Length-prefixed identifiers avoid delimiter collisions in imported IDs. */
    public String signature() {
        StringBuilder key = new StringBuilder();
        for (GearSlot slot : GearSlot.values()) if (gearSet.containsKey(slot)) {
            key.append(slot).append('=');
            appendId(key,gearSet.get(slot).id);
            for (Jewel jewel : jewels.getOrDefault(slot,List.of())) appendId(key,jewel == null ? "" : jewel.id);
            key.append(';');
        }
        return key.toString();
    }
    private static void appendId(StringBuilder key,String id) { key.append(id.length()).append(':').append(id); }
}
