/**
 * @author Li Liu
 */
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/* UserNode of Collage system */
public class UserNode implements ProjectLib.MessageHandling {
	/* id of this UserNode */
	private final String myId;
	/* Project Lib instance */
	private static ProjectLib PL;
	/* set to store file that is under decision */
	private static Set<String> lock;
	/* object used to lock concurrency */
	private static final Object locker = new Object();

	/**
	 * Constructor
	 * @param id id of this UserNode
	 */
	public UserNode( String id ) {
		myId = id;
	}

	/**
	 * Receive message from Server
	 * @param msg message received
	 * @return boolean value
	 */
	public boolean deliverMessage( ProjectLib.Message msg ) {
		try {
			/* receive message */
			Object object = Serilize.deserilize(msg.body);

			/* if it is vote request */
			if (object.getClass().equals(Collage.class)) {
				vote((Collage) object, msg.addr);
			} else {
				/* if it is ack of decision */
				ack((Ack) object, msg);
			}
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return true;
	}

	/**
	 * Deal with ack of decision
	 * @param ack ack from Server
	 * @param msg message from Server
	 */
	private void ack(Ack ack, ProjectLib.Message msg) {
		/* if ack is success, delete involved files, or just remove files from lock map */
		if (ack.success) {
			delete(ack.files);
		} else {
			lock.removeAll(ack.files);
		}

		/* send msg back to Server indicate this UserNode received ack */
		PL.sendMessage(msg);
	}

	/**
	 * Delete all files involved
	 * @param src list of filenames involved
	 */
	private void delete(List<String> src) {
		for (String filename : src) {
			File file = new File(filename);
			if (file.exists()) {
				file.delete();
			}
		}
	}

	/**
	 * Vote for a collage
	 * @param collage the collage to vote
	 * @param addr the address of Server
	 */
	private void vote(Collage collage, String addr) throws IOException {
		List<String> list = new ArrayList<>(collage.sources.get(myId));
		String[] sources = new String[list.size()];

		/* form the source array */
		for (int i = 0; i < list.size(); i++) {
			sources[i] = list.get(i);
		}

		/* ask user */
		boolean result = PL.askUser(collage.img, sources);


		synchronized (locker) {
			/* if user agree */
			if (result) {
				for (String s : sources) {
					/* if any file in lock map, change user decision into false and break */
					if (lock.contains(s)) {
						result = false;
						break;
					}
				}
			}
			/* if user decision is still true, put files into lock map */
			if (result) {
				lock.addAll(Arrays.asList(sources));
			}
		}

		/* reply UserNode's idea */
		Reply reply = new Reply(collage.filename, result);
		ProjectLib.Message msg = new ProjectLib.Message(addr, Serilize.serilize(reply));
		PL.sendMessage(msg);
	}

	/**
	 * Main method
	 * @param args input arguments
	 */
	public static void main ( String args[] ) throws Exception {
		if (args.length != 2) throw new Exception("Need 2 args: <port> <id>");
		UserNode UN = new UserNode(args[1]);
		lock = new ConcurrentSkipListSet<>();
		PL = new ProjectLib( Integer.parseInt(args[0]), args[1], UN );
	}
}

