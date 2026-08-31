# Build the jar inside the image so the only prerequisite for `docker compose up` is Docker itself.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved in their own layer, so application changes do not re-download the world.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# heif-convert turns iPhone HEIC into JPEG so browsers and face detection can use the pixels.
# heif-convert turns iPhone HEIC into JPEG. ffmpeg pulls stills from video for face detection.
RUN apk add --no-cache libheif-tools ffmpeg su-exec

# Never run the application as root.
RUN addgroup -S app && adduser -S -G app app

# The object storage directory must exist in the image and be owned by the application user: a fresh
# Docker volume inherits the ownership of its mount point, and without this the non-root process
# cannot write a single upload. The entrypoint re-applies ownership when a host/Railway volume is
# mounted over this path.
RUN mkdir -p /var/lib/memorygraph/storage && chown -R app:app /var/lib/memorygraph

COPY --from=build /build/target/*.jar application.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
