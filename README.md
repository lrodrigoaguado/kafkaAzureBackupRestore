#  Backup and Restore Azure Blob Storage Source Connector for Confluent Platform 

- [Backup and Restore Azure Blob Storage Source Connector for Confluent Platform](#backup-and-restore-azure-blob-storage-source-connector-for-confluent-platform)
  - [References](#references)
  - [Setup Connect](#setup-connect)
  - [Sink](#sink)
  - [Source](#source)
  - [Test Source Parallelization](#test-source-parallelization)
    - [Data Generation](#data-generation)
    - [Sink](#sink-1)
    - [Source](#source-1)
  - [TimeBasedPartitioner](#timebasedpartitioner)
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
docker compose exec kafka-connect-1 confluent-hub install --no-prompt confluentinc/kafka-connect-datagen:latest
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

## Test Source Parallelization

### Data Generation

Let's create our topic:

```shell
kafka-topics --bootstrap-server localhost:9091 --topic customers --create --partitions 4 --replication-factor 1
```

And after create our data generation connector:

```shell
curl -i -X PUT -H "Accept:application/json" -H  "Content-Type:application/json" http://localhost:8083/connectors/my-datagen-source/config -d '{
    "name" : "my-datagen-source",
    "connector.class": "io.confluent.kafka.connect.datagen.DatagenConnector",
    "kafka.topic" : "customers",
    "output.data.format" : "AVRO",
    "quickstart" : "SHOE_CUSTOMERS",
    "tasks.max" : "1"
}'
```

Let it be running for a while (like 10 minutes). And after pause the connector.

### Sink

Now let's create our sink as before.

```shell
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/customers-sink/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.AzureBlobStorageSinkConnector",
      "topics"                   : "customers",
      "tasks.max"                : "4",
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

Once Sink completed pause it (it should take about 2 minutes)

### Source

Delete the topic customers and recreate it:

```shell
kafka-topics --bootstrap-server localhost:9091 --delete --topic customers
```
```shell
kafka-topics --bootstrap-server localhost:9091 --topic customers --create --partitions 4 --replication-factor 1
```

Let's execute the source with just one task first:

```shell
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/customers-source/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                : "1",
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

It took to us about 25 minutes to load the whole data.

If we delete the connector, the topic and recreate the topic as before and now the connector with 4 tasks:

```shell
kafka-topics --bootstrap-server localhost:9091 --topic customers --create --partitions 4 --replication-factor 1
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/customers-source2/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                : "4",
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

It will take now more or less 10 minutes indicating the parallelization of restore through increased number of tasks.

## TimeBasedPartitioner

Let's reproduce our tests with `TimeBasedPartitioner`.

So first we create a  topic and a new Datagen Source connector:

```shell
kafka-topics --bootstrap-server localhost:9091 --topic customerst --create --partitions 4 --replication-factor 1
curl -i -X PUT -H "Accept:application/json" -H  "Content-Type:application/json" http://localhost:8083/connectors/my-datagen-sourcet/config -d '{
    "name" : "my-datagen-sourcet",
    "connector.class": "io.confluent.kafka.connect.datagen.DatagenConnector",
    "kafka.topic" : "customerst",
    "output.data.format" : "AVRO",
    "quickstart" : "SHOE_CUSTOMERS",
    "tasks.max" : "1"
}'
```

Let it run again for about 10 minutes and pause our connector after.

Now we can create a sink to Azure Blob Storage using a TimeBasedPartitioner:

```shell
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/customerst-sink/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.AzureBlobStorageSinkConnector",
      "topics"                   : "customerst",
      "tasks.max"                : "4",
      "flush.size"               : "1",
      "format.class"             : "io.confluent.connect.azure.blob.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "schema.compatibility"    : "FORWARD",
      "partitioner.class"       : "io.confluent.connect.storage.partitioner.TimeBasedPartitioner",
      "partition.duration.ms"   : "60000",
      "timestamp.extractor"     : "Record",
      "path.format"             : "YYYY/MM/dd/HH",
      "locale"                  : "en-US",
      "timezone"                : "Europe/Madrid",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

This should take about 1 minute to sink all data.

Pause the connector.

Delete the topic customerst and recreate it:

```shell
kafka-topics --bootstrap-server localhost:9091 --delete --topic customerst
```
```shell
kafka-topics --bootstrap-server localhost:9091 --topic customerst --create --partitions 4 --replication-factor 1
```

Let's delete first our other topic folders from Azure Blob Storage: blob_topic and customers. Use the Azure Storage Browser for that.

Let's create now our source connector for restore with a single task:

```shell
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/customerst-source/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                : "1",
      "confluent.topic.replication.factor" : "1",
      "format.class"             : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "mode"                     : "RESTORE_BACKUP",
      "partitioner.class"       : "io.confluent.connect.storage.partitioner.TimeBasedPartitioner",
      "partition.duration.ms"   : "60000",
      "timestamp.extractor"     : "Record",
      "path.format"             : "YYYY/MM/dd/HH",
      "locale"                  : "en-US",
      "timezone"                : "Europe/Madrid",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

You will be able to see that is quite slow and on control center in incoming messages you should see that is doing one partition at a time, starting from 0, then 1, etc. Each partition taking almost 10 minutes to load. So 30-40 minutes overall. We can pause the connector and recreate the topic.

```shell
kafka-topics --bootstrap-server localhost:9091 --delete --topic customerst
```
```shell
kafka-topics --bootstrap-server localhost:9091 --topic customerst --create --partitions 4 --replication-factor 1
```

And now create a new connector as before but with 4 tasks:

```shell
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/customerst-source2/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                : "4",
      "confluent.topic.replication.factor" : "1",
      "format.class"             : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "mode"                     : "RESTORE_BACKUP",
      "partitioner.class"       : "io.confluent.connect.storage.partitioner.TimeBasedPartitioner",
      "partition.duration.ms"   : "60000",
      "timestamp.extractor"     : "Record",
      "path.format"             : "YYYY/MM/dd/HH",
      "locale"                  : "en-US",
      "timezone"                : "Europe/Madrid",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

And we find the behaviour is basically the same. It's not clear what needs to be set to be able to do multitask/parallel restore using Azure Blob Storage connector when leveraging TimeBasedPartitioner. And if that is possible at all.

## Cleanup

```bash
docker compose down -v
```