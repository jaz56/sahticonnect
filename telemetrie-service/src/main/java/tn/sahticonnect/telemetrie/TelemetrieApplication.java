package tn.sahticonnect.telemetrie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Service gRPC de télémétrie : reçoit les mesures des capteurs en streaming, détecte les seuils
 * et diffuse les alertes. Les classes Protobuf et les stubs gRPC (même package) sont générés
 * depuis src/main/proto/telemetrie.proto.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TelemetrieApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetrieApplication.class, args);
    }
}
