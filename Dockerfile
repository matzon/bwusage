FROM arm64v8/openjdk:8-slim-buster
WORKDIR /opt/bwusage/

COPY target/bwusage.jar ./
COPY bwusage.sh ./

RUN ["chmod", "+x", "./bwusage.sh"]

RUN apt-get update && apt-get install -y screen && rm -rf /var/lib/apt/lists/*

ENTRYPOINT ["screen", "-S", "bwusage", "./bwusage.sh"]