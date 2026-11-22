package tn.sahticonnect.cnam.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.config.annotation.WsConfigurer;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.List;

/**
 * Exposition SOAP (contract-first).
 * <ul>
 *   <li>MessageDispatcherServlet sur /ws/* ;</li>
 *   <li>WSDL généré à partir du XSD : http://localhost:8081/ws/dossier-cnam.wsdl ;</li>
 *   <li>requêtes et réponses validées contre le XSD (fault Client si la requête est invalide).</li>
 * </ul>
 */
@EnableWs
@Configuration
public class WebServiceConfig implements WsConfigurer {

    public static final String NAMESPACE = "http://sahticonnect.tn/cnam";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext contexte) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(contexte);
        // remplace l'adresse du service dans le WSDL par l'hôte réellement appelé
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /** Le nom du bean (« dossier-cnam ») donne l'URL du WSDL : /ws/dossier-cnam.wsdl. */
    @Bean(name = "dossier-cnam")
    public DefaultWsdl11Definition dossierCnamWsdl(XsdSchema dossierCnamSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("DossierCnamPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(NAMESPACE);
        wsdl.setSchema(dossierCnamSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema dossierCnamSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/dossier-cnam.xsd"));
    }

    @Bean
    public PayloadValidatingInterceptor validationXsd(XsdSchema dossierCnamSchema) {
        PayloadValidatingInterceptor intercepteur = new PayloadValidatingInterceptor();
        intercepteur.setXsdSchema(dossierCnamSchema);
        intercepteur.setValidateRequest(true);
        intercepteur.setValidateResponse(true);
        return intercepteur;
    }

    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptors) {
        interceptors.add(validationXsd(dossierCnamSchema()));
    }
}
