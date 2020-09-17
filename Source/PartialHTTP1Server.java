import java.net.*;
import java.io.*;
import java.util.HashMap;

public class PartialHTTP1Server {

    private static HashMap<String, RequestHandler> handlerMap;

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
            
            System.out.println("Server started on port: " + PORT);

            handlerMap = new HashMap<>();
            handlerMap.put("GET", PartialHTTP1Server::GET);
            handlerMap.put("POST", PartialHTTP1Server::POST);
            handlerMap.put("HEAD", PartialHTTP1Server::HEAD);
            handlerMap.put("PUT", (request) ->  "HTTP/1.0" + StatusCode._501.toString());
            handlerMap.put("DELETE", (request) ->  "HTTP/1.0" + StatusCode._501.toString());
            handlerMap.put("LINK", (request) ->  "HTTP/1.0" + StatusCode._501.toString());
            handlerMap.put("UNLINK", (request) ->  "HTTP/1.0" + StatusCode._501.toString());


            Socket clientSocket;
            while ((clientSocket = serverSocket.accept()) != null) {
                System.out.println("New connection from client: " + clientSocket.getInetAddress());
                addClient(clientSocket);
            }

        } catch (IOException e) {
            System.err.println("[Fatal Error] Failed to set up server");
            e.printStackTrace();
        }

    }
 
    private static void addClient(Socket clientSocket) {
        // TODO: add to thread pool
        new Thread(new ClientHandler(clientSocket, handlerMap)).start();;
    }



    // --------- Method Handler Implementations --------------
    static interface RequestHandler {
        abstract String handler(String request);
    }

    private static String GET(String request) {
        return "HTTP/1.0 " + StatusCode._200.toString();
    }

    private static String POST(String request) {
        return GET(request);
    }

    private static String HEAD(String request) {
        return "HTTP/1.0 " + StatusCode._200.toString();
    }

    
}
