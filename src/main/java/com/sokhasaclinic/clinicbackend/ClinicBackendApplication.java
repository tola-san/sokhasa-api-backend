package com.sokhasaclinic.clinicbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClinicBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicBackendApplication.class, args);
    }

}
