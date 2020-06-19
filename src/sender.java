import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;



public class sender {
    int c = 0;
    int count = 0;
    int MAX_NUMBER = 500;

    int seqnum = 0;
    int seqnumACK = 0;

    static List<packet> packets = new ArrayList<packet>(32);


    int N = 10;

    BufferedReader br;

    Boolean EOF = false;


    //static Timeout checkTimeout;
    Timer timer;
    TimerTask timeout;

    int emulator_port;
    String hostname;

    private static final Object LOCK = new Object();

    File fileSeq;
    BufferedWriter bwSeq;
    File fileAck;
    BufferedWriter bwAck;

    public sender(File file, String hostname, int eport, int sport){
        try{
            br = new BufferedReader(new FileReader(file));
        }catch (Exception e){
            System.out.println("Can't read from file!");
        }


        this.hostname = hostname;
        this.emulator_port = eport;
        int sender_port = sport;

        //Set log files
        fileAck = new File("ack.log");
        fileSeq = new File("seqnum.log");
        try {
            bwAck = new BufferedWriter(new FileWriter(fileAck));
            bwSeq = new BufferedWriter(new FileWriter(fileSeq));
        }catch(Exception e){
            System.out.println("Problem with Buff writer");
        }
        //Receive thread
        ACKThread ack = new ACKThread(sender_port);
        ack.start();

        //timer
        timeout = createTimeoutTask();

        //Load and send thread
        DataThread dt = new DataThread(hostname, emulator_port);
        dt.start();

    }

    public static void main(String[] args) throws Exception {

        if(args.length != 4){
            throw new Exception("Usage: <host address of the network emulator>, <UDP port of emulator to sender>, <UDP port of the sender to receive>, <input file>");
        }

        File file = new File(args[3]);

        //Sender object to deal with sending and receiving
        sender newSender = new sender(file, args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));

    }

    private void loadNewPacket(String hostname, int port){

        StringBuilder sb = new StringBuilder();
        try {
            while ((count < MAX_NUMBER) && (c = br.read()) != -1) {

                sb.append((char) c);
                count++;
            }
        }catch(Exception e){
            System.out.println("Could not read from file using buffer reader");
            System.exit(0);
        }

        //Check if need to transmit more packets
        if(c == -1 && EOF){
            System.out.println("Nothing to send");
            return;
        }

        //Create packets
        String data = sb.toString();
        try{
            if(packets.size() < ((seqnum % 32) + 1)){
                packets.add(packet.createPacket(seqnum, data));
            }else{
                packet newP = packet.createPacket((seqnum % 32), data);
                packets.set((seqnum % 32), newP);
            }
        }catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(0);
        }

        //Send packet
        try{
            //System.out.println("Sending packet: " + (seqnum % 32));
            sendPacket(packets.get((seqnum % 32)), hostname, port);
        }catch (Exception e){
            System.out.println(e.getMessage());
            System.out.println("here?");
            System.out.println("Could not send packet");
            System.exit(0);
        }

        //Start timer
        if(seqnumACK == seqnum){
            stopTimer();
            timer = new Timer();
            timer.schedule(timeout, 100, 100);
        }
        seqnum++;
        count = 0;

        //Send last packet
        if((c == -1) && !EOF) {
            EOF = true;
            try {
                if(packets.size() < ((seqnum % 32) + 1)){
                    packets.add(packet.createEOT((seqnum % 32)));
                }else{
                    packets.set((seqnum % 32), packet.createEOT((seqnum % 32)));

                }
                sendPacket(packets.get((seqnum % 32)), hostname, port);
                //make sure a timer for last packet.
                if (seqnumACK + 1 == seqnum) {
                    stopTimer();
                    timer = new Timer();
                    timer.schedule(timeout, 100, 100);

                }
                seqnum++;

            } catch (Exception e) {
                System.out.println(e.getMessage());
                System.out.println("Could not send packet");
                System.exit(0);
            }
        }
    }

    private void sendPacket(packet p, String hostname, int port) throws Exception {
        InetAddress em_address = InetAddress.getByName(hostname);
        DatagramSocket socket = new DatagramSocket();

        bwSeq.write(Integer.toString(p.getSeqNum()));
        bwSeq.write(System.lineSeparator());
        bwSeq.flush();

        byte[] serializePacket = p.getUDPdata();

        DatagramPacket request = new DatagramPacket(serializePacket, serializePacket.length, em_address, port);
        socket.send(request);
        socket.close();
    }

    private packet receiveACK(int port) throws Exception{

        DatagramSocket socket = new DatagramSocket(port);

        byte[] buffer = new byte[512];

        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);
        packet p = packet.parseUDPdata(buffer);
        bwAck.write(Integer.toString(p.getSeqNum()));
        bwAck.write(System.lineSeparator());
        bwAck.flush();
        socket.close();
        return p;
    }

    class DataThread extends Thread {
        String hostname;
        int port;

        public DataThread(String hostname, int port){
            this.hostname = hostname;
            this.port = port;
        }

        public void run(){

            while (c != -1) {

                synchronized (LOCK){

                    while(seqnum >= seqnumACK + N){
                        //System.out.println("In load");
                        try {
                            LOCK.wait();
                        }catch (Exception e){
                            System.out.println("LOCK Errror");
                        }
                    }
                    loadNewPacket(hostname, port);
                }
            }
        }
    }

    class ACKThread extends Thread {
        int port;

        public ACKThread(int port){
            this.port = port;
        }

        public void run(){

            while(true) { //synchronized

                    packet p1;
                    int type = -1;
                    int receivedSeqnum = -1;
                    try{
                        p1 = receiveACK(port);
                        type = p1.getType();
                        receivedSeqnum = p1.getSeqNum();
                    }catch (Exception e){
                        System.out.println("Could not receive packet.");
                        System.exit(0);
                    }
                synchronized (LOCK){
                    if(type == 2 && (seqnum == seqnumACK + 1) && (receivedSeqnum >= (seqnumACK % 32))) {
                        //System.out.println("Ok we done");

                        stopTimer();
                        try{
                            bwSeq.close();
                            bwAck.close();
                        }catch (Exception e){
                            System.out.println("Could not close files");
                        }

                        break;
                    }else if(type == 0 && (seqnum == seqnumACK + 1) && (receivedSeqnum >= (seqnumACK % 32) )) {
                        stopTimer();
                    }else if(type == 0 && (receivedSeqnum > (seqnumACK % 32)) && (((seqnumACK + 10) % 32) < 11) && (((receivedSeqnum + 10) % 32) < ((seqnumACK + 10) % 32))){
                        continue;
                    }else if(type == 0 && (receivedSeqnum < (seqnumACK % 32)) && (receivedSeqnum + 10 > (seqnumACK % 32))){
                        continue;
                    }else if(type != 0){

                        stopTimer();
                        try{
                            bwSeq.close();
                            bwAck.close();
                        }catch (Exception e){
                            System.out.println("Could not close files");
                        }

                        break;
                    }else{
                        //System.out.println("This should be fine");
                    }

                    if(receivedSeqnum >= (seqnumACK % 32)){
                        int diff = receivedSeqnum - (seqnumACK % 32);
                        //System.out.println("How much is this?: " + diff + "val: " + seqnumACK + " receivedNum: " + receivedSeqnum);
                       // System.out.println("Received a non ACK packet:" + seqnum);
                        seqnumACK = seqnumACK + 1 + diff;
                        LOCK.notify();
                    }else{
                        int diff = ((receivedSeqnum + 10) % 32) - ((seqnumACK + 10) % 32);
                        //System.out.println("How much is this?: " + diff + "val: " + seqnumACK + " receivedNum: " + receivedSeqnum);
                        seqnumACK = seqnumACK + 1 + diff;
                        LOCK.notify();
                    }


                }

            }
        }
    }

    private void resendPackets(String hostname, int port){
        synchronized (LOCK) {

            for (int i = seqnumACK; i < seqnum; i++) {

                try {
                    sendPacket(packets.get((i % 32)), hostname, port);
                } catch (Exception e) {
                    System.out.println("Packet size: " + packets.size());
                    System.out.println("Error at index: " + i + ", could not resend packet");
                    System.out.println(e.getMessage());
                    System.exit(0);
                }
            }
        }
    }

    private TimerTask createTimeoutTask(){
        return new TimerTask() {
            @Override
            public void run() {
                resendPackets(hostname, emulator_port);
            }
        };
    }

    private void stopTimer(){
        try{
            timeout.cancel();
            timeout = createTimeoutTask();
            timer.cancel();
            //System.out.println("timer cancelled");
        } catch (Exception e){
            //Cause first time is  ok
            //System.out.println(e.getMessage());

        }
    }
}


