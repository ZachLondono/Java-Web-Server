import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class PartialHTTP1Server {

    public static final String SUPPORTED_VERSION = "HTTP/1.0";
    public static final int MAXIMUM_THREAD_COUNT = 50; 
    private static int activeThreadCount;
    private static HashMap<String, RequestHandler> handlerMap;

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
	
	    // Maps a given function name to it's defined method
            handlerMap = new HashMap<>();
            handlerMap.put("GET", PartialHTTP1Server::GET);
            handlerMap.put("POST", PartialHTTP1Server::POST);
            handlerMap.put("HEAD", PartialHTTP1Server::HEAD);
	    // The following functions are not implimented
            handlerMap.put("PUT", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            handlerMap.put("DELETE", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            handlerMap.put("LINK", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            handlerMap.put("UNLINK", (request) ->  "HTTP/1.0 " + StatusCode._501.toString());
            
            activeThreadCount = 0;

            Socket clientSocket;
            while ((clientSocket = serverSocket.accept()) != null) {
	        // Accepts a new connection from a client and submits it to a thread executor to be handled by the thread handler
                System.out.println("New Connection From: " + clientSocket.getInetAddress());
		setActiveCount(getActiveCount() + 1);
                executor.submit(new ClientHandler(clientSocket, handlerMap));
            }

        } catch (IOException e) {
            System.err.println("[Fatal Error] Failed to set up server");
            e.printStackTrace();
        }

    }

    // Both static functions used to interface with the static activeThreadCount variable are synchronized in order to restrict only 1 thread to access either at a time
    public synchronized static int getActiveCount() {
        return activeThreadCount;
    }

    public synchronized static void setActiveCount(int new_count) {
        activeThreadCount = new_count;
    }

    private static String getMimeType(String path) {

	// extracts the extension from a given file path and returns it's mime type
        String extension = (path.substring(path.lastIndexOf(".") + 1)).toLowerCase();
        String mimeType = "";
	switch (extension) {
	    case "html":
	    case "htm":
	    	mimeType = "text/html";
	        break;
	    case "txt":
	        mimeType = "text/plain";
		break;
	    case "jpg":
	    case "jpeg":
		mimeType = "image/jpeg";
		break;
	    case "png":
	        mimeType = "image/png";
		break;
	    case "gif":
		mimeType = "image/gif";
		break;
	    case "pdf":
		mimeType = "application/pdf";
		break;
	    case "gz":
		mimeType = "application/x-gzip";
		break;
	    case "zip":
		mimeType = "application/zip";
		break;
	    default:
	    	mimeType = "application/octet-stream";
	}

        return mimeType;

    }

    // --------- Method Handler Implementations --------------
    
    static interface RequestHandler {
        abstract String handler(String[] request);
    }

    private static String GET(String[] request) { 
    	String response = 
		"HTTP/1.0 200 OK\n" +
		"Content-Type:" + getMimeType(request[0].split(" ")[1]) +"\n\n" +
		"<html>" +
		"<h1>Hello World</h1>" +
		"</html>";
        //return "HTTP/1.0 " + StatusCode._200.toString();
	return response;
    }

    private static String POST(String[] request) {
        return GET(request);
    }

    private static String HEAD(String[] request) {
        return "HTTP/1.0 " + StatusCode._200.toString();
    }

    
}
