#!/bin/sh
role=$(./aeronstat_single.sh | grep "node role" | awk '{print $2}')
electionState=$(./aeronstat_single.sh | grep "election state" | awk '{print $2}')
moduleState=$(./aeronstat_single.sh | grep "Module state" | awk '{print $2}')

if [ $role = "2" ] && [ $electionState = "17" ] && [ $moduleState = "1" ]; then
    echo "LEADER"
else
    echo ""
fi