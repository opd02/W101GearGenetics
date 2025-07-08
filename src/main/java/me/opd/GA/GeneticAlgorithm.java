package me.opd.GA;

import me.opd.DataReading.Gear;
import me.opd.Fitness;
import me.opd.GeneticUtils;
import me.opd.Individual;
import me.opd.JewelHandeling.Jewel;
import me.opd.Utils.CopyUtils;

import java.util.ArrayList;
import java.util.List;

public class GeneticAlgorithm {
    public static Individual run(List<Gear> gearPool, List<Jewel> jewelPool) {
        List<Individual> population = PopulationInitializer.initialize(gearPool, jewelPool);
        Individual best = null;
        double bestFitness = Double.NEGATIVE_INFINITY;

        for (int gen = 0; gen < GAConfig.GENERATIONS; gen++) {
            List<Individual> newGen = new ArrayList<>();

            while (newGen.size() < GAConfig.POPULATION_SIZE) {
                Individual parent1 = Selection.tournament(population);
                Individual parent2 = Selection.tournament(population);

                Individual child;

                if (Math.random() < GAConfig.CROSSOVER_RATE) {
                    child = GeneticUtils.crossover(parent1, parent2);
                } else {
                    child = CopyUtils.deepCopy(parent1);
                }

                if (Math.random() < GAConfig.MUTATION_RATE) {
                    child = GeneticUtils.mutate(child, gearPool, jewelPool);
                }

                newGen.add(child);
            }

            population = newGen;

            // Check for best individual
            for (Individual ind : population) {
                double fit = Fitness.evaluate(ind);
                if (fit > bestFitness) {
                    bestFitness = fit;
                    best = CopyUtils.deepCopy(ind);
                }
            }

            System.out.printf("Gen %d — Best Fitness: %.4f%n", gen + 1, bestFitness);
        }

        return best;
    }
}

