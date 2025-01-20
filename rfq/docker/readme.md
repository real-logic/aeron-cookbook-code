# Docker

> **Note**: Assumes Docker Compose 2 is available.

This will start a three-node cluster, with 2 admin containers.
The cluster nodes will run an election, selecting one of the nodes as the leader.

Containers will be named as follows:

| Container      | Description  |
|----------------|--------------|
| aeron-engine0-1 | Cluster Node |
| aeron-engine1-1 | Cluster Node |
| aeron-engine2-1 | Cluster Node |
| aeron-admin1-1  | Admin Node   |
| aeron-admin2-1  | Admin Node   |

## Building containers

- First, build the source code by running `./gradlew` in the project root directory
- Then, build the containers with `docker compose build --no-cache`

## Running containers

Start the cluster and admin containers by running `docker compose up -d`

## Finding the cluster leader

To find the leader (e.g. to stop it to test failover), you can either review the logs of the containers with `docker compose logs` and search for `LEADER` (
e.g. `docker compose logs | grep LEADER`), or use the script `./docker_find_leader.sh`.

## Connecting to the admin container

Both admin containers can be used at the same time. Change `aeron-admin1-1` for `aeron-admin2-1` as needed.

(assumes a container named `aeron-admin1-1` is running)

`docker exec -it aeron-admin1-1 java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util.zip=ALL-UNNAMED -jar admin-uber.jar`

Within the admin, you can then connect to the cluster:

```bash
connect
```

## Stopping containers

To stop a specific container, run:

`docker compose stop <container name>` where `<container name>` matches the name of the container you want to stop.

To stop all the containers, and remove any networking changes, run:

`docker compose down`

## Tooling in containers

The cluster containers contain a number of Aeron operations tools. These are available via scripts such as `aeronstat-single.sh` and `snapshot.sh`.
