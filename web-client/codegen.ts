import type { CodegenConfig } from '@graphql-codegen/cli';

/**
 * Contract-first GraphQL : types TypeScript et services Angular (un par opération) générés depuis le schéma
 * du dashboard (docs/, source de vérité) et les documents de src/app/graphql. Une opération qui ne respecte pas
 * le schéma fait échouer la génération.
 */
const config: CodegenConfig = {
  schema: '../docs/graphql/dashboard-medical.graphqls',
  documents: 'src/app/graphql/**/*.graphql',
  generates: {
    'src/app/graphql/generated.ts': {
      // typescript-operations v7 génère lui-même les enums et inputs utilisés (le plugin « typescript » les doublerait)
      plugins: ['typescript-operations', 'typescript-apollo-angular'],
      config: {
        scalars: { Date: 'string', DateTime: 'string' },
        addExplicitOverride: true,
      },
    },
  },
};

export default config;
