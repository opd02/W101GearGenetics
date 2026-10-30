package me.opd.Optimization;
import me.opd.*;
import me.opd.DataReading.*;
import java.util.*;
public record OptimizationResult(String method, boolean exhaustive, long seed, int uniqueEvaluations,
        List<Progress> history, List<Solution> solutions, List<String> warnings) {
    public record Progress(int generation,boolean feasible,double hardDeficit,double objective,int evaluations) {}
    public record Solution(Individual equipment,StatBlock rawEquipmentStats,StatBlock rawCharacterStats,
            Map<School,Map<StatType,Double>> effectiveSchoolStats,FitnessBreakdown fitness,List<String> itemCards,
            List<String> appliedSets,List<GearSlot> differentSlotsFromBest) {}
    public static OptimizationResult create(SearchSpace space,FitnessEvaluator evaluator,List<Progress> history,boolean exact) {
        var ranked=evaluator.evaluated().stream().sorted(FitnessEvaluator.BEST_FIRST).toList();
        List<FitnessEvaluator.Scored> selected=new ArrayList<>();
        Set<String> statSignatures=new HashSet<>();
        for (var candidate : ranked) {
            // Irrelevant equipment swaps do not fill the list. Cards/set activations remain meaningful.
            String stats=candidate.totals().character().values.toString()
                + candidate.totals().cards().stream().sorted().toList() + candidate.totals().appliedSets();
            if (statSignatures.contains(stats)) continue;
            if (selected.stream().anyMatch(s -> differences(s.build(),candidate.build()).size()<space.request.minDifferentSlots)) continue;
            selected.add(candidate); statSignatures.add(stats);
            if (selected.size()>=space.request.topK) break;
        }
        List<Solution> solutions=new ArrayList<>();
        for (var scored : selected) {
            Map<School,Map<StatType,Double>> effective=new EnumMap<>(School.class);
            for (School school : School.values()) {
                Map<StatType,Double> values=new EnumMap<>(StatType.class);
                for (StatType type : StatType.values()) if (type.schoolSpecific)
                    values.put(type,scored.totals().character().effective(type,school));
                effective.put(school,values);
            }
            solutions.add(new Solution(scored.build(),scored.totals().equipment(),scored.totals().character(),effective,
                scored.fitness(),scored.totals().cards(),scored.totals().appliedSets(),differences(selected.getFirst().build(),scored.build())));
        }
        List<String> warnings=new ArrayList<>();
        warnings.add("Stats are raw additive totals; no unverified game curves/caps/rating conversions are applied.");
        if (!exact) warnings.add("Genetic search is heuristic; global optimality and discovery of a feasible build are not guaranteed.");
        if (!solutions.isEmpty() && !solutions.getFirst().fitness().feasible()) warnings.add("No feasible build found; candidates show unmet requirements.");
        if (solutions.size()<space.request.topK) warnings.add("Fewer distinct alternatives met the diversity rules than topK.");
        if (space.request.allowIncompleteItems) warnings.add("Incomplete records are permitted; eligibility and bonuses may be unverified.");
        if (space.request.wizard.baseOverrides!=null) warnings.add("Manual base-stat overrides supplied; unspecified entries use exact source values or zero.");
        return new OptimizationResult(exact?"EXACT":"GA",exact,space.request.search.seed,evaluator.evaluated().size(),
            List.copyOf(history),solutions,warnings);
    }
    public static List<GearSlot> differences(Individual a,Individual b) {
        List<GearSlot> different=new ArrayList<>();
        for (GearSlot slot : GearSlot.values()) {
            Gear x=a.gearSet.get(slot),y=b.gearSet.get(slot);
            if (!Objects.equals(x==null?null:x.id,y==null?null:y.id)
                || !a.jewels.getOrDefault(slot,List.of()).stream().map(j -> j==null?"":j.id).toList()
                    .equals(b.jewels.getOrDefault(slot,List.of()).stream().map(j -> j==null?"":j.id).toList())) different.add(slot);
        }
        return different;
    }
}
