package me.opd.Optimization;
import me.opd.*;
import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import java.util.*;
public class ExhaustiveSearch {
    public static OptimizationResult run(SearchSpace space) {
        long limit=space.request.search.exactLimit;
        if (limit==Long.MAX_VALUE || space.combinations(limit)>limit)
            throw new IllegalArgumentException("Exact search exceeds exactLimit; increase the limit or use GA");
        FitnessEvaluator evaluator=new FitnessEvaluator(space);
        visitSlot(space,evaluator,new Individual(),0,new HashMap<>());
        return OptimizationResult.create(space,evaluator,List.of(),true);
    }
    private static void visitSlot(SearchSpace space,FitnessEvaluator evaluator,Individual build,int index,Map<String,Integer> used) {
        if (index==space.slots.size()) { evaluator.evaluate(build); return; }
        GearSlot slot=space.slots.get(index);
        for (Gear item : space.gear.get(slot)) {
            build.gearSet.put(slot,item);
            build.jewels.put(slot,new ArrayList<>());
            visitSocket(space,evaluator,build,index,0,used);
        }
    }
    private static void visitSocket(SearchSpace space,FitnessEvaluator evaluator,Individual build,int slotIndex,int socketIndex,Map<String,Integer> used) {
        GearSlot slot=space.slots.get(slotIndex);
        var choices=space.socketChoices(build.gearSet.get(slot));
        if (socketIndex==choices.size()) { visitSlot(space,evaluator,build,slotIndex+1,used); return; }
        List<Jewel> selected=build.jewels.get(slot);
        for (Jewel jewel : choices.get(socketIndex)) if (space.available(jewel,used)) {
            selected.add(jewel);
            if (jewel!=null) used.merge(jewel.id,1,Integer::sum);
            visitSocket(space,evaluator,build,slotIndex,socketIndex+1,used);
            if (jewel!=null) used.merge(jewel.id,-1,Integer::sum);
            selected.removeLast();
        }
    }
}
