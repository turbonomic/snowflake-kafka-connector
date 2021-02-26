FROM registry.access.redhat.com/ubi8

ENV USER_UID=1001 \
    USER_NAME=snowflake

LABEL name="kafka-connect-snowflake" \
      maintainer="support@snowflake.com" \
      vendor="snowflake" \
      version="1.5.1" \
      release="1" \
      summary="Snowflake Connect for Kafka" \
      description="Snowflake Connect for Kafka is a Kafka Connect Sink for Snowflake."

# install kafka connnect binaries
COPY /snowflake-kafka-connect /snowflake-kafka-connect

# Install rsyslog and java-11 packages
RUN dnf install -y rsyslog procps-ng java-11-openjdk-headless && \
    dnf clean all && \
    rm -rf /var/cache/dnf

RUN mkdir /licenses \
    && curl -s -o /licenses/apache-2.0.txt https://www.apache.org/licenses/LICENSE-2.0.txt \
    && curl -s -o /licenses/EULA_Red_Hat_Universal_Base_Image_English_20190422.pdf https://www.redhat.com/licenses/EULA_Red_Hat_Universal_Base_Image_English_20190422.pdf

ENTRYPOINT ["/snowflake-kafka-connect/bin/connect-distributed.sh", "/snowflake-kafka-connect/config/connect-distributed.properties"]

USER ${USER_UID}
