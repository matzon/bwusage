#BWUsage

Small application for getting bandwidth usage from my isp.

_Quicker than messing with snmp on the router ;)_

Output stored in hsqldb, using raw hibernate - no spring-data or jpa.

Note: after some time, the DB grows quite a bit ;) - consider updating the 'data/db/bwusage.db.script' with 'CREATE CACHED TABLE' instead of MEMORY.
Note: using jdk8, due to device restraints

Using docker:
- Create or bind volumes for logs and data (docker volume create bwusage-data, docker volume create bwusage-logs)
- Create image (docker build -t matzon/bwusage .)
- Run image in container (docker run -itd --name bwusage -v bwusage-data:/opt/bwusage/data -v bwusage-logs:/opt/bwusage/logs matzon/bwusage)
- Shell into container to verify working (docker exec -it bwusage /bin/bash)
- Attach to screen (screen -x)

Will serve as basis for producing data for future graphing frontend - no rrd/backend!