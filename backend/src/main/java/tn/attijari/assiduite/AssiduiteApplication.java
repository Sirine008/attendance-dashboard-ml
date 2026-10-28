package tn.attijari.assiduite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AssiduiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(AssiduiteApplication.class, args);
    }
}
