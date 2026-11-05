package me.opd.Optimization;
import me.opd.*;
import java.util.*;
public class FitnessEvaluator {
    public record Scored(Individual build, StatCalculator.Totals totals, FitnessBreakdown fitness) {}
    private final SearchSpace space;
    private final StatCalculator calculator=new StatCalculator();
    private final Map<String,Scored> cache=new LinkedHashMap<>();
    public FitnessEvaluator(SearchSpace space) { this.space=space; }
    public Scored evaluate(Individual build) {
        space.validate(build);
        return cache.computeIfAbsent(build.signature(),key -> {
            var totals=calculator.calculate(build,space.catalog,space.base);
            return new Scored(build.copyGenome(),totals,Fitness.evaluate(totals.character(),space.request.targets,space.scaling));
        });
    }
    public Collection<Scored> evaluated() { return cache.values(); }
    public static final Comparator<Scored> BEST_FIRST=(a,b) -> {
        int c=b.fitness().compareTo(a.fitness());
        return c!=0 ? c : a.build().signature().compareTo(b.build().signature());
    };
}
