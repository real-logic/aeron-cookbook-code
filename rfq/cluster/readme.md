## Running Local

- run `./gradlew` to build the code
- run `./gradlew runSingleNodeCluster`

## Environment Variables

| Variable          | Description                                                                                     | Default     |
|-------------------|-------------------------------------------------------------------------------------------------|-------------|
| CLUSTER_PORT_BASE | The base port to use for the cluster.                                                           | `9000`      |
| CLUSTER_NODE      | The cluster node index in the CLUSTER_ADDRESSES comma separated list that this node represents. | `0`         |
| CLUSTER_ADDRESSES | A comma separated list of cluster addresses to connect to.                                      | `localhost` |

## Bundled Scripts within Cluster Containers

| Script              | Description                                                    |
|---------------------|----------------------------------------------------------------|
| aeronstat_single.sh | A script to run aeronstat just once.                           |
| clustererrors.sh    | A script to run Cluster Tool and list any errors raised.       |
| describe.sh         | A script to run Cluster Tool and describe the cluster.         |
| errorstat.sh        | This runs the Aeron error stat tool.                           |
| lossstat.sh         | This runs the Aeron loss stat tool.                            |
| noderole.sh         | This script returns LEADER on the current active leader node   |
| snapshot.sh         | This script instructs the cluster to take a snapshot           | 
| stackdump.sh        | This script uses jstack to dump the stack of the cluster node. |
| streamsstat.sh      | This runs the Aeron stream stat tool.                          |

## Uber Jar Manifest notes

- `Main-Class: io.aeron.samples.cluster.Cluster`
- `Add-Opens: java.base/sun.nio.ch`
