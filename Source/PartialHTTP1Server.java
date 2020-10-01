import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.nio.file.AccessDeniedException;

public class PartialHTTP1Server {

    public static final String SUPPORTED_VERSION = "HTTP/1.0";
    public static final int MAXIMUM_THREAD_COUNT = 50; 
    private static int activeThreadCount;
    private static HashMap<String, RequestHandler> handlerMap;
    public final static String CRLF  = "" + (char) 0x0D + (char) 0x0A; 

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
            handlerMap.put("PUT", (request) ->  ("HTTP/1.0 " + StatusCode._501.toString()).getBytes());
            handlerMap.put("DELETE", (request) ->("HTTP/1.0 " + StatusCode._501.toString()).getBytes());
            handlerMap.put("LINK", (request) ->  ("HTTP/1.0 " + StatusCode._501.toString()).getBytes());
            handlerMap.put("UNLINK", (request) ->  ("HTTP/1.0 " + StatusCode._501.toString()).getBytes());
            
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

    // Returns the content of a given file as an array of the file's bytes, if its unable to read the file for any reason it will return null
    private static byte[] getFileContent(File file) {
        try (FileInputStream reader = new FileInputStream(file)) {
            int offset = 0;
            byte[] buf = new byte[1024];
            int readin;
            while ((readin = reader.read(buf, offset, buf.length - offset)) != -1) {
                offset += readin;
                if (buf.length == offset) buf = Arrays.copyOf(buf, buf.length * 2);
            }
            return Arrays.copyOfRange(buf,0, offset);
        } catch(Exception e) {

            e.printStackTrace();
            
            return null;
        }
    }

    private static String getHeaders(File file) throws FileNotFoundException, AccessDeniedException {

        if(!file.exists()) throw new FileNotFoundException();

        if(!file.canRead()) throw new AccessDeniedException("Couldn't read file");
        
        String headers = "Content-Type: " + getMimeType(file.getName()) + CRLF;
        headers += "Content-Length: " + file.length() + CRLF;

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        headers += "Last-Modified: " + sdf.format(new Date(file.lastModified())) + CRLF;
        headers += "Expires: Tue, 1 Jan 2021 1:00:00 GMT" + CRLF;
        headers += "Allow: GET, POST, HEAD" + CRLF;
        headers += "Content-Encoding: identity";

        return headers;

    }

    // --------- Method Handler Implementations --------------
    
    static interface RequestHandler {
        abstract byte[] handler(String[] request);
    }

    private static byte[] GET(String[] request) { 

        String[] fields = request[0].split(Character.toString(32));
        String resource = fields[1];
        
        String response = "HTTP/1.0" + " "+ StatusCode._200.toString() + CRLF;
        
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        Date date1 = null;
        int truth = 0;
        //we have to split up the lines. We really only need two lines. First we have to check if request.length is greater than 1. If its greater than 1 then we take the first and check the rest. The moment we reach something that matches If-Modified-Since: then we can save that and stop the for loop looking for it.
        if (request.length > 1){

            //we must extract the If modified since value.
            String test = "If-Modified-Since:";
            String lastmodified = "";
            int x = 0;
            for(x=0;x<request.length;x++){
                if(request[x].contains(test)) {
                    lastmodified = request[x].substring(request[x].indexOf(" ") + 1);
                    try{
                        // If the if-modified-since date is of an invalid format, a ParseException will be thrown, so we can ignore the if-modified-date
                        date1 = sdf.parse(lastmodified);
                        truth = 1;
                    } catch(ParseException ex){
                        truth = 0;
                    }
                    break;
                }
            }
            
        }

        try{

            String cwd = new java.io.File(".").getCanonicalPath();
            File file = new File(cwd + "/"+ resource);
        
            // Generate headers for response
            response += getHeaders(file);

            Date checkIfGreater1 = new Date(file.lastModified());

            // if truth == 1 that means that the request contains a valid if-modified-since header 
            if (truth == 1 && checkIfGreater1.compareTo(date1)<0){
                response = "HTTP/1.0 " + StatusCode._304.toString() + CRLF +  "Expires: Tue, 1 Jan 2021 1:00:00 GMT" + CRLF;;
            } else { // if truth == 0 || (truth == 1 && checkIfGreater1.compareTo(date1) >= 0) 

                byte[] payload = getFileContent(file);
                response +=  CRLF + CRLF;
                byte[] response_bytes = response.getBytes();
                byte[] message = new byte[response_bytes.length + payload.length];

                // combine payload and response
                System.arraycopy(response_bytes, 0, message, 0, response_bytes.length);
                System.arraycopy(payload, 0, message, response_bytes.length, payload.length);
            
                return message;

            }

        }catch (AccessDeniedException e) {
            response = "HTTP/1.0 " + StatusCode._403.toString();
        }catch (FileNotFoundException e) {
            response = "HTTP/1.0 " + StatusCode._404.toString();
        }catch (IOException e){
            response = "HTTP/1.0 " + StatusCode._500;   
            e.printStackTrace();             
        }

        return response.getBytes();
    
    }

    private static byte[] POST(String[] request) {
        return GET(request);
    }

    private static byte[] HEAD(String[] request) {

        String[] fields = request[0].split(" ");
        String resource = fields[1];        
        String response = "HTTP/1.0 " + StatusCode._200.toString() + CRLF;

        try{
        
            String cwd = new java.io.File(".").getCanonicalPath();
            System.out.println(cwd + "/"+ resource);
            File file = new File(cwd + "/"+ resource);
            response += getHeaders(file);

        }catch (AccessDeniedException e) {
            response = "HTTP/1.0 " + StatusCode._403.toString();
        }catch (FileNotFoundException e) {
            response = "HTTP/1.0 " + StatusCode._404.toString();
        }catch (IOException e){
            response = "HTTP/1.0 " + StatusCode._500;    
            e.printStackTrace();            
        }

        return response.getBytes();
       
    }
    
}
