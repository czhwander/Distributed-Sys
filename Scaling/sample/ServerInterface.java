import java.rmi.Remote;
import java.rmi.RemoteException;

/* Server Interface */
public interface ServerInterface extends Remote {
    /* method of server interface */

    /**
     * Get requests from the central queue
     */
    Cloud.FrontEndOps.Request getRequest() throws RemoteException;

    /**
     * Put requests to the central queue
     * @param request the request to put
     */
    void putRequest(Cloud.FrontEndOps.Request request) throws  RemoteException;

    /**
     * When a VM is shut down, master node will change the number here
     * @param id id of VM shut down
     * @param type type of VM shut down
     */
    void shutDown(int id, int type) throws RemoteException;

    /**
     * Get the type of a VM
     * @param id the id of VM
     */
    int getType(int id) throws RemoteException;
}
