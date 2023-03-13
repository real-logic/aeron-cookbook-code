# Admin

## Admin Commands

> Note: The cluster starts up with two participants, `500` (`initiator`) and `501` (`responder`) preconfigured.

You can obtain per command help by typing `help` followed by the command name.

- `connect` - connect to a cluster. Optional arguments are `hostnames` and `baseport`. Defaults are `localhost`
  and `9000`.
- `disconnect` - disconnect from the cluster.
- `instrument-add` - adds an instrument
- `instrument-disable` - disables an instrument
- `instrument-enable` - enables an instrument
- `instrument-list` - lists all instruments
- `rfq-create` - creates an RFQ
- `rfq-accept` - accepts an RFQ
- `rfq-reject` - rejects an RFQ
- `rfq-cancel` - cancels an RFQ
- `rfq-counter` - counters an RFQ quote or counter
- `rfq-quote` - quotes an RFQ
- `help` - show help.
- `exit` - exit the application.

Sample happy path script (assumes starting with a clean cluster):

```
connect
add-instrument cusip=12345
rfq-create cusip=12345 quantity=250 created-by=500
rfq-quote rfq-id=1 price=1000 quoted-by=501
rfq-accept rfq-id=1 accepted-by=500
exit
```

## Running Admin outside of Kubernetes or Docker

> **Note**: You will need a running cluster for the Admin to connect to. `./gradlew runSingleNodeCluster` will start a
> cluster.

First, you will need to build the uber jar. You can do this via gradle in the project root directory:

```bash
./gradlew
```

This should output an Admin uber jar in:

`/admin/build/libs/`

Then you can move to that folder and run admin with:

```bash
java -jar admin-uber.jar
```

Note that the admin is a terminal application, and cannot run inside other tools such as IntelliJ terminal or via Gradle
run.

## Protocol Notes

The admin uses a simple protocol via SBE to communicate from the CLI commands to an Agrona Agent running the cluster communications. 
This Agrona agent then converts from the CLI SBE protocol to the cluster SBE protocol.
This approach is typical for gateways, for example you may have a web socket gateway that uses a json protocol, and then a cluster-specific protocol from the gateway to the cluster.

## Environment Variables

| Variable          | Description | Default |
|-------------------|-------------|---------|
| AUTO_CONNECT      | If set to `true`, the admin will automatically connect to the cluster on startup. | `false` |
| USER_ID           | The participant ID to use when connecting to the cluster. | `0` |
| DUMB_TERMINAL     | If set to `true`, the admin will not use ANSI escape codes for terminal output. | `false` |
| CLUSTER_ADDRESSES | A comma separated list of cluster addresses to connect to. | `localhost` |

## Uber Jar Manifest notes

- `Add-Opens: java.base/sun.nio.ch`

## Copyright Note

Portions of this code are copyright 2023 Adaptive Financial Consulting.