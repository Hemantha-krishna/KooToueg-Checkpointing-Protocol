import java.io.*;
import java.util.*;

public class KooTouegProtocol {
    private Node node;
    
    // State
    private boolean isParticipating = false;
    private int activeSeqNumber = -1;
    private int initiatorId = -1;
    private int parentId = -1; 
    private Set<Integer> expectedReplies = new HashSet<>();
    private boolean receivedNo = false;
    
    // Track processed decisions to prevent duplicates
    private Set<String> processedDecisions = new HashSet<>();
    
    // CRITICAL: Frozen vector clock at checkpoint time
    private int[] frozenVectorClock = null;

    public KooTouegProtocol(Node node) {
        this.node = node;
    }

    // --- INITIATOR ---
    public void startCheckpoint(int seqNumber) {
        resetState(seqNumber, node.id);
        this.isParticipating = true;
        this.parentId = -1; 
        
        // CRITICAL: Freeze vector clock state NOW, before sending checkpoint requests
        // No need to tick - checkpoint protocol messages don't advance clocks
        this.frozenVectorClock = node.vClock.getClockArray().clone();
        
        System.out.println("Node " + node.id + " initiating checkpoint " + seqNumber);
        
        takeTentativeCheckpoint();
        sendRequestsToNeighbors(null);
        
        if (expectedReplies.isEmpty()) {
            makeCheckpointPermanent();
            finish();
        }
    }

    // --- COHORT ---
    public void handleMessage(Message msg) {
        switch (msg.type) {
            case CKPT_REQ:
                handleCheckpointRequest(msg);
                break;

            case CKPT_RESP:
                handleCheckpointResponse(msg);
                break;

            case CKPT_COMMIT:
                handleCommit(msg);
                break;

            case CKPT_ABORT:
                handleAbort(msg);
                break;
        }
    }

    private void handleCheckpointRequest(Message msg) {
        if (isParticipating && msg.seqNumber == activeSeqNumber) {
            // Already in this instance -> Cycle detected -> YES
            System.out.println("Node " + node.id + " detected cycle, sending YES to " + msg.senderId);
            node.sendMessage(msg.senderId, new Message(Message.Type.CKPT_RESP, 
                node.id, node.vClock.getClockArray(), "YES", msg.seqNumber));
        } else if (isParticipating) {
            // Busy with different instance -> NO
            System.out.println("Node " + node.id + " busy with seq " + activeSeqNumber + 
                             ", sending NO to " + msg.senderId + " for seq " + msg.seqNumber);
            node.sendMessage(msg.senderId, new Message(Message.Type.CKPT_RESP, 
                node.id, node.vClock.getClockArray(), "NO", msg.seqNumber));
        } else {
            // Join the checkpoint
            int initId = (msg.initiatorId != -1) ? msg.initiatorId : msg.senderId;
            System.out.println("Node " + node.id + " joining checkpoint seq " + msg.seqNumber + 
                             " initiated by " + initId);
            
            resetState(msg.seqNumber, initId);
            this.parentId = msg.senderId;
            this.isParticipating = true;
            
            // CRITICAL: Freeze vector clock state NOW, before sending requests to children
            // This ensures we capture state at the moment we joined, not after message exchanges
            this.frozenVectorClock = node.vClock.getClockArray().clone();
            
            takeTentativeCheckpoint();
            sendRequestsToNeighbors(parentId);
            
            if (expectedReplies.isEmpty()) {
                sendResponseToParent(true);
            }
        }
    }

    private void handleCheckpointResponse(Message msg) {
        if (expectedReplies.remove(msg.senderId)) {
            System.out.println("Node " + node.id + " received " + msg.payload + 
                             " from node " + msg.senderId + 
                             " (" + expectedReplies.size() + " replies remaining)");
            
            if ("NO".equals(msg.payload)) {
                receivedNo = true;
            }
            
            if (expectedReplies.isEmpty()) {
                // All replies received
                if (node.id == initiatorId) {
                    // I'm the initiator - make decision
                    Message.Type decision = receivedNo ? Message.Type.CKPT_ABORT : Message.Type.CKPT_COMMIT;
                    System.out.println("Node " + node.id + " (initiator) making decision: " + decision);
                    
                    broadcastDecision(decision);
                    
                    if (!receivedNo) {
                        makeCheckpointPermanent();
                    } else {
                        discardTentativeCheckpoint();
                    }
                    finish();
                } else {
                    // I'm a cohort - send response to parent
                    sendResponseToParent(!receivedNo);
                }
            }
        }
    }

    private void handleCommit(Message msg) {
        // Create unique key for this decision
        String decisionKey = "COMMIT-" + msg.seqNumber;
        
        if (processedDecisions.contains(decisionKey)) {
            System.out.println("Node " + node.id + " ignoring duplicate COMMIT for seq " + msg.seqNumber);
            return;
        }
        
        if (isParticipating && msg.seqNumber == activeSeqNumber) {
            System.out.println("Node " + node.id + " processing COMMIT for seq " + msg.seqNumber);
            processedDecisions.add(decisionKey);
            
            makeCheckpointPermanent();
            propagateDecision(msg);
            finish();
        }
    }

    private void handleAbort(Message msg) {
        // Create unique key for this decision
        String decisionKey = "ABORT-" + msg.seqNumber;
        
        if (processedDecisions.contains(decisionKey)) {
            System.out.println("Node " + node.id + " ignoring duplicate ABORT for seq " + msg.seqNumber);
            return;
        }
        
        if (isParticipating && msg.seqNumber == activeSeqNumber) {
            System.out.println("Node " + node.id + " processing ABORT for seq " + msg.seqNumber);
            processedDecisions.add(decisionKey);
            
            discardTentativeCheckpoint();
            propagateDecision(msg);
            finish();
        }
    }

    private void resetState(int seq, int init) {
        activeSeqNumber = seq;
        initiatorId = init;
        expectedReplies.clear();
        receivedNo = false;
        isParticipating = false;
        frozenVectorClock = null;
        // Note: processedDecisions persists across checkpoints
    }

    private void sendRequestsToNeighbors(Integer excludeId) {
        List<Integer> neighbors = node.config.neighbors.get(node.id);
        if (neighbors == null) return;
        
        for (int nid : neighbors) {
            if (excludeId == null || nid != excludeId) {
                System.out.println("Node " + node.id + " sending CKPT_REQ to node " + nid);
                node.sendMessage(nid, new Message(Message.Type.CKPT_REQ, node.id, 
                    node.vClock.getClockArray(), null, activeSeqNumber, initiatorId));
                expectedReplies.add(nid);
            }
        }
        System.out.println("Node " + node.id + " expecting " + expectedReplies.size() + " replies");
    }

    private void sendResponseToParent(boolean voteYes) {
        System.out.println("Node " + node.id + " sending " + (voteYes ? "YES" : "NO") + 
                         " to parent " + parentId);
        node.sendMessage(parentId, new Message(Message.Type.CKPT_RESP, node.id, 
            node.vClock.getClockArray(), voteYes ? "YES" : "NO", activeSeqNumber));
    }

    private void broadcastDecision(Message.Type type) {
        System.out.println("Node " + node.id + " broadcasting " + type);
        propagateDecision(new Message(type, node.id, node.vClock.getClockArray(), 
            null, activeSeqNumber));
    }

    private void propagateDecision(Message msg) {
        List<Integer> neighbors = node.config.neighbors.get(node.id);
        if (neighbors == null) return;
        
        for (int nid : neighbors) {
            if (nid != msg.senderId) {
                System.out.println("Node " + node.id + " propagating " + msg.type + 
                                 " to node " + nid);
                node.sendMessage(nid, new Message(msg.type, node.id, 
                    node.vClock.getClockArray(), null, activeSeqNumber));
            }
        }
    }

    private void finish() {
        System.out.println("Node " + node.id + " finishing checkpoint seq " + activeSeqNumber);
        
        // Check if we're initiator BEFORE resetting
        boolean wasInitiator = (node.id == initiatorId);
        
        // Reset state completely
        isParticipating = false;
        activeSeqNumber = -1;
        initiatorId = -1;
        parentId = -1;
        expectedReplies.clear();
        receivedNo = false;
        
        if (wasInitiator) {
            node.broadcastFinished();
        }
    }

    // --- FILE I/O ---
    private void takeTentativeCheckpoint() {
        try (PrintWriter out = new PrintWriter(new FileWriter("temp_ckpt_" + node.id + ".tmp"))) {
            out.println(activeSeqNumber);
            // Use the frozen vector clock, not the current clock
            if (frozenVectorClock != null) {
                for (int i = 0; i < frozenVectorClock.length; i++) {
                    if (i > 0) out.print(" ");
                    out.print(frozenVectorClock[i]);
                }
                out.println();
            } else {
                // Fallback (should not happen)
                out.println(node.vClock.toString());
            }
            System.out.println("Node " + node.id + " took tentative checkpoint seq " + activeSeqNumber);
        } catch (IOException e) { 
            System.err.println("Node " + node.id + " error taking checkpoint: " + e.getMessage());
        }
    }

    private void makeCheckpointPermanent() {
        File temp = new File("temp_ckpt_" + node.id + ".tmp");
        File perm = new File("ckpt_node" + node.id + "_seq" + activeSeqNumber + ".out");
        
        if (temp.exists()) {
            if (temp.renameTo(perm)) {
                System.out.println("Node " + node.id + " COMMITTED seq " + activeSeqNumber);
            } else {
                System.err.println("Node " + node.id + " failed to rename checkpoint file");
            }
        } else {
            System.err.println("Node " + node.id + " temp checkpoint file not found");
        }
    }

    private void discardTentativeCheckpoint() {
        File temp = new File("temp_ckpt_" + node.id + ".tmp");
        if (temp.delete()) {
            System.out.println("Node " + node.id + " ABORTED seq " + activeSeqNumber);
        } else {
            System.err.println("Node " + node.id + " failed to delete temp checkpoint");
        }
    }
}