# Euphoria Streaming Server

[![Build Status](https://travis-ci.org/dbmdz/streaming-server-euphoria.svg?branch=master)](https://travis-ci.org/dbmdz/streaming-server-euphoria)
[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![codecov](https://codecov.io/gh/dbmdz/streaming-server-euphoria/branch/master/graph/badge.svg)](https://codecov.io/gh/dbmdz/streaming-server-euphoria)

## Supported formats

| Format | tested
| ------ | ------
| MP4    | yes
| MP3    | yes
| OGG    | yes
| PDF    | yes
| TXT    | yes
| XML    | yes


## Requirements

* Java 8: You will need the Java Runtime Environment (JRE) version 1.8 or higher. At a command line, check your Java version with "java -version".
* Apache Tomcat 8.0

## Apache Tomcat

* Homepage: http://tomcat.apache.org/
* Version: 8.0.35

### Installation

Download Tomcat 8 and decompress it to target directory.

```shell
$ cd /opt
$ sudo wget http://mirror.synyx.de/apache/tomcat/tomcat-8/v8.0.35/bin/apache-tomcat-8.0.35.tar.gz
$ sudo tar -xvfz apache-tomcat-8.0.35.tar.gz
```

### Usage

Start Tomcat:

```shell
$ cd /opt/tomcat/apache-tomcat-8.0.35
$ sudo bin/startup.sh
```

Stop Tomcat:
```shell
$ cd /opt/tomcat/apache-tomcat-8.0.35
$ sudo bin/shutdown.sh
```

## Euphoria webapp

### Installation

Build the webapp locally and copy it to server:

```shell
$ cd <source_directory_euphoria>
$ mvn clean install
$ scp target/streaming-server-euphoria.war <user>@<server>:/tmp
```

Deploy Bookshelf WAR into Tomcat:

```shell
$ mv /tmp/streaming-server-euphoria.war /opt/tomcat/apache-tomcat-8.0.35/webapps
```

### Configuration

Based on unique resource identifiers corresponding to the requested filenames the server tries to resolve identifiers to a "file:" path.
The resolving rules (one rule per line) are configurable with regular expressions in yaml-files, e.g. for production environment see [here](src/main/resources/de/digitalcollections/core/config/multiPatternResolving-PROD.yml).

### Usage

* To run streaming-server-euphoria run Tomcat.

```shell
# /opt/apache-tomcat-8.0.35/bin/startup.sh
```

Open webapp in browser: http://localhost:10000/

* To stop streaming-server-euphoria stop the Tomcat:

```shell
# /opt/apache-tomcat-8.0.35/bin/shutdown.sh
```
