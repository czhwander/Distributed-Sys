/**
 * @author Li Liu
 */
import java.io.*;

/* Util class for serilizing and deserilizing data structure */
class Serilize {

    /**
     * Serilize a data structure
     * @param obj Object to serilize
     * @return byte array of that object
     */
    static byte[] serilize(Object obj) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(obj);
        objectOutputStream.flush();
        byte[] out = byteArrayOutputStream.toByteArray();
        objectOutputStream.close();
        byteArrayOutputStream.close();
        return out;
    }

    /**
     * Deserilize a data structure
     * @param bytes byte array of the object
     * @return Object
     */
    static Object deserilize(byte[] bytes) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteArrayOutputStream = new ByteArrayInputStream(bytes);
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayOutputStream);
        Object obj = objectInputStream.readObject();
        return obj;
    }
}
