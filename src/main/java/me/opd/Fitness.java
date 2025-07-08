package me.opd;

import me.opd.DataReading.StatBlock;

public class Fitness {
    public static double evaluate(Individual individual) {
        StatBlock stats = individual.evaluateStats();

        //TODO Normalize weights and get the correct divisors
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
        score += stats.proviceblade ? 30 : -20;

        return score;
    }
}

