package me.opd.GA;
import me.opd.Optimization.FitnessEvaluator;
import java.util.*;
public class Selection {
    public static FitnessEvaluator.Scored tournament(List<FitnessEvaluator.Scored> population,int size,Random random) {
        FitnessEvaluator.Scored best=null;
        for (int i=0;i<size;i++) {
            var candidate=population.get(random.nextInt(population.size()));
            if (best==null || FitnessEvaluator.BEST_FIRST.compare(candidate,best)<0) best=candidate;
        }
        return best;
    }
}
