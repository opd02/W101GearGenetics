package me.opd;

import me.opd.Fitness;
import me.opd.Individual;
import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import me.opd.PetHandeling.PetCalculator;
import me.opd.Optimization.*;
import java.util.*;

/** Frozen pre-tuning reference (2026-11-05); keep independent of the optimized probe path. */
final class LegacySocketOptimizer {
    private LegacySocketOptimizer() {}

    public static void improve(Individual build,SearchSpace space,GearSlot slot) {
        Gear holder=build.gearSet.get(slot);
        var choices=space.socketChoices(holder);
        if (choices.isEmpty() || space.request.targets.stream().allMatch(t -> t.mode==StatTarget.Mode.IGNORE)) return;
        var selected=build.jewels.get(slot);
        StatBlock totals=new StatCalculator().calculate(build,space.catalog,space.base).character();
        FitnessBreakdown fitness=LegacyFitness.evaluate(totals,space.request.targets,space.scaling);
        Map<String,Integer> used=new HashMap<>();
        build.jewels.values().forEach(list -> list.forEach(j -> { if (j!=null) used.merge(j.id,1,Integer::sum); }));
        for (int pass=0;pass<2;pass++) {
            boolean changed=false;
            for (int index=0;index<choices.size();index++) {
                if (space.request.lockedJewels.containsKey(slot+":"+index)) continue;
                Jewel prior=selected.get(index), best=prior;
                if (prior!=null) used.merge(prior.id,-1,Integer::sum);
                StatBlock without=totals.copy();
                if (prior!=null) subtract(without,prior.statBonus);
                if (holder.pet!=null) subtract(without,PetCalculator.calculate(holder.pet,selected));
                StatBlock bestTotals=totals;
                for (Jewel candidate : choices.get(index)) {
                    if (candidate==prior || !space.available(candidate,used)) continue;
                    StatBlock trial=without.copy();
                    if (candidate!=null) trial.add(candidate.statBonus);
                    if (holder.pet!=null) {
                        selected.set(index,candidate);
                        try { trial.add(PetCalculator.calculate(holder.pet,selected)); }
                        catch (IllegalArgumentException incompatibleTalents) { continue; }
                        finally { selected.set(index,prior); }
                    }
                    FitnessBreakdown score=LegacyFitness.evaluate(trial,space.request.targets,space.scaling);
                    if (score.compareTo(fitness)>0) {
                        best=candidate; bestTotals=trial; fitness=score;
                    }
                }
                selected.set(index,best);
                if (best!=null) used.merge(best.id,1,Integer::sum);
                totals=bestTotals;
                changed|=best!=prior;
            }
            if (!changed) break;
        }
    }

    private static void subtract(StatBlock total,StatBlock contribution) {
        contribution.values.forEach((key,value) -> total.values.merge(key,-value,Double::sum));
    }
}

