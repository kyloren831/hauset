#!/bin/bash

# Update pom.xml for Spring Boot 3.3.4 and add dependencies
sed -i 's/<version>4.1.1<\/version>/<version>3.3.4<\/version>/' pom.xml
sed -i '/<\/dependencies>/i \
		<dependency>\
			<groupId>org.flywaydb</groupId>\
			<artifactId>flyway-core</artifactId>\
		</dependency>\
		<dependency>\
			<groupId>org.flywaydb</groupId>\
			<artifactId>flyway-database-postgresql</artifactId>\
		</dependency>\
		<dependency>\
			<groupId>io.jsonwebtoken</groupId>\
			<artifactId>jjwt-api</artifactId>\
			<version>0.12.5</version>\
		</dependency>\
		<dependency>\
			<groupId>io.jsonwebtoken</groupId>\
			<artifactId>jjwt-impl</artifactId>\
			<version>0.12.5</version>\
			<scope>runtime</scope>\
		</dependency>\
		<dependency>\
			<groupId>io.jsonwebtoken</groupId>\
			<artifactId>jjwt-jackson</artifactId>\
			<version>0.12.5</version>\
			<scope>runtime</scope>\
		</dependency>\
		<dependency>\
			<groupId>org.springframework.boot</groupId>\
			<artifactId>spring-boot-starter-actuator</artifactId>\
		</dependency>' pom.xml

# Fix groupId in pom.xml
sed -i 's/<groupId>hauset.server<\/groupId>/<groupId>com.appdomotica<\/groupId>/' pom.xml

# Create directory structure
BASE_DIR="src/main/java/com/appdomotica"
mkdir -p "$BASE_DIR"/{config,usuarios,clientes,catalogo,cotizaciones,proyectos,agenda,mantenimiento}
for mod in usuarios clientes catalogo cotizaciones proyectos agenda mantenimiento; do
  mkdir -p "$BASE_DIR/$mod"/{controller,service,repository,dto,entity}
done
mkdir -p src/main/resources/db/migration

# 1. Main Application Class
cat << 'APP' > "$BASE_DIR/AppDomoticaApplication.java"
package com.appdomotica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AppDomoticaApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppDomoticaApplication.class, args);
    }
}
APP

