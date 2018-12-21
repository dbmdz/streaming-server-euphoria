# Euphoria Streaming Server

[![Javadocs](https://javadoc.io/badge/de.digitalcollections/streaming-server-euphoria.svg)](https://javadoc.io/doc/de.digitalcollections/streaming-server-euphoria)
[![Build Status](https://img.shields.io/travis/dbmdz/streaming-server-euphoria/master.svg)](https://travis-ci.org/dbmdz/streaming-server-euphoria)
[![Codecov](https://img.shields.io/codecov/c/github/dbmdz/streaming-server-euphoria/master.svg)](https://codecov.io/gh/dbmdz/streaming-server-euphoria)
[![License](https://img.shields.io/github/license/dbmdz/streaming-server-euphoria.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/release/dbmdz/streaming-server-euphoria.svg)](https://github.com/dbmdz/streaming-server-euphoria/releases)
[![Maven Central](https://img.shields.io/maven-central/v/de.digitalcollections/streaming-server-euphoria.svg)](https://search.maven.org/search?q=a:streaming-server-euphoria)

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

### Installation

Build the webapp locally and copy it to server:

```shell
$ cd <source_directory_euphoria>
$ mvn clean install
$ scp target/streaming-server-euphoria-2.0.0-SNAPSHOT.jar <user>@<server>:/local/bin
```

### Configuration

Based on unique resource identifiers corresponding to the requested filenames the server tries to resolve identifiers to a "file:" path.
The resolving rules (one rule per line) are configurable with regular expressions in yaml-files, e.g. for production environment see [here](src/main/resources/de/digitalcollections/core/config/multiPatternResolving-PROD.yml).

### Usage

* To run streaming-server-euphoria, e.g.:

```shell
/usr/bin/java -XX:+ExitOnOutOfMemoryError -XX:+CrashOnOutOfMemoryError -XX:MaxDirectMemorySize=8G -Xmx4G -jar /local/bin/streaming-server-euphoria-2.0.0-SNAPSHOT.jar --spring.profiles.active=PROD --server.port=8080 --server.servlet.context-path=/media --management.server.port=9001 --logging.config=/local/config/euphoria/logback-spring.xml
```

Open webapp in browser (use configured 'server.port' and 'server.servlet.context-path'): http://localhost:8080/media

