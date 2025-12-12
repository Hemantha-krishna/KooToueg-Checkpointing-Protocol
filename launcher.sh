#!/bin/bash

# ==== USER CONFIGURATION ====

# Your NetID
netid=hxc230046

# Root directory of your project on dc machines
# CHANGED: Assumed project3, change if strictly different
PROJDIR=/home/010/h/hx/hxc230046/project3

# Config file path on dc machines
CONFIG=$PROJDIR/config-dc.txt

# Directory where compiled .class files are located
BINDIR=$PROJDIR

# Main Java class
PROG=Node

# =============================

# 1. Compile locally first (Safer for NFS)
echo "Compiling Java files..."
cd $PROJDIR
javac *.java

# 2. Create logs directory if it doesn't exist
if [ ! -d "logs" ]; then
  mkdir logs
fi

n=0

# Parse config file (ignore comments & blank lines)
cat $CONFIG | sed -e "s/#.*//" | sed -e "/^\s*$/d" | (
    # Read first line for number of nodes
    read firstline
    numNodes=$(echo $firstline | awk '{ print $1 }')

    echo "Launching $numNodes nodes (headless mode)..."

    while [[ $n -lt $numNodes ]]
    do
        read line
        nodeID=$(echo $line | awk '{ print $1 }')
        host=$(echo $line | awk '{ print $2 }')

        echo "Starting node $nodeID on $host..."

        # CHANGED: Removed 'javac Node.java' from inside the SSH command to prevent race conditions
        # CHANGED: Added 'nohup' to prevent hangup
        ssh -f -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no \
        $netid@$host "cd $BINDIR; nohup java $PROG config-dc.txt $nodeID > logs/node${nodeID}.log 2>&1 &" &

        n=$((n + 1))
    done
)

echo "All nodes launched in background."