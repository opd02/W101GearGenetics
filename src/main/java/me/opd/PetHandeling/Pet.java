package me.opd.PetHandeling;

import me.opd.DataReading.StatBlock;

import java.util.List;

public class Pet {
    public String name;
    public List<PetTrait> traits;

    public StatBlock getTotalStats() {
        StatBlock total = new StatBlock();
        for (PetTrait trait : traits) {
            //TODO add Mighty and other stat improving talents in correctly
            total.add(trait.statBonus);
        }
        return total;
    }
}

