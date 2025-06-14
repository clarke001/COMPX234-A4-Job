import java.io.File;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;
import java.util.Random;

public class UDPServer {
    private  static  final  Object fileLock = new Object();
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java UDPServer <port>");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        DatagramSocket socket = new DatagramSocket(port);
        byte[] buffer = new byte[1024];

        System.out.println("Server started on port " + port);

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String message = new String(packet.getData(), 0, packet.getLength());
            System.out.println("Received from " + packet.getAddress() + ":" + packet.getPort() + ": " + message);

            if(message.startsWith("DOWNLOAD")){
                String filename = message.substring(9).trim();
                System.out.println("Parsed download request for file:" + filename);

                File file = new File(filename);
                String response;
                int clientPort = packet.getPort();
                InetAddress clientAddress = packet.getAddress();
                Random rand = new Random();
                int filePort = 5000 + rand.nextInt(1001);

                if (file.exists() && file.isFile()) {
                    response = "OK " + filename + " SIZE " + file.length() + " PORT 50000";
                    Thread fileThread = new Thread(new FileHandler(filename, clientAddress, clientPort,filePort));
                    fileThread.start();
                } else {
                    response = "ERR " + filename + " NOT_FOUND";
                }

                byte[] responseBytes = response.getBytes();
                DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, packet.getAddress(), packet.getPort());
                socket.send(responsePacket);
                System.out.println("Sent to " + clientAddress + ":" + clientPort + ": " + response);
            } else{
                System.out.println("Invalid message format from " + packet.getAddress() + ":" + packet.getPort());
            }
        }
    }
    static class FileHandler implements Runnable {
        private String filename;
        private InetAddress clientAddress;
        private int clientPort;
        private  int filePort;

        public FileHandler(String filename, InetAddress clientAddress, int clientPort,int filePort) {
            this.filename = filename;
            this.clientAddress = clientAddress;
            this.clientPort = clientPort;
            this.filePort = filePort;
        }
        public void run() {
            DatagramSocket fileSocket = null;
            try {
                fileSocket = new DatagramSocket(filePort);
                byte[] buffer = new byte[1024];
                System.out.println("File thread started on port " + filePort + " for file: " + filename);

                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    fileSocket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("File thread received from " + clientAddress + ":" + clientPort + ": " + message);

                    if (message.startsWith("FILE " + filename + " GET START ")) {
                        String[] parts = message.split(" ");
                        int start = Integer.parseInt(parts[5]);
                        int end = Integer.parseInt(parts[7]);
                        byte[] data;
                        synchronized (fileLock) {
                            RandomAccessFile file = new RandomAccessFile(filename, "r");
                            file.seek(start);
                            data = new byte[end - start + 1];
                            file.read(data);
                            file.close();
                        }
                        String encodedData = Base64.getEncoder().encodeToString(data);
                        String response = "FILE " + filename + " OK START " + start + " END " + end + " DATA " + encodedData;
                        byte[] responseBytes = response.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, clientAddress, clientPort);
                        fileSocket.send(responsePacket);
                        System.out.println("Sent chunk: " + start + "-" + end);
                    }else if (message.equals("FILE" + filename + "CLOSE")){
                        String response = "FILE " + filename + " CLOSE_OK";
                        byte[] responseBytes = response.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, clientAddress, clientPort);
                        fileSocket.send(responsePacket);
                        System.out.println("File thread sent to " + clientAddress + ":" + clientPort + ": " + response);
                        break;
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in file thread: " + e.getMessage());
            } finally {
                if (fileSocket != null) {
                    fileSocket.close();
                }
            }
        }
    }
}
