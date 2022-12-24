

public class ribbonWorm_Main{

private static MDNSBroadcaster broadcaster;

  public static void main(String[] args) {
    // Create an instance of the MDNSBroadcaster class and start broadcasting and listening for mDNS packets
    broadcaster = new MDNSBroadcaster();
  }

  public static void stopBroadcasting() {
    // Stop the broadcasting and listening process by interrupting the thread
    broadcaster.stop();
  }
  
}

