/**
 * @author Li Liu
 */
import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* Collage class */
class Collage implements Serializable{
    /* file name of this Collage */
    String filename;
    /* number of votes this collage get */
    int votes;
    /* byte array of this collage */
    byte[] img;
    /* number of ack received from UserNodes */
    int acked;
    /* status of collage, 0 indicate before decision,
     * 1 indicate success, 2 indicate failure,
     * 3 indicate received all ack from UserNodes */
    int status;
    /* store collage source images */
    Map<String, Set<String>> sources;

    /**
     * Constructor
     * @param filename filename of this collage
     * @param img byte array of this collage
     * @param sources source images of this collage
     */
    Collage(String filename, byte[] img, String[] sources) {
        this.sources = new HashMap<>();
        this.filename = filename;
        this.img = img;
        this.votes = 0;
        this.acked = 0;
        this.status = 0;
        String[] tem;
        for (String s : sources) {
            tem = s.split(":");
            this.sources.putIfAbsent(tem[0], new HashSet<>());
            this.sources.get(tem[0]).add(tem[1]);
        }
    }

    /**
     * Save this collage to disk
     */
    void save() throws IOException {
        File file = new File(filename);
        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        randomAccessFile.write(img);
        randomAccessFile.close();
        status = 1;
    }
}
