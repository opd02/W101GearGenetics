package me.opd.DataReading;
import me.opd.JewelHandeling.*;
import me.opd.PetHandeling.*;
import org.apache.commons.csv.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Instant;
import java.util.*;
/** Legacy values lack school, level and ownership metadata. Scope is an explicit import choice. */
public class GearLoader {
    private static List<CSVRecord> records(String path) throws IOException {
        try (Reader reader=Files.newBufferedReader(Path.of(path),StandardCharsets.UTF_8);
                CSVParser parser=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                    .setTrim(true).setIgnoreEmptyLines(true).get().parse(reader)) {
            return parser.getRecords();
        }
    }
    public static String legacyId(String kind,String name) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest((kind + ":" + name).getBytes(StandardCharsets.UTF_8));
            return "legacy:" + kind.toLowerCase(Locale.ROOT) + ":" + HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static List<Gear> loadGearFromCSV(String path,School legacySchool) throws IOException {
        List<Gear> result=new ArrayList<>();
        for (CSVRecord row : records(path)) try {
            Gear gear=new Gear(); gear.name=row.get("name"); gear.slot=GearSlot.valueOf(row.get("slot"));
            gear.id=legacyId(gear.slot.name(),gear.name); gear.baseStats=stats(row,legacySchool);
            gear.level=1; gear.complete=false; gear.source="legacy-csv"; gear.importedAt=Instant.now().toString();
            gear.extensions.put("warning","Unknown eligibility/level and unverified stat scope; import mapping is " + legacySchool);
            gear.extensions.put("legacySchoolMapping",legacySchool.name());
            cards(row,gear.itemCards);
            for (int i=1;i<=4;i++) {
                String value=row.get("socket"+i);
                if (!value.isBlank() && !value.equalsIgnoreCase("null")) gear.sockets.add(new Socket(JewelType.valueOf(value)));
            }
            gear.validate(); result.add(gear);
        } catch (RuntimeException e) { throw new IOException(path + " row " + row.getRecordNumber() + ": " + e.getMessage(),e); }
        return result;
    }
    /** Compatibility overload deliberately retains ambiguity as UNIVERSAL, flagged incomplete. Prefer explicit scope. */
    public static List<Gear> loadGearFromCSV(String path) throws IOException { return loadGearFromCSV(path,School.UNIVERSAL); }
    public static List<Jewel> loadJewelsFromCSV(String path,School legacySchool) throws IOException {
        List<Jewel> result=new ArrayList<>();
        for (CSVRecord row : records(path)) try {
            Jewel jewel=new Jewel(); jewel.name=row.get("name"); jewel.type=JewelType.valueOf(row.get("jeweltype"));
            jewel.id=legacyId(jewel.type.name(),jewel.name); jewel.statBonus=stats(row,legacySchool);
            jewel.level=1; jewel.complete=false; jewel.source="legacy-csv"; jewel.importedAt=Instant.now().toString();
            jewel.extensions.put("warning","Unknown eligibility/level; explicit legacy stat mapping: " + legacySchool);
            if (legacySchool!=School.UNIVERSAL) jewel.bonusSchools.add(legacySchool);
            cards(row,jewel.itemCards); jewel.validate(); result.add(jewel);
        } catch (RuntimeException e) { throw new IOException(path + " row " + row.getRecordNumber() + ": " + e.getMessage(),e); }
        return result;
    }
    public static List<Jewel> loadJewelsFromCSV(String path) throws IOException { return loadJewelsFromCSV(path,School.UNIVERSAL); }
    public static List<PetTrait> loadTraitsFromCSV(String path,School legacySchool) throws IOException {
        List<PetTrait> result=new ArrayList<>();
        for (CSVRecord row : records(path)) try {
            PetTrait trait=new PetTrait(); trait.name=row.get("name"); trait.id=legacyId("trait",trait.name);
            trait.statBonus=stats(row,legacySchool); cards(row,trait.itemCards); trait.validate(); result.add(trait);
        } catch (RuntimeException e) { throw new IOException(path + " row " + row.getRecordNumber() + ": " + e.getMessage(),e); }
        return result;
    }
    public static List<PetTrait> loadTraitsFromCSV(String path) throws IOException { return loadTraitsFromCSV(path,School.UNIVERSAL); }
    private static StatBlock stats(CSVRecord row,School school) {
        Objects.requireNonNull(school,"Legacy school mapping is required");
        StatBlock block=new StatBlock();
        String[] columns={"health","powerpip","shadowpip","pipconversion","accuracy","critical","criticalblock","damage","resist","pierce"};
        StatType[] types={StatType.HEALTH,StatType.POWER_PIP_CHANCE,StatType.SHADOW_PIP_RATING,StatType.PIP_CONVERSION_RATING,
            StatType.ACCURACY,StatType.CRITICAL_RATING,StatType.BLOCK_RATING,StatType.DAMAGE,StatType.RESISTANCE,StatType.PIERCE};
        for (int i=0;i<columns.length;i++) {
            double value=Double.parseDouble(row.get(columns[i]));
            School scope=types[i].schoolSpecific && types[i]!=StatType.RESISTANCE && types[i]!=StatType.BLOCK_RATING ? school : School.UNIVERSAL;
            if (value!=0) block.put(types[i],scope,value);
        }
        return block;
    }
    private static void cards(CSVRecord row,List<String> cards) {
        for (String column : new String[]{"provideblade","providesharpen"}) {
            String value=row.get(column);
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException("Invalid boolean " + column);
            if (Boolean.parseBoolean(value)) cards.add("legacy:" + column + ":unspecified-spell");
        }
    }
}
