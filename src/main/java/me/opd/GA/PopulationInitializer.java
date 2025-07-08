package me.opd.GA;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearSlot;
import me.opd.Individual;
import me.opd.JewelHandeling.Jewel;
import me.opd.JewelHandeling.Socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PopulationInitializer {
    public static List<Individual> initialize(List<Gear> gearPool, List<Jewel> jewelPool) {
        List<Individual> population = new ArrayList<>();
        Random rand = new Random();
        System.out.println(gearPool.size());
        System.out.println(jewelPool.size());


        for (int i = 0; i < GAConfig.POPULATION_SIZE; i++) {
            Individual ind = new Individual();

            for (GearSlot slot : GearSlot.values()) {
                List<Gear> slotGear = gearPool.stream()
                        .filter(g -> g.slot == slot).toList();
                Gear chosen = slotGear.get(rand.nextInt(slotGear.size()-1));
                ind.gearSet.put(slot, chosen);

                List<Jewel> jewels = new ArrayList<>();
                for (Socket s : chosen.sockets) {
                    List<Jewel> validJewels = jewelPool.stream()
                            .filter(j -> j.type == s.type)
                            .toList();
                    if (!validJewels.isEmpty()) {
                        jewels.add(validJewels.get(rand.nextInt(validJewels.size())));
                    }
                }
                ind.jewels.put(slot, jewels);
            }

            population.add(ind);
        }

        return population;
    }
}

