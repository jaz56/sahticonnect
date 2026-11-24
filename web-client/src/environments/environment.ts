/**
 * Toutes les requêtes du client passent par l'API Gateway, jamais directement par un service.
 * (En Docker comme en local, le navigateur joint la gateway sur le port 8080 de la machine.)
 */
const gatewayUrl = 'http://localhost:8080';

export const environment = {
  gatewayUrl,
  portailUrl: `${gatewayUrl}/api/portail`,
  orchestrateurUrl: `${gatewayUrl}/api/orchestrateur`,
  graphqlUrl: `${gatewayUrl}/api/graphql`,
  graphqlWsUrl: `${gatewayUrl.replace(/^http/, 'ws')}/api/graphql`,
  cnamUrl: `${gatewayUrl}/api/cnam/ws`,
  fluxWorkflowsUrl: `${gatewayUrl}/api/orchestrateur/workflows/flux`,
  santeGatewayUrl: `${gatewayUrl}/actuator/health`,
};
