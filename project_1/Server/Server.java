import java.io.IOException;
import java.net.*;

public class Server {

    public static final int PORT_NUM = 12235;

    public static void main(String[] args) {
        try {

            boolean running = true;
            DatagramSocket socketA = new DatagramSocket(PORT_NUM);

            System.out.println("Server listening on " + PORT_NUM);

            while (running) {
                int expectedPacketLen = 24;
                byte[] buf = new byte[expectedPacketLen + 100]; // +100 for margin.
                DatagramPacket receive = new DatagramPacket(buf, buf.length);
                socketA.receive(receive);
                System.out.println("Received packet.");
                new ServerThread(socketA, receive).start();
            }

            socketA.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}