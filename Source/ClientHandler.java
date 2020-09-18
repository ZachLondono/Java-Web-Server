import java.net.*;
import java.nio.CharBuffer;
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

                // Read in all lines from client into a buffer
                int offset = 0;
                char[] cbuf = new char[1024];
                int readin;
                while (reader.ready() && (readin = reader.read(cbuf, offset, cbuf.length - offset)) != -1) {
                    offset += readin;
                    if (cbuf.length == offset) cbuf = Arrays.copyOf(cbuf, cbuf.length * 2);
                }
                
                String[] request = String.valueOf(cbuf).split("\n"); 
                String[] fields = request[0].split(Character.toString(32));	// split the first line into fields to validate request
            
                // Check that request is valid
                if (cbuf[offset - 1] != '\n') response = StatusCode._400.toString();    // check that request is terminated by a new line
                else if (fields.length != 3) response = StatusCode._400.toString();     // check that the request line contains only 3 fields
                else if (!fields[2].equals(supportedVerison)) response = supportedVerison + " " + StatusCode._505.toString();   // check the client http version
                else if (!handlerMap.containsKey(fields[0])) response = supportedVerison + " " + StatusCode._400.toString();    // check that the request is valid method
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
