import java.net.*;
import java.io.*;

public class Client {

	public static void main(String[] args) {
		
		if (args.length != 2) {
			System.err.println("Usage: java EchoClient <host name> <port number>");
			System.exit(1);
		}

        	String hostName = args[0];
        	int portNumber = Integer.parseInt(args[1]);

		try (
			Socket clientsocket = new Socket(hostName, portNumber);
			PrintWriter out = new PrintWriter(clientsocket.getOutputStream(), true);
			BufferedReader in = new BufferedReader(new InputStreamReader(clientsocket.getInputStream()));
		) {
			
			// out.println("GET 123 HTTP/1.1");
			out.println("POST /cgi_bin/upcase.cgi HTTP/1.0\n" +
						"From: me@mycomputer\n" +
						"User-Agent: telnet\n" +
						"Content-Type: application/x-www-form-urlencoded\n" + 
						"Content-Length: 14\n\n"+
						"x=1!&2=y");
			System.out.println("Server: " + in.readLine());

		} catch (Exception e) {
			System.err.println("Don't know about host " + hostName);
			System.exit(1);
		}
	}
}
