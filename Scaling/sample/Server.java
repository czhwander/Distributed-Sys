/* Sample code for basic Server */


import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

/* Server Class */
public class Server extends UnicastRemoteObject implements ServerInterface{
	/* map to record id to type */
	private static Map<Integer, Integer> idToType;
	/* central queue used to store requests */
	private static Queue<Cloud.FrontEndOps.Request> requests;
	/* number of front end */
	private static int numberOfFrontEnd;
	/* number of middle end */
	private static int numberOfMidEnd;
	/* master server for other nodes */
	private static ServerInterface server;
	/* cache server */
	private static Cloud.DatabaseOps cache;

	/* scaling parameters */
	private final static int frontScaleInTime = 1500;

	private final static double midScaleInMissTime = 880;

	private final static double frontScaleOutTimes = 4.0;

	private final static double midScaleOutTimes = 1.43;

	private final static int dropTimesToScaleOut = 2;

	private final static int dropTimesToScaleOutAtBeginning = 6;

	/* last requests handle time */
	private static double acceptTime = System.currentTimeMillis();
	/* number of requests dropped */
	private static int drop = 0;
	/* ServerLib */
	private static ServerLib SL;

	/**
	 * Constructor
	 */
	protected Server() throws RemoteException {
	}

	/**
	 * Main function
	 */
	public static void main (String args[] ) throws Exception {

		/* get and parse the input arguments */
		if (args.length != 3) throw new Exception("Need 3 args: <cloud_ip> <cloud_port> <VM id>");
		SL = new ServerLib( args[0], Integer.parseInt(args[1]) );
		int id = Integer.parseInt(args[2]);
		String addr = "//" + args[0] + ":" + args[1];

		/* type of node, 0 to master, 1 to front, 2 to middle */
		int type;

		/* master node */
		if (id == 1) {
			/* instantiate global variables */
			idToType = new HashMap<>();
			requests = new ArrayDeque<>();
			numberOfFrontEnd = 1;
			numberOfMidEnd = 0;
			type = 0;

			/* register master as a front end */
			SL.register_frontend();

			/* start a front end and middle end at the beginning */
			startVM(SL, 2);
			startVM(SL, 1);

			/* open master service and cache service */
			Server ser = new Server();
			Cache ca = new Cache(SL);
			try {
				Naming.rebind(addr + "/server", ser);
				Naming.rebind(addr + "/cache", ca);
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		} else {
			/* not master node */
			try {
				/* instantiate master server */
				server = (ServerInterface) Naming.lookup(addr + "/server");

				/* get type */
				type = server.getType(id);

				/* if is front end, do registration, if is middle end, instantiate cache */
				if (type == 1) {
					SL.register_frontend();
				} else {
					cache = (Cloud.DatabaseOps) Naming.lookup(addr + "/cache");
				}
			} catch (Exception e) {
				return;
			}
		}

		/* while loop to deal requests */
		while (true) {
			try {
				/* if this is master node */
				if (type == 0) {

					/* get request from queue */
					Cloud.FrontEndOps.Request request = SL.getNextRequest();

					/* before the first middle end running, drop all coming requests */
					if (SL.getStatusVM(2) != Cloud.CloudOps.VMStatus.Running) {
						SL.drop(request);
						drop++;

						/* count the start coming speed, if to fast (drop too many), start middle VM */
						if (drop % dropTimesToScaleOutAtBeginning == 0) startVM(SL, 2);
						continue;
					}

					/* if size of central queue is too large, drop request, or add to queue */
					if (requests.size() > numberOfMidEnd * midScaleOutTimes) {
						SL.drop(request);
						drop++;

						/* if drop to many, start middle VM */
						if (drop % dropTimesToScaleOut == 0) startVM(SL, 2);
					} else {
						requests.add(request);
						drop = 0;
					}


					/* if there are to many requests wait to enqueue, start front VM */
					if (SL.getQueueLength() > numberOfFrontEnd * frontScaleOutTimes) {
						startVM(SL, 1);
					}
					continue;
				}

				/* if this is front end */
				if (type == 1) {
					/* starting time */
					double time1 = System.currentTimeMillis();

					/* get request and put to central queue */
					Cloud.FrontEndOps.Request request = SL.getNextRequest();
					server.putRequest(request);

					/* end time */
					double time2 = System.currentTimeMillis();

					/* if this operation takes too long, means requests' coming rate is low, shut down front end */
					if (time2 - time1 > frontScaleInTime) {
						server.shutDown(id, type);
						SL.unregister_frontend();
						UnicastRemoteObject.unexportObject(server, true);
						SL.endVM(id);
					}
					continue;
				}

				/* if this is middle end */

				/* get request from server's central queue */
				Cloud.FrontEndOps.Request request = server.getRequest();

				/* if request is not null, process request and renew accept time */
				if (request != null) {
					SL.processRequest(request, cache);
					acceptTime = System.currentTimeMillis();
				}

				/* if the the time between tow coming requests is too long, shut down middle end */
				if (System.currentTimeMillis() - acceptTime > midScaleInMissTime && id != 2) {
					server.shutDown(id, type);
					UnicastRemoteObject.unexportObject(server, true);
					SL.endVM(id);
				}
			} catch (Exception e) {
				break;
			}
		}

	}

	/**
	 * Start a VM
	 * @param SL ServerLib instance
	 * @param type type of VM to start.
	 */
	private static void startVM(ServerLib SL ,int type) {
		int id = SL.startVM();
		idToType.put(id, type);
		if (type == 1) {
			numberOfFrontEnd++;
		} else {
			numberOfMidEnd++;
		}

	}

	/**
	 * When a VM is shut down, master node will change the number here
	 * @param id id of VM shut down
	 * @param type type of VM shut down
	 */
	@Override
	public void shutDown(int id, int type) throws RemoteException {
		if (type == 1) numberOfFrontEnd--;
		else numberOfMidEnd--;
	}

	/**
	 * Get requests from the central queue
	 */
	@Override
	public Cloud.FrontEndOps.Request getRequest() throws RemoteException {
		if (requests.isEmpty()) {
			return null;
		}
		return requests.poll();
	}

	/**
	 * Put requests to the central queue
	 * @param request the request to put
	 */
	@Override
	public void putRequest(Cloud.FrontEndOps.Request request) throws RemoteException {

		/* check the central queue size, if it is too large, drop request and start new middle VM */
		if (requests.size() > numberOfMidEnd * midScaleOutTimes) {
			SL.drop(request);
			drop++;
			if (drop % dropTimesToScaleOut == 0) {
				startVM(SL, 2);
			}
		} else {
			requests.add(request);
			drop = 0;
		}

	}

	/**
	 * Get the type of a VM
	 * @param id the id of VM
	 */
	@Override
	public int getType(int id) throws RemoteException {
		return idToType.get(id);
	}

}
