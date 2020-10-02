import java.net.*;
import java.io.*;
import java.util.*;

public class ClientHandler implements Runnable {

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

            byte[] response = {};

            if(PartialHTTP1Server.getActiveCount() > PartialHTTP1Server.MAXIMUM_THREAD_COUNT) {        // If maximum thread count was reached, deny service to client
                System.out.println("Connection from " + clientSocket.getInetAddress() + " denied: Maximum connected clients reached");
                response = (PartialHTTP1Server.SUPPORTED_VERSION + " " + StatusCode._503.toString()).getBytes();     
                sendResponseAndClose(output, response);
                reader.close();
                return;
            }

            long connectedTime = System.currentTimeMillis();
            while (!reader.ready()) {
                if (System.currentTimeMillis() - connectedTime > 5000) {        // Check if elapsed time since connection is over 5 seconds
                    // Client has timed out
                    System.out.println("Connection from " + clientSocket.getInetAddress() + " denied: Client timed out");
                    response = (PartialHTTP1Server.SUPPORTED_VERSION + " " + StatusCode._408.toString()).getBytes();       
                    sendResponseAndClose(output, response);
                    reader.close();
		            return;
                }
            }

            // Read in all lines from client into a buffer
            int offset = 0;
            char[] cbuf = new char[1024];
            int readin;
            while (reader.ready() && (readin = reader.read(cbuf, offset, cbuf.length - offset)) != -1) {
                offset += readin;
                if (cbuf.length == offset) cbuf = Arrays.copyOf(cbuf, cbuf.length * 2);
            }
            
            System.out.println("====================================================\nRecieved request from: " + clientSocket.getInetAddress() + "\n********************************************\n" + String.valueOf(cbuf) + "====================================================");
        
            String[] request = String.valueOf(cbuf).split("\n"); 
            String[] fields = request[0].split(" ");	// split the first line into fields to validate request

            // Check that request is valid

            if (/*request.length == 1 && */ cbuf[offset - 1] != '\n') response = (PartialHTTP1Server.SUPPORTED_VERSION + " " + StatusCode._400.toString()).getBytes();    // should check if there are no headers, then only one newline, if there are headers then only 2 new lines
            else if (fields.length != 3) response = (PartialHTTP1Server.SUPPORTED_VERSION + " " + StatusCode._400.toString()).getBytes();     // check that the request line contains only 3 fields
            else if (!isValidVersion(fields[2])) response = (PartialHTTP1Server.SUPPORTED_VERSION + " " + StatusCode._505.toString()).getBytes();   // check the client http version
            else if (!handlerMap.containsKey(fields[0])) response = (PartialHTTP1Server.SUPPORTED_VERSION + " " + StatusCode._400.toString()).getBytes();    // check that the request is valid method
            else response = handlerMap.get(fields[0]).handler(request);    // Generate response 

            sendResponseAndClose(output, response);
            reader.close();
            
        } catch(IOException e) {
            System.err.println("[Error] failed to communicate with client");
        }
    }

    private boolean isValidVersion(String version) {
        try {
            System.out.println("VERSION: " + Double.parseDouble(version.trim().split("/")[1]));
            return Double.parseDouble(version.trim().split("/")[1]) <= 1.0;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendResponseAndClose(DataOutputStream output, byte[] response) throws IOException {
        System.out.println("====================================================\nSending response to: " + clientSocket.getInetAddress() + "\n********************************************\n" + response + "\n====================================================");
        // if (response.charAt(response.length()-1) != '\n') response = response + "\n";
        output.write(response);	// Send response back to client
        output.flush();
        try {
            Thread.sleep(250);	// Wait 1/4 second
        } catch(InterruptedException e) {
            System.err.println("[Error] error occured while sending message to client");
        }
        System.out.println("Closing connection with client: " + clientSocket.getInetAddress());
        output.close();
        clientSocket.close();
        PartialHTTP1Server.setActiveCount(PartialHTTP1Server.getActiveCount()-1);
    }
}
