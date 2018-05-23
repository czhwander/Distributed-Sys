/**
 * @author Li Liu
 */
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
/* Server Side of Collage system */
public class Server implements ProjectLib.CommitServing {

	/* Project lib instance */
	private static ProjectLib PL;
	/* Map to store file name and its collage */
	private static Map<String, Collage> collages;
	/* locker used for concurrency */
	private final static Object locker = new Object();

	/**
	 * Method to call when a collage commit start
	 * @param filename filename of the collage
	 * @param img bytes array of the collage
	 * @param sources the source img information of the collage
	 */
	public void startCommit( String filename, byte[] img, String[] sources ) {

		/* new a collage instance and start vote for it */
		Collage collage = new Collage(filename, img, sources);
		startVote(collage);

		/* write to log */
		try {
			writeLog(collage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Start the vote process of a collage
	 * @param collage Collage to vote
	 */
	private void startVote(Collage collage) {
		/* put collage into map */
		collages.put(collage.filename, collage);

		/* send message indicate starting vote to UserNodes involved */
		for (String addr : collage.sources.keySet()) {
			try {
				ProjectLib.Message msg = new ProjectLib.Message(addr, Serilize.serilize(collage));
				PL.sendMessage(msg);

				/* turn on the timer in case of time out */
				Timer timer = new Timer(collage);
				Thread t = new Thread(timer);
				t.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Acknowledge UserNodes involved the decision
	 * @param collage the collage involved
	 * @param committed the commission decision result
	 */
	private static void ack(Collage collage, boolean committed) throws IOException {
		/* send message indicate UserNodes involved the result */
		for (String addr : collage.sources.keySet()) {
			Ack ack = new Ack(collage.filename, collage.sources.get(addr), committed);
			ProjectLib.Message msg = new ProjectLib.Message(addr, Serilize.serilize(ack));
			PL.sendMessage(msg);

			/* turn on the timer in case of timeout */
			Timer timer = new Timer(collage);
			Thread t = new Thread(timer);
			t.start();
		}
	}

	/**
	 * Read log when a server recovery from crash
	 */
	private static void readLog() throws IOException {
		File file = new File("serverLog");
		if (!file.exists()) return;
		RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
		String tem;

		/* store log information into a map, and renew the status of collages */
		Map<String, Collage> temMap = new HashMap<>();
		while ((tem = randomAccessFile.readLine()) != null) {
			String[] logs = tem.split(" ");
			if (temMap.containsKey(logs[0])) {
				temMap.get(logs[0]).status = Integer.parseInt(logs[1]);
			} else {
				Collage collage = new Collage(logs[0], new byte[]{}, logs[2].split(","));
				collage.status = Integer.parseInt(logs[1]);
				temMap.put(collage.filename, collage);
			}
		}

		/* close connection and delete log file */
		randomAccessFile.close();
		file.delete();

		/* deal with the collages */
		for (Collage collage : temMap.values()) {
			/* if in status 0 or 2, ack UserNodes commit is failed */
			if (collage.status == 0 || collage.status == 2) {
				collages.put(collage.filename, collage);
				ack(collage, false);
			} else  if (collage.status == 1){
				/* if status 1, ack UserNodes commit is successful */
				collages.put(collage.filename, collage);
				ack(collage, true);
			}
		}
	}

	/**
	 * Write information to log in case of crash
	 * @param collage collage that is dealt with
	 */
	private static void writeLog( Collage collage) throws IOException {
		RandomAccessFile randomAccessFile = new RandomAccessFile("serverLog", "rw");
		StringBuilder sb = new StringBuilder();

		/* form the log, filename status sources */
		sb.append(collage.filename).append(" ").append(collage.status).append(" ");
		for (Map.Entry<String, Set<String>> entry : collage.sources.entrySet()) {
			for (String s : entry.getValue()) {
				sb.append(entry.getKey()).append(":").append(s).append(",");
			}
		}
		if (collage.sources.size() != 0) sb.delete(sb.length() - 1, sb.length());
		sb.append("\n");

		/* block concurrent thread and write log */
		synchronized (locker) {
			randomAccessFile.seek(randomAccessFile.length());
			randomAccessFile.write(sb.toString().getBytes());
			randomAccessFile.close();
			PL.fsync();
		}
	}

	/**
	 * Main method
	 * @param args input arguments
	 */
	public static void main ( String args[] ) throws Exception {
		if (args.length != 1) throw new Exception("Need 1 arg: <port>");

		/* instantiate fields */
		Server srv = new Server();
		PL = new ProjectLib( Integer.parseInt(args[0]), srv );
		collages = new HashMap<>();

		/* read log when start */
		readLog();

		// main loop
		while (true) {
			/* receive and deserilize information */
			ProjectLib.Message msg = PL.getMessage();
			Object object = Serilize.deserilize(msg.body);

			/* if this is reply for vote */
			if (object.getClass().equals(Reply.class)) {
				processReply((Reply) object);
			} else {
				/* if this ack after UserNodes received ack */
				processAck((Ack) object);
			}

		}
	}

	/**
	 * Process ack after UserNodes received ack
	 * @param ack received ack
	 */
	private static void processAck(Ack ack) throws IOException {
		Collage collage = collages.get(ack.filename);
		if (collage == null) return;
		collage.acked++;
		/* if received enough acks, delete the collage from map */
		if (collage.acked == collage.sources.size()) {
			collage.status = 3;
			collages.remove(collage.filename);
			writeLog(collage);
		}
	}

	/**
	 * Process reply of vote request
	 * @param reply the reply from UserNodes
	 */
	private static void processReply(Reply reply) throws IOException {
		Collage collage = collages.get(reply.filename);
		if (collage == null) return;

		/* if the reply is agreement */
		if (reply.agree) {
			collage.votes++;
			/* when collect enough agreements, commit and ack UserNodes */
			if (collage.votes == collage.sources.size()) {
				collage.save();
				writeLog(collage);
				ack(collage, true);
			}
		} else {
			/* if the reply is not rejection */
			collage.status = 2;
			ack(collage, false);
		}
	}


	/* Timer used to prevent timeout */
	static class Timer implements Runnable {
		Collage collage;

		/**
		 * Constructor
		 * @param collage collage that need to count time
		 */
		Timer(Collage collage) {
			this.collage = collage;
		}

		/**
		 * run another thread to record time
		 */
		@Override
		public void run() {
			/* wait a RRT, 6s */
			try {
				Thread.sleep(6000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			/* if collage still in status 0, fail it and ack UserNodes */
			if (collage.status == 0) {
				try {
					collage.status = 2;
					ack(collage, false);
				} catch (IOException e) {
					e.printStackTrace();
				}
				return;
			}

			/* if collage still in status 1 or 2, re ack UserNodes */
			if (collage.status != 3) {
				try {
					collage.acked = 0;
					ack(collage, collage.status == 1);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}

