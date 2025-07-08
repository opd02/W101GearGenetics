package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearLoader;

import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));
        List<Gear> importedGear = GearLoader.loadGearFromCSV(System.getProperty("user.dir")+"\\GearDB.csv");
        System.out.println(importedGear.size());
    }
}