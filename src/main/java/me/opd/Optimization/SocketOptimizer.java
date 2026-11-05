package me.opd.Optimization;

import me.opd.Fitness;
import me.opd.Individual;
import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import me.opd.PetHandeling.PetCalculator;
import java.util.*;

/** Bounded coordinate improvement; complements random exploration without removing domains. */
public final class SocketOptimizer {
    private SocketOptimizer() {}

    public static void improve(Individual build,SearchSpace space,GearSlot slot) {
        Gear holder=build.gearSet.get(slot);
        var choices=space.socketChoices(holder);
        if (choices.isEmpty() || space.request.targets.stream().allMatch(t -> t.mode==StatTarget.Mode.IGNORE)) return;
        var selected=build.jewels.get(slot);
        StatBlock totals=new StatCalculator().calculate(build,space.catalog,space.base).character();
        Fitness.Score fitness=Fitness.score(totals,space.request.targets,space.scaling);
        AddedStats probe=new AddedStats();
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
                    StatBlock trial;
                    if (holder.pet==null) {
                        probe.base=without;
                        probe.addition=candidate==null ? null : candidate.statBonus;
                        trial=probe;
                    } else {
                        trial=without.copy();
                        if (candidate!=null) trial.add(candidate.statBonus);
                    }
                    if (holder.pet!=null) {
                        selected.set(index,candidate);
                        try { trial.add(PetCalculator.calculate(holder.pet,selected)); }
                        catch (IllegalArgumentException incompatibleTalents) { continue; }
                        finally { selected.set(index,prior); }
                    }
                    Fitness.Score score=Fitness.score(trial,space.request.targets,space.scaling);
                    if (score.compareTo(fitness)>0) {
                        if (holder.pet==null) {
                            trial=without.copy();
                            if (candidate!=null) trial.add(candidate.statBonus);
                        }
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

    /** Read-only probe; materialize a map only when a candidate improves the score. */
    private static final class AddedStats extends StatBlock {
        private StatBlock base,addition;
        @Override public double raw(StatType stat,School school) {
            String key=key(stat,school);
            Double before=base.values.get(key);
            Double added=addition==null ? null : addition.values.get(key);
            // Map.merge inserts an absent value unchanged, including negative zero.
            if (added==null) return before==null ? 0.0 : before;
            return before==null ? added : before+added;
        }
    }

    private static void subtract(StatBlock total,StatBlock contribution) {
        contribution.values.forEach((key,value) -> total.values.merge(key,-value,Double::sum));
    }
}
