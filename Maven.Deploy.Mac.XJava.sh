#!/bin/sh

mvn deploy:deploy-file -Dfile=hy.common.xjava.jar                              -DpomFile=./src/META-INF/maven/org/hy/common/xjava/pom.xml -DrepositoryId=thirdparty -Durl=http://HY-ZhengWei:1481/repository/thirdparty
mvn deploy:deploy-file -Dfile=hy.common.xjava-sources.jar -Dclassifier=sources -DpomFile=./src/META-INF/maven/org/hy/common/xjava/pom.xml -DrepositoryId=thirdparty -Durl=http://HY-ZhengWei:1481/repository/thirdparty
