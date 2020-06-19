import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class receiver {
    static int seqnum = 0;

    static File fileSeq;
    static BufferedWriter bwSeq;

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new Exception("Usage: <hostname for the network emulator>, <UDP port for link emulator to receive>, <UDP port for receiver  to  receive>" +
                    " <output file>");
        }

        File file = new File(args[3]);

        BufferedWriter bw = new BufferedWriter(new FileWriter(file));

        fileSeq = new File("arrival.log");
        try {
            bwSeq = new BufferedWriter(new FileWriter(fileSeq));
        }catch(Exception e){
            System.out.println("Problem with Buff writer");
        }
        //probably need to make single thread on sender because it is sending too many packets at once that I am probably not listening for a packet when the others are sent.
        String hostname = args[0];
        int emulator_port = Integer.parseInt(args[1]);

        while(true){
            packet p  = receivePacket(Integer.parseInt(args[2]));
            int type = p.getType();
            int curseqnum = p.getSeqNum();

            //Log in arrival.log
            bwSeq.write(Integer.toString(curseqnum));
            bwSeq.write(System.lineSeparator());
            bwSeq.flush();
            if(type == 0){
                throw new Exception("Received a non data packet");
            }else if(type == 2){
                if(curseqnum == (seqnum % 32)){
                    //close file
                    packet p1 = packet.createEOT(curseqnum);
                    sendACK(p1, hostname, emulator_port);
                    bwSeq.close();
                    bw.close();
                    //System.out.println("Received EOF. Done!");
                    break;
                }
            }


            if(curseqnum != (seqnum % 32)){
                //resend ACK packet
                //System.out.println("Got bad packet: " + curseqnum + ". I want: " + (seqnum % 32));
                if(seqnum != 0){
                    packet p1 = packet.createACK(((seqnum  - 1) % 32));
                    sendACK(p1, hostname, emulator_port);
                }

                continue;
            }
            //System.out.println("Got good packet: " + curseqnum);
            byte[] b = p.getData();
            String s = new String(b);
            bw.write(s);
            bw.flush();

            packet p1 = packet.createACK(curseqnum);

            sendACK(p1, hostname, emulator_port);
            seqnum++;
        }

    }

    private static packet receivePacket(int port) throws Exception {
        //System.out.println("PORT: " + port);
        DatagramSocket socket = new DatagramSocket(port);

        byte[] buffer = new byte[512];

        DatagramPacket response = new DatagramPacket(buffer, buffer.length);
        socket.receive(response);
        packet p = packet.parseUDPdata(buffer);
        socket.close();
        return p;
    }

    private static void sendACK(packet p, String hostname, int port) throws Exception{
        InetAddress em_address = InetAddress.getByName(hostname);
        DatagramSocket socket = new DatagramSocket();

        byte[] serializePacket = p.getUDPdata();

        DatagramPacket request = new DatagramPacket(serializePacket, serializePacket.length, em_address, port);
        socket.send(request);
        socket.close();
    }
}
