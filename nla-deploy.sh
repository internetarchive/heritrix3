#!/bin/bash

mvn package -DskipTests
tar --strip-components=1 -C $1 -zxf contrib/target/heritrix-contrib-3.4.0-SNAPSHOT-dist.tar.gz
