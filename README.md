#  Backup and Restore Azure Blob Storage Source Connector for Confluent Platform 

- [Backup and Restore Azure Blob Storage Source Connector for Confluent Platform](#backup-and-restore-azure-blob-storage-source-connector-for-confluent-platform)
  - [References](#references)
  - [Setup Connect](#setup-connect)
  - [Sink](#sink)
  - [Source](#source)
  - [Cleanup](#cleanup)

## References

https://docs.confluent.io/kafka-connectors/azure-blob-storage-sink/current/overview.html#quick-start

https://docs.confluent.io/kafka-connectors/azure-blob-storage-source/current/backup-and-restore/overview.html


## Setup Connect

Start containers.

```bash
docker compose up -d
```

Then execute:

```bash
docker compose exec kafka-connect-1 confluent-hub install --no-prompt confluentinc/kafka-connect-azure-blob-storage:latest
docker compose exec kafka-connect-1 confluent-hub install --no-prompt confluentinc/kafka-connect-azure-blob-storage-source:latest
``` 

Restart connect:

```bash
docker compose restart kafka-connect-1
```

List connector plugins:

```bash
curl localhost:8083/connector-plugins | jq
```

## Sink

Lets create a topic named blob_topic:

```bash
kafka-topics --bootstrap-server localhost:9091 --create --topic blob_topic --partitions 3 --replication-factor 1
```

To import some data into our topic we execute:

```bash
kafka-avro-console-producer --broker-list localhost:9091 --topic blob_topic \
--property value.schema='{"type":"record","name":"myrecord","fields":[{"name":"f1","type":"string"}]}' --property parse.key=true --property key.separator=, --property key.serializer=org.apache.kafka.common.serialization.StringSerializer
```

And enter:

```
0,{"f1": "value0"}
1,{"f1": "value1"}
2,{"f1": "value2"}
3,{"f1": "value3"}
4,{"f1": "value4"}
5,{"f1": "value5"}
6,{"f1": "value6"}
7,{"f1": "value7"}
8,{"f1": "value8"}
9,{"f1": "value9"}
```

You can navigate to http://localhost:9021/clusters and check the messages distributed per partition.

Stop the producer and create the connector (review command for having your account and key to access Azure Blob Storage):

```bash
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/blob-sink/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.AzureBlobStorageSinkConnector",
      "topics"                   : "blob_topic",
      "tasks.max"                : "3",
      "flush.size"               : "1",
      "format.class"             : "io.confluent.connect.azure.blob.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "schema.compatibility"    : "FORWARD",
      "partitioner.class"       : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

Check your Azure blob container you should have a folder topics, inside blob_topic and inside the partition folders with the avro messages.

Now we can stop kafka:

```bash
docker compose down -v
```

## Source

Starting a empty new environment run:

```bash
docker compose up -d
```

Lets create our topic named blob_topic:

```bash
kafka-topics --bootstrap-server localhost:9091 --create --topic blob_topic --partitions 3 --replication-factor 1
```

Let's create our connector (review command for having your account and key to access Azure Blob Storage):

```bash
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/blob-storage-source/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                : "3",
      "confluent.topic.replication.factor" : "1",
      "format.class"             : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "mode"                     : "RESTORE_BACKUP",
      "partitioner.class"       : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

We can see with control center http://localhost:9021/clusters all partitions populated for the topic.

## Cleanup

```bash
docker compose down -v
```