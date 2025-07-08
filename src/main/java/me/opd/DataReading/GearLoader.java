package me.opd.DataReading;

import me.opd.JewelHandeling.Jewel;
import me.opd.JewelHandeling.Socket;
import me.opd.PetHandeling.PetTrait;

import java.io.*;
import java.util.*;

public class GearLoader {
    public static List<Gear> loadGearFromCSV(String path) throws IOException {
        List<Gear> gearList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");

                Gear gear = new Gear();
                gear.name = fields[0].trim();
                gear.slot = GearSlot.valueOf(fields[1].trim());

                StatBlock stats = statTrim(fields);
                gear.baseStats = stats;

                List<Socket> sockets = new ArrayList<>();
                if (!fields[14].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[14].trim())));
                if (!fields[15].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[15].trim())));
                if (!fields[16].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[16].trim())));
                if (!fields[17].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[17].trim())));

                gear.sockets = sockets;

                gearList.add(gear);
            }
        }
        return gearList;
    }

    public static List<Jewel> loadJewelsFromCSV(String path) throws IOException {
        List<Jewel> jewelList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");

                Jewel jewel = new Jewel();
                jewel.name = fields[0].trim();
                jewel.type = JewelType.valueOf(fields[1].trim());

                StatBlock stats = statTrim(fields);
                jewel.statBonus = stats;
//
//                List<Socket> sockets = new ArrayList<>();
//                if (!fields[13].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[13].trim())));
//                if (!fields[14].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[14].trim())));
//                if (!fields[15].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[15].trim())));
//                if (!fields[16].trim().equals("null")) sockets.add(new Socket(JewelType.valueOf(fields[16].trim())));
//
//                gear.sockets = sockets;

                jewelList.add(jewel);
            }
        }
        return jewelList;
    }

    public static List<PetTrait> loadTraitsFromCSV(String path) throws IOException {
        List<PetTrait> jewelList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String header = reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");

                PetTrait trait = new PetTrait();
                trait.name = fields[0].trim();

                StatBlock stats = statTrim(fields);
                trait.statBonus = stats;

                jewelList.add(trait);
            }
        }
        return jewelList;
    }

    private static StatBlock statTrim(String[] fields) {
        StatBlock stats = new StatBlock();
        stats.health = Integer.parseInt(fields[2].trim());
        stats.powerpip = Integer.parseInt(fields[3].trim());
        stats.shadowpip = Integer.parseInt(fields[4].trim());
        stats.pipconversion = Integer.parseInt(fields[5].trim());
        stats.accuracy = Integer.parseInt(fields[6].trim());
        stats.critical = Integer.parseInt(fields[7].trim());
        stats.criticalblock = Integer.parseInt(fields[8].trim());
        stats.damage = Integer.parseInt(fields[9].trim());
        stats.resist = Integer.parseInt(fields[10].trim());
        stats.pierce = Integer.parseInt(fields[11].trim());
        stats.proviceblade = Boolean.parseBoolean(fields[12].trim());
        stats.providesharpen = Boolean.parseBoolean(fields[13].trim());
        return stats;
    }
}
