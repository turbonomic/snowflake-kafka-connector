# Getting Started with the Turbonomic Snowflake Exporter

Turbonomic 8 continuously monitors applications and infrastructure components communicating with different APIs
(Dynatrace, Openshift, VMware, VMM, HyperV, etc..) The topology and utilization data from these various sources are
continuously stitched together into a full stack topology containing all the current/most recent monitored values
for each entity as it is collected from the sources within the last few minutes.
This full stack topology and metrics is internally broadcasted using an kafka message bus within Turbonomic 8
to various components, for example to the market to run the analytics or for the history to store historical metrics.

We have created a container image that subscribes to the same live topology broadcast using the internal
kafka messaging bus and sends the same information with all the details to Snowflake using a Snowflake connector.
This leverages the opensource Snowflake connector, which is available to any free or paying Snowflake customer
https://github.com/snowflakedb/snowflake-kafka-connector

The Turbonomic Exporter makes it easy for Turbonomic Administrators to export time-series metrics
with topology context to Snowflake. Packaged as a container it can be easily deployed either using helm or a simple yaml.

## Installing the Turbonomic Exporter from a yaml file
````
kubectl create -f https://raw.githubusercontent.com/turbonomic/snowflake-kafka-connector/turbonomic/deploy/snowflake-kafka-connect_yamls/deployment.yaml -n turbonomic
````
## Installing the Turbonomic Exporter using helm
````
helm install snowflake-kafka-connect snowflake-kafka-connect -n turbonomic
````

This component expects that you subscribed to Snowflake and Snowflake is accessible from where Turbo 8 runs
and also that you have created credentials to authenticate this snowflake-kafka-connector instance.
This information can be sent to the snowflake-kafka-connector using a single http post api (as documented at https://community.snowflake.com/s/article/Docker-Compose-Setting-up-Kafka-using-Snowflake-Sink-Connector-and-testing-Streams):
````
$ curl snowflake-kafka-connect:8083/connectors -X POST -H "Content-Type: application/json" -d '
{
 "name":"snowflake-kafka-connect",
 "config":{
   "connector.class":"com.snowflake.kafka.connector.SnowflakeSinkConnector",
   "tasks.max":"1",
   "topics":"<Your Topic Name>",
   "snowflake.topic2table.map": "<Your Topic Name>:<Your Table Name>",
   "buffer.count.records":"1000",
   "buffer.size.bytes":"1048576",
   "snowflake.url.name":"ACCOUNT.snowflakecomputing.com",
   "snowflake.user.name":"<Your User Name>",
   "snowflake.private.key":"<Your private Key>",
   "snowflake.private.key.passphrase":"<Your Pass passphrase>",
   "snowflake.database.name":"<Your DB Name>",
   "snowflake.schema.name":"PUBLIC",
   "key.converter":"org.apache.kafka.connect.storage.StringConverter",
   "value.converter":"com.snowflake.kafka.connector.records.SnowflakeJsonConverter",
   "value.converter.schema.registry.url":"http://localhost:8081"",
   "value.converter.basic.auth.credentials.source":"USER_INFO",
   "value.converter.basic.auth.user.info":"<Username>:<password>"
 }
}
````

At which point the data will start flowing into the specified Snowflake table, from the exporter topic from Turbonomic 8


### Delete the Turbonomic Exporter from a yaml file

````
kubectl delete -f https://raw.githubusercontent.com/turbonomic/snowflake-kafka-connector/turbonomic/deploy/snowflake-kafka-connect_yamls/deployment.yaml -n turbonomic
````

### Delete the Turbonomic Exporter using helm

````
helm delete snowflake-kafka-connect -n turbonomic
````
