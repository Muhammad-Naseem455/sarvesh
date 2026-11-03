# Lightweight OpenJDK 21 Alpine image
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# Copy all project files
COPY . .

# Compile Java classes into out/
RUN mkdir -p out && javac -d out src/ecodrop/*.java

# Default port (Render overrides with dynamic $PORT)
ENV PORT=8080
EXPOSE 8080

# Run the web server
CMD ["java", "-cp", "out", "ecodrop.WebServer"]
