import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
  //   protected InetAddress address;
  //   protected int port;
  //   protected int localport;

  private Demultiplexer D;
  private Timer tcpTimer;
  private int windowSize = 20;

  private State tcpState = State.CLOSED;
  private int seqNum;
  private int ackNum;

  private enum State {
    CLOSED,SYN_SENT,LISTEN,SYN_RCVD,ESTABLISHED,FIN_WAIT_1,FIN_WAIT_2,CLOSE_WAIT,LAST_ACK,CLOSING,TIME_WAIT;
  }

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
  }

  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress address, int port) throws IOException{
    seqNum = 700;
    ackNum = 100;
    localport = D.getNextAvailablePort();

    D.registerConnection(address,localport,port,this);
    TCPPacket synPkt = new TCPPacket(localport, port,seqNum ,ackNum ,false , true, false, windowSize, null);
    TCPWrapper.send(synPkt, address);
    System.out.println(synPkt.getDebugOutput());

    tcpState = switchState(State.SYN_SENT);
  }

  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){
    System.out.println("Receive Packet    State: "+tcpState);
    System.out.println(p.toString());
    System.out.println(p.getDebugOutput());
    switch (tcpState){
      case LISTEN:
        if (p.synFlag && !p.ackFlag){
          seqNum = p.ackNum;
          ackNum = p.seqNum + 1;
          TCPPacket synAckPkt = new TCPPacket(localport, port,seqNum ,ackNum ,true , true, false, 1, null);
          TCPWrapper.send(synAckPkt, address);

          try {
            D.unregisterListeningSocket(localport, this);
            D.registerConnection(address, localport, port, this);
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
        this.notifyAll();
        break;

        default:
          break;
    }
  }

  /**
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling
   * ServerSocket.accept(), but this method belongs to the Socket object
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
      D.registerListeningSocket(localport, this);
      System.out.println("Accept Connection");
  }


  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    // project 4 return appIS;
    return null;

  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    // project 4 return appOS;
    return null;
  }


  /**
   * Closes this socket. 
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {
  }

  /**
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if(tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return 
   * information.
   */
  public synchronized void handleTimer(Object ref){

    // this must run only once the last timer (30 second timer) has expired
    tcpTimer.cancel();
    tcpTimer = null;
  }

  private State switchState(State newState){
    System.out.println("State Change from "+tcpState+" to "+newState);
    tcpState = newState;

    return newState;
  }
}
