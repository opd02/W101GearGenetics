package me.opd;
import me.opd.Optimization.SearchSpace;
import me.opd.DataReading.*;
import me.opd.JewelHandeling.Jewel;
import java.util.*;
public class GeneticUtils {
    public static Individual crossover(Individual a,Individual b,SearchSpace space,Random random) {
        Individual child=new Individual();
        for (GearSlot slot : space.slots) {
            Individual parent=random.nextBoolean()?a:b;
            child.gearSet.put(slot,parent.gearSet.get(slot));
            child.jewels.put(slot,new ArrayList<>(parent.jewels.get(slot)));
        }
        space.repairJewels(child,random);
        return child;
    }
    public static Individual mutate(Individual parent,SearchSpace space,Random random) {
        Individual child=parent.copyGenome();
        GearSlot slot=space.slots.get(random.nextInt(space.slots.size()));
        if (random.nextBoolean()) {
            List<Gear> choices=space.gear.get(slot);
            child.gearSet.put(slot,choices.get(random.nextInt(choices.size())));
            child.jewels.put(slot,new ArrayList<>()); // new holder; resolve each positional socket
        } else {
            var lists=space.socketChoices(child.gearSet.get(slot));
            if (!lists.isEmpty()) {
                int index=random.nextInt(lists.size());
                if (!space.request.lockedJewels.containsKey(slot + ":" + index)) {
                    List<Jewel> options=lists.get(index);
                    child.jewels.get(slot).set(index,options.get(random.nextInt(options.size())));
                }
            }
        }
        space.repairJewels(child,random);
        return child;
    }
}
