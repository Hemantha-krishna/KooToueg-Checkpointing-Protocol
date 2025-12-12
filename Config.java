import java.util.*;

public class Config {
    public int numNodes;
    public int minDelay;
    public Map<Integer, NodeInfo> nodes = new HashMap<>();
    public Map<Integer, List<Integer>> neighbors = new HashMap<>();
    public List<Operation> operations = new ArrayList<>();

    public static class NodeInfo {
        public int id;
        public String host;
        public int port;
        public NodeInfo(int id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
        }
    }

    public static class Operation {
        public int nodeId;
        public String type; // "c" (checkpoint) or "r" (recovery)
        public Operation(int nodeId, String type) {
            this.nodeId = nodeId;
            this.type = type;
        }
    }
}