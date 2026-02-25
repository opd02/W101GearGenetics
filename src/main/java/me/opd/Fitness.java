package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.StatBlock;
import me.opd.JewelHandeling.Jewel;

public class Fitness {
    public static double evaluate(Individual individual) {
        StatBlock stats = individual.evaluateStats().addBaseStats();

        double score = 0;

        // Priority: Damage > Pierce > Powerpip
        score += 25 * normalize(stats.damage, 280);      // cap ~238
        score += 10  * normalize(stats.pierce, 68);       // cap ~65
        score += 5  * normalize(stats.powerpip, 100);    // must be 100+

        // Secondary stats
        score += 3  * normalize(stats.critical, 900);
        score += 7  * normalize(stats.shadowpip, 100);
        score += 2  * normalize(stats.resist, 70);
        score += 1  * normalize(stats.accuracy, 40);
        score += 0  * normalize(stats.criticalblock, 700);
        score += 2  * normalize(stats.health, 13500);
        score += 0.5 * normalize(stats.pipconversion, 300);

        // Power pip requirement
        if (stats.powerpip < 100) score -= 100;
        if (stats.pierce < 50) score -= 100;


        // Blade/sharpen logic
        int bladeCount = 0;
        int sharpenCount = 0;

        for (Jewel jewel : individual.getJewels()) {
            if (jewel.statBonus.provideblade) bladeCount++;
            if (jewel.statBonus.providesharpen) sharpenCount++;
        }

        for (Gear gear : individual.getGear()) {
            if (gear.baseStats.provideblade) bladeCount++;
            if (gear.baseStats.providesharpen) sharpenCount++;
        }

        score += bladeCount == 1 ? 100 : (bladeCount > 1 ? -40 * (bladeCount - 1) : -25);
        score += sharpenCount == 1 ? 100 : (sharpenCount > 1 ? -40 * (sharpenCount - 1) : -25);

//        System.out.println(individual.gearSet.values());
//TODO make this spit out the gear set that topped that generation so I can see the evolution
//        for(Gear entry : individual.getGear()) {
//            System.out.println(entry.name);
//            List<Jewel> jewelsForSlot = entry.sockets.jewels.getOrDefault(entry, List.of());
//            for(Jewel jewel : jewelsForSlot) {
//                System.out.println("  with Jewel " + jewel.name);
//            }
//            if(entry.getKey().equals(GearSlot.PET)){
//                for(PetTrait trait : best.petTraits) {
//                    System.out.println("  with trait " + trait.name);
//                }
//            }
//        }


        return score;
    }

    private static double normalize(double stat, double maxExpected) {
        return Math.min(stat / maxExpected, 1.0); // never reward overcapping
    }
}

