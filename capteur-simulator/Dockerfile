# Capteur IoT simulé. À construire depuis la RACINE du dépôt (le .proto est dans docs/proto) :
#   docker build -f capteur-simulator/Dockerfile -t sahticonnect/capteur-simulator .
FROM python:3.12-slim
WORKDIR /app
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1

COPY capteur-simulator/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Stubs générés depuis le contrat, comme en local (generer_stubs.ps1)
COPY docs/proto/telemetrie.proto proto/
RUN python -m grpc_tools.protoc -I proto --python_out=. --grpc_python_out=. proto/telemetrie.proto

COPY capteur-simulator/*.py ./

ENTRYPOINT ["python", "capteur.py"]
CMD ["--patient", "PAT-0001", "--scenario", "tachycardie", "--hote", "telemetrie-service:9090"]
