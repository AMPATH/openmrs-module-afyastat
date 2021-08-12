package org.openmrs.module.afyastat.util;

import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Form;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class Utils {
	
	/**
	 * Lifted from KenyaEMR common metadata
	 */
	public static final String NEXT_OF_KIN_ADDRESS = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String NEXT_OF_KIN_CONTACT = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String NEXT_OF_KIN_NAME = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String NEXT_OF_KIN_RELATIONSHIP = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String SUBCHIEF_NAME = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String TELEPHONE_CONTACT = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String EMAIL_ADDRESS = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String ALTERNATE_PHONE_CONTACT = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String NEAREST_HEALTH_CENTER = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String GUARDIAN_FIRST_NAME = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String GUARDIAN_LAST_NAME = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String CWC_NUMBER = "1dc8b419-35f2-4316-8d68-135f0689859b";
	
	public static final String DISTRICT_REGISTRATION_NUMBER = "d8ee3b8c-a8fc-4d6b-af6a-9423be5f8906";
	
	public static final String HEI_UNIQUE_NUMBER = "0691f522-dd67-4eeb-92c8-af5083baf338";
	
	public static final String OPENMRS_ID = "dfacd928-0370-4315-99d7-6ec1c9f7ae76";
	
	public static final String NATIONAL_ID = "58a47054-1359-11df-a1f1-0026b9348838";
	
	public static final String OLD = "58a46e2e-1359-11df-a1f1-0026b9348838";
	
	public static final String PATIENT_CLINIC_NUMBER = "b4d66522-11fc-45c7-83e3-39a1af21ae0d";
	
	public static final String UNIQUE_PATIENT_NUMBER = "05ee9cf4-7242-4a17-b4d4-00f707265c8a";
	
	public static final String NATIONAL_UNIQUE_PATIENT_IDENTIFIER = "f85081e2-b4be-4e48-b3a4-7994b69bb101";
	
	public static final String HTS = "9c0a7a57-62ff-4f75-babe-5835b0e921b7";
	
	public static final String HTS_INITIAL_TEST = "402dc5d7-46da-42d4-b2be-f43ea4ad87b0";
	
	public static final String HTS_CONFIRMATORY_TEST = "b08471f6-0892-4bf7-ab2b-bf79797b8ea4";

	public static final String HTS_FORM_UUID = "930a7089-a7f2-462b-b13c-69d03bfb44c1";

	public static final String HTS_ENCOUNTER_UUID = "5014245b-7cdd-48f1-a278-6672cb91cb87";

	public static final String HTS_VISIT_TYPE_UUID = "1d697d92-a000-11ea-b1a0-d0577bb73cd4";

	public static String fetchRequestBody(BufferedReader reader) {
		String requestBodyJsonStr = "";
		try {
			
			BufferedReader br = new BufferedReader(reader);
			String output = "";
			while ((output = reader.readLine()) != null) {
				requestBodyJsonStr += output;
			}
			
		}
		catch (IOException e) {
			
			System.out.println("IOException: " + e.getMessage());
			
		}
		return requestBodyJsonStr;
	}
	
	/**
	 * Finds the last encounter during the program enrollment with the given encounter type
	 * 
	 * @param type the encounter type
	 * @return the encounter
	 */
	public static Encounter lastEncounter(Patient patient, EncounterType type, List<Form> forms) {
		List<Encounter> encounters = Context.getEncounterService().getEncounters(patient, null, null, null, forms,
		    Collections.singleton(type), null, null, null, false);
		return encounters.size() > 0 ? encounters.get(encounters.size() - 1) : null;
	}
	
}
