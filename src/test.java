import java.util.ArrayList;
import java.util.List;

public class test {
    public static void main(String[] args) { // let's create a list first
        List<packet> top5Books = new ArrayList<packet>(4);
        try{
            top5Books.add(packet.createPacket(0, "Clean Coder"));
            top5Books.add(packet.createPacket(1, "Effective Java"));
            top5Books.add(packet.createPacket(2, "Head First Java"));
            top5Books.add(packet.createPacket(3, "Head First Design patterns"));
            System.out.println("ArrayList before replace: " + top5Books.get(1).getSeqNum());
            top5Books.set(1, packet.createPacket(5, "Introductoin to Algorithms"));
            System.out.println("ArrayList after replace: " + top5Books.get(1).getSeqNum());
        }catch (Exception e){
            System.out.println(e.getMessage());
        }

    }
}
