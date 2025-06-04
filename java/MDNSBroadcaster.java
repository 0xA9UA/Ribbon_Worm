
import java.io.IOException;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Handles mDNS (Multicast DNS) discovery and broadcasting services.
 * This class is responsible for:
 * - Broadcasting mDNS packets to announce its presence.
 * - Discovering other clients on the network sending mDNS packets.
 * - Managing a list of discovered clients.
 * - Periodically attempting to connect to discovered clients via the ClientConnector.
 */
public class MDNSBroadcaster {
  private static final int PORT = 7237; // Port for mDNS communication
  private static final String GROUP_ADDRESS = "224.0.0.251"; // Standard mDNS multicast address

  // Volatile flag to control the running state of the broadcaster and its threads.
  private volatile boolean running;
  private MulticastSocket socket; // Socket for multicast communication
  private Thread discoveryThread; // Thread for listening for mDNS packets from other clients
  private Thread broadcastThread; // Thread for broadcasting mDNS packets
  private Thread clientConnectionManagerThread; // Thread for managing connections to discovered clients
  private List<InetAddress> clientList = new ArrayList<>(); // List of discovered client addresses
  private RelayServer relayServer; // Local relay server for tunneling

  /**
   * Constructs an MDNSBroadcaster.
   * The client list is initialized. Actual network operations start with the start() method.
   */
  public MDNSBroadcaster() {
    // clientList is initialized with a new ArrayList directly at declaration.
  }

  /**
   * Starts the mDNS broadcasting and discovery services.
   * Initializes and configures the multicast socket, then starts separate threads for:
   * - Discovering other mDNS clients (startDiscoveryListener).
   * - Broadcasting presence packets (startMdnsBroadcast).
   * - Managing connections to discovered clients (startClientConnectionManager).
   * If socket initialization fails, an error is logged and the broadcaster does not start.
   */
  public void start() {
    running = true; // Set the running flag to true to allow threads to operate.
    try {
      socket = new MulticastSocket(PORT); // Initialize socket to the specified mDNS port.
      socket.setInterface(InetAddress.getLocalHost());
      socket.joinGroup(InetAddress.getByName(GROUP_ADDRESS));

      relayServer = new RelayServer();
      relayServer.start();

      startDiscoveryListener();
      startMdnsBroadcast();
      startClientConnectionManager();
    } catch (IOException e) {
      System.err.println("MDNSBroadcaster: Error initializing multicast socket: " + e.getMessage());
      e.printStackTrace();
      running = false;
    }
  }

  /**
   * Starts a new thread dedicated to listening for mDNS packets from other clients.
   * When a packet is received, the sender's address is added to the clientList if not already present.
   * The loop continues as long as the 'running' flag is true.
   */
  private void startDiscoveryListener() {
    discoveryThread = new Thread(() -> {
      // Loop continues as long as the broadcaster is in a running state.
      while (running) {
        try {
          byte[] buffer = new byte[1024];
          DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
          socket.receive(packet);

          String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
          String[] addresses = msg.split(",");
          for (String addr : addresses) {
            try {
              InetAddress a = InetAddress.getByName(addr.trim());
              if (!clientList.contains(a) && !a.equals(InetAddress.getLocalHost())) {
                clientList.add(a);
              }
            } catch (UnknownHostException ignored) {
              // Ignore malformed addresses
            }
          }
        } catch (IOException e) {
          if (running) {
            System.err.println("MDNSBroadcaster: Error in discovery listener while receiving packet: " + e.getMessage());
            e.printStackTrace();
          }
        }
      }
    });
    discoveryThread.start();
  }

  /**
   * Starts a new thread dedicated to broadcasting mDNS "Hello!" packets.
   * These packets announce the presence of this service on the network.
   * Packets are sent periodically (every 1 second).
   * The loop continues as long as the 'running' flag is true.
   */
  private void startMdnsBroadcast() {
    broadcastThread = new Thread(() -> {
      // Loop continues as long as the broadcaster is in a running state.
      while (running) {
        try {
          StringJoiner joiner = new StringJoiner(",");
          joiner.add(InetAddress.getLocalHost().getHostAddress());
          for (InetAddress addr : new ArrayList<>(clientList)) {
            joiner.add(addr.getHostAddress());
          }
          byte[] buffer = joiner.toString().getBytes(StandardCharsets.UTF_8);
          socket.send(new DatagramPacket(buffer, buffer.length, InetAddress.getByName(GROUP_ADDRESS), PORT));
          Thread.sleep(1000);
        } catch (IOException e) {
          if (running) {
            System.err.println("MDNSBroadcaster: Error in mDNS broadcaster while sending packet: " + e.getMessage());
            e.printStackTrace();
          }
        } catch (InterruptedException e) {
          if (running) {
            System.err.println("MDNSBroadcaster: mDNS broadcaster thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
          } else {
            System.out.println("MDNSBroadcaster: mDNS broadcaster thread shutting down due to interruption.");
          }
        }
      }
    });
    broadcastThread.start();
  }

  /**
   * Starts a new thread dedicated to managing connections to discovered clients.
   * Periodically (every 1 second), it attempts to connect to clients in the clientList
   * using the ClientConnector. A copy of the clientList is passed to avoid concurrent modification issues.
   * The loop continues as long as the 'running' flag is true.
   */
  private void startClientConnectionManager() {
    clientConnectionManagerThread = new Thread(() -> {
      // Loop continues as long as the broadcaster is in a running state.
      while (running) {
        try {
          // Pass a copy of clientList to ClientConnector to prevent issues if clientList is modified
          ClientConnector.connectToClients(new ArrayList<>(clientList));
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          if (running) {
            System.err.println("MDNSBroadcaster: Client connection manager thread interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
          } else {
            System.out.println("MDNSBroadcaster: Client connection manager thread shutting down due to interruption.");
          }
        }
      }
    });
    clientConnectionManagerThread.start();
  }

  /**
   * Shuts down the mDNS broadcasting and discovery services.
   * Sets the 'running' flag to false, which signals all active threads to terminate their loops.
   * Cleans up network resources (leaves multicast group, closes socket).
   * Interrupts and joins all managed threads to ensure they complete their current tasks and exit gracefully.
   */
  public void shutdown() {
    running = false; // Signal all threads to stop their loops.
    cleanup(); // Clean up network resources like the multicast socket.

    if (relayServer != null) {
      relayServer.stop();
    }

    // Interrupt each thread to wake them if they are sleeping or waiting.
    if (discoveryThread != null) {
      discoveryThread.interrupt();
    }
    if (broadcastThread != null) {
      broadcastThread.interrupt();
    }
    if (clientConnectionManagerThread != null) {
      clientConnectionManagerThread.interrupt();
    }

    try {
      if (discoveryThread != null && discoveryThread.isAlive()) {
        discoveryThread.join(1000);
      }
      if (broadcastThread != null && broadcastThread.isAlive()) {
        broadcastThread.join(1000);
      }
      if (clientConnectionManagerThread != null && clientConnectionManagerThread.isAlive()) {
        clientConnectionManagerThread.join(1000);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Cleans up network resources used by the MDNSBroadcaster.
   * If the multicast socket is open, this method attempts to leave the multicast group
   * and then closes the socket. Errors during this process are logged.
   */
  private void cleanup() {
    if (socket != null && !socket.isClosed()) {
      try {
        // Leave the multicast group to stop receiving multicast packets.
        socket.leaveGroup(InetAddress.getByName(GROUP_ADDRESS));
      } catch (IOException e) {
        System.err.println("MDNSBroadcaster: Error leaving multicast group: " + e.getMessage());
        e.printStackTrace();
      }
      socket.close();
    }
  }
}

