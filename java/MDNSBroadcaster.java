
import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;

public class MDNSBroadcaster {
  private static final int PORT = 7237;
  private static final String GROUP_ADDRESS = "224.0.0.251";
  private static boolean stop = false;
  private static boolean stopConnect = false;

  // Setter for stop() private variable in the form of a method to be called from outside of this class. Used to stop MDNS Discovery/Broadcasts
  public static void stop() {
    stop = true;
  }
	
  // Setter for stopConnect() private variable in the form of a method to be called from outside of this class. Used to stop SOCKS5 Proxy connection attempts
  public static void stopConnect() {
    stop = true;
  }

  public MDNSBroadcaster() {
    List<InetAddress> clientList = new ArrayList<>();

    // Create a multicast socket and bind it to the specified port
    try (MulticastSocket socket = new MulticastSocket(PORT)) {
      // Set the multicast socket to use the network interface of the local host
      socket.setInterface(InetAddress.getLocalHost());

      // Join the multicast group
      socket.joinGroup(InetAddress.getByName(GROUP_ADDRESS));

      // Start a separate thread to listen for mDNS packets from other clients
      new Thread(() -> {
        try {
          // Continuously listen for mDNS packets and add the sender's address to the client list
          while (!stop) {
            byte[] buffer = new byte[1024];
            socket.receive(new java.net.DatagramPacket(buffer, buffer.length));
            clientList.add(socket.getInetAddress());
          }
        }catch (IOException e) {
          e.printStackTrace();
        }
      }).start();

      // Continuously broadcast mDNS packets to other clients
      while (!stop) {
        byte[] buffer = "Hello!".getBytes();
        socket.send(new java.net.DatagramPacket(buffer, buffer.length, InetAddress.getByName(GROUP_ADDRESS), PORT));
        Thread.sleep(1000);
      }
      while (!stopConnect) {
	ClientConnector.connectToClients(clientList);
	Thread.sleep(1000);	
      }  
    }catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }
}

