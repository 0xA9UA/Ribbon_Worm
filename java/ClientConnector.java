import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Handles connections to client machines discovered via mDNS.
 * Connections are established through a SOCKS5 proxy.
 */
public class ClientConnector {
  private static final int PROXY_PORT = 1080; // Port for the SOCKS5 proxy

  /**
   * Attempts to connect to a list of client addresses through a SOCKS5 proxy.
   * For each client address in the provided list, it tries to establish a socket connection.
   * Connection successes and failures are logged to standard output and standard error, respectively.
   *
   * @param clientList A list of {@link InetAddress} objects representing the clients to connect to.
   *                   MDNSBroadcaster passes a copy of its list, so direct iteration is safe.
   */
  public static void connectToClients(List<InetAddress> clientList) {
    // Define the SOCKS5 proxy settings. Uses localhost and a predefined PROXY_PORT.
    SocketAddress proxyAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), PROXY_PORT);
    Proxy proxy = new Proxy(Proxy.Type.SOCKS, proxyAddress);

    // Iterate over the provided list of client addresses.
    for (InetAddress clientAddress : clientList) {
      try (Socket socket = new Socket(proxy)) { // Create a new socket that connects through the proxy.
        // Define the target client's socket address. Port 0 means any available port (standard for mDNS services not specifying one).
        SocketAddress clientSocketAddress = new InetSocketAddress(clientAddress, 0);

        // Attempt to connect to the client.
        socket.connect(clientSocketAddress);
        System.out.println("Successfully connected to client: " + clientAddress);

        // Placeholder for actual data exchange or further interaction with the client.
        // e.g., socket.getOutputStream().write(...); socket.getInputStream().read(...);
      } catch (IOException e) {
        // Log connection failures to standard error.
        System.err.println("Failed to connect to client: " + clientAddress + ". Error: " + e.getMessage());
      }
    }
  }
}

