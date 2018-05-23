import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;

/* Cache Class */
public class Cache extends UnicastRemoteObject implements Cloud.DatabaseOps {
    /* database instance */
    private Cloud.DatabaseOps database;
    /* map to cache */
    private HashMap<String, String> map;

    /**
     * Constructor
     * @param SL ServerLib instance
     */
    Cache(ServerLib SL) throws RemoteException {
        database = SL.getDB();
        map = new HashMap<>();
    }

    /**
     * Get operation
     * @param s incoming query string
     */
    @Override
    public String get(String s) throws RemoteException {
        if (map.containsKey(s)) {
            return map.get(s);
        }
        String tem = database.get(s);
        map.put(s, tem);
        return tem;
    }

    /**
     * Set operation
     * @param s incoming query string
     * @param s1 incoming query string
     * @param s2 incoming query string
     */
    @Override
    public boolean set(String s, String s1, String s2) throws RemoteException {
        map.put(s, s1);
        return database.set(s, s1, s2);
    }

    /**
     * Transaction operation
     * @param s incoming query string
     * @param v incoming query float
     * @param i incoming query int
     */
    @Override
    public boolean transaction(String s, float v, int i) throws RemoteException {
        return database.transaction(s, v, i);
    }
}
