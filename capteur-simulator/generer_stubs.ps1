# Génère les stubs Python (telemetrie_pb2.py, telemetrie_pb2_grpc.py) depuis le contrat docs/proto.
# À lancer depuis le dossier capteur-simulator, après « pip install -r requirements.txt ».
$ErrorActionPreference = "Stop"
$racine = $PSScriptRoot
python -m grpc_tools.protoc `
    -I "$racine\..\docs\proto" `
    --python_out="$racine" `
    --pyi_out="$racine" `
    --grpc_python_out="$racine" `
    "$racine\..\docs\proto\telemetrie.proto"
if ($LASTEXITCODE -ne 0) { throw "Échec de la génération des stubs" }
Write-Host "Stubs générés dans $racine" -ForegroundColor Green
