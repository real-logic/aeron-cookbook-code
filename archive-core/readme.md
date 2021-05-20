The archive-host, archive-client projects, and the docker-compose are a work in progress.

Before running the docker compose, you will need to run a full gradle build in the root folder so that the shadow jars are built for the archive-host and archive-client.

Then, just run `docker compose up`

Known issues:
- the sample currently does not correctly assign IP addresses. The IPs will need to be per the docker-compose file to work. Sometimes they are assigned as requested, sometimes not.
- the docker compose file in this folder requires a recent version of Docker with at least 4Gb memory assigned. 2Gb of that is assigned to /dev/shm.
