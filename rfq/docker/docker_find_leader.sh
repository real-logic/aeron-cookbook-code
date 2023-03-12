nodes=(
  "aeron-node0-1"
  "aeron-node1-1"
  "aeron-node2-1"
)

for i in {0..2}; do
  output=$(docker exec -it "${nodes[i]}" ./noderole.sh)
  if [[ $output == *"LEADER"* ]]; then
    echo "${nodes[i]}"
    break
  fi
done
