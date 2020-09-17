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
			
			out.println("GET 123 HTTP/1.1");
			System.out.println("Server: " + in.readLine());

		} catch (Exception e) {
			System.err.println("Don't know about host " + hostName);
			System.exit(1);
		}/* catch (IOException e) {
			System.err.println("Couldn't get I/O for the connection to " + hostName);
			System.exit(1);
 		} */

	}
}
