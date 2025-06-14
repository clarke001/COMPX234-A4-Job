import java.io.BufferedReader;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class UDPClient {
    public static void main(String[] args) throws Exception{
        if (args.length != 3){
            System.err.println("Usage: java UDPClient <hostname> <port> <files.txt>");
            System.exit(1);
        }
        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String fileList = args[2];

        List<String> filenames = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(fileList))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    filenames.add(line);
                }
            }}
        System.out.println("Files to download: " + filenames);

        // Create a UDP socket
        DatagramSocket socket = new DatagramSocket();
        InetAddress serverAddress = InetAddress.getByName(hostname);

        for (String filename : filenames){
            String request = "DOWNLOAD" + filename;
            byte[] requestBytes = request.getBytes();
            DatagramPacket requestPacket = new DatagramPacket(requestBytes, requestBytes.length, serverAddress, port);
            socket.send(requestPacket);
            System.out.println("Sent: " + request);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
            System.out.println("Received: " + response);

            String[] parts = response.split(" ");
            if (parts[0].equals("OK") && parts[1].equals(filename)) {
                long fileSize = Long.parseLong(parts[3]);
                int filePort = Integer.parseInt(parts[5]);

                System.out.println("File " + filename + " size: " + fileSize + ", port: " + filePort);
                //  download the file
            } else if (parts[0].equals("ERR") && parts[1].equals(filename)) {
                System.err.println("Error: File " + filename + " not found");
            } else {
                System.err.println("Invalid response for file " + filename);
            }
        }
        socket.close();
    }
}
