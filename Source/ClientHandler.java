import java.net.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

public class ClientHandler implements Runnable {

    private final String supportedVerison = "HTTP/1.0";
    private Socket clientSocket;
    private HashMap<String, PartialHTTP1Server.RequestHandler> handlerMap;

    public ClientHandler(Socket clientSocket, HashMap<String, PartialHTTP1Server.RequestHandler> handlerMap) {
        this.clientSocket = clientSocket;
        this.handlerMap = handlerMap;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());) {

            String response = "";
            boolean timedOut = false;
            long connectedTime = System.currentTimeMillis();
            while (!reader.ready()) {
                if (System.currentTimeMillis() - connectedTime > 5000) {        // Check if elapsed time since connection is over 5 seconds
                    // Client has timed out
                    response = supportedVerison + " " + StatusCode._408.toString();
                    timedOut = true;
                }
            }

            if (!timedOut) {
                String line;
                ArrayList<String> lines = new ArrayList<>();
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }

                String[] request = (String[]) lines.toArray();
                String[] fields = request[0].split(Character.toString(32));

                if (fields.length != 3) response = StatusCode._400.toString();
                else if (!fields[2].equals(supportedVerison)) response = supportedVerison + " " + StatusCode._505.toString();
                else if (!handlerMap.containsKey(fields[0])) response = supportedVerison + " " + StatusCode._400.toString();
                else response = handlerMap.get(fields[0]).handler(request);    
            }

            System.out.println("Sending: " + response);
            output.writeBytes(response);
            output.flush();

            try {
                Thread.sleep(250);
            } catch(InterruptedException e) {
                System.err.println("[Error] error occured while sending message to client");
            }

            
            reader.close();
            output.close();

        } catch(IOException e) {
            System.err.println("[Error] failed to communicate with client");
        }
    }


}
