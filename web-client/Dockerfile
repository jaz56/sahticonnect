# Client web Angular. À construire depuis la RACINE du dépôt (les contrats sont dans docs/) :
#   docker build -f web-client/Dockerfile -t sahticonnect/web-client .

# ---------- Étape 1 : build (Node + Java pour openapi-generator) ----------
FROM node:22-bookworm-slim AS build
# openapi-generator-cli est une application Java : un JRE suffit pour générer le client REST
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src/web-client

# Dépendances d'abord (couche mise en cache tant que package-lock.json ne change pas),
# puis le générateur OpenAPI (jar 7.16.0, téléchargé une fois)
COPY web-client/package.json web-client/package-lock.json web-client/openapitools.json ./
# réseau instable : 5 essais par paquet (délais croissants) et, en dernier recours, un second npm ci complet
RUN npm config set fetch-retries 5 && npm config set fetch-retry-mintimeout 20000 && npm config set fetch-retry-maxtimeout 120000 \
    && (npm ci --no-audit --no-fund || (sleep 10 && npm ci --no-audit --no-fund)) && npx openapi-generator-cli version

# Contrats (source de vérité) puis sources : le build régénère les clients REST et GraphQL (prebuild)
COPY docs/openapi /src/docs/openapi
COPY docs/graphql /src/docs/graphql
COPY web-client/ ./
RUN npm run build

# ---------- Étape 2 : exécution (nginx sert les fichiers statiques) ----------
FROM nginx:1.28-alpine
COPY web-client/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/web-client/dist/web-client/browser /usr/share/nginx/html
EXPOSE 80
