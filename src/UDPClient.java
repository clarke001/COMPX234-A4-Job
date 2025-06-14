import java.io.BufferedReader;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.*;

public class UDPClient {
    private  static  final  int Max_NUMBER_RETRIES = 5;
    private static final  int INITIAL_TIMEOUT = 1000;
    private static final  int BLOCK_SIZE = 1000;


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
            String response = sendAndReceive(socket, serverAddress, port, request);
            if (response == null) {
                System.err.println("Failed to download " + filename + " after " + Max_NUMBER_RETRIES + " retries");
                continue;
            }
            System.out.println("Received: " + response);

            String[] parts = response.split(" ");
            if (parts[0].equals("OK") && parts[1].equals(filename)) {
                long fileSize = Long.parseLong(parts[3]);
                int filePort = Integer.parseInt(parts[5]);

                System.out.println("File " + filename + " size: " + fileSize + ", port: " + filePort);

                RandomAccessFile file = new RandomAccessFile(filename,"rw") ;
                //  download the file
                long bytesReceived = 0;
                System.out.print("Downloading " + filename + ": ");
                while (bytesReceived < fileSize) {
                    long start = bytesReceived;
                    long end = Math.min(bytesReceived + BLOCK_SIZE - 1, fileSize - 1);
                    String fileRequest = "FILE " + filename + " GET START " + start + " END " + end;
                    String fileResponse = sendAndReceive(socket, serverAddress, filePort, fileRequest);
                    if (fileResponse == null) {
                        System.err.println("Failed to receive chunk " + start + "-" + end + " for " + filename);
                        break;}
                    System.out.println("Received: " + fileResponse);

                    //Analyze the response
                    String[] fileParts = fileResponse.split(" ", 9);
                    if (fileParts[0].equals("FILE") && fileParts[1].equals(filename) && fileParts[2].equals("OK")) {
                        long respStart = Long.parseLong(fileParts[4]);
                        long respEnd = Long.parseLong(fileParts[6]);
                        String encodedData = fileParts[8];
                        byte[] data = Base64.getDecoder().decode(encodedData);
                        file.seek(respStart);
                        file.write(data);
                        bytesReceived += (respEnd - respStart + 1);
                        System.out.print("*");
                    } else {
                        System.err.println("Invalid file response for " + filename);
                        break;
                    }
                }
                System.out.println();
                file.close();

                // Send CLOSE
                String closeRequest = "FILE " + filename + " CLOSE";
                String closeResponse = sendAndReceive(socket, serverAddress, filePort, closeRequest);
                if (closeResponse == null || !closeResponse.equals("FILE " + filename + " CLOSE_OK")) {
                    System.err.println("Failed to close file " + filename);
                }else {
                    System.out.println("Received: " + closeResponse);}
            } else if (parts[0].equals("ERR") && parts[1].equals(filename)) {
                System.err.println("Error: File " + filename + " not found");
            } else {
                System.err.println("Invalid response for file " + filename);
            }
        }
        socket.close();
    }

    private static String sendAndReceive(DatagramSocket socket, InetAddress address, int port, String request) throws Exception {
        byte[] requestBytes = request.getBytes();
        DatagramPacket requestPacket = new DatagramPacket(requestBytes, requestBytes.length, address, port);
        byte[] buffer = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

        int retries = 0;
        int timeout = INITIAL_TIMEOUT;
        while (retries < Max_NUMBER_RETRIES) {
            socket.send(requestPacket);
            System.out.println("Sent: " + request + " (retry " + retries + ")");
            socket.setSoTimeout(timeout);

            try {
                socket.receive(responsePacket);
                if (responsePacket.getAddress().equals(address) && responsePacket.getPort() == port) {
                    String response = new String(responsePacket.getData(), 0, responsePacket.getLength());
                    return response;
                }else{
                    System.out.println("Received response from unexpected source: " + responsePacket.getAddress() + ":" + responsePacket.getPort());
                }
            } catch (SocketTimeoutException e) {
                retries++;
                timeout *= 2; // 指数退避
                System.out.println("Timeout, retrying...");
            }
         }
        return null;
    }
}


