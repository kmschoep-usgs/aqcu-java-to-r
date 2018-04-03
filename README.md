# aqcu-java-to-r
Aquarius Customization - Java to RServe Bridge

This service sits between the AQCU Report Microservices and Repgen.

It is built as a war to be deployed into a Tomcat container.

Configured functionality includes:

- **Swagger Api Documentation** Located at https://localhost:8443/aqcu-java-to-r/swagger-ui.html

## Running the Application

The war built using maven can be deployed the same as any other to a Tomcat instance. Some additional configurations are needed before starting Tomcat:

- Add ```<Parameter name="spring.config.location" value="${catalina.base}/conf/application.yml" />``` to the context.xml file
- Copy (or append) application.yml to the conf folder's application.yml and adjust any values as required.
