FROM maven@sha256:b37da91062d450f3c11c619187f0207bbb497fc89d265a46bbc6dc5f17c02a2b AS build
# The above is a temporary fix
# See:
# https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=911925
# https://github.com/carlossg/docker-maven/issues/92
# FROM maven:3-jdk-8-slim AS build

#Pass build args into env vars
ARG CI
ENV CI=$CI

ARG SONAR_HOST_URL
ENV SONAR_HOST_URL=$SONAR_HOST_URL

ARG SONAR_LOGIN
ENV SONAR_LOGIN=$SONAR_LOGIN

COPY pom.xml /build/pom.xml
WORKDIR /build

RUN if getent ahosts "sslhelp.doi.net" > /dev/null 2>&1; then \
		wget 'http://sslhelp.doi.net/docs/DOIRootCA2.cer' && \
		keytool -import -trustcacerts -file DOIRootCA2.cer -alias DOIRootCA2.cer -keystore $JAVA_HOME/jre/lib/security/cacerts -noprompt -storepass changeit; \
	fi

#download all maven dependencies (this will only re-run if the pom has changed)
RUN mvn -B dependency:go-offline

COPY .git /build
COPY src /build/src
ARG BUILD_COMMAND="mvn -B clean verify"
RUN ${BUILD_COMMAND}

FROM usgswma/wma-spring-boot-base:8-jre-slim-0.0.4

ENV serverPort=7500
ENV HEALTHY_RESPONSE_CONTAINS='{"status":"UP"}'

COPY --chown=1000:1000 --from=build /build/target/*.jar app.jar

HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -k "https://127.0.0.1:${serverPort}${serverContextPath}${HEALTH_CHECK_ENDPOINT}" | grep -q ${HEALTHY_RESPONSE_CONTAINS} || exit 1
