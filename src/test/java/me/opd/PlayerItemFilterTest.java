package me.opd;

import me.opd.DataReading.*;
import me.opd.GA.GAConfig;
import me.opd.JewelHandeling.*;
import me.opd.Optimization.*;
import me.opd.Storage.JsonIO;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerItemFilterTest {
    private Gear archive(String id,String name,GearSlot slot) {
        Gear item=OptimizerTest.gear(id,slot); item.name=name; item.source="wizard101-wad"; item.complete=false;
        item.extensions.put("displayNameKey","Items_001");
        item.extensions.put("member","ObjectData/Gear/Ordinary.xml");
        item.extensions.put("issues",List.of()); return item;
    }
    private OptimizationRequest request(GearSlot... slots) {
        var request=OptimizerTest.request(slots); request.allowIncompleteItems=true; return request;
    }
    @Test void camelCaseInternalMarkersAndMonsterPathsAreRejectedEvenWithLocalizedNames() {
        for (String name : List.of("HG_Dog_BiblioTransform_L170_StatHat","GR_AZ_Parasaur_Priest_A_StatHat",
                "DevOnlyHat","QAAccuracyRing","DebugBoots","Developer Wand","Godmode Ring","DoNotUseHat",
                "Mdeck-B-HG-Dog-Bibliomancer-A-01-L170","NPC Equipment")) {
            Gear item=archive("internal",name,GearSlot.HAT);
            assertEquals("internal-item-marker",PlayerItemFilter.rejectionReason(item),name);
        }
        Gear localized=archive("localized","Ordinary Hat",GearSlot.HAT);
        localized.extensions.put("member","ObjectData/MonsterItems/Stats/Ordinary.xml");
        assertEquals("internal-item-marker",PlayerItemFilter.rejectionReason(localized));
        localized.extensions.put("member","ObjectData/Gear/Ordinary.xml");
        localized.extensions.put("rawObjectName","DeveloperStatHat");
        assertEquals("internal-item-marker",PlayerItemFilter.rejectionReason(localized));
    }
    @Test void unlocalizedAndUnresolvedEligibilityRecordsAreQuarantinedSeparatelyFromEffectIssues() {
        Gear item=archive("item","Damage-Amulet-All-120",GearSlot.AMULET);
        item.extensions.put("issues",List.of("unresolved-display-name"));
        assertEquals("unresolved-display-name",PlayerItemFilter.rejectionReason(item));
        item.extensions.put("issues",List.of()); item.extensions.remove("displayNameKey");
        assertEquals("unresolved-display-name",PlayerItemFilter.rejectionReason(item));
        item.extensions.put("displayNameKey","Items_001");
        for (String issue : List.of("invalid-level-requirement","unsupported-requirement:ReqIsGender",
                "unsupported-requirement-logic","conflicting-school-requirements","level-school-semantics-unverified:FIRE")) {
            item.extensions.put("issues",List.of(issue));
            assertEquals("unresolved-eligibility",PlayerItemFilter.rejectionReason(item),issue);
        }
        item.extensions.put("issues",List.of("unsupported-stat-table:Mastery_Death","unsupported-effect:StartingPipEffectInfo","unresolved-set:123"));
        item.name="Howling Werewolf's Collar";
        assertNull(PlayerItemFilter.rejectionReason(item));
        item.extensions.put("availability","npc-only");
        assertEquals("non-player-metadata",PlayerItemFilter.rejectionReason(item));
    }
    @Test void ordinaryNamesAndManualFixturesKeepTheirExistingContract() {
        for (String name : List.of("Electric Monster Zapper","Testament Wand","Devil's Hat","Statue Staff","Professor's Hoard Hat"))
            assertNull(PlayerItemFilter.rejectionReason(archive("ordinary",name,GearSlot.HAT)),name);
        Gear manual=OptimizerTest.gear("Debug StatHat",GearSlot.HAT);
        assertNull(PlayerItemFilter.rejectionReason(manual));
        manual.source="wizard101-wad-pet-profile";
        assertNull(PlayerItemFilter.rejectionReason(manual));
    }
    @Test void gaAndExactCannotSelectInflatedInternalGearAndKeepCatalogAndRequestsUnchanged() {
        Gear ordinary=archive("ordinary","Ordinary Hat",GearSlot.HAT);
        Gear internal=archive("internal","HG_Dog_BiblioTransform_L170_StatHat",GearSlot.HAT);
        internal.complete=true;
        internal.baseStats.put(StatType.DAMAGE,School.UNIVERSAL,10000);
        Catalog catalog=OptimizerTest.catalog(ordinary,internal); var request=request(GearSlot.HAT);
        request.targets.add(OptimizerTest.target(StatType.DAMAGE,School.DEATH,StatTarget.Mode.MINIMUM,100,true));
        request.targets.add(OptimizerTest.target(StatType.HEALTH,School.UNIVERSAL,StatTarget.Mode.MAXIMIZE,0,false));
        String beforeCatalog=JsonIO.encode(catalog),beforeRequest=JsonIO.encode(request);
        for (GAConfig.Method method : GAConfig.Method.values()) {
            request.search.method=method;
            var result=new Optimizer().optimize(catalog,request);
            assertFalse(result.solutions().getFirst().fitness().feasible());
            assertEquals("ordinary",result.solutions().getFirst().equipment().gearSet.get(GearSlot.HAT).id);
            assertTrue(result.warnings().stream().anyMatch(s -> s.contains("Player-item filter rejected 1") && s.contains(internal.name)));
        }
        request.search.method=GAConfig.Method.GA;
        assertEquals(beforeCatalog,JsonIO.encode(catalog)); assertEquals(beforeRequest,JsonIO.encode(request));
    }
    @Test void namedExclusionsStillResolveAndBothLockFormsRejectInternalItemsWithReasons() {
        Gear ordinary=archive("ordinary","Ordinary Hat",GearSlot.HAT);
        Gear internal=archive("internal","GR_AZ_Parasaur_Priest_A_StatHat",GearSlot.HAT);
        Catalog catalog=OptimizerTest.catalog(ordinary,internal); var request=request(GearSlot.HAT);
        request.excludedGear.add(new GearSelector(internal.name));
        assertEquals(List.of(ordinary),new SearchSpace(catalog,request).gear.get(GearSlot.HAT));
        request.excludedGear.clear(); request.lockedGear.put(GearSlot.HAT,new GearSelector(internal.name));
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request)).getMessage().contains("internal-item-marker"));
        request.lockedGear.clear(); request.lockedItems.put(GearSlot.HAT,internal.id);
        assertTrue(assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request)).getMessage().contains("internal-item-marker"));
    }
    @Test void unsafeJewelsCannotEnterSocketDomainsAndJewelLocksCannotBypassScreening() {
        Gear holder=archive("ordinary","Ordinary Hat",GearSlot.HAT); holder.sockets.add(new Socket(JewelType.SQUARE));
        Jewel jewel=OptimizerTest.jewel("jewel",JewelType.SQUARE); jewel.source="wizard101-wad";
        jewel.name="DeveloperHealthJewel"; jewel.extensions.put("displayNameKey","Items_002");
        jewel.statBonus.put(StatType.HEALTH,School.UNIVERSAL,100000);
        Catalog catalog=OptimizerTest.catalog(holder); catalog.jewels.add(jewel); var request=request(GearSlot.HAT);
        SearchSpace space=new SearchSpace(catalog,request);
        assertEquals(Collections.singletonList(null),space.socketChoices(holder).getFirst());
        assertEquals("internal-item-marker",space.rejectedItems.get(jewel.id));
        request.lockedJewels.put("HAT:0",jewel.id);
        assertThrows(IllegalArgumentException.class,() -> new SearchSpace(catalog,request));
    }
    @Test void ambiguousFriendlyLockIgnoresUnsafeSameNameVersion() {
        Gear ordinary=archive("ordinary","Same Hat",GearSlot.HAT),internal=archive("internal","Same Hat",GearSlot.HAT);
        internal.extensions.put("member","ObjectData/MonsterItems/Stats/Hidden.xml");
        var request=request(GearSlot.HAT); request.lockedGear.put(GearSlot.HAT,new GearSelector("Same Hat"));
        assertEquals(List.of(ordinary),new SearchSpace(OptimizerTest.catalog(ordinary,internal),request).gear.get(GearSlot.HAT));
    }
}
