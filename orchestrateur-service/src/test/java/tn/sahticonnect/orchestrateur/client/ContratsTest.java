package tn.sahticonnect.orchestrateur.client;

import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.UnExecutableSchemaGenerator;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les clients sont générés depuis des COPIES des contrats de docs/ : ce test vérifie que les copies sont identiques,
 * et que les documents GraphQL de l'orchestrateur sont valides pour le schéma du dashboard.
 */
class ContratsTest {

    private static final Path DOCS = Path.of("..", "docs");

    @ParameterizedTest(name = "{1} = docs/{0}")
    @CsvSource({
            "openapi/orchestrateur.yaml,      src/main/resources/openapi/orchestrateur.yaml",
            "openapi/portail-patient.yaml,    src/main/contrats/portail-patient.yaml",
            "proto/telemetrie.proto,          src/main/proto/telemetrie.proto",
            "wsdl/dossier-cnam.xsd,           src/main/xsd/dossier-cnam.xsd",
            "graphql/dashboard-medical.graphqls, src/test/resources/contrats/dashboard-medical.graphqls"})
    void copieIdentiqueAuContrat(String contrat, String copie) throws IOException {
        assertThat(Files.readString(Path.of(copie)).replace("\r\n", "\n"))
                .as("la copie %s doit être identique à docs/%s", copie, contrat)
                .isEqualTo(Files.readString(DOCS.resolve(contrat)).replace("\r\n", "\n"));
    }

    @ParameterizedTest(name = "{0}.graphql")
    @ValueSource(strings = {"enregistrerAlerte", "creerIntervention", "mettreAJourIntervention"})
    void documentGraphQlValidePourLeSchemaDuDashboard(String document) throws IOException {
        GraphQLSchema schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(new SchemaParser().parse(
                Files.readString(Path.of("src/test/resources/contrats/dashboard-medical.graphqls"))));
        Document requete = Parser.parse(Files.readString(Path.of("src/main/resources/graphql-documents", document + ".graphql")));

        List<ValidationError> erreurs = new Validator().validateDocument(schema, requete, Locale.FRENCH);

        assertThat(erreurs).as("erreurs de validation de %s", document).isEmpty();
    }

    @Test
    void unDocumentInvalideEstBienDetecte() {
        GraphQLSchema schema = UnExecutableSchemaGenerator.makeUnExecutableSchema(new SchemaParser().parse(
                "type Query { a: Int }"));
        assertThat(new Validator().validateDocument(schema, Parser.parse("{ inconnu }"), Locale.FRENCH)).isNotEmpty();
    }
}
