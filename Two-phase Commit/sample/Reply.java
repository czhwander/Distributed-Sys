/**
 * @author Li Liu
 */
import java.io.Serializable;

/* Reply for vote request */
class Reply implements Serializable {
    /* file name of collage */
    String filename;
    /* UserNode's idea */
    boolean agree;

    /**
     * Constructor
     * @param filename filename
     * @param agree UserNode's idea
     */
    Reply(String filename, boolean agree) {
        this.filename = filename;
        this.agree = agree;
    }
}
