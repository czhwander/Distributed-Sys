//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

public class RPCreceiver implements Runnable {
    private final FileHandlingMaking fileHandlerFactory;
    private final ServerSocket proxySocket;

    public RPCreceiver(FileHandlingMaking var1) throws IOException {
        this.fileHandlerFactory = var1;
        String var2 = System.getenv("proxyport15440");
        int var3;
        if (var2 != null) {
            var3 = Integer.parseInt(var2);
        } else {
            var3 = 15440;
        }

        System.out.format("RPCreceiver: Using port %d%n", var3);
        this.proxySocket = new ServerSocket(var3);
    }

    public void run() {
        String var1 = System.getenv("proxyruntrace");
        if (var1 != null && !var1.equals("")) {
            if (var1.equals("latencytest")) {
                (new RPCreceiver.LatencyTest()).run();
            }

            if (var1.equals("moonshot1")) {
                (new RPCreceiver.MoonShot1()).run();
            }

            if (var1.equals("moonshot2")) {
                (new RPCreceiver.MoonShot2()).run();
            }

        } else {
            try {
                while(true) {
                    Socket var2 = this.proxySocket.accept();
                    (new Thread(new RPCreceiver.ClientHandler(var2, this.fileHandlerFactory.newclient()))).start();
                }
            } catch (IOException var3) {
                System.out.println("RPCreceiver: Exception " + var3);
            }
        }
    }

    private class LatencyTest implements Runnable {
        private LatencyTest() {
        }

        public void run() {
            FileHandling var1 = RPCreceiver.this.fileHandlerFactory.newclient();
            String[] var2 = new String[]{"smallfile", "mediumfile", "smallfile", "mediumfile"};
            byte var3 = 1;
            long var4 = System.nanoTime();
            String var6 = "";
            String[] var7 = var2;
            int var8 = var2.length;

            for(int var9 = 0; var9 < var8; ++var9) {
                String var10 = var7[var9];
                System.err.println("LATENCYTEST START " + var10);
                int var11 = var1.open(var10, FileHandling.OpenOption.READ);
                byte[] var12 = new byte[10];
                long var13 = var1.read(var11, var12);
                var1.close(var11);
                long var16 = System.nanoTime();
                long var18 = (var16 - var4) / 1000000L;
                if (var11 < 0 || var13 < 0L || var13 < 1L) {
                    var3 = 0;
                }

                var6 = var6 + " " + var18;
                System.err.println("LATENCYTEST END " + var10);
                var4 = var16;
            }

            byte[] var21 = (var3 + var6).getBytes();

            try {
                FileOutputStream var22 = new FileOutputStream("latencytest");
                var22.write(var21);
                var22.close();
            } catch (IOException var20) {
                System.out.println("Exception " + var20);
            }

        }
    }

    private class MoonShot2 implements Runnable {
        private MoonShot2() {
        }

        public void run() {
            FileHandling var1 = RPCreceiver.this.fileHandlerFactory.newclient();

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException var7) {
                System.out.print(var7);
            }

            int var2 = var1.open("smallfile", FileHandling.OpenOption.WRITE);
            byte[] var3 = (new String("version2")).getBytes();
            var1.write(var2, var3);
            var1.close(var2);
        }
    }

    private class MoonShot1 implements Runnable {
        private MoonShot1() {
        }

        public void run() {
            FileHandling var1 = RPCreceiver.this.fileHandlerFactory.newclient();
            byte var2 = 1;
            boolean var3 = false;
            long var4 = System.nanoTime();
            int var6 = var1.open("smallfile", FileHandling.OpenOption.READ);
            byte[] var7 = new byte[8];
            long var8 = var1.read(var6, var7);
            int var10 = var1.close(var6);
            if (var6 < 0 || var8 < 0L || var10 < 0 || !(new String(var7)).equals("original")) {
                var2 = -1;
            }

            var6 = var1.open("mediumfile", FileHandling.OpenOption.READ);
            var8 = var1.read(var6, var7);
            var10 = var1.close(var6);
            if (var6 < 0 || var8 < 0L || var10 < 0) {
                var2 = 0;
            }

            for(int var19 = 0; var19 < 30; ++var19) {
                var6 = var1.open("smallfile", FileHandling.OpenOption.READ);
                var8 = var1.read(var6, var7);

                try {
                    Thread.sleep(67L);
                } catch (InterruptedException var18) {
                    System.out.print(var18);
                }

                var10 = var1.close(var6);
                if (var6 < 0 || var8 < 0L || var10 < 0) {
                    var2 = -2;
                }
            }

            long var11 = System.nanoTime();
            long var13 = (var11 - var4) / 1000000L;
            if (!(new String(var7)).equals("version2")) {
                var2 = -3;
            }

            byte[] var15 = (var2 + " " + var13).getBytes();

            try {
                FileOutputStream var16 = new FileOutputStream("moonshot");
                var16.write(var15);
                var16.close();
            } catch (IOException var17) {
                System.out.println("Exception " + var17);
            }

        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final FileHandling fileHandler;
        private InputStream clientIn;
        private OutputStream clientOut;
        private static final int MIN_FD = 1000;

        public ClientHandler(Socket var1, FileHandling var2) {
            this.clientSocket = var1;
            this.fileHandler = var2;
        }

        private int readInt() throws EOFException, IOException {
            int var1 = 0;

            for(int var2 = 0; var2 < 4; ++var2) {
                int var3 = this.clientIn.read();
                if (var3 < 0 || var3 > 255) {
                    throw new EOFException();
                }

                var1 |= var3 << var2 * 8;
            }

            return var1;
        }

        private long readLong() throws EOFException, IOException {
            long var1 = 0L;

            for(int var3 = 0; var3 < 8; ++var3) {
                long var4 = (long)this.clientIn.read();
                if (var4 < 0L || var4 > 255L) {
                    throw new EOFException();
                }

                var1 |= var4 << var3 * 8;
            }

            return var1;
        }

        private void readBuf(byte[] var1) throws EOFException, IOException {
            int var2 = var1.length;

            int var4;
            for(int var3 = 0; var2 > 0; var2 -= var4) {
                var4 = this.clientIn.read(var1, var3, var2);
                if (var4 < 0) {
                    throw new EOFException();
                }

                var3 += var4;
            }

        }

        private void writeInt(byte[] var1, int var2, int var3) {
            for(int var4 = 0; var4 < 4; ++var4) {
                var1[var2 + var4] = (byte)(var3 >> var4 * 8 & 255);
            }

        }

        private void writeLong(byte[] var1, int var2, long var3) {
            for(int var5 = 0; var5 < 8; ++var5) {
                var1[var2 + var5] = (byte)((int)(var3 >> var5 * 8 & 255L));
            }

        }

        private void writeBuf(byte[] var1, int var2, byte[] var3) {
            for(int var4 = 0; var4 < var3.length; ++var4) {
                var1[var2 + var4] = var3[var4];
            }

        }

        public void run() {
            try {
                this.clientIn = this.clientSocket.getInputStream();
                this.clientOut = this.clientSocket.getOutputStream();

                while(true) {
                    int var1;
                    try {
                        var1 = this.readInt();
                    } catch (EOFException var11) {
                        break;
                    }

                    int var2;
                    int var5;
                    byte[] var8;
                    long var13;
                    long var18;
                    byte[] var21;
                    switch(var1) {
                        case 1:
                            var2 = this.readInt();
                            FileHandling.OpenOption var17;
                            switch(var2) {
                                case 1:
                                    var17 = FileHandling.OpenOption.WRITE;
                                    break;
                                case 2:
                                    var17 = FileHandling.OpenOption.CREATE;
                                    break;
                                case 3:
                                    var17 = FileHandling.OpenOption.CREATE_NEW;
                                    break;
                                default:
                                    var17 = FileHandling.OpenOption.READ;
                            }

                            int var15 = this.readInt();
                            byte[] var20 = new byte[var15];
                            this.readBuf(var20);
                            String var22 = new String(var20);
                            int var23 = this.fileHandler.open(var22, var17);
                            if (var23 >= 0) {
                                var23 += 1000;
                            }

                            var8 = new byte[4];
                            this.writeInt(var8, 0, var23);
                            this.clientOut.write(var8);
                            break;
                        case 2:
                            var2 = this.readInt() - 1000;
                            int var16 = this.fileHandler.close(var2);
                            byte[] var14 = new byte[4];
                            this.writeInt(var14, 0, var16);
                            this.clientOut.write(var14);
                            break;
                        case 3:
                            var2 = this.readInt() - 1000;
                            var13 = this.readLong();
                            var8 = new byte[8];
                            if (var13 > 65536L) {
                                var13 = 65536L;
                            }
                            System.err.println("buffer length is : " + var13);
                            if (var13 < 0L) {
                                var18 = -22L;
                            } else {
                                var21 = new byte[(int)var13];
                                var18 = this.fileHandler.read(var2, var21);

                                if (var18 > 0L) {
                                    var8 = new byte[8 + (int)var18];
                                    this.writeBuf(var8, 8, Arrays.copyOfRange(var21, 0, (int)var18));
                                }
                            }
                            System.err.println(var18);
                            this.writeLong(var8, 0, var18);
                            this.clientOut.write(var8);
                            break;
                        case 4:
                            var2 = this.readInt() - 1000;
                            var13 = this.readLong();
                            var21 = new byte[(int)var13];
                            var8 = new byte[8];
                            this.readBuf(var21);
                            var18 = this.fileHandler.write(var2, var21);
                            this.writeLong(var8, 0, var18);
                            this.clientOut.write(var8);
                            break;
                        case 5:
                            var2 = this.readInt() - 1000;
                            var13 = this.readLong();
                            var5 = this.readInt();
                            FileHandling.LseekOption var19 = FileHandling.LseekOption.FROM_START;
                            if (var5 == 2) {
                                var19 = FileHandling.LseekOption.FROM_END;
                            } else if (var5 == 1) {
                                var19 = FileHandling.LseekOption.FROM_CURRENT;
                            }

                            long var7 = this.fileHandler.lseek(var2, var13, var19);
                            byte[] var9 = new byte[8];
                            this.writeLong(var9, 0, var7);
                            this.clientOut.write(var9);
                            break;
                        case 6:
                        case 8:
                        case 9:
                        case 10:
                        default:
                            throw new IOException();
                        case 7:
                            var2 = this.readInt();
                            byte[] var3 = new byte[var2];
                            this.readBuf(var3);
                            String var4 = new String(var3);
                            var5 = this.fileHandler.unlink(var4);
                            byte[] var6 = new byte[4];
                            this.writeInt(var6, 0, var5);
                            this.clientOut.write(var6);
                    }
                }
            } catch (IOException var12) {
                System.out.println("ClientHandler: Exception " + var12);
            }

            this.fileHandler.clientdone();

            try {
                this.clientSocket.close();
            } catch (IOException var10) {
                System.out.println("ClientHandler: Exception " + var10);
            }

        }
    }
}
