#!/bin/sh

mvn deploy:deploy-file -Dfile=hy.common.xjava.jar                              -DpomFile=./src/META-INF/maven/org/hy/common/xjava/pom.xml -DrepositoryId=thirdparty -Durl=http://218.21.3.19:1481/nexus/content/repositories/thirdparty
mvn deploy:deploy-file -Dfile=hy.common.xjava-sources.jar -Dclassifier=sources -DpomFile=./src/META-INF/maven/org/hy/common/xjava/pom.xml -DrepositoryId=thirdparty -Durl=http://218.21.3.19:1481/nexus/content/repositories/thirdparty
