#!/bin/bash
sed -i '/<build>/,$d' pom.xml
cat << 'XML' >> pom.xml
	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
			</plugin>
		</plugins>
	</build>
</project>
XML
