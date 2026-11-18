package com.sokhasaclinic.clinicbackend.appointment.model;

/**
 * What the patient submitted with a request, already validated and normalised (AP-04, AP-05). The
 * appointment keeps this copy, so a later booking never changes it.
 *
 * @param phone E.164
 * @param email null when not given
 * @param notes null when not given
 */
public record PatientDetails(String fullName, String phone, String email, String languageCode, String notes) {
}
