package me.opd.PetHandeling;
import me.opd.DataReading.StatBlock;
import java.util.*;
public class Pet {
    public String name;
    public boolean hypothetical;
    public List<PetTrait> traits = new ArrayList<>();
    public void validate() {
        if (traits == null || traits.size() > 5) throw new IllegalArgumentException("At most five manifested pet talents; use a PET socket for the jewel");
        Set<String> ids = new HashSet<>();
        for (PetTrait trait : traits) {
            if (trait == null) throw new IllegalArgumentException("Null pet talent");
            trait.validate();
            if (!ids.add(trait.id)) throw new IllegalArgumentException("Duplicate pet talent " + trait.id);
        }
    }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public StatBlock getTotalStats() { validate(); StatBlock total = new StatBlock(); traits.forEach(t -> total.add(t.statBonus)); return total; }
}
