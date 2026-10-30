package me.opd.GA;
import me.opd.Individual;
import me.opd.Optimization.SearchSpace;
import java.util.*;
public class PopulationInitializer {
    public static List<Individual> initialize(SearchSpace space,Random random) {
        List<Individual> population=new ArrayList<>();
        for (int i=0;i<space.request.search.populationSize;i++) {
            Individual build=space.random(random);
            // Randomize unlocked sockets, including empty choices, without shifting positions.
            for (var slot : space.slots) {
                var choices=space.socketChoices(build.gearSet.get(slot));
                for (int j=0;j<choices.size();j++) if (!space.request.lockedJewels.containsKey(slot + ":" + j)) {
                    var options=choices.get(j);
                    build.jewels.get(slot).set(j,options.get(random.nextInt(options.size())));
                }
            }
            space.repairJewels(build,random);
            population.add(build);
        }
        return population;
    }
}
