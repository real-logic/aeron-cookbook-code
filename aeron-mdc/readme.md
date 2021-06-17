The aeron-mdc-publisher and aeron-mdc-subscriber projects, and the docker-compose represent the Aeron Multi Destination Cast sample.

Before running the docker compose, you will need to run a full gradle build in the root folder so that the shadow jars are built.

Then, just run `docker-compose up`.

Note: the docker compose file in this folder requires a recent version of Docker with at least 4Gb memory assigned. 3Gb of that is assigned to /dev/shm.
