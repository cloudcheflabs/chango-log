#!/bin/sh

set -ex

export CHANGO_LOG_VERSION=1.1.0


for i in "$@"
do
case $i in
    --version=*)
    CHANGO_LOG_VERSION="${i#*=}"
    shift
    ;;
    *)
          # unknown option
    ;;
esac
done

# build all.
mvn -e clean install;

echo "CHANGO_LOG_VERSION = ${CHANGO_LOG_VERSION}"

export CURRENT_DIR=$(pwd);
export CHANGO_LOG_DIST_NAME=chango-log-${CHANGO_LOG_VERSION}-linux-x64
export CHANGO_LOG_DIST_BASE=${CURRENT_DIR}/dist
export CHANGO_LOG_DIST_DIR=${CHANGO_LOG_DIST_BASE}/${CHANGO_LOG_DIST_NAME}

rm -rf ${CHANGO_LOG_DIST_BASE}/*

mkdir -p ${CHANGO_LOG_DIST_DIR}/{bin,lib,java,conf};

chmod +x *.sh;
cp *-chango-log.sh ${CHANGO_LOG_DIST_DIR}/bin;
cp target/*-shaded.jar ${CHANGO_LOG_DIST_DIR}/lib;
cp src/main/resources/configuration.yml ${CHANGO_LOG_DIST_DIR}/conf;
cp LICENSE ${CHANGO_LOG_DIST_DIR}/

# download jdk.
cd ${CHANGO_LOG_DIST_DIR}/java;
export JAVA_DIST_NAME=openlogic-openjdk-17.0.7+7-linux-x64;
curl -L -O https://github.com/cloudcheflabs/chango-libs/releases/download/chango-private-deps/${JAVA_DIST_NAME}.tar.gz;
tar -zxf ${JAVA_DIST_NAME}.tar.gz;
cp -R ${JAVA_DIST_NAME}/* .;
rm -rf ${JAVA_DIST_NAME}*;

# package as tar.gz.
cd ${CHANGO_LOG_DIST_BASE};
tar -czvf ${CHANGO_LOG_DIST_NAME}.tar.gz ${CHANGO_LOG_DIST_NAME}