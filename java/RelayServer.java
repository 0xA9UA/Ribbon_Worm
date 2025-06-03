import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Simple relay server used for tunneling connections between peers.
 * Each node listens on RELAY_PORT and can forward traffic to another
 * peer when requested. The first line received from a client is treated
 * as the target peer's address.
 */
public class RelayServer {
  public static final int RELAY_PORT = 9090;

  private ServerSocket serverSocket;
  private ExecutorService executor;
  private volatile boolean running;

  /** Starts the relay server on RELAY_PORT. */
  public void start() throws IOException {
    serverSocket = new ServerSocket(RELAY_PORT);
    executor = Executors.newCachedThreadPool();
    running = true;
    executor.execute(() -> listenLoop());
  }

  /** Main accept loop for incoming relay requests. */
  private void listenLoop() {
    while (running) {
      try {
        Socket client = serverSocket.accept();
        executor.execute(() -> handleClient(client));
      } catch (IOException e) {
        if (running) {
          System.err.println("RelayServer: accept error: " + e.getMessage());
        }
      }
    }
  }

  /** Handles a single relay connection. */
  private void handleClient(Socket client) {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
         PrintWriter writer = new PrintWriter(client.getOutputStream(), true)) {
      String targetLine = reader.readLine();
      if (targetLine == null) {
        client.close();
        return;
      }
      InetAddress target = InetAddress.getByName(targetLine.trim());
      Socket targetSocket = new Socket(target, RELAY_PORT);
      forwardStreams(client, targetSocket);
    } catch (IOException e) {
      System.err.println("RelayServer: failed to relay connection: " + e.getMessage());
      try { client.close(); } catch (IOException ignored) {}
    }
  }

  /** Bridges two sockets until either is closed. */
  private void forwardStreams(Socket a, Socket b) throws IOException {
    Thread t1 = new Thread(() -> copy(a, b));
    Thread t2 = new Thread(() -> copy(b, a));
    t1.start();
    t2.start();
  }

  /** Utility to copy data from one socket to another. */
  private static void copy(Socket src, Socket dst) {
    try (InputStream in = src.getInputStream();
         OutputStream out = dst.getOutputStream()) {
      byte[] buf = new byte[8192];
      int len;
      while ((len = in.read(buf)) != -1) {
        out.write(buf, 0, len);
        out.flush();
      }
    } catch (IOException ignored) {
    } finally {
      try { src.close(); } catch (IOException ignored) {}
      try { dst.close(); } catch (IOException ignored) {}
    }
  }

  /** Stops the relay server and releases resources. */
  public void stop() {
    running = false;
    try {
      if (serverSocket != null) {
        serverSocket.close();
      }
    } catch (IOException ignored) {}
    if (executor != null) {
      executor.shutdownNow();
    }
  }
}
