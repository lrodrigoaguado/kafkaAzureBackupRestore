#  Backup and Restore Azure Blob Storage Source Connector for Confluent Platform 

- [Backup and Restore Azure Blob Storage Source Connector for Confluent Platform](#backup-and-restore-azure-blob-storage-source-connector-for-confluent-platform)
  - [Disclaimer](#disclaimer)
  - [References](#references)
  - [Setup Azure](#setup-azure)
    - [Step 1: Sign in to Azure Portal](#step-1-sign-in-to-azure-portal)
    - [Step 2: Create a Storage Account (if you don’t already have one)](#step-2-create-a-storage-account-if-you-dont-already-have-one)
    - [Step 3: Create a Container](#step-3-create-a-container)
    - [Step 4: Get Storage Account Key](#step-4-get-storage-account-key)
  - [Setup Connect](#setup-connect)
  - [Sink](#sink)
  - [Source](#source)
  - [Test Source Parallelization](#test-source-parallelization)
    - [Data Generation](#data-generation)
    - [Sink](#sink-1)
    - [Source](#source-1)
  - [TimeBasedPartitioner](#timebasedpartitioner)
  - [Parallel Recovery with Default Partitioner and Between Dates](#parallel-recovery-with-default-partitioner-and-between-dates)
    - [Avoiding having to validate too many files from Azure](#avoiding-having-to-validate-too-many-files-from-azure)
  - [Cleanup](#cleanup)

## Disclaimer

The code and/or instructions here available are **NOT** intended for production usage. 
It's only meant to serve as an example or reference and does not replace the need to follow actual and official documentation of referenced products.

## References

https://docs.confluent.io/kafka-connectors/azure-blob-storage-sink/current/overview.html#quick-start

https://docs.confluent.io/kafka-connectors/azure-blob-storage-source/current/backup-and-restore/overview.html

## Setup Azure

You will need:
- `YOUR_ACCOUNT_NAME`: Name of your storage account
- `YOUR_ACCOUNT_KEY`: Access key of your storage account
- `YOUR_CONTAINER_NAME`: Name of the container you created

### Step 1: Sign in to Azure Portal
Go to https://portal.azure.com and log in with your Azure credentials.

### Step 2: Create a Storage Account (if you don’t already have one)

1. Search for Storage accounts in the search bar and select it.
2. Click + Create.
3. Choose:
- Subscription
- Resource group (create one if needed)
- Storage account name (this will be your `YOUR_ACCOUNT_NAME`)
- Region, Performance, and Redundancy options
4. Click Review + Create, then Create

Once deployed, go to the Storage account you just created.

### Step 3: Create a Container
1. In your storage account's left-side menu, select Containers under Data storage.
2. Click + Container
3. Enter a Name (e.g., mycontainer) → this is `YOUR_CONTAINER_NAME`
4. Choose Public access level (usually Private unless you need public access).
5. Click Create

### Step 4: Get Storage Account Key
1. In the storage account's menu, click Access keys (under Security + networking)
2. You’ll see key1 and key2. Click Show keys to view the values.
3. Copy the Key value (either key1 or key2) — this is your `YOUR_ACCOUNT_KEY`.

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

## Parallel Recovery with Default Partitioner and Between Dates

First make sure to cleanup your Blob Storage container. And remove connectors. Keep the `customers` topic with 4 partitions populated. Or populate again as before.

Next run the sink connector but with an SMT to include message timestamp:

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
      "transforms": "addTS",
      "transforms.addTS.type": "org.apache.kafka.connect.transforms.InsertField$Value",
      "transforms.addTS.timestamp.field": "event_timestamp",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

**Important Notice:** If you already have a field with a timestamp more relevant than the event timestamp it wouldnt be required to include the event timestamp and you could leverage that field directly.

Once everything has been uploaded you probably will have a long interval to pick a couple of minutes in between. In our case we have from more or less:
- **1747400925841**: 20250516130845 (in `yyyyMMddHHmmss` format)
- **1747401300569**: 20250516131500 (in `yyyyMMddHHmmss` format)

First let's built our custom SMT library:

```shell
mvn clean install
cp target/timestampBetween-1.0-SNAPSHOT-jar-with-dependencies.jar plugins
```

Now we can drop our environment and restart:

```shell
docker compose down -v
docker compose up -d
```

And after confirm transformer is available to be used by executing:

```shell
docker compose logs kafka-connect-1 | grep FilterByFieldTimestamp
```

Now if we create the topic:

```shell
kafka-topics --bootstrap-server localhost:9091 --topic customers --create --partitions 4 --replication-factor 1
```

And after the source connector using our custom SMT with a specific date time interval:

```shell
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
      "transforms": "filterByTime",
      "transforms.filterByTime.type": "io.confluent.csta.timestamp.transforms.FilterByFieldTimestamp",
      "transforms.filterByTime.timestamp.field": "event_timestamp",
      "transforms.filterByTime.start.datetime": "20250516131200",
      "transforms.filterByTime.end.datetime": "20250516131400",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

You will see only some of the messages coming in.

If you want to guarantee the event time field is not restored you can do:

```shell
kafka-topics --bootstrap-server localhost:9091 --delete --topic customers
kafka-topics --bootstrap-server localhost:9091 --topic customers --create --partitions 4 --replication-factor 1
curl -i -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/customers-source3/config \
  -d '{
      "connector.class"          : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                : "4",
      "confluent.topic.replication.factor" : "1",
      "format.class"             : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "mode"                     : "RESTORE_BACKUP",
      "partitioner.class"       : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "transforms": "filterByTime,dropField",
      "transforms.filterByTime.type": "io.confluent.csta.timestamp.transforms.FilterByFieldTimestamp",
      "transforms.filterByTime.timestamp.field": "event_timestamp",
      "transforms.filterByTime.start.datetime": "20250516131200",
      "transforms.filterByTime.end.datetime": "20250516131400",
      "transforms.dropField.type": "org.apache.kafka.connect.transforms.ReplaceField$Value",
      "transforms.dropField.blacklist": "event_timestamp",
      "azblob.account.name"     : "YOUR_ACCOUNT_NAME",
      "azblob.account.key"      : "YOUR_ACCOUNT_KEY",
      "azblob.container.name"   : "YOUR_CONTAINER"
      }'
```

This custom SMT is only meant to be an example of what you can do.

### Avoiding having to validate too many files from Azure

Besides having proper retention periods in the Azure Blob Storage you can also leverage copying only the possibly relevant files from one container to another as a previous step for the recovery. This way the overall event set will be much more limmited and the performance impact of the validation will be restricted just to double check that events really sit on your desired (sub)interval. In some scenarios this step alone may be even enough with no need to use the custom SMT.

References:
- https://learn.microsoft.com/en-us/cli/azure/storage/blob?view=azure-cli-latest#az-storage-blob-list (`lastModified` can potentially be used under query for filtering the blobs you are interested into copying - check next references)
- https://learn.microsoft.com/en-us/dotnet/api/azure.storage.blobs.models.blobitemproperties.lastmodified?view=azure-dotnet#azure-storage-blobs-models-blobitemproperties-lastmodified
- https://learn.microsoft.com/en-us/cli/azure/storage/blob/copy?view=azure-cli-latest#az-storage-blob-copy-start

All of this can also be done programatically through scripting or leveraging Azure language SDKs. Check this python example: https://learn.microsoft.com/en-us/azure/developer/python/sdk/azure-sdk-overview 

## Cleanup

```bash
docker compose down -v
```