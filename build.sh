#!/bin/bash

# variables
kafkaversion=2.7.0
builddir=/tmp/snowflake-kafka-connect-build/snowflake-kafka-connect

githash=`git rev-parse --short HEAD 2>/dev/null | sed "s/\(.*\)/@\1/"` # get current git hash
gitbranch=`git rev-parse --abbrev-ref HEAD` # get current git branch
gitversion=`git describe --abbrev=0 --tags 2>/dev/null` # returns the latest tag from current commit
jarversion=${gitversion}

# if no version found from git tag, it is a dev build
if [[ -z "$gitversion" ]]; then
  gitversion="dev"
  jarversion=${gitversion}-SNAPSHOT
fi

/bin/rm -rf ${builddir}
mkdir -p ${builddir}/connectors
mkdir -p ${builddir}/bin
mkdir -p ${builddir}/config
mkdir -p ${builddir}/libs

# Build the package
#echo "Building the connector package ..."
#mvn versions:set -DnewVersion=${jarversion}
#mvn package > /dev/null

# Copy over the package
#echo "Copy over snowflake-kafka-connect jar ..."
#cp target/snowflake-kafka-connect-${jarversion}.jar ${builddir}/connectors
# Add Snowflake connector jar
curl -sSL "https://repo1.maven.org/maven2/com/snowflake/snowflake-kafka-connector/1.5.1/snowflake-kafka-connector-1.5.1.jar" -o ${builddir}/connectors/snowflake-kafka-connector-1.5.1.jar
# Add Snowflake JDBC connector jar
curl -sSL "https://repo1.maven.org/maven2/net/snowflake/snowflake-jdbc/3.12.15/snowflake-jdbc-3.12.15.jar" -o ${builddir}/connectors/snowflake-jdbc-connector-3.12.15.jar
# Install the below jars
curl -sSL "https://repo1.maven.org/maven2/org/bouncycastle/bc-fips/1.0.2/bc-fips-1.0.2.jar" -o ${builddir}/libs/bc-fips-1.0.2.jar
curl -sSL "https://repo1.maven.org/maven2/org/bouncycastle/bcpkix-fips/1.0.4/bcpkix-fips-1.0.4.jar" -o ${builddir}/libs/bcpkix-fips-1.0.4.jar
cp config/connect-distributed-quickstart.properties ${builddir}/config/connect-distributed.properties
cp README.md ${builddir}
cp LICENSE ${builddir}

# Download kafka
echo "Downloading kafka_2.13-${kafkaversion} ..."
wget -q --no-check-certificate https://archive.apache.org/dist/kafka/${kafkaversion}/kafka_2.13-${kafkaversion}.tgz -P ${builddir}
cd ${builddir} && tar xzf kafka_2.13-${kafkaversion}.tgz

# Copy over kafka connect runtime
echo "Copy over kafka connect runtime ..."
cp kafka_2.13-${kafkaversion}/bin/connect-distributed.sh ${builddir}/bin
cp kafka_2.13-${kafkaversion}/bin/kafka-run-class.sh ${builddir}/bin
cp kafka_2.13-${kafkaversion}/config/connect-log4j.properties ${builddir}/config
cp kafka_2.13-${kafkaversion}/libs/*.jar ${builddir}/libs

# Clean up
echo "Clean up ..."
/bin/rm -rf kafka_2.13-${kafkaversion}
/bin/rm -f kafka_2.13-${kafkaversion}.tgz


# Create a container image
cd -
mv ${builddir}/ build
docker build build -t turbonomic/snowflake-kafka-connect

/bin/rm -rf build/snowflake-kafka-connect
echo "Done with build & packaging"
