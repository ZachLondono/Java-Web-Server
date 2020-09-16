import java.net.*;
import java.io.*;

public class PartialHTTP1Server {

    public static void main(String[] args) {

        final int PORT;

        if (args.length != 1) {
            System.err.println("[Fatal Error] First argument must be the port number for the server to listen on");
            return;
        }

        try {
            PORT = Integer.parseInt(args[0]);
            if (PORT < 0 || PORT > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            System.err.println("[Fatal Error] Port number must be between 0 and 65535");
            return;
        }

        try(ServerSocket serverSocket = new ServerSocket(PORT)) {
            
            Socket clientSocket;
            while ((clientSocket = serverSocket.accept()) != null) {
                System.out.println("New connection from client: " + clientSocket.getInetAddress());
                addClient(clientSocket);
            }

        } catch (IOException e) {
            System.err.println("[Fatal Error] Failed to set up server");
        }

    }
 
    private static void addClient(Socket clientSocket) {
        // TODO: add to thread pool
        new Thread(new ClientHandler(clientSocket)).start();;
    }
    
}