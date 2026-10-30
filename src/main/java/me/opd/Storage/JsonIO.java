package me.opd.Storage;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import me.opd.DataReading.*;
import me.opd.Optimization.*;
import java.io.*;
import java.nio.file.*;
public final class JsonIO {
    private JsonIO() {}
    public static final ObjectMapper MAPPER=new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(SerializationFeature.INDENT_OUTPUT)
        .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    public static <T> T read(Path path,Class<T> type) throws IOException { return MAPPER.readValue(path.toFile(),type); }
    public static Catalog readCatalog(Path path) throws IOException {
        JsonNode root=MAPPER.readTree(path.toFile());
        if (!root.has("schemaVersion") || !root.has("gear") || !root.has("jewels"))
            throw new IllegalArgumentException("Catalog needs schemaVersion, gear and jewels");
        for (String section : new String[]{"gear","jewels"}) {
            if (!root.get(section).isArray()) throw new IllegalArgumentException(section + " must be an array");
            for (JsonNode item : root.get(section)) if (item.path("complete").asBoolean(false))
                for (String field : section.equals("gear") ? new String[]{"level","schools","baseStats","sockets","source","gameVersion"}
                        : new String[]{"level","schools","statBonus","type","source","gameVersion"})
                    if (!item.hasNonNull(field)) throw new IllegalArgumentException("Complete record missing " + field + ": " + item.path("id"));
        }
        Catalog catalog=MAPPER.treeToValue(root,Catalog.class); catalog.validate(); return catalog;
    }
    public static OptimizationRequest readRequest(Path path) throws IOException {
        OptimizationRequest request=read(path,OptimizationRequest.class); request.validate(); return request;
    }
    public static void write(Path path,Object value) throws IOException {
        Path absolute=path.toAbsolutePath(); Files.createDirectories(absolute.getParent());
        MAPPER.writeValue(absolute.toFile(),value);
    }
    public static String encode(Object value) {
        try { return MAPPER.writeValueAsString(value); } catch (IOException e) { throw new IllegalArgumentException("Cannot serialize",e); }
    }
    public static <T> T decode(String json,Class<T> type) {
        try { return MAPPER.readValue(json,type); } catch (IOException e) { throw new IllegalArgumentException("Invalid stored JSON",e); }
    }
}
