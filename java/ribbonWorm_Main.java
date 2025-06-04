

/**
 * Main entry point for the RibbonWorm application.
 * This class initializes and starts the mDNS broadcasting and discovery services,
 * and ensures graceful shutdown of these services.
 */
public class ribbonWorm_Main{

private static MDNSBroadcaster broadcaster; // Static instance of the MDNSBroadcaster

  /**
   * The main method that starts the application.
   * It creates an instance of {@link MDNSBroadcaster}, starts its services,
   * and registers a JVM shutdown hook to ensure that the broadcaster's resources
   * are cleaned up properly upon application termination (e.g., Ctrl+C).
   *
   * @param args Command-line arguments (not used).
   */
  public static void main(String[] args) {
    // Create an instance of the MDNSBroadcaster class.
    broadcaster = new MDNSBroadcaster();
    // Start the mDNS broadcasting and discovery services.
    broadcaster.start();

    // Add a JVM shutdown hook to be executed when the application is terminating.
    // This ensures that network services are stopped gracefully.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      System.out.println("Shutting down RibbonWorm...");
      shutdownApplication(); // Call the application-specific shutdown logic.
    }));
  }

  /**
   * Gracefully shuts down the application's services.
   * This method calls the shutdown method on the {@link MDNSBroadcaster} instance,
   * which stops all its threads and releases network resources.
   */
  public static void shutdownApplication() {
    // Ensure the broadcaster instance exists and then call its shutdown method.
    if (broadcaster != null) {
      broadcaster.shutdown();
    }
  }
}

