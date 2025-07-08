package me.opd.GA;

import me.opd.Fitness;
import me.opd.Individual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class Selection {
    public static Individual tournament(List<Individual> population) {
        Random rand = new Random();
        List<Individual> contenders = new ArrayList<>();
        for (int i = 0; i < GAConfig.TOURNAMENT_SIZE; i++) {
            contenders.add(population.get(rand.nextInt(population.size())));
        }

        return contenders.stream()
                .max(Comparator.comparingDouble(Fitness::evaluate))
                .get();
    }
}

