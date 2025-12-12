import java.io.*;
import java.net.*;
import java.util.*;

public class Node {
    public int id;
    public Config config;
    public VectorClock vClock;
    private Map<Integer, ObjectOutputStream> outputStreams = new HashMap<>();
    private ServerSocket serverSocket;
    private KooTouegProtocol ktProtocol;
    
    // Logic Flow
    private int currentOpIndex = 0;
    private boolean active = true;
    
    // CRITICAL: Track which operations we've already processed
    private Set<Integer> processedOperations = new HashSet<>();

    public Node(int id, Config config) {
        this.id = id;
        this.config = config;
        this.vClock = new VectorClock(config.numNodes, id);
        this.ktProtocol = new KooTouegProtocol(this);
    }

    public void start() {
        // 1. Listen
        new Thread(this::listen).start();
        
        // 2. Wait for system startup
        try { Thread.sleep(5000 + config.numNodes * 2000); } catch(Exception e){}
        
        // 3. Connect
        connectToNeighbors();
        
        // 4. Additional wait for bidirectional connections
        try { Thread.sleep(5000); } catch(Exception e){}
        
        // 5. App Simulation
        new Thread(this::simulateApp).start();

        // 6. Check if I start the first operation
        checkAndStartOperation();
    }

    // --- OPERATION MANAGEMENT ---
    private synchronized void checkAndStartOperation() {
        if (currentOpIndex >= config.operations.size()) {
            System.out.println("Node " + id + " completed all operations");
            return;
        }
        
        // Check if we already processed this operation
        if (processedOperations.contains(currentOpIndex)) {
            System.out.println("Node " + id + " already processed op " + currentOpIndex);
            return;
        }
        
        Config.Operation op = config.operations.get(currentOpIndex);
        System.out.println("Node " + id + " checking operation " + currentOpIndex + 
                           ": initiator=" + op.nodeId + ", type=" + op.type);
        
        if (op.nodeId == this.id) {
            processedOperations.add(currentOpIndex);
            
            if (op.type.equals("c")) {
                // Use operation index as sequence number (1-based for clarity)
                int seqNumber = currentOpIndex + 1;
                System.out.println("Node " + id + " INITIATING checkpoint seq=" + seqNumber);
                ktProtocol.startCheckpoint(seqNumber);
            } else {
                System.out.println("Node " + id + " skipping RECOVERY (Working Alone)");
                broadcastFinished();
            }
        }
    }

    public void broadcastFinished() {
        Message msg = new Message(Message.Type.OP_FINISHED, id, null, 
                                  String.valueOf(currentOpIndex), 0);
        System.out.println("Node " + id + " broadcasting OP_FINISHED for operation " + currentOpIndex);
        
        // Send to ALL neighbors
        if (config.neighbors.containsKey(id) && config.neighbors.get(id) != null) {
            for (int nid : config.neighbors.get(id)) {
                sendMessage(nid, msg);
            }
        }
        
        // Advance to next operation
        advanceToNextOperation();
    }

    private synchronized void advanceToNextOperation() {
        currentOpIndex++;
        System.out.println("Node " + id + " advanced to operation index " + currentOpIndex);
        
        if (currentOpIndex < config.operations.size()) {
            try { 
                Thread.sleep(config.minDelay); 
            } catch(Exception e){}
            checkAndStartOperation();
        } else {
            System.out.println("Node " + id + " reached end of operations");
        }
    }

    // --- NETWORKING ---
    private void listen() {
        try {
            serverSocket = new ServerSocket(config.nodes.get(id).port);
            System.out.println("Node " + id + " listening on port " + config.nodes.get(id).port);
            while(active) {
                Socket s = serverSocket.accept();
                new Thread(() -> handleClient(s)).start();
            }
        } catch(IOException e) { 
            if (active) e.printStackTrace(); 
        }
    }

    private void handleClient(Socket s) {
        try {
            ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
            while(active) {
                Message msg = (Message) ois.readObject();
                processMessage(msg);
            }
        } catch(Exception e) {
            // Connection closed or error - normal during shutdown
        }
    }

    private synchronized void processMessage(Message msg) {
        if (msg.vectorClock != null) vClock.update(msg.vectorClock);
        
        switch(msg.type) {
            case APP_MSG:
                // Clock updated, done.
                break;
                
            case OP_FINISHED:
                handleOpFinished(msg);
                break;
                
            default:
                ktProtocol.handleMessage(msg);
                break;
        }
    }

    private synchronized void handleOpFinished(Message msg) {
        // Extract the operation index from the payload
        int finishedOpIndex = -1;
        try {
            finishedOpIndex = Integer.parseInt(msg.payload);
        } catch (Exception e) {
            System.err.println("Node " + id + " received invalid OP_FINISHED payload");
            return;
        }
        
        // Only process if this is for our current operation and we haven't seen it yet
        if (finishedOpIndex != currentOpIndex) {
            System.out.println("Node " + id + " ignoring OP_FINISHED for op " + 
                             finishedOpIndex + " (current=" + currentOpIndex + ")");
            return;
        }
        
        if (processedOperations.contains(finishedOpIndex)) {
            System.out.println("Node " + id + " already processed OP_FINISHED for op " + finishedOpIndex);
            return;
        }
        
        System.out.println("Node " + id + " received OP_FINISHED for operation " + finishedOpIndex + 
                          " from node " + msg.senderId);
        
        processedOperations.add(finishedOpIndex);
        
        // Propagate to neighbors (except sender) - flood protocol
        if (config.neighbors.containsKey(id)) {
            for (int nid : config.neighbors.get(id)) {
                if (nid != msg.senderId) {
                    sendMessage(nid, msg);
                }
            }
        }
        
        // Advance to next operation
        advanceToNextOperation();
    }

    private void connectToNeighbors() {
        for (Config.NodeInfo info : config.nodes.values()) {
            if (info.id == id) continue;
            
            if (config.neighbors.get(id).contains(info.id)) {
                boolean connected = false;
                for (int retry = 0; retry < 5 && !connected; retry++) {
                    try {
                        Socket s = new Socket(info.host, info.port);
                        ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                        outputStreams.put(info.id, oos);
                        connected = true;
                        System.out.println("Node " + id + " connected to node " + info.id);
                    } catch(IOException e) {
                        if (retry < 4) {
                            try { Thread.sleep(1000); } catch(Exception ex) {}
                        } else {
                            System.err.println("Node " + id + " failed to connect to node " + info.id);
                        }
                    }
                }
            }
        }
    }

    public synchronized void sendMessage(int destId, Message msg) {
        try {
            if (outputStreams.containsKey(destId)) {
                // Tick clock ONLY for application messages, NOT for checkpoint protocol messages
                if(msg.type == Message.Type.APP_MSG) {
                    vClock.tick();
                }
                
                // Attach clock if not present
                if (msg.vectorClock == null) {
                    msg.vectorClock = vClock.getClockArray();
                }
                
                outputStreams.get(destId).writeObject(msg);
                outputStreams.get(destId).flush();
                outputStreams.get(destId).reset();
            }
        } catch(IOException e) { 
            System.err.println("Node " + id + " error sending to " + destId + ": " + e.getMessage());
        }
    }

    private void simulateApp() {
        Random rand = new Random();
        List<Integer> neighbors = config.neighbors.get(id);
        if (neighbors == null) return;
        
        while(active) {
            try { Thread.sleep(rand.nextInt(2000) + 500); } catch(Exception e){}
            if (!neighbors.isEmpty()) {
                int target = neighbors.get(rand.nextInt(neighbors.size()));
                sendMessage(target, new Message(Message.Type.APP_MSG, id, null, null, 0));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        if(args.length != 2) { 
            System.out.println("Usage: java Node <config> <id>"); 
            return; 
        }
        Config c = ConfigParser.parse(args[0]);
        Node node = new Node(Integer.parseInt(args[1]), c);
        System.out.println("Starting Node " + node.id);
        node.start();
    }
}