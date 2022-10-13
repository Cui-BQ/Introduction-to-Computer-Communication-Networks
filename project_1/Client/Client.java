import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;


public class Client {

    private static final String SERVER_IP = "attu2.cs.washington.edu";//"localhost";
    private static final int PORT_NUM = 12235;
    private static final int STUDENT_ID = 583;

    public static void main (String[] args) throws Exception {
        DatagramSocket UDP_Socket = new DatagramSocket();
        InetAddress ip = InetAddress.getByName(SERVER_IP);

        // Stage A -------------------------------------------------------------------------
        byte[] messageA = ("hello world\0").getBytes();
        byte[] headerA = headerMaker(messageA.length, 0, 1);
        byte[] bufferA = new byte[messageA.length + headerA.length];
        System.arraycopy(headerA, 0, bufferA, 0, 12);
        System.arraycopy(messageA, 0, bufferA, 12, messageA.length);

        // Packet UDP.
        DatagramPacket UDP_Packet = new DatagramPacket(bufferA, bufferA.length, ip, PORT_NUM);

        // prepare for receive packet.
        byte[] b = new byte[28];
        DatagramPacket receive = new DatagramPacket(b, b.length);

        // send UDP Packet
        UDP_Socket.send(UDP_Packet);

        // Receive UDP Packet from server.
        UDP_Socket.receive(receive);
        byte[] Data = receive.getData();

        // Extract info from received packet.
        int num = ByteBuffer.wrap(Arrays.copyOfRange(Data, 12, 16)).getInt();
        int len = ByteBuffer.wrap(Arrays.copyOfRange(Data, 16, 20)).getInt();
        int udp_port = ByteBuffer.wrap(Arrays.copyOfRange(Data, 20, 24)).getInt();
        int secretA = ByteBuffer.wrap(Arrays.copyOfRange(Data, 24, 28)).getInt();


        // Stage A done.
        System.out.println("Stage A done");
        // Stage B -------------------------------------------------------------------------
        // make sure len aligned to a 4-byte boundary.
        int payloadLength = 4 + len;
        if (len%4 != 0) {
            len += 4 - len%4;
        }
        // Prepare header, message and buffer.
        byte[] headerB = headerMaker(payloadLength, secretA, 1);
        byte[] messageB = new byte[len+4];
        byte[] bufferB = new byte[messageB.length + headerB.length];
        System.arraycopy(headerB, 0, bufferB, 0, 12);

        // For each packet
        for (int i = 0; i < num; i++) {
            // Make & insert packet_id to buffer.
            byte[] packet_id = ByteBuffer.allocate(4).putInt(i).array();
            System.arraycopy(packet_id, 0, bufferB, 12, 4);
            // Set socket timeout = 0.5 sec.
            UDP_Socket.setSoTimeout(500);
            // Packet UDP.
            UDP_Packet = new DatagramPacket(bufferB, bufferB.length, ip, udp_port);
            //prepare for recive acked_packet_id.
            b = new byte[20];
            receive = new DatagramPacket(b, b.length);

            // Sent UDP_Packet and try to receive acked packet_id from server.
            boolean received = false;

            //A counter to count the number of times of Packet have been sent.
            int count = 0;

            // repeatedly send packet until all packets were successfully sent.
            while (!received) {
                try {
                    if (count == 7 && i == 0) {
                        System.out.println("Error occurred. Restarting.");
                        UDP_Socket.close();
                        main(args);
                        return;
                    }
                    UDP_Socket.send(UDP_Packet);
                    UDP_Socket.receive(receive);
                    byte[] receivedData = receive.getData();
                    int ackedID = ByteBuffer.wrap(Arrays.copyOfRange(receivedData, 12, 16)).getInt();
                    if (ackedID == i) {
                        received = true;
                    } else {
                        count++;
                    }
                } catch (SocketTimeoutException e) {
                    count++;
                }
            }
        }
        // receive & extract secretB and next port number.
        UDP_Socket.receive(receive);
        Data = receive.getData();
        int tcp_port = ByteBuffer.wrap(Arrays.copyOfRange(Data, 12, 16)).getInt();
        int secretB = ByteBuffer.wrap(Arrays.copyOfRange(Data, 16, 20)).getInt();
        // stage 2 done.
        // Close UDP socket when stage 1 & 2 were done.
        UDP_Socket.close();

        System.out.println("Stage B done");

        // Stage C -------------------------------------------------------------------------
        // create a TCP socket, and connect to attu 2 on port# tcp_port.
        Socket TCP_Socket = new Socket(SERVER_IP, tcp_port);

        // receive info from server.
        InputStream in = TCP_Socket.getInputStream();
        Data = new byte[28];
        in.read(Data);

        // Extract num2, len2, secretC, and c.
        int num2 = ByteBuffer.wrap(Arrays.copyOfRange(Data, 12, 16)).getInt();
        int len2 = ByteBuffer.wrap(Arrays.copyOfRange(Data, 16, 20)).getInt();
        int secretC = ByteBuffer.wrap(Arrays.copyOfRange(Data,20, 24)).getInt();
        byte c = Data[24];
        // Stage c done.
        System.out.println("Stage C done");


        // Stage D -------------------------------------------------------------------------
        // make sure len2 aligned to a 4-byte boundary.
        int payloadLengthTCP = len2;
        if (len2%4 != 0) {
            len2 += 4 - len2%4;
        }

        // Prepare header, message and buffer.
        byte[] headerD = headerMaker(payloadLengthTCP, secretC, 1);
        byte[] messageD = new byte[len2];
        Arrays.fill(messageD, c);
        byte[] bufferD = new byte[messageD.length + headerD.length];
        System.arraycopy(headerD, 0, bufferD, 0, 12);
        System.arraycopy(messageD, 0, bufferD, 12, messageD.length);

        // Send num2 packets
        OutputStream out = TCP_Socket.getOutputStream();
        for (int i = 0; i < num2; i++){
            //send to server.
            out.write(bufferD);
        }

        // Try to receive from server.
        int secretD = -1;
        try {
            Data = new byte[16];
            in.read(Data);
            secretD = ByteBuffer.wrap(Arrays.copyOfRange(Data,12, 16)).getInt();

        } catch (SocketException e) {
            System.out.println("Reconnecting.....");
            TCP_Socket.close();
            main(args);
            return;
        }


        TCP_Socket.close();
        System.out.println("Stage D done");

        // print secrets.
        System.out.println("secretA: " + Integer.toUnsignedString(secretA));
        System.out.println("secretB: " + Integer.toUnsignedString(secretB));
        System.out.println("secretC: " + Integer.toUnsignedString(secretC));
        System.out.println("secretD: " + Integer.toUnsignedString(secretD));
    }

    private static byte[] headerMaker (int payload_len, int psecret, int step) {
        byte[] res = new byte[12];
        byte[] lenBytes = ByteBuffer.allocate(4).putInt(payload_len).array();
        byte[] secretBytes = ByteBuffer.allocate(4).putInt(psecret).array();
        byte[] stepBytes = ByteBuffer.allocate(4).putInt(step).array();
        byte[] IDBytes = ByteBuffer.allocate(4).putInt(STUDENT_ID).array();
        System.arraycopy(lenBytes, 0, res, 0, 4);
        System.arraycopy(secretBytes, 0, res, 4, 4);
        System.arraycopy(stepBytes, 2, res, 8, 2);
        System.arraycopy(IDBytes, 2, res, 10, 2);
        return res;
    }
}
