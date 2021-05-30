This sample does the following:

- host, backup and client start
- host begins writing sequence of longs to local archive
- backup replicates the archive over
- client connects to host, reads first 10 longs in the sequence
- client disconnects from host, connects to backup and continues reading from sequence 11

Before running the docker compose, you will need to run a full gradle build in the root folder so that the shadow jars are built for the archive-host and archive-client.

Then, just run `docker compose up` (or `docker-compose up`, depending on your Docker version).

Note: the docker compose file in this folder requires a recent version of Docker with at least 4Gb memory assigned. 2Gb of that is assigned to /dev/shm.
