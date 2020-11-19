import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.nio.file.AccessDeniedException;

public class PartialHTTP1Server {

    public static int PORT;
    public static final String SUPPORTED_VERSION = "HTTP/1.0";
    public static final int MAXIMUM_THREAD_COUNT = 50; 
    public final static String CRLF  = "" + (char) 0x0D + (char) 0x0A; 

    private static int activeThreadCount;
    private static HashMap<String, RequestHandler> handlerMap;
    
    public static void main(String[] args) {

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

    private static byte[] checkExecutable(String path) {

        if (!path.endsWith(".cgi")) {
            String response = SUPPORTED_VERSION + " "  + StatusCode._405;
            return response.getBytes();
        }

        String cwd = "";
        try {
            cwd = new java.io.File(".").getCanonicalPath();
        } catch (Exception e) {
            e.printStackTrace();
            String response = SUPPORTED_VERSION + " "  + StatusCode._500;
            return response.getBytes();
        }

        File file = new File(cwd + "/"+ path);

        if (!file.exists()) {
            String response = SUPPORTED_VERSION + " "  + StatusCode._404;
            return response.getBytes();
        }
        if (!file.canExecute()) {
            String response = SUPPORTED_VERSION + " "  + StatusCode._403;
            return response.getBytes();
        }

        return null;
    }

    private static String decode(String string) {
        
        ArrayList<Integer> arr = new ArrayList<>();    
        for(int i=0;i<string.length();i++) {   
                if(i<string.length()-1 && Pattern.matches("^[!][!*'();:@$+,/?#\\[\\]\\s\\t]", string.substring(i,i+2))) {        
                    arr.add(i);                    
                    i=i+1;                    
                }
        }

        String str3="";

        int val =0;
        for(int i=0;i<string.length();i++) {    
            //1 == 1,3    
            for(int j=0;j<arr.size();j++) {        
                //1==1
                if(arr.get(j)==i) {
                    val=1;            
                }
            }    
            if(val==1) {
                val=0;
                continue;//starts the i th lopp again    
            }else {
                
                str3=str3+string.charAt(i);
                val=0;
                
            }
        }        

    	return str3;
    }

    private static String execute(String program, String params, String from, String userAgent) {

        try {

            ProcessBuilder builder = new ProcessBuilder("./" + program);

            builder.environment().put("CONTENT_LENGTH", params.length() + "");
            builder.environment().put("SCRIPT_NAME", program);
            builder.environment().put("SERVER_NAME", InetAddress.getLocalHost().getHostName());
            builder.environment().put("SERVER_PORT", PORT + "");
            if (from != null) builder.environment().put("HTTP_FROM", from);
            if (userAgent!= null) builder.environment().put("HTTP_USER_AGENT", userAgent);


            Process p = builder.start();

            BufferedWriter bWriter = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));

            bWriter.write(params);
            bWriter.close();

            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));

            String programOutput= "";
            String currentLine = null;
            while ((currentLine = input.readLine()) != null)
                programOutput += currentLine + "\n";
            
            if (programOutput.length() == 0) return programOutput;
            return programOutput.substring(0, programOutput.length() - 1);
    
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

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
                                response = SUPPORTED_VERSION + " "  + StatusCode._304.toString() + CRLF +  "Expires: Tue, 1 Jan 2021 1:00:00 GMT" + CRLF;
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

        // Parsing the headers
        String from = null;
        String userAgent = null;
        boolean contains_content_length = false;
        boolean contains_content_type = false;
        if (request.length > 1) {
            for (int i = 0; i < request.length; i++) {
                
                if (request[i].contains("Content-Type: ")) {

                    contains_content_type = true;
                    String content_type = request[i].substring(request[i].indexOf(" ") + 1).strip();

                    if (!content_type.equals("application/x-www-form-urlencoded"))  {
                        String response = SUPPORTED_VERSION + " "  + StatusCode._500;
                        return response.getBytes();
                    }

                } else if (request[i].contains("Content-Length:")) {
                
                    contains_content_length = true;
                    String content_length = request[i].substring(request[i].indexOf(" ") + 1).strip();

                    try {
                        Integer.parseInt(content_length);
                    } catch (NumberFormatException e) {
                        String response = SUPPORTED_VERSION + " "  + StatusCode._411;
                        return response.getBytes();
                    }
                
                } else if (request[i].contains("From:")) {
                    from = request[i].substring(request[i].indexOf(" ") + 1);
                } else if (request[i].contains("User-Agent:")) {
                    from = request[i].substring(request[i].indexOf(" ") + 1);
                }

            }
        }

        if (!contains_content_length) {
            String response = SUPPORTED_VERSION + " "  + StatusCode._411;
            return response.getBytes();
        }

        if (!contains_content_type)  {     
            System.out.println("No Content Type!");   
            System.out.println("Content Type MISSSING");
            String response = SUPPORTED_VERSION + " "  + StatusCode._500;
            return response.getBytes();
        }

        // three firs fields of the request, "HTTP/1.0 executable POST"
        String[] fields = request[0].split(" ");
        String executable = fields[1];        

        System.out.println("Executable: " + executable);

        // if the executable is invalid, it will return the byte[] error response
        byte[] response;
        if ((response = checkExecutable(executable)) != null) return response;        

        // TODO: might want to look for multi line entity bodys
        String encoded_body = request[request.length - 2];
        String decoded_body = decode(encoded_body);

        System.out.println("Encoded Body: " + encoded_body);
        System.out.println("Decoded Body: " + decoded_body);

        String output = execute(executable, decoded_body, from, userAgent);
        if (output == null) return (SUPPORTED_VERSION + " "  + StatusCode._500).getBytes();

        String response_code = output.length() == 0 ? StatusCode._204.toString() : StatusCode._200.toString(); 
        
        response = (SUPPORTED_VERSION + " "  + response_code + CRLF + 
                                "Content-Length: " + output.length() + CRLF + 
                                "Content-Type: text/html" + CRLF +
                                "Allow: GET, POST, HEAD" + CRLF +
                                "Expires: Tue, 1 Jan 2021 1:00:00 GMT" + CRLF +
                                "Content-Encoding: identity" + CRLF + CRLF +
                                output + CRLF + CRLF
                                ).getBytes();     
        
        return response;
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
