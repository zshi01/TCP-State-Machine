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

  private State tcpState;
  private int seqNum;
  private int ackNum;
  private TCPPacket lastPacket;

  public enum State {
    CLOSED,SYN_SENT,LISTEN,SYN_RCVD,ESTABLISHED,FIN_WAIT_1,FIN_WAIT_2,CLOSE_WAIT,LAST_ACK,CLOSING,TIME_WAIT;
  }

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
    tcpState = State.CLOSED;
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
    seqNum = 1000;
    ackNum = 100;
    localport = D.getNextAvailablePort();
    this.address = address;

    D.registerConnection(address,localport,port,this);
    TCPPacket synPacket = new TCPPacket(localport, port,seqNum ,ackNum ,false , true, false, windowSize, null);
    sendPacketWrapper(synPacket);
    System.out.println(synPacket.getDebugOutput());

    switchState(State.SYN_SENT);

    while (tcpState != State.ESTABLISHED){
      try {
        wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){
    System.out.println("Receive Packet    State: "+tcpState);
    System.out.println(p.toString());
    System.out.println(p.getDebugOutput());

    this.notifyAll();

    address = p.sourceAddr;
    port = p.sourcePort;
    seqNum = p.ackNum;
    ackNum = p.seqNum + 1;

    switch (tcpState){
      case LISTEN:
        if (p.synFlag && !p.ackFlag){
          seqNum = 200;
          switchState(State.SYN_RCVD);

          TCPPacket synAckPkt = new TCPPacket(localport, port,seqNum ,ackNum ,true , true, false, windowSize, null);
          sendPacketWrapper(synAckPkt);

          try {
            D.unregisterListeningSocket(localport, this);
            D.registerConnection(address, localport, port, this);
          } catch (IOException e) {
            e.printStackTrace();
          }

        }
        break;

      case SYN_SENT:
        if (p.synFlag && p.ackFlag){
          cancelTimer();
          switchState(State.ESTABLISHED);
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          sendPacketWrapper(ackPkt);
        }
        break;

      case SYN_RCVD:
        if (p.ackFlag){
          cancelTimer();
          switchState(State.ESTABLISHED);
        }
        break;

      case ESTABLISHED:
        if (p.finFlag){
          switchState(State.CLOSE_WAIT);
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          sendPacketWrapper(ackPkt);
        }
        break;

      case FIN_WAIT_1:
        if (p.finFlag){
          switchState(State.CLOSING);
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          sendPacketWrapper(ackPkt);
        }

        if (p.ackFlag){
          cancelTimer();
          switchState(State.FIN_WAIT_2);

        }
        break;

      case CLOSING:
        if (p.ackFlag){
          cancelTimer();
          switchState(State.TIME_WAIT);
          createTimerTask(30 * 1000, null);
        }
        break;

      case FIN_WAIT_2:
        if (p.finFlag){
          switchState(State.TIME_WAIT);
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          sendPacketWrapper(ackPkt);
          createTimerTask(30 * 1000, null);

        }
        break;

      case LAST_ACK:
        if (p.ackFlag){
          cancelTimer();
          switchState(State.TIME_WAIT);
          createTimerTask(30 * 1000, null);
        }
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
    switchState(State.LISTEN);

    while (tcpState != State.ESTABLISHED && tcpState != State.SYN_RCVD){
      try {
        wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
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
    if (tcpState == State.ESTABLISHED){
      switchState(State.FIN_WAIT_1);
    } else if (tcpState == State.CLOSE_WAIT){
      switchState(State.LAST_ACK);
    }

    TCPPacket finPkt = new TCPPacket(localport, port,ackNum ,seqNum ,false , false, true, windowSize, null);
    sendPacketWrapper(finPkt);



    try {
      backgroundThread newThread = new backgroundThread(this);
      newThread.run();
    }
    catch( Exception e){
      e.printStackTrace();
    }
    return;
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

    if (tcpState == State.TIME_WAIT){
      switchState(State.CLOSED);
      notifyAll();
      try {
        D.unregisterConnection(address, localport, port, this);
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      sendPacketWrapper(lastPacket);
    }
  }

  private synchronized void cancelTimer(){
    tcpTimer.cancel();
    tcpTimer = null;
    System.out.println("!!! cancel timer");
  }

  public void sendPacketWrapper(TCPPacket p){
    if(p.synFlag || p.finFlag){
      lastPacket = p;
      createTimerTask(2500,null);
      System.out.println("create timer!!!!");
    }
    TCPWrapper.send(p, address);
  }

  private void switchState(State newState){
    System.out.println("!!! "+tcpState+"->"+newState);
    tcpState = newState;
  }

  public State getTcpState() {
    return tcpState;
  }
}

class backgroundThread implements Runnable{
  public StudentSocketImpl waitingToClose;
  public backgroundThread(StudentSocketImpl s) throws InterruptedException{
    waitingToClose = s;
  }

  @Override
  public void run(){
    while(waitingToClose.getTcpState()!= StudentSocketImpl.State.CLOSED){
      try{
        waitingToClose.wait();
      }catch(Exception e){
        e.printStackTrace();
      }
    }
  }
}
