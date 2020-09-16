import java.net.*;
import java.io.*;

public class ClientHandler implements Runnable {

    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());
            //BufferedOutputStream output = new BufferedOutputStream(clientSocket.getOutputStream());
        ){

            String request = reader.readLine();

            System.out.println("Read data from client: " + request);

            String response = request.toUpperCase();

            System.out.println("Sending data to client: " + response);

            output.writeBytes(response);
            
        } catch(IOException e) {
            System.err.println("[Error] failed to communicate with client");
        }
    }

}