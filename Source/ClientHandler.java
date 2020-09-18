import java.net.*;
import java.io.*;
import java.util.*;

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
		    break;
                }
            }

            if (!timedOut) {

	        // Read in all lines from client
                String line;
                ArrayList<String> lines = new ArrayList<>();		
                while (reader.ready() && (line = reader.readLine()) != null) {
			lines.add(line);
			System.out.println(lines.get(lines.size() - 1));
		}

                String[] request = lines.toArray(String[]::new); 
                String[] fields = request[0].split(Character.toString(32));	// split the first line into fields to validate request
		
		// Check that request is valid
                if (fields.length != 3) response = StatusCode._400.toString();
                else if (!fields[2].equals(supportedVerison)) response = supportedVerison + " " + StatusCode._505.toString();
                else if (!handlerMap.containsKey(fields[0])) response = supportedVerison + " " + StatusCode._400.toString();
                else response = handlerMap.get(fields[0]).handler(request);    // Generate response 
           
	   }

            System.out.println("Sending: " + response);
            output.writeBytes(response);	// Send response back to client
            output.flush();

            try {
                Thread.sleep(250);	// Wait 1/4 second
            } catch(InterruptedException e) {
                System.err.println("[Error] error occured while sending message to client");
            }

            // Close socket I/O
            reader.close();
            output.close();

        } catch(IOException e) {
            System.err.println("[Error] failed to communicate with client");
        }
    }


}
