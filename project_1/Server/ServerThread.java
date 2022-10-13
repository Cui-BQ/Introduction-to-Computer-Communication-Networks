import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

public class ServerThread extends Thread {

    public static final int HEADER_SIZE = 12;
    public static final int ALIGNMENT = 4;
    public static final int RANDOM_BOUND = 100;
    public static final int TIMEOUT = 3000;

    private final DatagramPacket firstPacket;
    private final DatagramSocket socketA;
    private final Random random;

    public ServerThread(DatagramSocket socketA, DatagramPacket firstPacket) {
        this.firstPacket = firstPacket;
        this.socketA = socketA;
        // Random numbers to send to client:
        random = new Random();
        random.setSeed(1);
    }

    public void run() {
       try {
            //////////////////////////////////////////////////
            //PART A
            ///////////////////////////////////////////////////
           int student_id = -1;

           System.out.println("Starting Part A");
            //Receive:
            int expectedPacketLen = 24;
            //DatagramSocket socketA = new DatagramSocket();
            byte[] buf = new byte[expectedPacketLen + 100]; //+100 for margin.

            if (!verifyPacket(firstPacket, 0, expectedPacketLen, 12, student_id)) {
                System.out.println("verify packet failed");
                return;
            }

            //Compare for expected payload
            byte[] data = firstPacket.getData();
            byte[] payload = Arrays.copyOfRange(data, 12, 24);
            byte[] correctPayload = "hello world\0".getBytes(StandardCharsets.UTF_8);
            if (!Arrays.equals(payload, correctPayload)) {
                System.out.println("payload not equal");
                return;
            }

            //Set student_id for verification in later parts.
            byte[] studentIdBytes = new byte[4];
            studentIdBytes[2] = data[10];
            studentIdBytes[3] = data[11];
            student_id = ByteBuffer.wrap(studentIdBytes).getInt();

            int num = random.nextInt(RANDOM_BOUND - 1) + 1;
            int len = random.nextInt(RANDOM_BOUND - 1) + 1;
            int secretA = random.nextInt();
            DatagramSocket socketB = new DatagramSocket();
            int udp_port = socketB.getLocalPort();
            //first bind to port, then send port info.

            //Response:

            //Make packet to send
            byte[] header = headerMaker(16, 0, 2, student_id);
            byte[] contentsToSend = new byte[12 + 16]; //12 is header, 16 is payload
            System.arraycopy(header, 0, contentsToSend, 0, 12);
            System.arraycopy(intToByteArr(num), 0, contentsToSend, 12, 4);
            System.arraycopy(intToByteArr(len), 0, contentsToSend, 16, 4);
            System.arraycopy(intToByteArr(udp_port), 0, contentsToSend, 20, 4);
            System.arraycopy(intToByteArr(secretA), 0, contentsToSend, 24, 4);

            System.out.println("Server generated nums:");
            System.out.println("num: " + num);
            System.out.println("len: " + len);
            System.out.println("udp_port: " + udp_port);
            System.out.println("secretA: " + secretA);

            //Get address to send it to:
            InetAddress address = firstPacket.getAddress();
            int port = firstPacket.getPort();
            DatagramPacket packet = new DatagramPacket(contentsToSend, contentsToSend.length, address, port);
            socketA.send(packet);

            /////////////////////////////////////
            //STAGE B
            ////////////////////////////////////////
            System.out.println("Starting Part B");
            socketB.setSoTimeout(TIMEOUT);

            int roundedLen = len;
            if (roundedLen % 4 != 0) {
                roundedLen += 4 - len % 4;
            }
            int expectedPacketSize = 12 + 4 + roundedLen; //12 for header, 4 for id, roundedLen for payload.
            int expectedId = 0;
            int numIter = 0;
            DatagramPacket receive = new DatagramPacket(new byte[1], 1); //arbitrary definition (needs to be defined to prevent error later)
            while (expectedId < num) { //start a loop to receive each packet at a time.
                buf = new byte[expectedPacketSize + 100]; //+100 is for error checking
                receive = new DatagramPacket(buf, buf.length);
                try {
                    socketB.receive(receive);
                } catch (SocketTimeoutException ex) {
                    System.out.println("Sock timeout part b");
                    socketB.close();
                    return;
                }
                int expectedPayloadLen = len + 4; //we have payload of length len + 4 bytes of packet_id
                if (!verifyPacket(receive, secretA, expectedPacketSize, expectedPayloadLen, student_id)) { // the +4 for packet_i
                    System.out.println("verifyPacket failed");
                    socketB.close();
                    return;
                }
                data = receive.getData();
                int receivedId = ByteBuffer.wrap(Arrays.copyOfRange(data, 12, 16)).getInt();

                //if receivedId is greater than expectedId, client is sending an id which we haven't acked - invalid
                //if receivedID is 2 or more less than expected ID, client is sending a packet that it know's we've already acked - invalid
                if (receivedId < 0 || receivedId < expectedId - 1 || receivedId > expectedId) {
                    System.out.println("error with receivedID");
                    socketB.close();
                    return;
                }

                if (receivedId == expectedId || receivedId == expectedId - 1) { //if the received packet had the expected id
                    int randomNum = random.nextInt();

                    //randomly chose when to send ack, but always don't send an ack
                    // for the first packet, since we need to not send an ack atleast once.
                    if (randomNum % 2 == 0 && numIter != 0) {
                        //Make packet to send
                        header = headerMaker(4, secretA, 2, student_id); //psecret shouldn't matter here anyway
                        contentsToSend = new byte[12 + 4]; //12 is header, 4 is paylaod
                        System.arraycopy(header, 0, contentsToSend, 0, 12);
                        System.arraycopy(intToByteArr(receivedId), 0, contentsToSend, 12, 4);

                        //Get address to send it to:
                        address = receive.getAddress();
                        port = receive.getPort();
                        packet = new DatagramPacket(contentsToSend, contentsToSend.length, address, port);

                        socketB.send(packet);

                        if (receivedId == expectedId) {
                            expectedId++; //now expecting the next id;
                        }
                    }
                }
                numIter++;
            }

            //Now have received all num packets. Now send a UDP packet to communicate TCP info.
            ServerSocket TCPSocket = new ServerSocket(0);
            int TCP_port_num = TCPSocket.getLocalPort();
            int secretB = random.nextInt();

            //Make packet to send
            header = headerMaker(8, secretB, 2, student_id); //psecret shouldn't matter here anyway
            contentsToSend = new byte[12 + 8]; //12 is header, 8 is paylaod
            System.arraycopy(header, 0, contentsToSend, 0, 12);
            System.arraycopy(intToByteArr(TCP_port_num), 0, contentsToSend, 12, 4);
            System.arraycopy(intToByteArr(secretB), 0, contentsToSend, 16, 4);

            //Get address to send it to:
            address = receive.getAddress();
            port = receive.getPort();
            packet = new DatagramPacket(contentsToSend, contentsToSend.length, address, port);
            socketB.send(packet);
            socketB.close();
            //////////////////////////
            /////////////////////////
            //////PART C AND D
            ///////////////////////
            /////////////////////
            System.out.println("Part 2C");

            Socket client = TCPSocket.accept();
            client.setSoTimeout(TIMEOUT);
            InputStream cis = client.getInputStream();
            OutputStream cos = client.getOutputStream();

            // Your server should send three integers:
            // num2, len2, secretC (12 bytes)
            // and a character: 'c' (1 byte converted)
            // total payload len of 12 + 1 = 13 bytes

            // set the header
            int payloadLen = 13;
            int psecret = secretB;
            byte[] writeHeader = headerMaker(payloadLen, psecret, 2, student_id);

            // set the payload
            int num2 = random.nextInt(RANDOM_BOUND) + 1;
            int len2 = random.nextInt(RANDOM_BOUND) + 1;
            int secretC = random.nextInt();

            System.out.println("num2: " + num2);
            System.out.println("len2: " + len2);
            System.out.println("secretC: " + secretC);

            // fill the buffer
            int size = align(HEADER_SIZE + payloadLen);
            ByteBuffer writeBuffer = ByteBuffer.allocate(size)
                    .put(writeHeader)
                    .putInt(num2)
                    .putInt(len2)
                    .putInt(secretC)
                    .put((byte) 'c');

            // write to client output stream
            cos.write(writeBuffer.array());

            //////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.println("Part 2D");

            // Part 2D
            // Client sends num2 payloads, each payload of length len2, and each payload containing all bytes set
            // to the character 'c'

            // READ

            size = align(HEADER_SIZE + len2);
            for (int i = 0; i < num2; i++) {
                // read from client input stream

                // read the header
                try {
                    ByteBuffer readHeader = ByteBuffer.wrap(cis.readNBytes(HEADER_SIZE));
                    int readPayloadSize = readHeader.getInt(0);
                    int readPsecret = readHeader.getInt(4);
                    int readStep = readHeader.getShort(8);
                    int readStudentId = readHeader.getShort(10);

                    if (readPayloadSize != len2) {
                        System.out.println("Unexpected payload length. Closing.");
                        client.close();
                        return;
                    }

                    if (readPsecret != secretC) {
                        System.out.println("Did not receive correct secret. Closing.");
                        client.close();
                        return;
                    }

                    // client is step 1
                    if (readStep != 1) {
                        System.out.println(readStep);
                        System.out.println(Integer.toBinaryString(readStep));
                        System.out.println("Incorrect step number. Closing.");
                        client.close();
                        return;
                    }

                    if (readStudentId != student_id) {
                        System.out.println("Incorrect student id. Closing.");
                        client.close();
                        return;
                    }

                    // read the payload
                    ByteBuffer readPayload = ByteBuffer.wrap(cis.readNBytes(size - HEADER_SIZE));

                    for (int j = 0; j < len2; j += Character.SIZE) {
                        if (readPayload.get() != (byte) 'c') {
                            System.out.println("Incorrect payload content. Closing.");
                            client.close();
                            return;
                        }
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println("Socket timed out. Closing.");
                    client.close();
                    return;
                }
            }

            if (cis.available() > 0) {
                System.out.println("Unexpected number of buffers. Closing.");
                client.close();
                return;
            }

            // WRITE

            // Server should respond with an integer:
            // secretD (4 bytes)
            // total payload len of 4 bytes

            // set the header
            payloadLen = 4;
            psecret = secretC;
            writeHeader = headerMaker(payloadLen, psecret, 2, student_id);

            // set the payload
            int secretD = random.nextInt();

            System.out.println("secretD: " + secretD);

            // fill the buffer
            size = align(HEADER_SIZE + payloadLen);
            writeBuffer = ByteBuffer.allocate(size)
                    .put(writeHeader)
                    .putInt(secretD);

            // write to client output stream
            cos.write(writeBuffer.array());

            //////////////////////////////////////////////////////////////////////////////////////////////////////

            System.out.println("Success. Closing.");
        }  catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] intToByteArr(int n) {
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(n);
        return b.array();
    }


    private static byte[] headerMaker (int payload_len, int psecret, int step, int student_id) {
        byte[] res = new byte[12];
        byte[] lenBytes = ByteBuffer.allocate(4).putInt(payload_len).array();
        byte[] secretBytes = ByteBuffer.allocate(4).putInt(psecret).array();
        byte[] stepBytes = ByteBuffer.allocate(4).putInt(step).array();
        byte[] IDBytes = ByteBuffer.allocate(4).putInt(student_id).array();
        System.arraycopy(lenBytes, 0, res, 0, 4);
        System.arraycopy(secretBytes, 0, res, 4, 4);
        System.arraycopy(stepBytes, 2, res, 8, 2);
        System.arraycopy(IDBytes, 2, res, 10, 2);
        return res;
    }

    private static boolean verifyPacket(DatagramPacket receive, int expectedSecret, int expectedPacketLength, int expectedPayloadLength, int expStudentID) {
        byte[] bytes = receive.getData();
        if (receive.getLength() < 12) { //12 is header size.
            return false;
        }
        int secret = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 4, 8)).getInt();
        int payloadLenField = ByteBuffer.wrap(Arrays.copyOfRange(bytes, 0, 4)).getInt();
        boolean verified = secret == expectedSecret;
        verified &= receive.getLength() == expectedPacketLength;
        verified &= payloadLenField == expectedPayloadLength;
        byte[] studentIdBytes = new byte[4];
        studentIdBytes[2] = bytes[10];
        studentIdBytes[3] = bytes[11];
        int studentId = ByteBuffer.wrap(studentIdBytes).getInt();

        if (expStudentID != -1) {
            verified &= studentId == expStudentID;
        }

        byte[] stepNumBytes = new byte[4];
        stepNumBytes[2] = bytes[8];
        stepNumBytes[3] = bytes[9];
        int stepNum= ByteBuffer.wrap(stepNumBytes).getInt();
        verified &= stepNum == 1;
        return verified;
    }

    /*
     * Rounds up to nearest multiple of ALIGNMENT
     */
    private static int align(int size) {
        return (size + ALIGNMENT - 1) / ALIGNMENT * ALIGNMENT;
    }
}
