The archive-host, archive-client projects, and the docker-compose represent the multi-host sample.

Before running the docker compose, you will need to run a full gradle build in the root folder so that the shadow jars are built for the archive-host and archive-client.

Then, just run `docker compose up` (or `docker-compose up`, depending on your Docker version).

Note: the docker compose file in this folder requires a recent version of Docker with at least 4Gb memory assigned. 2Gb of that is assigned to /dev/shm.
