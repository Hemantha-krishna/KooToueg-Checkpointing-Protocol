import java.io.Serializable;

public class Message implements Serializable {
    public enum Type {
        APP_MSG,        // Random application message
        CKPT_REQ,       // Koo-Toueg Request
        CKPT_RESP,      // Koo-Toueg Response (YES/NO)
        CKPT_COMMIT,    // Make permanent
        CKPT_ABORT,     // Discard
        OP_FINISHED     // Flooding signal that operation ended
    }

    public Type type;
    public int senderId;
    public int[] vectorClock;
    public String payload; // "YES" or "NO"
    public int seqNumber;  // Checkpoint Sequence Number
    public int initiatorId; // ID of checkpoint initiator

    public Message(Type type, int senderId, int[] vectorClock, String payload, int seqNumber) {
        this(type, senderId, vectorClock, payload, seqNumber, -1);
    }

    public Message(Type type, int senderId, int[] vectorClock, String payload, int seqNumber, int initiatorId) {
        this.type = type;
        this.senderId = senderId;
        this.vectorClock = vectorClock;
        this.payload = payload;
        this.seqNumber = seqNumber;
        this.initiatorId = initiatorId;
    }
}