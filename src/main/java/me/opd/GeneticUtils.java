package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearSlot;
import me.opd.JewelHandeling.Jewel;
import me.opd.JewelHandeling.Socket;
import me.opd.Utils.CopyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GeneticUtils {
    public static Individual mutate(Individual ind, List<Gear> gearPool, List<Jewel> jewelPool) {
        Random rand = new Random();
        Individual copy = CopyUtils.deepCopy(ind); // implement deep copy

        // Mutate one gear slot
        GearSlot slot = GearSlot.values()[rand.nextInt(GearSlot.values().length)];
        List<Gear> options = gearPool.stream()
                .filter(g -> g.slot == slot).toList();
        if (!options.isEmpty()) {
            copy.gearSet.put(slot, options.get(rand.nextInt(options.size())));
        }

        // Mutate one jewel on that gear
        List<Socket> sockets = copy.gearSet.get(slot).sockets;
        List<Jewel> jewelsForSlot = new ArrayList<>();
        for (Socket s : sockets) {
            List<Jewel> validJewels = jewelPool.stream()
                    .filter(j -> j.type == s.type)
                    .toList();
            jewelsForSlot.add(validJewels.get(rand.nextInt(validJewels.size())));
        }
        copy.jewels.put(slot, jewelsForSlot);

        return copy;
    }

    public static Individual crossover(Individual a, Individual b) {
        Individual child = new Individual();
        for (GearSlot slot : GearSlot.values()) {
            if (Math.random() < 0.5) {
                child.gearSet.put(slot, a.gearSet.get(slot));
                child.jewels.put(slot, a.jewels.get(slot));
            } else {
                child.gearSet.put(slot, b.gearSet.get(slot));
                child.jewels.put(slot, b.jewels.get(slot));
            }
        }
        return child;
    }
}

