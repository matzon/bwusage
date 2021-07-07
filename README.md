#BWUsage

Small application for getting bandwidth usage from my isp.

_Quicker than messing with snmp on the router ;)_

Output stored in hsqldb, using raw hibernate - no spring-data or jpa.

Note: after some time, the DB grows quite a bit ;) - consider updating the 'data/db/bwusage.db.script' with 'CREATE CACHED TABLE' instead of MEMORY.
Note: using jdk8, due to device restraints

Will serve as basis for producing data for future graphing frontend - no rrd/backend!