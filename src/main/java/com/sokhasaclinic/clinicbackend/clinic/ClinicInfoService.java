package com.sokhasaclinic.clinicbackend.clinic;

import org.springframework.stereotype.Service;

@Service
public class ClinicInfoService {

    public ClinicInfoResponse getClinicInfo() {
        return new ClinicInfoResponse(
                "Clinic Sokhasa",
                "Welcome to Clinic Sokhasa."
        );
    }
}
