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
            handlerMap.put("PUT", (request) ->  (SUPPORTED_VERSION + " "  + StatusCode._501.toString()).getBytes());
            handlerMap.put("DELETE", (request) ->(SUPPORTED_VERSION + " "  + StatusCode._501.toString()).getBytes());
            handlerMap.put("LINK", (request) ->  (SUPPORTED_VERSION + " "  + StatusCode._501.toString()).getBytes());
            handlerMap.put("UNLINK", (request) ->  (SUPPORTED_VERSION + " "  + StatusCode._501.toString()).getBytes());
            
            activeThreadCount = 0;

            Socket clientSocket;
            while ((clientSocket = serverSocket.accept()) != null) {
                // Accepts a new connection from a client and submits it to a thread executor to be handled by the thread handler
                incrimentActiveCount();
                System.out.println("New Connection From: " + clientSocket.getInetAddress() + " Active Connections: " + getActiveCount());
                executor.submit(new ClientHandler(clientSocket, handlerMap, getActiveCount()));
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

    public synchronized static void incrimentActiveCount() {
        activeThreadCount++;
    }

    public synchronized static void decrimentActiveCount() {
        activeThreadCount--;
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
    private static byte[] getFileContent(File file) throws FileNotFoundException, IOException {
        FileInputStream reader = new FileInputStream(file);
        int offset = 0;
        byte[] buf = new byte[1024];
        int readin;
        while ((readin = reader.read(buf, offset, buf.length - offset)) != -1) {
            offset += readin;
            if (buf.length == offset) buf = Arrays.copyOf(buf, buf.length * 2);
        }
        reader.close();
        return Arrays.copyOfRange(buf,0, offset);
    }

    private static String getHeaders(File file) throws FileNotFoundException, AccessDeniedException {

        // Throw exception if we can't create the headers
        if(!file.exists()) throw new FileNotFoundException();
        if(!file.canRead()) throw new AccessDeniedException("Couldn't read file");
        
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        String headers = "Content-Type: " + getMimeType(file.getName()) + CRLF;
        headers += "Content-Length: " + file.length() + CRLF;
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
        
        String response = SUPPORTED_VERSION + " "+ StatusCode._200.toString() + CRLF;
        
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        try {
            
            String cwd = new java.io.File(".").getCanonicalPath();
            File file = new File(cwd + "/"+ resource);

            // Generate headers for response
            response += getHeaders(file);

            // If the request has headers, we need to check if it has the If-Modified-Since header
            if (request.length > 1){
                int x = 0;
                for(x=0;x<request.length;x++){
                    if(request[x].contains("If-Modified-Since:")) {
                        // Get the date substring from the header
                        String ifModifiedString = request[x].substring(request[x].indexOf(" ") + 1);
                        try{
                            // If the if-modified-since date is of an invalid format, a ParseException will be thrown, so we can ignore the if-modified-date
                            Date ifModifiedDate = sdf.parse(ifModifiedString);
                            Date lastModifiedDate = new Date(file.lastModified());
                            // Compare it against file's last modified date, if the file is older than the ifModifiedDate, then we return status 304 Not Modified, with the expiration date 
                            if (lastModifiedDate.compareTo(ifModifiedDate) < 0)
                                response = SUPPORTED_VERSION + " "  + StatusCode._304.toString() + CRLF +  "Expires: Tue, 1 Jan 2021 1:00:00 GMT" + CRLF;;
                            break;
                        } catch(ParseException ex){
                            // malformed if-modified-since, ignore it
                            break;                            
                        }
                    }
                }

            } 

            byte[] payload = getFileContent(file);
            response +=  CRLF + CRLF;
            byte[] response_bytes = response.getBytes();
            byte[] message = new byte[response_bytes.length + payload.length];

            // combine payload and response
            System.arraycopy(response_bytes, 0, message, 0, response_bytes.length);
            System.arraycopy(payload, 0, message, response_bytes.length, payload.length);
        
            return message;

        }catch (AccessDeniedException e) {
            response = SUPPORTED_VERSION + " "  + StatusCode._403.toString();
        }catch (FileNotFoundException e) {
            response = SUPPORTED_VERSION + " "  + StatusCode._404.toString();
        }catch (IOException e){
            response = SUPPORTED_VERSION + " "  + StatusCode._500;   
            e.printStackTrace();             
        }

        return response.getBytes();
    
    }

    private static byte[] POST(String[] request) {
    	
	/* 1) Check that request is valid POST request
		(only POST request specific errors would need to be checked,
		the rest should have been checked before this function is executed)
	*  2) Decode entity body
	*  3) Execute requested script, using parameters from decoded entity body
	*  4) Format and return response
	*/
	
        return GET(request);
    }
	
    private static String execute(String application, HashMap<String, String> parameters) {

    }



    private static byte[] HEAD(String[] request) {

        String[] fields = request[0].split(" ");
        String resource = fields[1];        
        String response = SUPPORTED_VERSION + " "  + StatusCode._200.toString() + CRLF;

        try{
        
            String cwd = new java.io.File(".").getCanonicalPath();
            File file = new File(cwd + "/"+ resource);
            response += getHeaders(file);

        }catch (AccessDeniedException e) {
            response = SUPPORTED_VERSION + " "  + StatusCode._403.toString();
        }catch (FileNotFoundException e) {
            response = SUPPORTED_VERSION + " "  + StatusCode._404.toString();
        }catch (IOException e){
            response = SUPPORTED_VERSION + " "  + StatusCode._500;    
            e.printStackTrace();            
        }

        return response.getBytes();
       
    }
    
}
