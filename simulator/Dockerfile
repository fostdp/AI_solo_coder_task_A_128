FROM python:3.12-slim

WORKDIR /app

RUN pip install --no-cache-dir requests paho-mqtt

COPY weather_simulator.py .
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt 2>/dev/null || true

ENV SIMULATOR_MODE=realtime
ENV SEASON=spring
ENV EXTREME_WEATHER=false
ENV REPORT_INTERVAL=3600
ENV BACKEND_URL=http://backend:8080/api
ENV MQTT_ENABLED=false
ENV MQTT_BROKER=tcp://mqtt:1883
ENV MQTT_TOPIC=silkroad/weather

ENTRYPOINT ["python", "-u", "weather_simulator.py"]
