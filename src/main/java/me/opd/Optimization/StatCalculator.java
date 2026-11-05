package me.opd.Optimization;
import me.opd.Individual;
import me.opd.DataReading.*;
import java.util.*;
/** Raw aggregation only. No undocumented curves, caps or rating-to-chance conversions. */
public class StatCalculator {
    public record Totals(StatBlock equipment, StatBlock character, List<String> cards, List<String> appliedSets) {}
    public Totals calculate(Individual build, Catalog catalog, StatBlock base) {
        StatBlock raw = build.evaluateStats();
        Map<String,Integer> counts = new TreeMap<>();
        List<String> cards = new ArrayList<>(), sets = new ArrayList<>();
        build.gearSet.forEach((slot,g) -> {
            cards.addAll(g.itemCards);
            if (g.pet != null) g.pet.traits.forEach(t -> cards.addAll(t.itemCards));
            g.setIds.forEach(id -> counts.merge(id,1,Integer::sum));
            build.jewels.getOrDefault(slot,List.of()).forEach(j -> {
                if (j != null) { cards.addAll(j.itemCards); j.petTalents.forEach(t -> cards.addAll(t.itemCards)); }
            });
        });
        for (SetBonus bonus : catalog.setBonuses) if (counts.getOrDefault(bonus.setId,0) >= bonus.pieces) {
            raw.add(bonus.stats); cards.addAll(bonus.itemCards); sets.add(bonus.setId + ":" + bonus.pieces);
        }
        raw.validate();
        StatBlock total = base.copy(); total.add(raw); total.validate();
        return new Totals(raw,total,cards,sets);
    }
}
