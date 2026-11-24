import { Provider, inject } from '@angular/core';
import { provideApollo } from 'apollo-angular';
import { HttpLink } from 'apollo-angular/http';
import { InMemoryCache, split } from '@apollo/client';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { getMainDefinition } from '@apollo/client/utilities';
import { Kind, OperationTypeNode } from 'graphql';
import { createClient } from 'graphql-ws';
import { environment } from '../../../environments/environment';

/**
 * Apollo : requêtes et mutations en HTTP (via HttpClient, donc via l'intercepteur d'erreurs), subscriptions en
 * WebSocket (protocole graphql-transport-ws de graphql-ws), les deux sur la gateway (/api/graphql).
 * errorPolicy « all » : une réponse partielle (champ distant en panne) garde ses données ET ses erreurs.
 */
declare module '@apollo/client' {
  // Apollo Client 4 : les options par défaut doivent être déclarées pour typer les résultats en conséquence
  export namespace ApolloClient {
    namespace DeclareDefaultOptions {
      interface WatchQuery {
        errorPolicy: 'all';
      }
      interface Query {
        errorPolicy: 'all';
      }
      interface Mutate {
        errorPolicy: 'all';
      }
    }
  }
}

export function provideGraphQl(): Provider {
  return provideApollo(() => {
    const http = inject(HttpLink).create({ uri: environment.graphqlUrl });
    const ws = new GraphQLWsLink(
      createClient({
        url: environment.graphqlWsUrl,
        lazy: true, // la WebSocket ne s'ouvre qu'à la première subscription
        retryAttempts: Number.POSITIVE_INFINITY, // dashboard ou gateway redémarré : on se reconnecte
        shouldRetry: () => true,
        retryWait: async (essai) => new Promise((ok) => setTimeout(ok, Math.min(1000 * 2 ** essai, 30000))),
      }),
    );
    const link = split(
      ({ query }) => {
        const definition = getMainDefinition(query);
        return definition.kind === Kind.OPERATION_DEFINITION && definition.operation === OperationTypeNode.SUBSCRIPTION;
      },
      ws,
      http,
    );
    return {
      link,
      cache: new InMemoryCache(),
      defaultOptions: {
        watchQuery: { errorPolicy: 'all', fetchPolicy: 'network-only' },
        query: { errorPolicy: 'all', fetchPolicy: 'network-only' },
        mutate: { errorPolicy: 'all' },
      },
    };
  });
}
