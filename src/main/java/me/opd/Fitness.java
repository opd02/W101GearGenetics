package me.opd;

import me.opd.DataReading.Gear;
import me.opd.DataReading.GearSlot;
import me.opd.DataReading.StatBlock;
import me.opd.JewelHandeling.Jewel;

public class Fitness {
    public static double evaluate(Individual individual) {
        StatBlock stats = individual.evaluateStats().addBaseStats();

        double score = 0;

        // Priority: Damage > Pierce > Powerpip
        score += 25 * normalize(stats.damage, 238);      // cap ~238
        score += 11  * normalize(stats.pierce, 75);       // cap ~65
        score += 5  * normalize(stats.powerpip, 100);    // must be 100+

        // Secondary stats
        score += 3  * normalize(stats.critical, 850);
        score += 4  * normalize(stats.shadowpip, 60);
        score += 2  * normalize(stats.resist, 50);
        score += 1  * normalize(stats.accuracy, 40);
        score += 0  * normalize(stats.criticalblock, 700);
        score += 2  * normalize(stats.health, 11500);
        score += 0.5 * normalize(stats.pipconversion, 300);

        // Power pip requirement
        if (stats.powerpip < 100) score -= 100;

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

        return score;
    }

    private static double normalize(double stat, double maxExpected) {
        return Math.min(stat / maxExpected, 1.0); // never reward overcapping
    }
}

