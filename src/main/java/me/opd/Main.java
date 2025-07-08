package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearLoader;
import me.opd.DataReading.GearSlot;
import me.opd.GA.GeneticAlgorithm;
import me.opd.JewelHandeling.Jewel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        //TODO Add the real stats to the DBs from the wiki
        List<Gear> gearPool = GearLoader.loadGearFromCSV(System.getProperty("user.dir")+"\\GearDB.csv");
        List<Jewel> jewelPool = GearLoader.loadJewelsFromCSV(System.getProperty("user.dir")+"\\JewelDB.csv");

        Individual best = GeneticAlgorithm.run(gearPool, jewelPool);

        System.out.println("Best Gear Set:");
        for (Map.Entry<GearSlot, Gear> entry : best.gearSet.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue().name);
        }
        System.out.println("Stats:");
        System.out.println(best.statsToString());
        //TODO Also print out what the stats would be for a base player at max level. Include minimum health and powerpip.

    }
}