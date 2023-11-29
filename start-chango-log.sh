
set -x

export CHANGO_LOG_HOME=$(pwd);

nohup ${CHANGO_LOG_HOME}/java/bin/java \
-cp ${CHANGO_LOG_HOME}/lib/*.jar \
-Xmx4G \
-Dsun.misc.URLClassPath.disableJarChecking=true \
--add-opens jdk.naming.rmi/com.sun.jndi.rmi.registry=ALL-UNNAMED \
--add-opens java.base/java.util=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/sun.security.action=ALL-UNNAMED \
--add-opens java.base/sun.net=ALL-UNNAMED \
co.cloudcheflabs.chango.log.Chango ${CHANGO_LOG_HOME}/conf/configuration.yml > /dev/null 2>&1 & echo $! > pid;

