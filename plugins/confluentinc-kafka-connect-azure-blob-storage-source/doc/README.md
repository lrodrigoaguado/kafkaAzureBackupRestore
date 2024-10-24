# Kafka Cloud Storage Source Connectors

A multi Maven Module [Kafka Connector](http://kafka.apache.org/documentation.html#connect) project that targets specifically Cloud Storage services.

# Development

To build a development version you'll need a recent version of Kafka. You can build
*kafka-connect-cloud-storage-source-parent* with Maven using the standard lifecycle phases.

# Modules

* [cloud-storage-source-common](cloud-storage-source-common): Responsible for the shared code between all Cloud Storage [Kafka Connectors](http://kafka.apache.org/documentation.html#connect). Changes which impact all Cloud Storage Connectors should be made here to propagate down to the other connectors.
* [kafka-connect-s3-source](kafka-connect-s3-source): The connector responsible for reading data from S3 storage to Kafka topics.

# Documentation

Documentation on the connector is hosted on Confluent's
[docs site](https://docs.confluent.io/current/connect/kafka-connect-s3-source/).

Source code is located in Confluent's
[docs repo](https://github.com/confluentinc/docs/tree/master/connect/kafka-connect-s3-source). If changes
are made to configuration options for the connector, be sure to generate the RST docs (as described
below) and open a PR against the docs repo to publish those changes!

# Contribute

- Source Code: https://github.com/confluentinc/kafka-connect-s3-source
- Issue Tracker: https://github.com/confluentinc/kafka-connect-s3-source/issues
