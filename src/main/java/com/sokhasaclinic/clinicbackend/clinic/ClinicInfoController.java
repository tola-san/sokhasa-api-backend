package com.sokhasaclinic.clinicbackend.clinic;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinic")
public class ClinicInfoController {

    private final ClinicInfoService clinicInfoService;

    public ClinicInfoController(ClinicInfoService clinicInfoService) {
        this.clinicInfoService = clinicInfoService;
    }

    @GetMapping("/info")
    public ClinicInfoResponse getClinicInfo() {
        return clinicInfoService.getClinicInfo();
    }
}
