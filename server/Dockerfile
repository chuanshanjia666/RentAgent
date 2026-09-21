# RentAgent 后端镜像（多阶段：Maven 构建 → JRE 运行）
#
# 用途：docker compose up -d server 需要本文件（此前的 compose 里 build: ./server 指向了
# 一个不存在的构建上下文，该服务实际起不来）。构建产物与 CI 的 mvn package 一致。
#
# 构建外部依赖：Maven 中央仓库的镜像需按所处网络自备（公司/校园网常需 DaoCloud 等镜像源）。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
# 先只拷 pom 预热依赖，源码改动不会让依赖层缓存失效
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
# spring-boot-maven-plugin 只产出唯一一个可执行 jar（原始包被改名为 *.jar.original，不匹配本通配）
COPY --from=build /build/target/*.jar app.jar
# 上传目录：compose 用 uploads 卷挂载到这里
RUN mkdir -p /app/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
