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

    D.registerConnection(address,localport,port,this);
    TCPPacket synPacket = new TCPPacket(localport, port,seqNum ,ackNum ,false , true, false, windowSize, null);
    TCPWrapper.send(synPacket, address);
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
//          ackNum = p.seqNum + 1;
          TCPPacket synAckPkt = new TCPPacket(localport, port,seqNum ,ackNum ,true , true, false, windowSize, null);
          TCPWrapper.send(synAckPkt, address);

          try {
            D.unregisterListeningSocket(localport, this);
            D.registerConnection(address, localport, port, this);
          } catch (IOException e) {
            e.printStackTrace();
          }
          switchState(State.SYN_RCVD);
        }
        break;

      case SYN_SENT:
        if (p.synFlag && p.ackFlag){
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          TCPWrapper.send(ackPkt, address);
          switchState(State.ESTABLISHED);
        }
        break;

      case SYN_RCVD:
        if (p.ackFlag){
          switchState(State.ESTABLISHED);
        }
        break;

      case ESTABLISHED:
        if (p.finFlag){
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          TCPWrapper.send(ackPkt, address);
          switchState(State.CLOSE_WAIT);
        }
        break;

      case FIN_WAIT_1:
        if (p.finFlag){
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          TCPWrapper.send(ackPkt, address);
          switchState(State.CLOSING);
        }

        if (p.ackFlag){
          switchState(State.FIN_WAIT_2);
        }
        break;

      case CLOSING:
        if (p.ackFlag){
          switchState(State.TIME_WAIT);
        }
        break;

      case FIN_WAIT_2:
        if (p.finFlag){
          TCPPacket ackPkt = new TCPPacket(localport, port,-2 ,ackNum ,true , false, false, windowSize, null);
          TCPWrapper.send(ackPkt, address);
          switchState(State.TIME_WAIT);
        }
        break;

      case LAST_ACK:
        if (p.ackFlag){
          switchState(State.TIME_WAIT);
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
    TCPPacket finPkt = new TCPPacket(localport, port,ackNum ,seqNum ,false , false, true, windowSize, null);
    TCPWrapper.send(finPkt, address);

    if (tcpState == State.ESTABLISHED){
      tcpState = State.FIN_WAIT_1;
    } else if (tcpState == State.CLOSE_WAIT){
      tcpState = State.LAST_ACK;
    }

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
        wait();
      }catch(Exception e){
        e.printStackTrace();
      }
    }
  }
}
