import java.net.*;
import java.nio.charset.StandardCharsets;
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
            return null;
        }
    }

    // --------- Method Handler Implementations --------------
    
    static interface RequestHandler {
        abstract String handler(String[] request);
    }

    private static String GET(String[] request) { 
        Date date1 = null;
        int truth = 0;
        String lineRequest = request[0];
        String[] fields = request[0].split(Character.toString(32));
        String resource = fields[1];
        //building header
        String header = "";
        header += "HTTP/1.0" + " "+ StatusCode._200.toString() + '\n';
        String contentType = "";
        header += "Content-Type: " + contentType + '\n';
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        //first we must separate request---
        //we have to split up the lines. We really only need two lines. First we have to check if request.length is greater than 1. If its greater than 1 then we take the first and check the rest. The moment we reach something that matches If-Modified-Since: then we can save that and stop the for loop looking for it.
        if (request.length > 1){

            //we must extract the If modified since value.
            String test = "If-Modified-Since:";
            String lastmodified = "";
            int x = 0;
            for(x=0;x<request.length;x++){
                String find = request[x].substring(0,request[x].indexOf(' '));
                if(find.equals(test)) {
                    truth = 1;
                    lastmodified = request[x];
                    lastmodified = lastmodified.substring(lastmodified.indexOf(' ') + 1,lastmodified.length());
                    try{
                        date1 = sdf.parse(lastmodified);
                        //System.out.println("This is lastmodified line:" + date1);
                    }
                    catch(ParseException ex){
                        //do somethign here
                        truth = 0;
                        //System.out.println("Parsing didnt work this is lastmodified:" + lastmodified);
                    }
                    break;
                }
            }
            
        }
        //System.out.println("This is the line request:" + lineRequest);
        //We will now take the resource packet from the line request
        //System.out.println("This is the resource we have to examine: " + resource);
        //FOR CONTENT LENGTHmak
        try{
            
            String cwd = new java.io.File(".").getCanonicalPath();
            File file = new File(cwd + resource);
            boolean exists = file.exists();
            Path path = Paths.get(cwd);
            if(exists == false) {
                return "HTTP/1.0" + " " + StatusCode._404.toString();
            }
            //System.out.println("File Length:" + file.length());
            boolean readable = Files.isReadable(path);
            if(readable == false){
                return "HTTP/1.0" + " " + StatusCode._403.toString();
            }
            header += "Content-Length: " + file.length() + '\n';
            // System.out.println("File last modified:" + file.lastModified());
        }catch(IOException e){
        }
        //FOR CONTENT LAST MODIFIED
        try{
            //check if truth is 1 if it is 1 then we must compare the dates if not just normally continue printing out the date.
            String cwd = new java.io.File(".").getCanonicalPath();
            File file = new File(cwd + resource);
            String checkIfGreater = sdf.format(file.lastModified());
            Date checkIfGreater1 = sdf.parse(checkIfGreater);
            if(truth == 1){
                if (checkIfGreater1.compareTo(date1)<0){
                    return("HTTP/1.0" + " " + StatusCode._304.toString());
                }
            }
            //System.out.println("File last modified:" + checkIfGreater);
            header += "Last-Modified: " + checkIfGreater + '\n';
            // System.out.println("File last modified:" + file.lastModified());
        }catch(Exception e){
        }
        header += "Content-Encoding: identity" + '\n';
        header += '\n' + "Some numbers go here";
        return header;
       
        //System.out.println("lastModifiedTime: " + attr.lastModifiedTime());
        //WORK ON GETTING LAST MODIFIED TO WORK AND RUN PROPERLY, IF THE NUMBERS GREATER THAN THE IF CONDIITON THEN BREAK WITH SOME SORT OF ERROR MESSAGe. WORK ON ERROR MESSAGES THEN FIGURE OUT SENDING FILES
        // return "HTTP/1.0 " + StatusCode._200.toString();
    }

    private static String POST(String[] request) {

        return GET(request);
    }

    private static String HEAD(String[] request) {
        Date date1 = null;
        int truth = 0;
        String lineRequest = request[0];
        String[] fields = request[0].split(Character.toString(32));
        String resource = fields[1];
        //building header
        String header = "";
        header += "HTTP/1.0" + " "+ StatusCode._200.toString() + '\n';
        String contentType = "";
        header += "Content-Type: " + contentType + '\n';
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy kk:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        //first we must separate request---
        //we have to split up the lines. We really only need two lines. First we have to check if request.length is greater than 1. If its greater than 1 then we take the first and check the rest. The moment we reach something that matches If-Modified-Since: then we can save that and stop the for loop looking for it.
        if (request.length > 1){

            //we must extract the If modified since value.
            String test = "If-Modified-Since:";
            String lastmodified = "";
            int x = 0;
            for(x=0;x<request.length;x++){
                String find = request[x].substring(0,request[x].indexOf(' '));
                if(find.equals(test)) {
                    truth = 1;
                    lastmodified = request[x];
                    lastmodified = lastmodified.substring(lastmodified.indexOf(' ') + 1,lastmodified.length());
                    try{
                        date1 = sdf.parse(lastmodified);
                        //System.out.println("This is lastmodified line:" + date1);
                    }
                    catch(ParseException ex){
                        //do somethign here
                        truth = 0;
                        //System.out.println("Parsing didnt work this is lastmodified:" + lastmodified);
                    }
                    break;
                }
            }
            
        }
        //System.out.println("This is the line request:" + lineRequest);
        //We will now take the resource packet from the line request
        //System.out.println("This is the resource we have to examine: " + resource);
        //FOR CONTENT LENGTHmak
        try{
            
            String cwd = new java.io.File(".").getCanonicalPath();
            File file = new File(cwd + resource);
            boolean exists = file.exists();
            Path path = Paths.get(cwd);
            if(exists == false) {
                return "HTTP/1.0" + " " + StatusCode._404.toString();
            }
            //System.out.println("File Length:" + file.length());
            boolean readable = Files.isReadable(path);
            if(readable == false){
                return "HTTP/1.0" + " " + StatusCode._403.toString();
            }
            header += "Content-Length: " + file.length() + '\n';
            // System.out.println("File last modified:" + file.lastModified());
        }catch(IOException e){
        }
        //FOR CONTENT LAST MODIFIED
        try{
            //check if truth is 1 if it is 1 then we must compare the dates if not just normally continue printing out the date.
            String cwd = new java.io.File(".").getCanonicalPath();
            File file = new File(cwd + resource);
            String checkIfGreater = sdf.format(file.lastModified());
            Date checkIfGreater1 = sdf.parse(checkIfGreater);
            // if(truth == 1){
            //     if (checkIfGreater1.compareTo(date1)<0){
            //         return("HTTP/1.0" + " " + StatusCode._304.toString());
            //     }
            // }
            //System.out.println("File last modified:" + checkIfGreater);
            header += "Last-Modified: " + checkIfGreater + '\n';
            // System.out.println("File last modified:" + file.lastModified());
        }catch(Exception e){
        }
        header += "Content-Encoding: identity" + '\n';
        header += '\n' + "Some numbers go here";
        return header;
       
        //System.out.println("lastModifiedTime: " + attr.lastModifiedTime());
        //WORK ON GETTING LAST MODIFIED TO WORK AND RUN PROPERLY, IF THE NUMBERS GREATER THAN THE IF CONDIITON THEN BREAK WITH SOME SORT OF ERROR MESSAGe. WORK ON ERROR MESSAGES THEN FIGURE OUT SENDING FILES
        // return "HTTP/1.0 " + StatusCode._200.toString();
    }
    
}
