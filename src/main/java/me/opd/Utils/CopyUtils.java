package me.opd.Utils;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearSlot;
import me.opd.DataReading.StatBlock;
import me.opd.Individual;
import me.opd.JewelHandeling.Jewel;
import me.opd.JewelHandeling.Socket;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CopyUtils {
        public static Individual deepCopy(Individual original) {
            Individual copy = new Individual();

            // Deep copy gear set
            for (Map.Entry<GearSlot, Gear> entry : original.gearSet.entrySet()) {
                copy.gearSet.put(entry.getKey(), copyGear(entry.getValue()));
            }

            // Deep copy jewels
            for (Map.Entry<GearSlot, List<Jewel>> entry : original.jewels.entrySet()) {
                List<Jewel> jewelCopy = new ArrayList<>();
                for (Jewel j : entry.getValue()) {
                    jewelCopy.add(copyJewel(j));
                }
                copy.jewels.put(entry.getKey(), jewelCopy);
            }

            return copy;
        }

        private static Gear copyGear(Gear original) {
            Gear copy = new Gear();
            copy.name = original.name;
            copy.slot = original.slot;
            copy.baseStats = copyStatBlock(original.baseStats);

            copy.sockets = new ArrayList<>();
            for (Socket s : original.sockets) {
                Socket socketCopy = new Socket(s.type);
                copy.sockets.add(socketCopy);
            }

            return copy;
        }

        private static Jewel copyJewel(Jewel original) {
            Jewel copy = new Jewel(original.name,original.type,copyStatBlock(original.statBonus));
            return copy;
        }

        private static StatBlock copyStatBlock(StatBlock original) {
            StatBlock copy = new StatBlock();
            copy.health = original.health;
            copy.powerpip = original.powerpip;
            copy.shadowpip = original.shadowpip;
            copy.accuracy = original.accuracy;
            copy.pipconversion = original.pipconversion;
            copy.criticalblock = original.criticalblock;
            copy.critical = original.critical;
            copy.resist = original.resist;
            copy.pierce = original.pierce;
            copy.damage = original.damage;
            copy.proviceblade = original.proviceblade;

            return copy;
        }
    }


