/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.afyastat.api;

import org.openmrs.Cohort;
import org.openmrs.Patient;
import org.openmrs.api.OpenmrsService;

import org.openmrs.module.afyastat.api.service.MedicQueData;
import org.openmrs.module.reporting.common.DurationUnit;

import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * The main service of this module, which is exposed for other modules. See
 * moduleApplicationContext.xml on how it is wired up.
 */
@Transactional
public interface AfyastatService extends OpenmrsService {
	
	public List<PatientContact> getPatientContacts();
	
	public PatientContact savePatientContact(PatientContact patientContact);
	
	public List<PatientContact> searchPatientContact(String searchName);
	
	public void voidPatientContact(int theId);
	
	public PatientContact getPatientContactByID(Integer patientContactId);
	
	public PatientContact getPatientContactByUuid(String uuid);
	
	public List<PatientContact> getPatientContactByPatient(Patient patient);
	
	public ContactTrace saveClientTrace(ContactTrace contactTrace);
	
	public MedicQueData saveQueData(MedicQueData medicQueData);
	
	public List<ContactTrace> getContactTraceByPatientContact(PatientContact patientContact);
	
	public ContactTrace getPatientContactTraceById(Integer patientContactId);
	
	public ContactTrace getLastTraceForPatientContact(PatientContact patientContact);
	
	public PatientContact getPatientContactEntryForPatient(Patient patient);
	
	public Cohort getPatientsWithGender(boolean includeMales, boolean includeFemales, boolean includeUnknownGender);
	
	public Cohort getPatientsWithAgeRange(Integer minAge, DurationUnit minAgeUnit, Integer maxAge, DurationUnit maxAgeUnit,
	        boolean unknownAgeIncluded, Date effectiveDate);
	
	public List<PatientContact> getPatientContactListForRegistration();
}
