#!/bin/bash

# ==== USER CONFIGURATION ====
netid=hxc230046
# CHANGED: Update this path to your actual project 3 folder
PROJDIR=/home/010/h/hx/hxc230046/project3
CONFIG=$PROJDIR/config-dc.txt
# =============================

# Read number of nodes (first line)
numNodes=$(grep -v '^[[:space:]]*#' "$CONFIG" | sed '/^[[:space:]]*$/d' | head -n 1 | awk '{print $1}')
echo "Performing full cleanup on $numNodes nodes..."

# Read hostnames for each node
# (This logic works perfectly for Project 3's config format)
hosts=$(grep -v '^[[:space:]]*#' "$CONFIG" | sed '/^[[:space:]]*$/d' | tail -n +2 | head -n "$numNodes" | awk '{print $2}')

# Clean on each remote host
for host in $hosts; do
    echo "==> Cleaning on $host ..."
    ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no "$netid@$host" "bash -c '
        cd $PROJDIR || exit
        echo \"  - Killing Node processes...\"
        pkill -u $netid -f Node 2>/dev/null

        echo \"  - Removing remote logs...\"
        rm -f logs/node*.log logs/node*.pid 2>/dev/null

        echo \"  - Removing Checkpoint outputs...\"
        # CHANGED: Matches Project 3 checkpoint files
        rm -f ckpt_*.out temp_*.tmp 2>/dev/null

        echo \"  - Removing class files...\"
        rm -f *.class 2>/dev/null

        echo \"  - Cleanup done on \$(hostname).\"'"
done

# Local cleanup
echo "==> Cleaning local directory ..."
cd "$PROJDIR" || exit
pkill -u "$USER" -f Node 2>/dev/null
rm -f logs/node*.log logs/node*.pid 2>/dev/null
# CHANGED: Local removal of Project 3 files
rm -f ckpt_*.out temp_*.tmp *.class 2>/dev/null
echo "Local cleanup complete."

echo "All nodes fully cleaned."