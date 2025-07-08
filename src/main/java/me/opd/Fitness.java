package me.opd;

import me.opd.DataReading.StatBlock;

public class Fitness {
    public static double evaluate(Individual individual) {
        StatBlock stats = individual.evaluateStats().addBaseStats();

        //TODO Normalize weights and get the correct divisors
        //TODO Make the weights configurable or enterable on run with scanner
        double score = 0;
        score += 5 * (stats.health / 30.0);
        score += 3 * (stats.powerpip / 200.0);
        score += 2 * (stats.shadowpip / 20.0);
        score += 1 * (stats.accuracy / 1500.0);
        score += 1 * (stats.pipconversion / 10.0);
        score += 5 * (stats.critical / 30.0);
        score += 3 * (stats.criticalblock / 200.0);
        score += 2 * (stats.resist / 20.0);
        score += 1 * (stats.pierce / 10.0);
        score += 1 * (stats.damage / 10.0);
        score += stats.proviceblade ? 55 : -20;
        score += stats.providesharpen ? 55 : -20;
        //TODO add other corrections, like sharpen blade + item card blade, not having 100% powerpip, certain accuraacy

        return score;
    }
}

