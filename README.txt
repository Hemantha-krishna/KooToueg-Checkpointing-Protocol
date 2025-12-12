================================================================================
CS/CE 6378: Advanced Operating Systems - Project 3
Koo-Toueg Checkpointing Protocol Implementation
================================================================================

TEAM MEMBERS:
------------
Hemantha Krishna Challa (hxc230046)
Working alone - Checkpointing protocol only (no recovery)

COMPILATION:
-----------
On dcXX machines:
  javac *.java

RUNNING THE PROGRAM:
-------------------
The program uses a configuration file format as specified in the project PDF.

Usage:
  java Node <config_file> <node_id>

Example for node 0:
  java Node config-dc.txt 0

AUTOMATED DEPLOYMENT:
--------------------
For deployment across DC machines, use the provided launcher script:
  
  1. Upload files to dc01:
     scp *.java *.sh config-dc.txt <netid>@dc01.utdallas.edu:~/project3/
  
  2. SSH to dc01 and run:
     cd ~/project3
     chmod +x *.sh
     ./launcher.sh
  
  3. Monitor execution:
     tail -f logs/node*.log
  
  4. Check checkpoint files:
     cat ckpt_node*_seq*.out
  
  5. Cleanup:
     ./cleanup.sh

CONFIGURATION FILE FORMAT:
-------------------------
The configuration file follows the format specified in Section 3 of the PDF:

Line 1: <numNodes> <minDelay>
Next n lines: <nodeID> <hostname> <port>
Next n lines: <space-delimited neighbor list for node 0..n-1>
Remaining lines: Operation tuples in format (c, nodeID)

Example (config-dc.txt):
  5 15
  0 dc01.utdallas.edu 1234
  1 dc02.utdallas.edu 1233
  ...
  1 4          # neighbors for node 0
  0 2 3        # neighbors for node 1
  ...
  (c, 1)       # checkpoint initiated by node 1
  (c, 3)       # checkpoint initiated by node 3

Lines starting with # are comments and are ignored.

KEY IMPLEMENTATION DETAILS:
--------------------------
1. Vector Clocks: Each node maintains a vector clock for causal ordering

2. Koo-Toueg Protocol:
   - Two-phase commit for checkpointing
   - Phase 1: Request/Response with YES/NO votes
   - Phase 2: COMMIT/ABORT decision broadcast
   - Cycle detection: Responds YES if already in active checkpoint

3. Checkpoint Contents:
   - Sequence number
   - Vector clock state
   - Stored in files: ckpt_node<id>_seq<num>.out

4. Operation Sequencing:
   - Uses OP_FINISHED flooding to signal completion
   - Next initiator waits minDelay before starting
   - Ensures no concurrent checkpoint instances

5. Network:
   - TCP sockets with ObjectOutputStream for message serialization
   - Connection retry logic (5 attempts)
   - Synchronized startup delays for stable connections

TESTING RESULTS:
---------------
Successfully tested on dcXX machines with:
  - 5 nodes in ring topology
  - Multiple checkpoint operations
  - Vector clocks showing proper causal ordering
  - All nodes achieving COMMIT for first checkpoint sequence

Example vector clocks from successful run (seq 1):
  Node 0: [7, 6, 4, 11, 7]
  Node 1: [3, 7, 4, 9, 4]
  Node 2: [3, 6, 8, 10, 4]
  Node 3: [3, 6, 4, 9, 4]
  Node 4: [4, 6, 4, 11, 7]

FILES INCLUDED:
--------------
  - Node.java        
  - KooTouegProtocol.java  
  - Message.java           
  - VectorClock.java       
  - Config.java            
  - ConfigParser.java  
  - launcher.sh          
  - cleanup.sh           
  - verify.sh              
  - config-dc.txt       
  - README.txt           


================================================================================
