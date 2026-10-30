package me.opd.Utils;
import me.opd.Individual;
import me.opd.Storage.JsonIO;
public class CopyUtils {
    /** For external callers needing a fully independent mutable object graph. */
    public static Individual deepCopy(Individual original) {
        return JsonIO.MAPPER.convertValue(original,Individual.class);
    }
}
