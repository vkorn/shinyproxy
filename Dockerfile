FROM openjdk:8-jre

WORKDIR /opt/shinyproxy

COPY target/ShinyProxy.jar shinyproxy.jar

CMD ["java", "-jar", "shinyproxy.jar"]
