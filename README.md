# Backup and Restore Azure Blob Storage Source Connector for Confluent Platform

- [Backup and Restore Azure Blob Storage Source Connector for Confluent Platform](#backup-and-restore-azure-blob-storage-source-connector-for-confluent-platform)
  - [Objectives](#objectives)
  - [Disclaimer](#disclaimer)
  - [Limitations](#limitations)
  - [References](#references)
- [Initial Setup](#initial-setup)
  - [Prerequisites](#prerequisites)
  - [Setup Azure](#setup-azure)
    - [Step 1: Sign in to Azure Portal](#step-1-sign-in-to-azure-portal)
    - [Step 2: Create a Storage Account (if you don’t already have one)](#step-2-create-a-storage-account-if-you-dont-already-have-one)
    - [Step 3: Create the Containers](#step-3-create-the-containers)
    - [Step 4: Get Storage Account Key](#step-4-get-storage-account-key)
    - [Step 5: Configure environment variables](#step-5-configure-environment-variables)
  - [Setup Connect](#setup-connect)
  - [Setup Topics](#setup-topics)
- [Testing the options](#testing-the-options)
  - [First step: Data Generation](#first-step-data-generation)
  - [Backup/Restore option 1: Testing basic parallelism](#backuprestore-option-1-testing-basic-parallelism)
  - [Backup/Restore option 2: TimeBasedPartitioner](#backuprestore-option-2-timebasedpartitioner)
  - [Backup/Restore option 3: Parallel Recovery with Field Partitioner](#backuprestore-option-3-parallel-recovery-with-field-partitioner)
  - [Backup/Restore option 4: Parallel Recovery with Default Partitioner and Between Dates](#backuprestore-option-4-parallel-recovery-with-default-partitioner-and-between-dates)
    - [Avoiding having to validate too many files from Azure](#avoiding-having-to-validate-too-many-files-from-azure)
  - [Summary](#summary)
- [Cleanup](#cleanup)

## Objectives

The objective of this repo is to test the different capabilities of the existing Partitioners for using Azure Blob Storage as a way to backup and later recover the data in a Kafka topic. For that, we will use Azure Blob Storage Sink and Source Connectors. Ideally, the recovery process should allow the recovery of data belonging to certain dates only.

## Disclaimer

The code and/or instructions here available are **NOT** intended for production usage.
It's only meant to serve as an example or reference and does not replace the need to follow actual and official documentation of referenced products.

## Limitations

A thorough review of the documentation reveals the following limitations:
- The Sink connector currently does not write keys or headers to storage, it only stores the messages values.
- The Sink connector does not currently support Single Message Transformations (SMTs) that modify the topic name, neither any of these:
  - `io.debezium.transforms.ByLogicalTableRouter`
  - `io.debezium.transforms.outbox.EventRouter`
  - `org.apache.kafka.connect.transforms.RegexRouter`
  - `org.apache.kafka.connect.transforms.TimestampRouter`
  - `io.confluent.connect.transforms.MessageTimestampRouter`
  - `io.confluent.connect.transforms.ExtractTopic$Key`
  - `io.confluent.connect.transforms.ExtractTopic$Value`

- In the Source connector, for the TimeBasedPartitioner, the capacity to scale the connector across various time ranges is limited in Backup and Restore mode. Currently, the connector does not support processing data that spans several years.
- In the Source connector, if a FieldPartitioner is used, it isn’t possible to guarantee the order of the messages.

## References

https://docs.confluent.io/kafka-connectors/azure-blob-storage-sink/current/overview.html#quick-start

https://docs.confluent.io/kafka-connectors/azure-blob-storage-source/current/backup-and-restore/overview.html

---

# Initial Setup

## Prerequisites

You will need a working Azure account and a local environment with docker, java and maven to execute the demo.

## Setup Azure

For this demo you will need to create four different Blob containers in an Storage Account of Azure. You can follow these steps:

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

### Step 3: Create the Containers

1. In your storage account's left-side menu, select Containers under Data storage.
2. Click + Container
3. Repeat four times:
   1. Enter a Name (e.g., mycontainer) → this is `TESTX_CONTAINER_NAME`
   2. Choose Public access level (usually Private unless you need public access).
   3. Click Create

### Step 4: Get Storage Account Key

1. In the storage account's menu, click Access keys (under Security + networking)
2. You’ll see key1 and key2. Click Show keys to view the values.
3. Copy the Key value (either key1 or key2) — this is your `YOUR_ACCOUNT_KEY`.

### Step 5: Configure environment variables

Once you have created the containers, you need to configure the Azure access data and the continer names as environment variables, as:

- `YOUR_ACCOUNT_NAME`: Name of your storage account
- `YOUR_ACCOUNT_KEY`: Access key of your storage account
- `TEST1_CONTAINER_NAME`: Name of the container you will create for test 1
- `TEST2_CONTAINER_NAME`: Name of the container you will create for test 2
- `TEST3_CONTAINER_NAME`: Name of the container you will create for test 3
- `TEST4_CONTAINER_NAME`: Name of the container you will create for test 4

You can manually create the environment variables with that names and the appropriate values for your Azure account or, if you are going to run the demo several times, populate the file "setup_env.sh" with the values and then run:

```bash
source ./setup_env.sh
```

## Setup Connect

Start containers.

```bash
docker compose up -d
```

Then execute the following commands to install the Datagen and Azure Blob Storage Source and Sink connectors:

```bash
docker compose exec kafka-connect-1 confluent-hub install --no-prompt confluentinc/kafka-connect-azure-blob-storage:latest
docker compose exec kafka-connect-1 confluent-hub install --no-prompt confluentinc/kafka-connect-azure-blob-storage-source:latest
docker compose exec kafka-connect-1 confluent-hub install --no-prompt confluentinc/kafka-connect-datagen:latest
```

Now build a custom SMT that will be used in one of the tests:

```bash
mvn clean install
cp target/timestampBetween-1.0-SNAPSHOT-jar-with-dependencies.jar plugins/
```

And restart connect:

```bash
docker compose restart kafka-connect-1
```

## Setup Topics

Create all the topics that will be used along the demo:

```bash
kafka-topics --bootstrap-server localhost:9091 --topic customer-data --create --partitions 4 --replication-factor 1
kafka-topics --bootstrap-server localhost:9091 --topic default-partitioner-source-1task-copy-of-customer-data --create --partitions 4 --replication-factor 1
kafka-topics --bootstrap-server localhost:9091 --topic default-partitioner-source-4tasks-copy-of-customer-data --create --partitions 4 --replication-factor 1
kafka-topics --bootstrap-server localhost:9091 --topic timebased-partitioner-copy-of-customer-data --create --partitions 4 --replication-factor 1
kafka-topics --bootstrap-server localhost:9091 --topic fieldbased-partitioner-copy-of-customer-data --create --partitions 4 --replication-factor 1
kafka-topics --bootstrap-server localhost:9091 --topic default-partitioner-withSMT-copy-of-customer-data --create --partitions 4 --replication-factor 1
```

---

# Testing the options

## First step: Data Generation

First, let's create our data generation connector to populate the demo topic:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" -H  "Content-Type:application/json" http://localhost:8083/connectors/my-datagen-source/config -d '{
    "name" : "my-datagen-source",
    "connector.class": "io.confluent.kafka.connect.datagen.DatagenConnector",
    "kafka.topic" : "customer-data",
    "output.data.format" : "AVRO",
    "quickstart" : "SHOE_CUSTOMERS",
    "tasks.max" : "1"
}'
```

Let it be running for a while (like 10 minutes). And after pause the connector, either via web, or running:

```bash
curl -i -X PUT http://localhost:8083/connectors/my-datagen-source/pause
```

## Backup/Restore option 1: Testing basic parallelism

Now let's create our sink.

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/default-partitioner-sink/config \
  -d "$(cat <<EOF | envsubst  '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST1_CONTAINER_NAME}'
{
      "connector.class"                  : "io.confluent.connect.azure.blob.AzureBlobStorageSinkConnector",
      "topics"                           : "customer-data",
      "tasks.max"                        : "4",
      "flush.size"                       : "1",
      "format.class"                     : "io.confluent.connect.azure.blob.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "schema.compatibility"             : "FORWARD",
      "partitioner.class"                : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "azblob.account.name"              : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"               : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"            : "\${TEST1_CONTAINER_NAME}"
}
EOF
)"

```

If successful, you will see that the curl command returns a "201 Created" status, together with the configuration of the sink connector just created.

Let the sink connector do its job, and once it is completed, you can pause it either via web, or running:

```bash
curl -i -X PUT http://localhost:8083/connectors/default-partitioner-sink/pause
```

Let's execute now the source with just one task first:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/default-partitioner-source-1task/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST1_CONTAINER_NAME}'
{
      "connector.class"                    : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                          : "1",
      "confluent.topic.replication.factor" : "1",
      "format.class"                       : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers"  : "broker:19091",
      "mode"                               : "RESTORE_BACKUP",
      "partitioner.class"                  : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "transforms"                         : "AddPrefix",
      "transforms.AddPrefix.type"          : "org.apache.kafka.connect.transforms.RegexRouter",
      "transforms.AddPrefix.regex"         : ".*",
      "transforms.AddPrefix.replacement"   : "default-partitioner-source-1task-copy-of-\$0",
      "azblob.account.name"                : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"                 : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"              : "\${TEST1_CONTAINER_NAME}"
}
EOF
)"
```

It can take around 25 minutes to load the whole data, depending on how much you let the datagen connector run.

Note that an SMT has been added here and in all the following source connectors for convenience. The "AddPrefix" transform modifies the name of the topic where the information will be restored, so that no loops exist if both the sink and source connectors are running simultaneously (the sink would be sending to Azure the information just restored by the source, generating duplicated messages).

If we create now the connector with 4 tasks:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/default-partitioner-source-4tasks/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST1_CONTAINER_NAME}'
{
      "connector.class"                    : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                          : "4",
      "confluent.topic.replication.factor" : "1",
      "format.class"                       : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers"  : "broker:19091",
      "mode"                               : "RESTORE_BACKUP",
      "partitioner.class"                  : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "transforms"                         : "AddPrefix",
      "transforms.AddPrefix.type"          : "org.apache.kafka.connect.transforms.RegexRouter",
      "transforms.AddPrefix.regex"         : ".*",
      "transforms.AddPrefix.replacement"   : "default-partitioner-source-4tasks-copy-of-\$0",
      "azblob.account.name"                : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"                 : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"              : "\${TEST1_CONTAINER_NAME}"
}
EOF
)"
```

It will take now more or less 10 minutes indicating the parallelization of restore through increased number of tasks.

The number of active tasks can also be checked in the Concluent Control Center.

## Backup/Restore option 2: TimeBasedPartitioner

Let's reproduce our tests with `TimeBasedPartitioner`.

Create a sink to Azure Blob Storage using a TimeBasedPartitioner:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/timebased-partitioner-sink/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST2_CONTAINER_NAME}'
{
      "connector.class"                  : "io.confluent.connect.azure.blob.AzureBlobStorageSinkConnector",
      "topics"                           : "customer-data",
      "tasks.max"                        : "4",
      "flush.size"                       : "1",
      "format.class"                     : "io.confluent.connect.azure.blob.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "schema.compatibility"             : "FORWARD",
      "partitioner.class"                : "io.confluent.connect.storage.partitioner.TimeBasedPartitioner",
      "partition.duration.ms"            : "60000",
      "timestamp.extractor"              : "Record",
      "path.format"                      : "YYYY/MM/dd/HH/mm",
      "locale"                           : "en-US",
      "timezone"                         : "Europe/Madrid",
      "azblob.account.name"              : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"               : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"            : "\${TEST2_CONTAINER_NAME}"
}
EOF
)"
```

This should take about 1 minute to sink all data.

Pause the sink connector using the following command:

```bash
curl -i -X PUT http://localhost:8083/connectors/timebased-partitioner-sink/pause
```

Let's create now our source connector for restore with a single task:

```bash
curl -s -D - -o /dev/null \
 -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/timebased-partitioner-source/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST2_CONTAINER_NAME}'
{
      "connector.class"                    : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                          : "4",
      "confluent.topic.replication.factor" : "1",
      "format.class"                       : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers"  : "broker:19091",
      "mode"                               : "RESTORE_BACKUP",
      "partitioner.class"                  : "io.confluent.connect.storage.partitioner.TimeBasedPartitioner",
      "partition.duration.ms"              : "60000",
      "timestamp.extractor"                : "Record",
      "path.format"                        : "YYYY/MM/dd/HH/mm",
      "locale"                             : "en-US",
      "timezone"                           : "Europe/Madrid",
      "transforms"                         : "AddPrefix",
      "transforms.AddPrefix.type"          : "org.apache.kafka.connect.transforms.RegexRouter",
      "transforms.AddPrefix.regex"         : ".*",
      "transforms.AddPrefix.replacement"   : "timebased-partitioner-copy-of-\$0",
      "azblob.account.name"                : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"                 : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"              : "\${TEST2_CONTAINER_NAME}"
}
EOF
)"
```

You will be able to see that is quite slow and on control center in incoming messages you should see that is doing one partition at a time, starting from 0, then 1, etc. even though we configured 4 tasks, the connector is creating only one, and processing the different partitions sequentially. Each partition taking almost 10 minutes to load. So 30-40 minutes overall. We can pause the connector when finished.

```bash
curl -i -X PUT http://localhost:8083/connectors/timebased-partitioner-source/pause
```

## Backup/Restore option 3: Parallel Recovery with Field Partitioner

Next, run the sink connector. In this case, the sink creates a new field called "formattedTS" that will be used for partitioning:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/field-partitioner-sink/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST3_CONTAINER_NAME}'
{
      "connector.class"                    : "io.confluent.connect.azure.blob.AzureBlobStorageSinkConnector",
      "topics"                             : "customer-data",
      "tasks.max"                          : "4",
      "flush.size"                         : "1",
      "format.class"                       : "io.confluent.connect.azure.blob.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers"  : "broker:19091",
      "schema.compatibility"               : "FORWARD",
      "partitioner.class"                  : "io.confluent.connect.storage.partitioner.FieldPartitioner",
      "partition.field.name"               : "formattedTS",
      "timestamp.extractor"                : "Record",
      "transforms"                         : "insertTS, formatTS",
      "transforms.insertTS.type"           : "org.apache.kafka.connect.transforms.InsertField\$Value",
      "transforms.insertTS.timestamp.field": "formattedTS",
      "transforms.formatTS.type"           : "org.apache.kafka.connect.transforms.TimestampConverter\$Value",
      "transforms.formatTS.target.type"    : "string",
      "transforms.formatTS.field"          : "formattedTS",
      "transforms.formatTS.format"         : "yyyyMMddHHmm",
      "azblob.account.name"                : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"                 : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"              : "\${TEST3_CONTAINER_NAME}"
}
EOF
)"
```

When the Sink connector runs, it will create data in the Azure container, in subfolders named "*formattedTS=yyyyMMddHHmm*". The granularity of the information in the folders can be adjusted by modifying the "transforms.formatTS.format" attribute value. The current configuration will create subfolders for each minute, but folders can be generated (for example) hourly or daily, using "*yyyyMMddHH*" or "*yyyyMMdd*", respectively.

After the information has been copied to the Blob Storage in Azure, we can launch the Source connector with this command:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/field-partitioner-source/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST3_CONTAINER_NAME}'
{
      "connector.class"                        : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                              : "4",
      "topics"                                 : "customer-data",
      "confluent.topic.replication.factor"     : "1",
      "format.class"                           : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers"      : "broker:19091",
      "mode"                                   : "RESTORE_BACKUP",
      "partitioner.class"                      : "io.confluent.connect.storage.partitioner.FieldPartitioner",
      "partition.field.name"                   : "formattedTS",
      "timestamp.extractor"                    : "Record",
      "transforms"                             : "AddPrefix,removeTSField",
      "transforms.AddPrefix.type"              : "org.apache.kafka.connect.transforms.RegexRouter",
      "transforms.AddPrefix.regex"             : ".*",
      "transforms.AddPrefix.replacement"       : "fieldbased-partitioner-copy-of-\$0",
      "transforms.removeTSField.type"          : "org.apache.kafka.connect.transforms.ReplaceField\$Value",
      "transforms.removeTSField.blacklist"     : "formattedTS",
      "azblob.account.name"                    : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"                     : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"                  : "\${TEST3_CONTAINER_NAME}"
}
EOF
)"
```

This will restore the contents of the Blob Storage in the Kafka cluster. A second SMT has been added to this example, that removes from the messages the field we created specifically for the partitioning purposes ("formattedTS"), so that the messages are restored exactly as they were originally.

However, regarding the recovery of data, it is important to note that, as per the documentation (https://docs.confluent.io/kafka-connectors/azure-blob-storage-source/current/backup-and-restore/overview.html#features):

> *If a FieldPartitioner is used, it isn’t possible to guarantee the order of these messages*

So the order of the messages in the restored topic could not be exactly the same as the order in the original topic.

## Backup/Restore option 4: Parallel Recovery with Default Partitioner and Between Dates

Create the following sink connector with an SMT to include message timestamp:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/default-partitioner-sink-customSMT/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST4_CONTAINER_NAME}'
{
      "connector.class"                  : "io.confluent.connect.azure.blob.AzureBlobStorageSinkConnector",
      "topics"                           : "customer-data",
      "tasks.max"                        : "4",
      "flush.size"                       : "1",
      "format.class"                     : "io.confluent.connect.azure.blob.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers": "broker:19091",
      "schema.compatibility"             : "FORWARD",
      "partitioner.class"                : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "transforms"                       : "addTS",
      "transforms.addTS.type"            : "org.apache.kafka.connect.transforms.InsertField\$Value",
      "transforms.addTS.timestamp.field" : "event_timestamp",
      "azblob.account.name"              : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"               : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"            : "\${TEST4_CONTAINER_NAME}"
}
EOF
)"
```

**Important Notice:** If you already have a field with a timestamp more relevant than the event timestamp it wouldn't be required to include the event timestamp and you could leverage that field directly.

Once everything has been uploaded you probably will have a long interval to pick a couple of minutes in between. In our case we have from more or less:

- **1747400925841**: 20250516130845 (in `yyyyMMddHHmmss` format)
- **1747401300569**: 20250516131500 (in `yyyyMMddHHmmss` format)

And after the source connector using our custom SMT with a specific date time interval:

```bash
curl -s -D - -o /dev/null -X PUT -H "Accept:application/json" \
  -H  "Content-Type:application/json" http://localhost:8083/connectors/default-partitioner-source-customSMT/config \
  -d "$(cat <<EOF | envsubst '${YOUR_ACCOUNT_NAME} ${YOUR_ACCOUNT_KEY} ${TEST4_CONTAINER_NAME}'
{
      "connector.class"                        : "io.confluent.connect.azure.blob.storage.AzureBlobStorageSourceConnector",
      "tasks.max"                              : "4",
      "confluent.topic.replication.factor"     : "1",
      "format.class"                           : "io.confluent.connect.azure.blob.storage.format.avro.AvroFormat",
      "confluent.topic.bootstrap.servers"      : "broker:19091",
      "mode"                                   : "RESTORE_BACKUP",
      "partitioner.class"                      : "io.confluent.connect.storage.partitioner.DefaultPartitioner",
      "transforms"                             : "AddPrefix,filterByTime,dropField",
      "transforms.AddPrefix.type"              : "org.apache.kafka.connect.transforms.RegexRouter",
      "transforms.AddPrefix.regex"             : ".*",
      "transforms.AddPrefix.replacement"       : "default-partitioner-withSMT-copy-of-\$0",
      "transforms.filterByTime.type"           : "io.confluent.csta.timestamp.transforms.FilterByFieldTimestamp",
      "transforms.filterByTime.timestamp.field": "event_timestamp",
      "transforms.filterByTime.start.datetime" : "20240516131200",
      "transforms.filterByTime.end.datetime"   : "20250716131400",
      "transforms.dropField.type"              : "org.apache.kafka.connect.transforms.ReplaceField\$Value",
      "transforms.dropField.blacklist"         : "event_timestamp",
      "azblob.account.name"                    : "\${YOUR_ACCOUNT_NAME}",
      "azblob.account.key"                     : "\${YOUR_ACCOUNT_KEY}",
      "azblob.container.name"                  : "\${TEST4_CONTAINER_NAME}"
}
EOF
)"
```

You will see only some of the messages coming in, the rest are being filtered by the custom SMT that we put in place.

The "dropField" transform included makes sure the generated field is not restored with the original message. If you want to keep this field, you can just remove the transform.

This custom SMT is only meant to be an example of what you can do.

---

### Avoiding having to validate too many files from Azure

Besides having proper retention periods in the Azure Blob Storage you can also leverage copying only the possibly relevant files from one container to another as a previous step for the recovery. This way the overall event set will be much more limmited and the performance impact of the validation will be restricted just to double check that events really sit on your desired (sub)interval. In some scenarios this step alone may be even enough with no need to use the custom SMT.

References:

- https://learn.microsoft.com/en-us/cli/azure/storage/blob?view=azure-cli-latest#az-storage-blob-list (`lastModified` can potentially be used under query for filtering the blobs you are interested into copying - check next references)
- https://learn.microsoft.com/en-us/dotnet/api/azure.storage.blobs.models.blobitemproperties.lastmodified?view=azure-dotnet#azure-storage-blobs-models-blobitemproperties-lastmodified
- https://learn.microsoft.com/en-us/cli/azure/storage/blob/copy?view=azure-cli-latest#az-storage-blob-copy-start

All of this can also be done programatically through scripting or leveraging Azure language SDKs. (example: Python sdk https://learn.microsoft.com/en-us/azure/developer/python/sdk/azure-sdk-overview)

---

## Summary
The four alternatives shown present pros and cons and the specific use case should guide on which option to use.

The following table summarizes the main characteristics:

| Partitioner                     | Parallelism                          | Ordering                          | Time based backup filtering (on restore)                                      |
| ------------------------------- | ------------------------------------ | --------------------------------  | ----------------------------------------------------------------------------- |
| DefaultPartitioner              | $${\color{lightgreen}&#x2714;}$$     | $${\color{lightgreen}&#x2714;}$$  | $${\color{red}&#x2718;}$$ (only possible relying on Azure last modified time) |
| TimeBasedPartitioner            | $${\color{red}&#x2718;}$$            | $${\color{lightgreen}&#x2714;}$$  | $${\color{lightgreen}&#x2714;}$$  (based on folder names in storage)          |
| FieldPartitioner                | $${\color{lightgreen}&#x2714;}$$     | $${\color{red}&#x2718;}$$         | $${\color{lightgreen}&#x2714;}$$  (based on folder names in storage)          |
| DefaultPartitioner + custom SMT | $${\color{lightgreen}&#x2714;}$$     | $${\color{lightgreen}&#x2714;}$$  | $${\color{lightgreen}&#x2714;}$$  (based on custom SMT)                       |

---
# Cleanup

```bash
docker compose down -v
rm -rf plugins
rm -rf target
```
