import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
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
    SocketAddress proxyAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), PROXY_PORT);
    Proxy proxy = new Proxy(Proxy.Type.SOCKS, proxyAddress);

    for (InetAddress clientAddress : clientList) {
      boolean connected = false;
      try (Socket socket = new Socket(proxy)) {
        SocketAddress target = new InetSocketAddress(clientAddress, RelayServer.RELAY_PORT);
        socket.connect(target, 1000);
        System.out.println("Successfully connected to client: " + clientAddress);
        connected = true;
      } catch (IOException e) {
        System.err.println("Direct connection to " + clientAddress + " failed: " + e.getMessage());
      }

      if (!connected) {
        for (InetAddress relay : clientList) {
          if (relay.equals(clientAddress)) {
            continue;
          }
          try (Socket relaySocket = new Socket(proxy)) {
            SocketAddress relayAddr = new InetSocketAddress(relay, RelayServer.RELAY_PORT);
            relaySocket.connect(relayAddr, 1000);
            PrintWriter out = new PrintWriter(relaySocket.getOutputStream(), true);
            out.println(clientAddress.getHostAddress());
            System.out.println("Connected to client " + clientAddress + " via relay " + relay);
            connected = true;
            break;
          } catch (IOException e) {
            System.err.println("Relay attempt via " + relay + " failed: " + e.getMessage());
          }
        }
      }

      if (!connected) {
        System.err.println("Unable to reach client " + clientAddress + " via any known path.");
      }
    }
  }
}

