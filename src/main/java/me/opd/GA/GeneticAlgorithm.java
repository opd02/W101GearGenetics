package me.opd.GA;
import me.opd.*;
import me.opd.Optimization.*;
import java.util.*;
import java.util.function.Consumer;
public class GeneticAlgorithm {
    public static OptimizationResult run(SearchSpace space,Consumer<OptimizationResult.Progress> progress) {
        GAConfig config=space.request.search;
        Random random=new Random(config.seed);
        FitnessEvaluator evaluator=new FitnessEvaluator(space);
        List<Individual> population=PopulationInitializer.initialize(space,random);
        List<OptimizationResult.Progress> history=new ArrayList<>();
        for (int generation=0;generation<=config.generations;generation++) {
            List<FitnessEvaluator.Scored> ranked=population.stream().map(evaluator::evaluate)
                .sorted(FitnessEvaluator.BEST_FIRST).toList();
            var best=evaluator.evaluated().stream().min(FitnessEvaluator.BEST_FIRST).orElseThrow();
            var snapshot=new OptimizationResult.Progress(generation,best.fitness().feasible(),best.fitness().hardDeficit(),
                best.fitness().objective(),evaluator.evaluated().size());
            history.add(snapshot); progress.accept(snapshot);
            if (generation==config.generations) break;
            List<Individual> next=new ArrayList<>();
            for (int i=0;i<config.elites;i++) next.add(ranked.get(i).build().copyGenome());
            while (next.size()<config.populationSize) {
                Individual a=Selection.tournament(ranked,config.tournamentSize,random).build();
                Individual b=Selection.tournament(ranked,config.tournamentSize,random).build();
                Individual child=random.nextDouble()<config.crossoverRate ? GeneticUtils.crossover(a,b,space,random) : a.copyGenome();
                if (random.nextDouble()<config.mutationRate) child=GeneticUtils.mutate(child,space,random);
                next.add(child);
            }
            population=next;
        }
        return OptimizationResult.create(space,evaluator,history,false);
    }
}
