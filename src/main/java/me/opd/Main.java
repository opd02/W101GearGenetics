package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearLoader;
import me.opd.DataReading.GearSlot;
import me.opd.GA.GeneticAlgorithm;
import me.opd.JewelHandeling.Jewel;
import me.opd.JewelHandeling.Socket;
import me.opd.PetHandeling.Pet;
import me.opd.PetHandeling.PetTrait;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        List<Gear> gearPool = GearLoader.loadGearFromCSV(System.getProperty("user.dir")+"\\GearDB.csv");
        List<Jewel> jewelPool = GearLoader.loadJewelsFromCSV(System.getProperty("user.dir")+"\\JewelDB.csv");
        List<PetTrait> traitPool = GearLoader.loadTraitsFromCSV(System.getProperty("user.dir")+"\\PetTraitsDB.csv");

        Individual best = GeneticAlgorithm.run(gearPool, jewelPool, traitPool);

        System.out.println("Best Gear Set:");
        for (Map.Entry<GearSlot, Gear> entry : best.gearSet.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().name);
            List<Jewel> jewelsForSlot = best.jewels.getOrDefault(entry.getKey(), List.of());
            for(Jewel jewel : jewelsForSlot) {
                System.out.println("  with Jewel " + jewel.name);
            }
            if(entry.getKey().equals(GearSlot.PET)){
                for(PetTrait trait : best.petTraits) {
                    System.out.println("  with trait " + trait.name);
                }
            }
        }
        System.out.println("TOTAL Stats with Base Stats:");
        System.out.println(best.statsToString());
    }
}