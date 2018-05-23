/**
 * @author Li Liu
 */
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/* Ack class */
class Ack implements Serializable{
    /* filename of collage */
    String filename;
    /* files in UserNodes that involved in this collage */
    List<String> files;
    /* decision is commit or not */
    boolean success;

    /**
     * Constructor
     * @param filename filename of collage
     * @param set set of file involved
     * @param success succeed or not
     */
    Ack(String filename, Set<String> set, boolean success) {
        this.filename = filename;
        files = new ArrayList<>(set);
        this.success = success;
    }
}
