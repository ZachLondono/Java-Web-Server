import java.net.*;
import java.io.*;
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
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());
        ){

            // TODO: Timeout if client does not respond:
            // while(not timed out) 
            //    if ready 
            //      readLine()
            //      sendResponse(output, request)   
            // send timeout
	
            String request = reader.readLine();
            sendResponse(request, output);


        } catch(IOException e) {
            System.err.println("[Error] failed to communicate with client");
        }
    }

    private void sendResponse(String request, DataOutputStream output) throws IOException {

        // TODO: correctly parse request
        String[] fields = request.split(Character.toString(32));

        String response = "";
        if (fields.length != 3) response = StatusCode._400.toString();
        else if (!fields[2].equals(supportedVerison)) response = supportedVerison + " " + StatusCode._505.toString();
        else if (!handlerMap.containsKey(fields[0])) response = supportedVerison + " " + StatusCode._400.toString();
        else response = handlerMap.get(fields[0]).handler(fields[1]);

        System.out.println("Sending: " + response);
        output.writeBytes(response);
        output.flush();

        // TODO: flush and wait before closing

    }

}
