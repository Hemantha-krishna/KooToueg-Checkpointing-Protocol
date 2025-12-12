#!/bin/bash
# Checkpoint Consistency Verification Script
# Verifies that checkpoint files represent consistent global states

echo "========================================================================"
echo "CHECKPOINT CONSISTENCY VERIFICATION"
echo "========================================================================"
echo ""

# Check if checkpoint files exist
if ! ls ckpt_node*.out &> /dev/null; then
    echo "ERROR: No checkpoint files found!"
    echo "Please run the protocol first: ./launcher.sh"
    exit 1
fi

# Extract unique sequence numbers
sequences=$(cat ckpt_node*.out | awk 'NR % 2 == 1' | sort -u)

for seq in $sequences; do
    echo "========================================================================"
    echo "CHECKPOINT SEQUENCE $seq"
    echo "========================================================================"
    echo ""
    
    # Extract vector clocks for this sequence
    echo "Vector Clocks:"
    
    declare -A vclocks
    num_nodes=0
    
    for node in {0..4}; do
        file="ckpt_node${node}_seq${seq}.out"
        if [ -f "$file" ]; then
            # Read sequence number and vector clock
            line_num=1
            found=false
            while IFS= read -r line; do
                if [ "$line" == "$seq" ]; then
                    found=true
                    read -r vclock
                    echo "  Node $node: [$vclock]"
                    vclocks[$node]="$vclock"
                    ((num_nodes++))
                    break
                fi
                ((line_num++))
            done < "$file"
            
            if [ "$found" = false ]; then
                echo "  Node $node: MISSING (sequence $seq not found)"
            fi
        else
            echo "  Node $node: MISSING (file not found)"
        fi
    done
    
    echo ""
    
    if [ $num_nodes -eq 0 ]; then
        echo "WARNING: No nodes have checkpoints for this sequence"
        echo ""
        continue
    fi
    
    # Check consistency: VC[i][i] >= VC[j][i] for all i,j
    echo "Checking consistency condition: VC[i][i] >= VC[j][i] for all i,j"
    echo "----------------------------------------------------------------------"
    
    consistent=true
    violations=0
    
    for i in {0..4}; do
        if [ -n "${vclocks[$i]}" ]; then
            vc_i=(${vclocks[$i]})
            
            for j in {0..4}; do
                if [ -n "${vclocks[$j]}" ]; then
                    vc_j=(${vclocks[$j]})
                    
                    # Check: vc_i[i] >= vc_j[i]
                    if [ "${vc_i[$i]}" -lt "${vc_j[$i]}" ]; then
                        echo "  ✗ VIOLATION: VC[$i][$i] = ${vc_i[$i]} < VC[$j][$i] = ${vc_j[$i]}"
                        echo "     Node $j knows more about node $i than node $i itself!"
                        consistent=false
                        ((violations++))
                    fi
                fi
            done
        fi
    done
    
    if [ "$consistent" = true ]; then
        total_checks=$((num_nodes * num_nodes))
        echo "  ✓ All consistency conditions satisfied!"
        echo "     Checked $total_checks conditions - all passed"
        echo ""
        echo "  ✓ RESULT: Checkpoint sequence $seq is CONSISTENT"
        echo "     Forms a valid global snapshot of the distributed system"
    else
        echo ""
        echo "  ✗ RESULT: Checkpoint sequence $seq is INCONSISTENT"
        echo "     Found $violations violation(s)"
    fi
    
    echo ""
done

echo "========================================================================"
echo "ANALYSIS COMPLETE"
echo "========================================================================"
