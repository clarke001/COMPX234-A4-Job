import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class UDPServer {
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
            System.out.println("Received: " + message);

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
                System.out.println("Sent: " + response);

            } else{
                System.out.println("Incorrect message format");
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
            System.out.println("Started thread to handle file: " + filename);
        }
    }
}
