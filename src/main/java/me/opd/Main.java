package me.opd;
import me.opd.DataReading.*;
import me.opd.Optimization.*;
import me.opd.Storage.*;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class Main {
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        try {
            if (args.length==0 || args[0].equals("help") || args[0].equals("--help")) { help(out); return 0; }
            Map<String,String> options=parse(args);
            switch (args[0]) {
                case "init" -> { check(options,"output"); JsonIO.write(path(options,"output"),interactive(new Scanner(System.in),out)); out.println("Request saved."); }
                case "optimize" -> {
                    check(options,"request","profile","inventory","catalog","db","output","save-request","quiet");
                    if (options.containsKey("request")==options.containsKey("profile")) throw new IllegalArgumentException("Choose exactly one of --request or --profile");
                    if (options.containsKey("catalog")==options.containsKey("db")) throw new IllegalArgumentException("Choose exactly one of --catalog or --db");
                    OptimizationRequest request;
                    Catalog catalog;
                    if (options.containsKey("db")) try (var repo=new SqliteItemRepository(path(options,"db"))) {
                        request=options.containsKey("request") ? JsonIO.readRequest(path(options,"request")) : repo.loadProfile(required(options,"profile"));
                        String inventory=options.getOrDefault("inventory",options.get("profile"));
                        if (inventory!=null) request.wizard.ownedQuantities.putAll(repo.ownership(inventory));
                        catalog=repo.snapshot();
                    } else {
                        if (options.containsKey("profile") || options.containsKey("inventory")) throw new IllegalArgumentException("Saved profiles/inventory require --db");
                        request=JsonIO.readRequest(path(options,"request")); catalog=JsonIO.readCatalog(path(options,"catalog"));
                    }
                    if (options.containsKey("save-request")) JsonIO.write(path(options,"save-request"),request);
                    var result=new Optimizer().optimize(catalog,request,p -> {
                        if (!options.containsKey("quiet") && (p.generation()%10==0 || p.generation()==request.search.generations))
                            err.printf(Locale.ROOT,"Generation %d: feasible=%s deficit=%.4f objective=%.4f (%d unique)%n",
                                p.generation(),p.feasible(),p.hardDeficit(),p.objective(),p.evaluations());
                    });
                    printResult(result,out);
                    if (options.containsKey("output")) JsonIO.write(path(options,"output"),result);
                }
                case "import-json" -> {
                    check(options,"db","catalog");
                    Catalog catalog=JsonIO.readCatalog(path(options,"catalog"));
                    try (var repo=new SqliteItemRepository(path(options,"db"))) { repo.importCatalog(catalog); }
                    out.printf("Imported %d gear and %d jewels.%n",catalog.gear.size(),catalog.jewels.size());
                }
                case "export-json" -> {
                    check(options,"db","output");
                    try (var repo=new SqliteItemRepository(path(options,"db"))) { JsonIO.write(path(options,"output"),repo.snapshot()); }
                    out.println("Catalog exported.");
                }
                case "import-csv" -> {
                    check(options,"db","gear","jewels","traits","legacy-school","traits-output");
                    School school=School.valueOf(required(options,"legacy-school").toUpperCase(Locale.ROOT));
                    Catalog catalog=new Catalog();
                    catalog.gear=GearLoader.loadGearFromCSV(required(options,"gear"),school);
                    catalog.jewels=GearLoader.loadJewelsFromCSV(required(options,"jewels"),school);
                    if (options.containsKey("traits")) JsonIO.write(Path.of(options.getOrDefault("traits-output","target/legacy-traits.json")),
                        GearLoader.loadTraitsFromCSV(options.get("traits"),school));
                    try (var repo=new SqliteItemRepository(path(options,"db"))) { repo.importCatalog(catalog); }
                    out.println("Legacy records imported as incomplete. Verify eligibility, stat scopes, talents and set rules before relying on results.");
                }
                case "profile-save" -> {
                    check(options,"db","name","request");
                    var request=JsonIO.readRequest(path(options,"request"));
                    try (var repo=new SqliteItemRepository(path(options,"db"))) { repo.saveProfile(required(options,"name"),request); }
                    out.println("Profile saved.");
                }
                case "profile-export" -> {
                    check(options,"db","name","output");
                    try (var repo=new SqliteItemRepository(path(options,"db"))) { JsonIO.write(path(options,"output"),repo.loadProfile(required(options,"name"))); }
                }
                case "own" -> {
                    check(options,"db","profile","id","quantity");
                    try (var repo=new SqliteItemRepository(path(options,"db"))) {
                        repo.setOwnedQuantity(required(options,"profile"),required(options,"id"),Integer.parseInt(required(options,"quantity")));
                    }
                    out.println("Inventory updated.");
                }
                case "find" -> {
                    check(options,"db","catalog","name","slot","school","level","limit");
                    if (options.containsKey("catalog")==options.containsKey("db")) throw new IllegalArgumentException("Choose exactly one of --catalog or --db");
                    String query=GearSelector.normalize(required(options,"name"));
                    if (query.isEmpty()) throw new IllegalArgumentException("Find name cannot be blank");
                    GearSlot slot=options.containsKey("slot")?GearSlot.valueOf(options.get("slot").toUpperCase(Locale.ROOT)):null;
                    School school=options.containsKey("school")?School.valueOf(options.get("school").toUpperCase(Locale.ROOT)):null;
                    int level=Integer.parseInt(options.getOrDefault("level",Integer.toString(Integer.MAX_VALUE)));
                    int limit=Integer.parseInt(options.getOrDefault("limit","20"));
                    if (level<1 || limit<1 || limit>1000 || school==School.UNIVERSAL) throw new IllegalArgumentException("Use a positive character level, a wizard school and limit 1..1000");
                    Catalog catalog;
                    if (options.containsKey("catalog")) catalog=JsonIO.readCatalog(path(options,"catalog"));
                    else {
                        if (!Files.isRegularFile(path(options,"db"))) throw new IllegalArgumentException("Catalog database not found");
                        try (var repo=new SqliteItemRepository(path(options,"db"))) { catalog=repo.snapshot(); }
                    }
                    var matches=catalog.gear.stream().filter(g -> GearSelector.normalize(g.name).contains(query))
                        .filter(g -> slot==null || g.slot==slot).filter(g -> g.level<=level && (school==null || g.schools.isEmpty() || g.schools.contains(school)))
                        .sorted(Comparator.comparing((Gear g) -> GearSelector.normalize(g.name)).thenComparingInt(g -> g.level).thenComparing(g -> g.id)).toList();
                    for (Gear gear : matches.stream().limit(limit).toList()) out.printf("%s | %s | level %d | schools=%s | complete=%s | %s | %s%n",
                        gear.name,gear.slot,gear.level,gear.schools.isEmpty()?"all":gear.schools,gear.complete,gear.source,gear.id);
                    out.printf("Showing %d of %d matching gear records. Locks use exact names; add an item level to distinguish versions.%n",Math.min(limit,matches.size()),matches.size());
                }
                case "list" -> {
                    check(options,"db","slot","school","level","stat","stat-school","minimum");
                    var query=new ItemRepository.Query(GearSlot.valueOf(required(options,"slot")),School.valueOf(required(options,"school")),
                        Integer.parseInt(required(options,"level")),options.containsKey("stat")?StatType.valueOf(options.get("stat")):null,
                        School.valueOf(options.getOrDefault("stat-school","UNIVERSAL")),Double.parseDouble(options.getOrDefault("minimum","0")));
                    try (var repo=new SqliteItemRepository(path(options,"db"))) {
                        for (Gear item : repo.query(query)) out.println(item.id + " | " + item.name + " | level " + item.level + " | complete=" + item.complete);
                    }
                }
                default -> throw new IllegalArgumentException("Unknown command: " + args[0] + ". Run help.");
            }
            return 0;
        } catch (Exception e) { err.println("Error: " + e.getMessage()); return 2; }
    }
    private static Map<String,String> parse(String[] args) {
        Map<String,String> result=new LinkedHashMap<>();
        for (int i=1;i<args.length;i++) {
            String key=args[i];
            if (!key.startsWith("--")) throw new IllegalArgumentException("Expected --option, got " + key);
            key=key.substring(2);
            String value;
            if (key.equals("quiet")) value="true";
            else { if (++i>=args.length || args[i].startsWith("--")) throw new IllegalArgumentException("Missing value for --" + key); value=args[i]; }
            if (result.putIfAbsent(key,value)!=null) throw new IllegalArgumentException("Duplicate --" + key);
        }
        return result;
    }
    private static void check(Map<String,String> options,String... allowed) {
        Set<String> known=Set.of(allowed);
        for (String key : options.keySet()) if (!known.contains(key)) throw new IllegalArgumentException("Unknown option --" + key);
    }
    private static String required(Map<String,String> options,String key) {
        String value=options.get(key);
        if (value==null || value.isBlank()) throw new IllegalArgumentException("Missing --" + key);
        return value;
    }
    private static Path path(Map<String,String> options,String key) { return Path.of(required(options,key)); }
    public static void printResult(OptimizationResult result,PrintStream out) {
        out.printf("Search: %s | exhaustive=%s | seed=%d | unique evaluations=%d%n",
            result.method(),result.exhaustive(),result.seed(),result.uniqueEvaluations());
        if (result.scaling()!=null) out.printf("Character level: %d | scaled combat level: %d | targets use POST-SCALING stats | table: %s%n",
            result.scaling().characterLevel(),result.scaling().scaledLevel(),result.scaling().tableVersion());
        int rank=0;
        for (var solution : result.solutions()) {
            out.printf(Locale.ROOT,"%n#%d feasible=%s hard deficit=%.4f objective=%.4f%n",
                ++rank,solution.fitness().feasible(),solution.fitness().hardDeficit(),solution.fitness().objective());
            for (GearSlot slot : GearSlot.values()) if (solution.equipment().gearSet.containsKey(slot)) {
                Gear item=solution.equipment().gearSet.get(slot);
                out.println(slot + ": " + item.name + " [" + item.id + "]");
                var jewels=solution.equipment().jewels.get(slot);
                for (int i=0;i<jewels.size();i++) out.println("  socket " + i + " (" + item.sockets.get(i).type + "): "
                    + (jewels.get(i)==null?"empty":jewels.get(i).name + " [" + jewels.get(i).id + "]"));
                if (item.pet!=null) item.pet.traits.forEach(t -> out.println("  talent: " + t.name + " [" + t.id + "]"));
                if (item.pet!=null) {
                    out.println("  pet: hypothetical=" + item.pet.hypothetical + " age=" + item.pet.age
                        + " attributes=" + item.pet.attributes + " rounding=" + item.pet.rounding);
                    out.println("  pet talent totals including jewel: " + me.opd.PetHandeling.PetCalculator.calculate(item.pet,jewels));
                    for (var jewel : jewels) if (jewel != null) jewel.petTalents.forEach(t -> out.println("  jewel talent: " + t.name + " [" + t.id + "]"));
                }
            }
            out.println(result.scaling()==null?"Effective school totals (raw units):":"Effective school totals AFTER level scaling:");
            solution.effectiveSchoolStats().forEach((school,stats) -> out.println("  " + school + ": " + stats));
            out.println("Raw equipment bonuses: " + solution.rawEquipmentStats());
            out.println("Raw character totals: " + solution.rawCharacterStats());
            if (result.scaling()!=null) out.println("Post-scaling character totals used for targets: "+solution.targetStats());
            out.println("Cards/effects: " + solution.itemCards() + "; set thresholds: " + solution.appliedSets());
            for (var component : solution.fitness().components()) out.printf(Locale.ROOT,
                "  %s %s actual=%.2f desired=%.2f %s achieved=%s distance=%.4f weight=%.2f scale=%.2f hard=%.4f soft=%.4f%n",
                component.stat(),component.mode(),component.actual(),component.desired(),component.mandatory()?"MANDATORY":"soft",
                component.achieved(),component.normalizedDistance(),component.weight(),component.scale(),component.hardContribution(),component.contribution());
            out.println("Different slots from #1: " + solution.differentSlotsFromBest());
        }
        result.warnings().forEach(w -> out.println("Note: " + w));
    }
    private static OptimizationRequest interactive(Scanner input,PrintStream out) throws IOException {
        OptimizationRequest r=new OptimizationRequest();
        out.println("School (FIRE/ICE/STORM/MYTH/LIFE/DEATH/BALANCE):");
        r.wizard.school=School.valueOf(input.nextLine().trim().toUpperCase(Locale.ROOT));
        out.println("Current character level (gear eligibility):"); r.wizard.level=Integer.parseInt(input.nextLine().trim());
        out.println("Scaled combat level (blank for unscaled stats):");
        String scaledInput=input.nextLine().trim();
        if (!scaledInput.isEmpty()) r.wizard.scaledLevel=Integer.parseInt(scaledInput);
        out.println("Use manual base stats? yes/no (no requires an exact catalog entry):");
        if (input.nextLine().trim().equalsIgnoreCase("yes")) {
            r.wizard.baseOverrides=new StatBlock();
            out.println("Enter base STAT:SCHOOL=value entries, separated by commas; blank explicitly uses zero:");
            String line=input.nextLine().trim();
            if (!line.isEmpty()) for (String entry : line.split(",")) {
                String[] pair=entry.split("="); r.wizard.baseOverrides.values.put(pair[0].trim(),Double.parseDouble(pair[1].trim()));
            }
        }
        out.println("Add targets as STAT SCHOOL MODE VALUE MANDATORY WEIGHT SCALE; blank ends. Targets use post-scaling stats when a scaled level is set.");
        out.println("Example: DAMAGE STORM MINIMUM 150 true 1 150");
        for (String line=input.nextLine().trim();!line.isEmpty();line=input.nextLine().trim()) {
            String[] p=line.split("\\s+");
            if (p.length!=7) throw new IllegalArgumentException("Target requires seven fields");
            StatTarget t=new StatTarget(); t.stat=StatType.valueOf(p[0].toUpperCase(Locale.ROOT)); t.school=School.valueOf(p[1].toUpperCase(Locale.ROOT));
            t.mode=StatTarget.Mode.valueOf(p[2].toUpperCase(Locale.ROOT)); t.value=Double.parseDouble(p[3]);
            if (!Set.of("true","false").contains(p[4].toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Mandatory must be true or false");
            t.mandatory=Boolean.parseBoolean(p[4]); t.weight=Double.parseDouble(p[5]); t.scale=Double.parseDouble(p[6]); r.targets.add(t);
        }
        out.println("Locked gear as JSON, e.g. {\"HAT\":\"Exact item name\"} or {\"HAT\":{\"name\":\"Exact item name\",\"level\":180}}; blank for none:");
        String line=input.nextLine().trim();
        if (!line.isEmpty()) r.lockedGear=JsonIO.MAPPER.readValue(line,new TypeReference<Map<GearSlot,GearSelector>>() {});
        out.println("Locked sockets SLOT:0=jewel-id, comma separated; blank for none:");
        line=input.nextLine().trim();
        if (!line.isEmpty()) for (String pair : line.split(",")) { String[] p=pair.split("=",2); r.lockedJewels.put(p[0].trim().toUpperCase(Locale.ROOT),p[1].trim()); }
        out.println("Excluded gear as a JSON array, e.g. [\"Exact item name\",{\"name\":\"Other item\",\"level\":170}]; blank for none:");
        line=input.nextLine().trim(); if (!line.isEmpty()) r.excludedGear=JsonIO.MAPPER.readValue(line,new TypeReference<List<GearSelector>>() {});
        out.println("Owned only? yes/no:"); r.wizard.ownedOnly=input.nextLine().trim().equalsIgnoreCase("yes");
        if (r.wizard.ownedOnly) {
            out.println("Owned item-id=quantity entries, comma separated:");
            line=input.nextLine().trim();
            if (!line.isEmpty()) for (String pair : line.split(",")) { String[] p=pair.split("=",2); r.wizard.ownedQuantities.put(p[0].trim(),Integer.parseInt(p[1].trim())); }
        }
        out.println("Random seed:"); r.search.seed=Long.parseLong(input.nextLine().trim());
        r.validate(); return r;
    }
    private static void help(PrintStream out) {
        out.println("""
            W101GearGenetics (Java 22+)
            init --output request.json
            optimize --request request.json (--catalog catalog.json | --db items.sqlite) [--output results.json] [--save-request copy.json] [--quiet]
            optimize --db items.sqlite --profile NAME [--output results.json]
            import-json --db items.sqlite --catalog catalog.json
            export-json --db items.sqlite --output catalog.json
            import-csv --db items.sqlite --gear GearDB.csv --jewels JewelDB.csv --legacy-school FIRE [--traits PetTraitsDB.csv] [--traits-output traits.json]
            profile-save --db items.sqlite --name NAME --request request.json
            profile-export --db items.sqlite --name NAME --output request.json
            own --db items.sqlite --profile NAME --id ITEM-ID --quantity N
            list --db items.sqlite --slot HAT --school DEATH --level 170 [--stat DAMAGE --stat-school STORM --minimum 20]
            find (--catalog catalog.json | --db items.sqlite) --name "part of a gear name" [--slot HAT] [--school STORM] [--level 180] [--limit 20]
            Enum inputs and JSON use uppercase. Run examples/offschool-request.json with examples/catalog.json.
            Requests: wizard.level controls gear eligibility; optional wizard.scaledLevel makes all targets post-scaling.
            Optional wizard.scaledCapOverrides supplies modeled post-scaling ceilings while retaining bundled scaling ratios.
            Name constraints: lockedGear maps slots to names or {name,level}; excludedGear lists names or {name,level,slot}.
            Pet search: petSearch.mode is PREDEFINED (default) or AUTO; AUTO needs template and 5..20 talentPool names.
            petSearch.lockedTalents locks manifested talent names in either mode. See docs/AUTO_PETS.md.
            """);
    }
}
