import java.io.Serializable;
import java.util.Arrays;

public class VectorClock implements Serializable {
    private int[] clock;
    private int myId;
    private int numNodes;

    public VectorClock(int numNodes, int myId) {
        this.numNodes = numNodes;
        this.myId = myId;
        this.clock = new int[numNodes];
    }

    public synchronized void tick() {
        clock[myId]++;
    }

    public synchronized void update(int[] receivedClock) {
        if (receivedClock == null) return;
        for (int i = 0; i < numNodes; i++) {
            clock[i] = Math.max(clock[i], receivedClock[i]);
        }
        clock[myId]++;
    }

    public synchronized int[] getClockArray() {
        return Arrays.copyOf(clock, numNodes);
    }
    
    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numNodes; i++) {
            sb.append(clock[i]).append(i < numNodes - 1 ? " " : "");
        }
        return sb.toString();
    }
}