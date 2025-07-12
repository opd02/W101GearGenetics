package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearSlot;
import me.opd.DataReading.StatBlock;
import me.opd.JewelHandeling.Jewel;
import me.opd.JewelHandeling.Socket;
import me.opd.PetHandeling.Pet;
import me.opd.PetHandeling.PetTrait;

import java.util.*;
import java.util.stream.Collectors;

public class Individual {
    public Map<GearSlot, Gear> gearSet = new HashMap<>();
    public Map<GearSlot, List<Jewel>> jewels = new HashMap<>();
    public List<PetTrait> petTraits = new ArrayList<>(); // exactly 5

    public StatBlock evaluateStats() {
        StatBlock total = new StatBlock();
        for (Gear gear : gearSet.values()) {
            total.add(gear.baseStats);
            List<Socket> sockets = gear.sockets;
            List<Jewel> jewelsForSlot = jewels.getOrDefault(gear.slot, List.of());

            for (int i = 0; i < Math.min(sockets.size(), jewelsForSlot.size()); i++) {
                Jewel jewel = jewelsForSlot.get(i);
                if (jewel != null) {
                    total.add(jewel.statBonus);
                }
            }
        }
        for (PetTrait trait : petTraits) {
            total.add(trait.statBonus);
        }
        if(this.gearSet.get(GearSlot.PET).name.equals("Scratchy Frillasaur") &&
                this.gearSet.get(GearSlot.MOUNT).name.equals("Stompy-Bronto")){
            total.addDamage(8);
        }

        return total;
    }

    public List<Jewel> getJewels() {
        return this.jewels.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public List<Gear> getGear(){
        return this.gearSet.values().stream().toList();
    }
        public String statsToString(){
        return evaluateStats().addBaseStats().toString();
    }
}
