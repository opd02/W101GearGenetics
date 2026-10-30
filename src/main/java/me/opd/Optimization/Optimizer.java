package me.opd.Optimization;
import me.opd.DataReading.Catalog;
import me.opd.GA.*;
import java.util.function.Consumer;
public class Optimizer {
    public OptimizationResult optimize(Catalog catalog,OptimizationRequest request,Consumer<OptimizationResult.Progress> progress) {
        SearchSpace space=new SearchSpace(catalog,request);
        return request.search.method==GAConfig.Method.EXACT ? ExhaustiveSearch.run(space) : GeneticAlgorithm.run(space,progress);
    }
    public OptimizationResult optimize(Catalog catalog,OptimizationRequest request) { return optimize(catalog,request,p -> {}); }
}
