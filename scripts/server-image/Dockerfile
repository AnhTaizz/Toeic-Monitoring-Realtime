# Image TOEIC Server dung boi scripts/redeploy-server.ps1 (khac server/Dockerfile cho dev):
# chay bang user thuong, khoa JRE 21.0.8, tat delta va khong tao tai khoan demo.
FROM eclipse-temurin:21.0.8_9-jre
RUN useradd --system --uid 10001 --home-dir /app toeic
WORKDIR /app
COPY server.jar /app/server.jar
USER toeic
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=UTC", "-jar", "/app/server.jar", \
    "--server.address=0.0.0.0", "--server.port=8080", \
    "--toeic.state.delta-enabled=false", \
    "--toeic.monitoring.signal-experiment-enabled=true", \
    "--toeic.seed.demo-accounts=false"]
