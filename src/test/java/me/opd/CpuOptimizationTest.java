package me.opd;

import me.opd.DataReading.*;
import me.opd.JewelHandeling.*;
import me.opd.Optimization.*;
import me.opd.Storage.JsonIO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Differential checks against the implementations retained before CPU tuning. */
class CpuOptimizationTest {
    @Test void detailedAndRankingOnlyScoresMatchOriginalArithmeticExactly() {
        Random random=new Random(913);
        var request=OptimizerTest.request(GearSlot.HAT);
        request.wizard.school=School.STORM; request.wizard.level=180; request.wizard.scaledLevel=170;
        StatScaling scaling=StatScaling.forRequest(request,new Catalog());
        List<StatTarget> targets=new ArrayList<>();
        for (StatType stat : List.of(StatType.DAMAGE,StatType.PIERCE,StatType.ACCURACY,StatType.RESISTANCE,
                StatType.HEALTH,StatType.POWER_PIP_CHANCE)) for (StatTarget.Mode mode : StatTarget.Mode.values()) {
            var target=OptimizerTest.target(stat,stat.schoolSpecific ? School.DEATH : School.UNIVERSAL,mode,30,false);
            target.scale=3.7; target.weight=0.31; target.abovePenalty=0.7; target.belowPenalty=1.3;
            targets.add(target);
            if (mode==StatTarget.Mode.MINIMUM || mode==StatTarget.Mode.MAXIMUM) {
                var hard=OptimizerTest.target(stat,target.school,mode,17,true);
                hard.scale=2.9; hard.weight=1.31; targets.add(hard);
            }
        }
        for (int sample=0;sample<500;sample++) {
            StatBlock stats=new StatBlock();
            for (StatType stat : StatType.values()) for (School school : School.values())
                if (stat.schoolSpecific || school==School.UNIVERSAL)
                    stats.put(stat,school,sample==0 ? -0.0 : random.nextDouble()*1000-50);
            for (StatScaling model : new StatScaling[]{null,scaling}) {
                var original=LegacyFitness.evaluate(stats,targets,model);
                assertEquals(original,Fitness.evaluate(stats,targets,model));
                var score=Fitness.score(stats,targets,model);
                assertEquals(original.feasible(),score.feasible());
                assertEquals(Double.doubleToRawLongBits(original.hardDeficit()),Double.doubleToRawLongBits(score.hardDeficit()));
                assertEquals(Double.doubleToRawLongBits(original.objective()),Double.doubleToRawLongBits(score.objective()));
                var other=LegacyFitness.evaluate(new StatBlock(),targets,model);
                assertEquals(Integer.signum(original.compareTo(other)),Integer.signum(score.compareTo(Fitness.score(new StatBlock(),targets,model))));
            }
        }
        var overflow=OptimizerTest.target(StatType.HEALTH,School.UNIVERSAL,StatTarget.Mode.MAXIMIZE,0,false);
        overflow.weight=Double.MAX_VALUE;
        var stats=new StatBlock().put(StatType.HEALTH,School.UNIVERSAL,2);
        assertThrows(IllegalArgumentException.class,() -> Fitness.score(stats,List.of(overflow),null));
    }

    @Test void socketChoicesAndPetRecalculationMatchOriginalAcrossSeedsScalingAndOwnership() {
        var petFixtures=new PetIntegrationTest();
        Gear hat=OptimizerTest.gear("hat",GearSlot.HAT);
        for (int i=0;i<3;i++) hat.sockets.add(new Socket(JewelType.SWORD_PIN));
        hat.baseStats.put(StatType.DAMAGE,School.DEATH,15.3).put(StatType.DAMAGE,School.UNIVERSAL,2.7);
        Gear pet=OptimizerTest.gear("pet",GearSlot.PET); pet.pet=petFixtures.pet();
        pet.sockets.add(new Socket(JewelType.PET));
        Catalog catalog=OptimizerTest.catalog(hat,pet);
        Random random=new Random(17);
        for (int i=0;i<24;i++) {
            Jewel jewel=OptimizerTest.jewel("pin-"+i,JewelType.SWORD_PIN);
            if (i!=0) {
                jewel.statBonus.put(StatType.DAMAGE,School.DEATH,random.nextDouble()*60);
                jewel.statBonus.put(StatType.ACCURACY,School.STORM,random.nextDouble()*20);
                jewel.statBonus.put(StatType.HEALTH,School.UNIVERSAL,random.nextDouble()*300);
            } else jewel.statBonus.put(StatType.DAMAGE,School.UNIVERSAL,-0.0);
            catalog.jewels.add(jewel);
        }
        catalog.jewels.add(petFixtures.mighty());
        Jewel petDamage=OptimizerTest.jewel("pet-damage",JewelType.PET);
        petDamage.statBonus.put(StatType.DAMAGE,School.DEATH,1.9);
        catalog.jewels.add(petDamage);
        String catalogBefore=JsonIO.encode(catalog);
        for (boolean scaled : new boolean[]{false,true}) for (boolean owned : new boolean[]{false,true}) {
            var request=OptimizerTest.request(GearSlot.HAT,GearSlot.PET);
            request.wizard.school=School.STORM; request.wizard.level=180;
            request.wizard.scaledLevel=scaled ? 170 : null;
            request.wizard.ownedOnly=owned;
            request.wizard.ownedItemIds.addAll(List.of("hat","pet"));
            catalog.jewels.forEach(j -> request.wizard.ownedItemIds.add(j.id));
            request.lockedJewels.put("HAT:0","pin-0");
            request.targets.add(OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MINIMUM,80,true));
            request.targets.add(OptimizerTest.target(StatType.ACCURACY,School.STORM,StatTarget.Mode.MAXIMUM,30,true));
            request.targets.add(OptimizerTest.target(StatType.HEALTH,School.UNIVERSAL,StatTarget.Mode.MAXIMIZE,0,false));
            request.targets.getLast().scale=1000;
            request.targets.add(OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MAXIMIZE,0,false));
            SearchSpace space=new SearchSpace(catalog,request);
            for (int seed=0;seed<60;seed++) {
                Individual actual=space.random(new Random(seed)), expected=actual.copyGenome();
                for (int pass=0;pass<2;pass++) for (GearSlot slot : space.slots) {
                    LegacySocketOptimizer.improve(expected,space,slot);
                    SocketOptimizer.improve(actual,space,slot);
                    assertEquals(expected.signature(),actual.signature(),"seed "+seed+", scaled "+scaled+", owned "+owned);
                    space.validate(actual);
                    assertEquals(expected.evaluateStats().values,actual.evaluateStats().values);
                }
            }
        }
        assertEquals(catalogBefore,JsonIO.encode(catalog));
    }

    @Test void membershipIndexDistinguishesAnEmptySocketFromAnInvalidJewel() {
        Gear hat=OptimizerTest.gear("hat",GearSlot.HAT); hat.sockets.add(new Socket(JewelType.SWORD_PIN));
        Jewel pin=OptimizerTest.jewel("pin",JewelType.SWORD_PIN);
        Catalog catalog=OptimizerTest.catalog(hat); catalog.jewels.add(pin);
        SearchSpace space=new SearchSpace(catalog,OptimizerTest.request(GearSlot.HAT));
        Individual build=space.random(new Random(7));
        build.jewels.get(GearSlot.HAT).set(0,null); space.validate(build);
        build.jewels.get(GearSlot.HAT).set(0,new Jewel());
        assertThrows(IllegalArgumentException.class,() -> space.validate(build));
        Gear invalid=OptimizerTest.gear("invalid",GearSlot.HAT); invalid.id=null;
        build.gearSet.put(GearSlot.HAT,invalid);
        assertThrows(IllegalArgumentException.class,() -> space.validate(build));
    }
}
