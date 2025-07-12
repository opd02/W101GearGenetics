package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearSlot;

import java.util.List;

public class StatBounder {
    public static double[] findDamageBounds(List<Gear> gearPool) {
        List<Gear> hats = filterBySlot(gearPool, GearSlot.HAT);
        List<Gear> robes = filterBySlot(gearPool, GearSlot.ROBE);
        List<Gear> boots = filterBySlot(gearPool, GearSlot.BOOTS);
        List<Gear> wands = filterBySlot(gearPool, GearSlot.WAND);
        List<Gear> athames = filterBySlot(gearPool, GearSlot.ATHAME);
        List<Gear> amulets = filterBySlot(gearPool, GearSlot.AMULET);
        List<Gear> rings = filterBySlot(gearPool, GearSlot.RING);

        double minDamage = Double.MAX_VALUE;
        double maxDamage = Double.MIN_VALUE;

        for (Gear hat : hats) {
            for (Gear robe : robes) {
                for (Gear boot : boots) {
                    for (Gear wand : wands) {
                        for (Gear athame : athames) {
                            for (Gear amulet : amulets) {
                                for (Gear ring : rings) {

                                    Individual ind = new Individual();
                                    ind.gearSet.put(GearSlot.HAT, hat);
                                    ind.gearSet.put(GearSlot.ROBE, robe);
                                    ind.gearSet.put(GearSlot.BOOTS, boot);
                                    ind.gearSet.put(GearSlot.WAND, wand);
                                    ind.gearSet.put(GearSlot.ATHAME, athame);
                                    ind.gearSet.put(GearSlot.AMULET, amulet);
                                    ind.gearSet.put(GearSlot.RING, ring);

                                    double dmg = ind.evaluateStats().shadowpip;

                                    if (dmg < minDamage) minDamage = dmg;
                                    if (dmg > maxDamage) maxDamage = dmg;
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("Min Damage: %.2f, Max Damage: %.2f%n", minDamage, maxDamage);
        return new double[]{minDamage, maxDamage};
    }

    private static List<Gear> filterBySlot(List<Gear> gearPool, GearSlot slot) {
        return gearPool.stream()
                .filter(g -> g.slot == slot)
                .toList();
    }
}

