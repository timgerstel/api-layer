#!/bin/sh

################################################################################
# This program and the accompanying materials are made available under the terms of the
# Eclipse Public License v2.0 which accompanies this distribution, and is available at
# https://www.eclipse.org/legal/epl-v20.html
#
# SPDX-License-Identifier: EPL-2.0
#
# Copyright IBM Corporation 2019
################################################################################

# Variables required on shell:
# - ZOWE_PREFIX
# - DISCOVERY_PORT - the port the discovery service will use
# - CATALOG_PORT - the port the api catalog service will use
# - GATEWAY_PORT - the port the api gateway service will use
# - VERIFY_CERTIFICATES - boolean saying if we accept only verified certificates
# - DISCOVERY_PORT - The port the data sets server will use
# - KEY_ALIAS
# - KEYSTORE - The keystore to use for SSL certificates
# - KEYSTORE_TYPE - The keystore type to use for SSL certificates
# - KEYSTORE_PASSWORD - The password to access the keystore supplied by KEYSTORE
# - KEY_ALIAS - The alias of the key within the keystore
# - ALLOW_SLASHES - Allows encoded slashes on on URLs through gateway
# - ZOWE_MANIFEST - The full path to Zowe's manifest.json file

# API Mediation Layer Debug Mode
# To activate `debug` mode, set LOG_LEVEL=debug (in lowercase)
LOG_LEVEL=diag

# If set append $ZWEAD_EXTERNAL_STATIC_DEF_DIRECTORIES to $STATIC_DEF_CONFIG_DIR
APIML_STATIC_DEF=${STATIC_DEF_CONFIG_DIR}
if [[ ! -z "$ZWEAD_EXTERNAL_STATIC_DEF_DIRECTORIES" ]]
then
  APIML_STATIC_DEF="${APIML_STATIC_DEF};${ZWEAD_EXTERNAL_STATIC_DEF_DIRECTORIES}"
fi
echo "active profile" $LOG_LEVEL
echo "starting " ${DISCOVERY_CODE}

_BPX_JOBNAME=${ZOWE_PREFIX}${DISCOVERY_CODE} java -Xms32m -Xmx256m -Xquickstart \
    -Dibm.serversocket.recover=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp \
    -Dspring.profiles.active=https \
    -Dspring.profiles.include=$LOG_LEVEL \
    -Dserver.address=${ZOWE_IP_ADDRESS} \
    -Dapiml.discovery.userid=eureka \
    -Dapiml.discovery.password=password \
    -Dapiml.discovery.allPeersUrls=https://${ZOWE_EXPLORER_HOST}:${DISCOVERY_PORT}/eureka/ \
    -Dapiml.service.hostname=${ZOWE_EXPLORER_HOST} \
    -Dapiml.service.port=${DISCOVERY_PORT} \
    -Dapiml.service.ipAddress=${ZOWE_IP_ADDRESS} \
    -Dapiml.service.preferIpAddress=false \
    -Dapiml.discovery.staticApiDefinitionsDirectories=${APIML_STATIC_DEF} \
    -Dapiml.security.ssl.verifySslCertificatesOfServices=${VERIFY_CERTIFICATES} \
    -Dapiml.security.zosmf.useJwtToken=true \
    -Dserver.ssl.enabled=true \
    -Dserver.ssl.keyStore=${KEYSTORE} \
    -Dserver.ssl.keyStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.keyStorePassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.keyAlias=${KEY_ALIAS} \
    -Dserver.ssl.keyPassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.trustStore=${TRUSTSTORE} \
    -Dserver.ssl.trustStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.trustStorePassword=${KEYSTORE_PASSWORD} \
    -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
    -Dapiml.service.preferIpAddress=false \
    -jar ${ROOT_DIR}"/components/api-mediation/discovery-service.jar" &
echo "starting " ${CATALOG_CODE}
_BPX_JOBNAME=${ZOWE_PREFIX}${CATALOG_CODE} java -Xms16m -Xmx512m -Xquickstart \
    -Dibm.serversocket.recover=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp \
    -Denvironment.hostname=${ZOWE_EXPLORER_HOST} \
    -Denvironment.port=${CATALOG_PORT} \
    -Denvironment.discoveryLocations=https://${ZOWE_EXPLORER_HOST}:${DISCOVERY_PORT}/eureka/ \
    -Denvironment.ipAddress=${ZOWE_IP_ADDRESS} \
    -Denvironment.preferIpAddress=false \
    -Denvironment.gatewayHostname=${ZOWE_EXPLORER_HOST} \
    -Denvironment.eurekaUserId=eureka \
    -Denvironment.eurekaPassword=password \
    -Dapiml.security.auth.zosmfServiceId=zosmfca32 \
    -Dapiml.security.ssl.verifySslCertificatesOfServices=${VERIFY_CERTIFICATES} \
    -Dspring.profiles.include=$LOG_LEVEL \
    -Dserver.address=${ZOWE_IP_ADDRESS} \
    -Dserver.ssl.enabled=true \
    -Dserver.ssl.keyStore=${KEYSTORE} \
    -Dserver.ssl.keyStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.keyStorePassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.keyAlias=${KEY_ALIAS} \
    -Dserver.ssl.keyPassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.trustStore=${TRUSTSTORE} \
    -Dserver.ssl.trustStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.trustStorePassword=${KEYSTORE_PASSWORD} \
    -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
    -jar ${ROOT_DIR}"/components/api-mediation/api-catalog-services.jar" &
echo "starting " ${GATEWAY_CODE}
_BPX_JOBNAME=${ZOWE_PREFIX}${GATEWAY_CODE} java -Xms32m -Xmx256m -Xquickstart -Xdiag \
    -Dibm.serversocket.recover=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp \
    -Dspring.profiles.include=$LOG_LEVEL \
    -Dapiml.service.hostname=${ZOWE_EXPLORER_HOST} \
    -Dapiml.service.port=${GATEWAY_PORT} \
    -Dapiml.service.discoveryServiceUrls=https://${ZOWE_EXPLORER_HOST}:${DISCOVERY_PORT}/eureka/ \
    -Dapiml.service.preferIpAddress=false \
    -Dapiml.service.allowEncodedSlashes=false \
    -Dapiml.cache.storage.location=${WORKSPACE_DIR}/api-mediation/ \
    -Denvironment.ipAddress=${ZOWE_IP_ADDRESS} \
    -Dapiml.gateway.timeoutMillis=30000 \
    -Dapiml.security.ssl.verifySslCertificatesOfServices=${VERIFY_CERTIFICATES} \
    -Dapiml.security.auth.zosmfServiceId=zosmfca32 \
    -Dapiml.zoweManifest=${ZOWE_MANIFEST} \
    -Dserver.address=${ZOWE_IP_ADDRESS} \
    -Dserver.ssl.enabled=true \
    -Dserver.ssl.keyStore=${KEYSTORE} \
    -Dserver.ssl.keyStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.keyStorePassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.keyAlias=${KEY_ALIAS} \
    -Dserver.ssl.keyPassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.trustStore=${TRUSTSTORE} \
    -Dserver.ssl.trustStoreType=${KEYSTORE_TYPE} \
    -Dserver.ssl.trustStorePassword=${KEYSTORE_PASSWORD} \
    -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
    -Dapiml.service.allowEncodedSlashes=true \
    -cp ${ROOT_DIR}"/components/api-mediation/gateway-service.jar":/a/apimtst/IRRRacf.jar \
    org.springframework.boot.loader.PropertiesLauncher &
echo "starting DC"
DISCOVERABLECLIENT_CODE=DC
_BPX_JOBNAME=${ZOWE_PREFIX}${DISCOVERABLECLIENT_CODE} java -Xms32m -Xmx256m -Xquickstart \
    -Dibm.serversocket.recover=true \
    -Dfile.encoding=UTF-8 \
    -Djava.io.tmpdir=/tmp \
    -Dapiml.service.serviceId=discoverableclient \
    -Dapiml.service.hostname=${ZOWE_EXPLORER_HOST} \
    -Dapiml.service.port=${DISCOVERABLECLIENT_PORT} \
    -Dapiml.service.serviceIpAddress=${ZOWE_IP_ADDRESS} \
    -Dapiml.service.discoveryServiceUrls=https://${ZOWE_EXPLORER_HOST}:${DISCOVERY_PORT}/eureka/ \
    -Dserver.ssl.keyAlias=${KEY_ALIAS} \
    -Dserver.ssl.keyPassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.keyStore=${KEYSTORE} \
    -Dserver.ssl.keyStorePassword=${KEYSTORE_PASSWORD} \
    -Dserver.ssl.trustStore=${TRUSTSTORE} \
    -Dserver.ssl.trustStorePassword=${KEYSTORE_PASSWORD} \
    -Djava.protocol.handler.pkgs=com.ibm.crypto.provider \
    -Dapiml.service.preferIpAddress=false \
    -jar ${ROOT_DIR}"/components/api-mediation/discoverable-client.jar" &
echo "Done"

//TODO
Running API Mediation Layer
.: start.sh 2: FSUM7351 not found
.: start.sh 12: FSUM7351 not found
.: start.sh 27: FSUM7351 not found
.: start.sh 31: FSUM7351 not found
