import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PartialHTTP1Server {

    public static final String SUPPORTED_VERSION = "HTTP/1.0";
    public static final int MAXIMUM_THREAD_COUNT = 50; 
    private static int activeThreadCount;
    private static HashMap<String, RequestHandler> handlerMap;
    private static HashMap<String, String> mimeMap;

    public static void main(String[] args) {

        final int PORT;
        ExecutorService executor = Executors.newCachedThreadPool();

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
            handlerMap.put("PUT", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            handlerMap.put("DELETE", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            handlerMap.put("LINK", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            handlerMap.put("UNLINK", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            
            mimeMap = new HashMap<>();
            mimeMap.put("html", "text/html");
            mimeMap.put("htm", "text/html");
            mimeMap.put("txt", "text/plain");
            mimeMap.put("jpg", "image/jpeg");
            mimeMap.put("jpeg", "text/jpeg");
            mimeMap.put("png", "text/png");
            mimeMap.put("gif", "text/gif");
            mimeMap.put("pdf", "application/pdf");
            mimeMap.put("gz", "application/x-gzip");
            mimeMap.put("zip", "application/zip");

            activeThreadCount = 0;

            Socket clientSocket;
            while ((clientSocket = serverSocket.accept()) != null) {
                System.out.println("New Connection From: " + clientSocket.getInetAddress());
                activeThreadCount++;
                executor.submit(new ClientHandler(clientSocket, handlerMap));
            }

        } catch (IOException e) {
            System.err.println("[Fatal Error] Failed to set up server");
            e.printStackTrace();
        }

    }

    public synchronized static int getActiveCount() {
        return activeThreadCount;
    }

    public synchronized static void setActiveCount(int new_count) {
        activeThreadCount = new_count;
    }

    private String getMimeType(String path) {

        String extension = path.substring(path.lastIndexOf(".") + 1);
        String mimeType = "";
        if (mimeMap.containsKey(extension)) {
            mimeType = mimeMap.get(extension);
        } else mimeType = "application/octet-stream";

        return mimeType;

    }

    // --------- Method Handler Implementations --------------
    
    static interface RequestHandler {
        abstract String handler(String[] request);
    }

    private static String GET(String[] request) { 
        return "HTTP/1.0 " + StatusCode._200.toString();
    }

    private static String POST(String[] request) {
        return GET(request);
    }

    private static String HEAD(String[] request) {
        return "HTTP/1.0 " + StatusCode._200.toString();
    }

    
}
