/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.afyastat.handler;

import com.jayway.jsonpath.InvalidPathException;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.*;
import org.openmrs.annotation.Handler;
import org.openmrs.api.ConceptService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.afyastat.api.service.RegistrationInfoService;
import org.openmrs.module.afyastat.exception.StreamProcessorException;
import org.openmrs.module.afyastat.model.AfyaStatQueueData;
import org.openmrs.module.afyastat.model.RegistrationInfo;
import org.openmrs.module.afyastat.model.handler.QueueInfoHandler;
import org.openmrs.module.afyastat.utils.JsonFormatUtils;
import org.openmrs.module.afyastat.utils.PatientLookUpUtils;
import org.openmrs.util.HttpClient;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.openmrs.module.afyastat.utils.JsonFormatUtils.getElementFromJsonObject;

/**
 * Handles processing of registration data in KenyaEMR Adapted from openmrs-module-muzimacore See
 * https
 * ://github.com/muzima/openmrs-module-muzimacore/blob/master/api/src/main/java/org/openmrs/module
 * /muzima/handler/JsonRegistrationQueueDataHandler.java
 */
@Handler(supports = AfyaStatQueueData.class, order = 1)
public class JsonRegistrationQueueInfoHandler implements QueueInfoHandler {
	
	private static final String DISCRIMINATOR_VALUE = "json-registration";
	
	private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	
	private final Log log = LogFactory.getLog(JsonRegistrationQueueInfoHandler.class);
	
	private Patient unsavedPatient;
	
	private String payload;
	
	Set<PersonAttribute> personAttributes;
	
	private StreamProcessorException queueProcessorException;
	
	public static final String NEXT_OF_KIN_ADDRESS = "a657a4f1-9c0f-444b-a1fd-445bb91dd12d";
	
	public static final String NEXT_OF_KIN_CONTACT = "a657a4f1-9c0f-444b-a1fd-445bb91dd12d";
	
	public static final String NEXT_OF_KIN_NAME = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String NEXT_OF_KIN_RELATIONSHIP = "a657a4f1-9c0f-444b-a1fd-445bb91dd12d";
	
	public static final String SUBCHIEF_NAME = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String TELEPHONE_CONTACT = "72a75bec-1359-11df-a1f1-0026b9348838";
	
	public static final String EMAIL_ADDRESS = "2f65dbcb-3e58-45a3-8be7-fd1dc9aa0faa";
	
	public static final String ALTERNATE_PHONE_CONTACT = "72a759a8-1359-11df-a1f1-0026b9348838";
	
	public static final String NEAREST_HEALTH_CENTER = "8d87236c-c2cc-11de-8d13-0010c6dffd0f";
	
	public static final String GUARDIAN_FIRST_NAME = "48876f06-7493-416e-855d-8413d894ea93";
	
	public static final String GUARDIAN_LAST_NAME = "bb8684a5-ac0b-4c2c-b9a5-1203e99952c2";
	
	@Override
	public void process(final AfyaStatQueueData queueData) throws StreamProcessorException {
		log.info("Processing registration form data: " + queueData.getUuid());
		queueProcessorException = new StreamProcessorException();
		try {
			if (validate(queueData)) {
				registerUnsavedPatient();
				
				Object obsObject = JsonFormatUtils.readAsObject(queueData.getPayload(), "$['observation']");
				if (obsObject != null) {
					registerUnsavedObs(obsObject, queueData);
				}
			}
		}
		catch (Exception e) {
			/*Custom exception thrown by the validate function should not be added again into @queueProcessorException.
			 It should add the runtime dao Exception while saving the data into @queueProcessorException collection */
			if (!e.getClass().equals(StreamProcessorException.class)) {
				queueProcessorException.addException(new Exception("Exception while process payload ", e));
			}
		}
		finally {
			if (queueProcessorException.anyExceptions()) {
				throw queueProcessorException;
			}
		}
	}
	
	@Override
	public boolean validate(AfyaStatQueueData queueData) {
		log.info("Processing registration form data: " + queueData.getUuid());
		queueProcessorException = new StreamProcessorException();
		try {
			payload = queueData.getPayload();
			unsavedPatient = new Patient();
			populateUnsavedPatientFromPayload();
			validateUnsavedPatient();
			return true;
		}
		catch (Exception e) {
			queueProcessorException.addException(new Exception("Exception while validating payload ", e));
			return false;
		}
		finally {
			if (queueProcessorException.anyExceptions()) {
				throw queueProcessorException;
			}
		}
	}
	
	@Override
	public String getDiscriminator() {
		return DISCRIMINATOR_VALUE;
	}
	
	private void validateUnsavedPatient() {
		if (!JsonFormatUtils.readAsBoolean(payload, "$['skipPatientMatching']")) {
			Patient savedPatient = findSimilarSavedPatient();
			if (savedPatient != null) {
				queueProcessorException.addException(new Exception(
				        "Found a patient with similar characteristic :  patientId = " + savedPatient.getPatientId()
				                + " Identifier Id = " + savedPatient.getPatientIdentifier().getIdentifier()));
			}
		}
	}
	
	private void populateUnsavedPatientFromPayload() {
		setPatientIdentifiersFromPayload();
		setPatientBirthDateFromPayload();
		setPatientBirthDateEstimatedFromPayload();
		setPatientGenderFromPayload();
		setPatientNameFromPayload();
		setPatientAddressesFromPayload();
		setPersonAttributesFromPayload();
		setUnsavedPatientCreatorFromPayload();
	}
	
	private void setPatientIdentifiersFromPayload() {
		Set<PatientIdentifier> patientIdentifiers = new TreeSet<PatientIdentifier>();
		PatientIdentifier preferredIdentifier = getPreferredPatientIdentifierFromPayload();
		if (preferredIdentifier != null) {
			patientIdentifiers.add(preferredIdentifier);
		}
		List<PatientIdentifier> otherIdentifiers = getOtherPatientIdentifiersFromPayload();
		if (!otherIdentifiers.isEmpty()) {
			patientIdentifiers.addAll(otherIdentifiers);
		}
		setIdentifierTypeLocation(patientIdentifiers);
		unsavedPatient.setIdentifiers(patientIdentifiers);
	}
	
	private PatientIdentifier getPreferredPatientIdentifierFromPayload() {
		//        String identifierValue = JsonUtils.readAsString(payload, "$['patient']['patient.medical_record_number']");
		//        String identifierTypeName = "AMRS Universal ID";
		
		// PatientIdentifier preferredPatientIdentifier = createPatientIdentifier(identifierTypeName, identifierValue);
		PatientIdentifier preferredPatientIdentifier = generateOpenMRSID();//createPatientIdentifier(identifierTypeName, identifierValue);
		if (preferredPatientIdentifier != null) {
			preferredPatientIdentifier.setPreferred(true);
			return preferredPatientIdentifier;
		} else {
			return null;
		}
	}
	
	private List<PatientIdentifier> getOtherPatientIdentifiersFromPayload() {
		List<PatientIdentifier> otherIdentifiers = new ArrayList<PatientIdentifier>();
		try {
			Object otheridentifierObject = JsonFormatUtils.readAsObject(payload, "$['patient']['patient.otheridentifier']");
			if (JsonFormatUtils.isJSONArrayObject(otheridentifierObject)) {
				for (Object otherIdentifier : (JSONArray) otheridentifierObject) {
					PatientIdentifier identifier = createPatientIdentifier((JSONObject) otherIdentifier);
					if (identifier != null) {
						otherIdentifiers.add(identifier);
					}
				}
			} else {
				PatientIdentifier identifier = createPatientIdentifier((JSONObject) otheridentifierObject);
				if (identifier != null) {
					otherIdentifiers.add(identifier);
				}
			}
			
			JSONObject patientObject = (JSONObject) JsonFormatUtils.readAsObject(payload, "$['patient']");
			Set keys = patientObject.keySet();
			for (Object key : keys) {
				if (((String) key).startsWith("patient.otheridentifier^")) {
					PatientIdentifier identifier = createPatientIdentifier((JSONObject) patientObject.get(key));
					if (identifier != null) {
						otherIdentifiers.add(identifier);
					}
				}
			}
		}
		catch (InvalidPathException e) {
			log.error("Error while parsing other identifiers ", e);
		}
		return otherIdentifiers;
	}
	
	private PatientIdentifier createPatientIdentifier(JSONObject identifierObject) {
		if (identifierObject == null) {
			return null;
		}
		
		String identifierTypeName = (String) getElementFromJsonObject(identifierObject, "identifier_type_name");
		String identifierUuid = (String) getElementFromJsonObject(identifierObject, "identifier_type_uuid");
		String identifierValue = (String) getElementFromJsonObject(identifierObject, "identifier_value");
		
		return createPatientIdentifier(identifierUuid, identifierTypeName, identifierValue);
	}
	
	private PatientIdentifier createPatientIdentifier(String identifierTypeUuid, String identifierTypeName,
	        String identifierValue) {
		if (StringUtils.isBlank(identifierTypeUuid) && StringUtils.isBlank(identifierTypeName)) {
			queueProcessorException.addException(new Exception(
			        "Cannot create identifier. Identifier type name or uuid must be supplied"));
		}
		
		if (StringUtils.isBlank(identifierValue)) {
			queueProcessorException.addException(new Exception(
			        "Cannot create identifier. Supplied identifier value is blank for identifier type name:'"
			                + identifierTypeName + "', uuid:'" + identifierTypeUuid + "'"));
		}
		PatientIdentifierType identifierType = null;
		if (StringUtils.isNotBlank(identifierTypeUuid)) {
			identifierType = Context.getPatientService().getPatientIdentifierTypeByUuid(identifierTypeUuid);
		}
		if (identifierType == null && StringUtils.isNotBlank(identifierTypeName)) {
			identifierType = Context.getPatientService().getPatientIdentifierTypeByName(identifierTypeName);
		}
		if (identifierType == null) {
			queueProcessorException.addException(new Exception("Unable to find identifier type with name:'"
			        + identifierTypeName + "', uuid:'" + identifierTypeUuid + "'"));
		} else {
			PatientIdentifier patientIdentifier = new PatientIdentifier();
			patientIdentifier.setIdentifierType(identifierType);
			patientIdentifier.setIdentifier(identifierValue);
			return patientIdentifier;
		}
		return null;
	}
	
	private void setIdentifierTypeLocation(final Set<PatientIdentifier> patientIdentifiers) {
		String locationIdString = JsonFormatUtils.readAsString(payload, "$['encounter']['encounter.location_id']");
		Location location = null;
		int locationId;
		
		if (locationIdString != null) {
			locationId = Integer.parseInt(locationIdString);
			location = Context.getLocationService().getLocation(locationId);
		}
		
		if (location == null) {
			queueProcessorException.addException(new Exception("Unable to find encounter location using the id: "
			        + locationIdString));
		} else {
			Iterator<PatientIdentifier> iterator = patientIdentifiers.iterator();
			while (iterator.hasNext()) {
				PatientIdentifier identifier = iterator.next();
				identifier.setLocation(location);
			}
		}
	}
	
	private void setPatientBirthDateFromPayload() {
		Date birthDate = JsonFormatUtils.readAsDate(payload, "$['patient']['patient.birth_date']");
		unsavedPatient.setBirthdate(birthDate);
	}
	
	private void setPatientBirthDateEstimatedFromPayload() {
		boolean birthdateEstimated = JsonFormatUtils.readAsBoolean(payload, "$['patient']['patient.birthdate_estimated']");
		unsavedPatient.setBirthdateEstimated(birthdateEstimated);
	}
	
	private void setPatientGenderFromPayload() {
		String gender = JsonFormatUtils.readAsString(payload, "$['patient']['patient.sex']");
		unsavedPatient.setGender(gender);
	}
	
	private void setPatientNameFromPayload() {
		String givenName = JsonFormatUtils.readAsString(payload, "$['patient']['patient.given_name']");
		String familyName = JsonFormatUtils.readAsString(payload, "$['patient']['patient.family_name']");
		String middleName = "";
		try {
			middleName = JsonFormatUtils.readAsString(payload, "$['patient']['patient.middle_name']");
		}
		catch (Exception e) {
			log.error(e);
		}
		
		PersonName personName = new PersonName();
		personName.setGivenName(givenName);
		personName.setMiddleName(middleName);
		personName.setFamilyName(familyName);
		unsavedPatient.addName(personName);
	}
	
	private void registerUnsavedPatient() {
		try {
			RegistrationInfoService registrationDataService = Context.getService(RegistrationInfoService.class);
			String temporaryUuid = getPatientUuidFromPayload();
			RegistrationInfo registrationData = registrationDataService.getRegistrationDataByTemporaryUuid(temporaryUuid);
			if (registrationData == null) {
				registrationData = new RegistrationInfo();
				registrationData.setTemporaryUuid(temporaryUuid);
				Patient patient = Context.getPatientService().savePatient(unsavedPatient);
				String assignedUuid = patient.getUuid();
				registrationData.setAssignedUuid(assignedUuid);
				registrationDataService.saveRegistrationData(registrationData);
			}
		}
		catch (Exception e) {
			queueProcessorException.addException(new Exception("Patient registration error: " + e));
		}
	}
	
	private String getPatientUuidFromPayload() {
		return JsonFormatUtils.readAsString(payload, "$['patient']['patient.uuid']");
	}
	
	private void setPatientAddressesFromPayload() {
		PersonAddress patientAddress = new PersonAddress();
		
		String county = JsonFormatUtils.readAsString(payload, "$['patient']['patient.county']");
		patientAddress.setCountyDistrict(county);
		
		String subCounty = JsonFormatUtils.readAsString(payload, "$['patient']['patient.sub_county']");
		patientAddress.setStateProvince(subCounty);
		
		String ward = JsonFormatUtils.readAsString(payload, "$['patient']['patient.ward']");
		patientAddress.setAddress4(ward);
		
		String location = JsonFormatUtils.readAsString(payload, "$['patient']['patient.location']");
		patientAddress.setAddress6(location);
		String postalAddress = JsonFormatUtils.readAsString(payload, "$['patient']['patient.postal_address']");
		patientAddress.setAddress1(postalAddress);
		
		String landMark = JsonFormatUtils.readAsString(payload, "$['patient']['patient.landmark']");
		patientAddress.setAddress2(landMark);
		
		String sub_location = JsonFormatUtils.readAsString(payload, "$['patient']['patient.sub_location']");
		patientAddress.setAddress5(sub_location);
		
		String village = JsonFormatUtils.readAsString(payload, "$['patient']['patient.village']");
		patientAddress.setCityVillage(village);
		
		Set<PersonAddress> addresses = new TreeSet<PersonAddress>();
		addresses.add(patientAddress);
		unsavedPatient.setAddresses(addresses);
	}
	
	private void setPersonAttributesFromPayload() {
		personAttributes = new TreeSet<PersonAttribute>();
		PersonService personService = Context.getPersonService();
		
		//String mothersName = JsonUtils.readAsString(payload, "$['patient']['patient.mothers_name']"); // not currently implemented in Afyastat
		setAsAttribute("Mother's Name", "");
		
		String phoneNumber = JsonFormatUtils.readAsString(payload, "$['patient']['patient.phone_number']");
		setAsAttribute("Contact Phone Number", phoneNumber);
		
		//        String phoneNumber = JsonUtils.readAsString(payload, "$['patient']['patient.phone_number']");
		//        setAsAttributeByUUID(TELEPHONE_CONTACT,phoneNumber);
		
		String nearestHealthCenter = JsonFormatUtils.readAsString(payload, "$['patient']['patient.nearest_health_center']");
		setAsAttributeByUUID(NEAREST_HEALTH_CENTER, nearestHealthCenter);
		
		String emailAddress = JsonFormatUtils.readAsString(payload, "$['patient']['patient.email_address']");
		setAsAttributeByUUID(EMAIL_ADDRESS, emailAddress);
		
		String guardianFirstName = JsonFormatUtils.readAsString(payload, "$['patient']['patient.guardian_first_name']");
		setAsAttributeByUUID(GUARDIAN_FIRST_NAME, guardianFirstName);
		
		String guardianLastName = JsonFormatUtils.readAsString(payload, "$['patient']['patient.guardian_last_name']");
		setAsAttributeByUUID(GUARDIAN_LAST_NAME, guardianLastName);
		
		String alternativePhoneContact = JsonFormatUtils.readAsString(payload,
		    "$['patient']['patient.alternate_phone_contact']");
		setAsAttributeByUUID(ALTERNATE_PHONE_CONTACT, alternativePhoneContact);
		
		String nextOfKinName = JsonFormatUtils.readAsString(payload, "$['patient']['patient.next_of_kin_name']");
		setAsAttributeByUUID(NEXT_OF_KIN_NAME, nextOfKinName);
		
		String nextOfKinRelationship = JsonFormatUtils.readAsString(payload,
		    "$['patient']['patient.next_of_kin_relationship']");
		setAsAttributeByUUID(NEXT_OF_KIN_RELATIONSHIP, nextOfKinRelationship);
		
		String nextOfKinContact = JsonFormatUtils.readAsString(payload, "$['patient']['patient.next_of_kin_contact']");
		setAsAttributeByUUID(NEXT_OF_KIN_CONTACT, nextOfKinContact);
		
		String nextOfKinAddress = JsonFormatUtils.readAsString(payload, "$['patient']['patient.next_of_kin_address']");
		setAsAttributeByUUID(NEXT_OF_KIN_ADDRESS, nextOfKinAddress);
		
		unsavedPatient.setAttributes(personAttributes);
	}
	
	private void setAsAttributeByUUID(String uuid, String value) {
		PersonService personService = Context.getPersonService();
		PersonAttributeType attributeType = personService.getPersonAttributeTypeByUuid(uuid);
		if (attributeType != null && value != null && org.apache.commons.lang3.StringUtils.isNotBlank(value)) {
			PersonAttribute personAttribute = new PersonAttribute(attributeType, value);
			personAttributes.add(personAttribute);
		} else if (attributeType == null) {
			queueProcessorException
			        .addException(new Exception("Unable to find Person Attribute type by uuid '" + uuid + "'"));
		}
	}
	
	private void setAsAttribute(String attributeTypeName, String value) {
		PersonService personService = Context.getPersonService();
		PersonAttributeType attributeType = personService.getPersonAttributeTypeByName(attributeTypeName);
		if (attributeType != null && value != null && org.apache.commons.lang3.StringUtils.isNotBlank(value)) {
			PersonAttribute personAttribute = new PersonAttribute(attributeType, value);
			personAttributes.add(personAttribute);
		} else if (attributeType == null) {
			queueProcessorException.addException(new Exception("Unable to find Person Attribute type by name '"
			        + attributeTypeName + "'"));
		}
	}
	
	private void setUnsavedPatientCreatorFromPayload() {
		String userString = JsonFormatUtils.readAsString(payload, "$['encounter']['encounter.user_system_id']");
		String providerString = JsonFormatUtils.readAsString(payload, "$['encounter']['encounter.provider_id']");
		
		User user = Context.getUserService().getUserByUsername(userString);
		if (user == null) {
			providerString = JsonFormatUtils.readAsString(payload, "$['encounter']['encounter.provider_id']");
			user = Context.getUserService().getUserByUsername(providerString);
		}
		if (user == null) {
			queueProcessorException.addException(new Exception("Unable to find user using the User Id: " + userString
			        + " or Provider Id: " + providerString));
		} else {
			unsavedPatient.setCreator(user);
		}
	}
	
	private Patient findSimilarSavedPatient() {
		Patient savedPatient = null;
		if (unsavedPatient.getNames().isEmpty()) {
			PatientIdentifier identifier = unsavedPatient.getPatientIdentifier();
			if (identifier != null) {
				List<Patient> patients = Context.getPatientService().getPatients(identifier.getIdentifier());
				savedPatient = PatientLookUpUtils.findSimilarPatientByNameAndGender(patients, unsavedPatient);
			}
		} else {
			PersonName personName = unsavedPatient.getPersonName();
			List<Patient> patients = Context.getPatientService().getPatients(personName.getFullName());
			savedPatient = PatientLookUpUtils.findSimilarPatientByNameAndGender(patients, unsavedPatient);
		}
		return savedPatient;
	}
	
	@Override
	public boolean accept(final AfyaStatQueueData queueData) {
		return StringUtils.equals(DISCRIMINATOR_VALUE, queueData.getDiscriminator());
	}
	
	/**
	 * Can't save patients unless they have required OpenMRS IDs
	 */
	private PatientIdentifier generateOpenMRSID() {
		PatientIdentifierType openmrsIDType = Context.getPatientService().getPatientIdentifierTypeByUuid(
		    "58a4732e-1359-11df-a1f1-0026b9348838");
		
		String locationIdString = JsonFormatUtils.readAsString(payload, "$['encounter']['encounter.location_id']");
		Location location = null;
		int locationId;
		
		if (locationIdString != null) {
			locationId = Integer.parseInt(locationIdString);
			location = Context.getLocationService().getLocation(locationId);
		}
		
		/**
		 * TODO Fix ID gen returns null identifier
		 */
		PatientIdentifier identifier = null;
		String userIdString = JsonFormatUtils.readAsString(payload, "$['encounter']['encounter.provider_id']");
		Unirest.setTimeouts(0, 0);
		try {
			HttpResponse<String> response = Unirest.post("http://10.50.80.115:8016/generateidentifier")
			        .header("Content-Type", "application/json").body("{\n    \"user\":1\n}").asString();
			String generated2 = response.getBody();
			String a = JsonFormatUtils.readAsString(generated2, "$['identifier']");
			identifier = new PatientIdentifier(a, openmrsIDType, location);
		}
		catch (UnirestException e) {
			e.printStackTrace();
		}
		return identifier;
	}
	
	private void registerUnsavedObs(Object obsObject, AfyaStatQueueData queueData) {
		ObjectNode obNode = null;
		ObjectMapper mapper = new ObjectMapper();
		try {
			obNode = (ObjectNode) mapper.readTree(obsObject.toString());
			
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		if (obNode != null) {
			ConceptService cs = Context.getConceptService();
			RegistrationInfoService regDataService = Context.getService(RegistrationInfoService.class);
			RegistrationInfo regData = regDataService.getRegistrationDataByTemporaryUuid(queueData.getPatientUuid());
			if (regData != null) {
				Patient p = Context.getPatientService().getPatientByUuid(regData.getAssignedUuid());
				Iterator<Map.Entry<String, JsonNode>> iterator = obNode.getFields();
				Integer valueCoded = null;
				while (iterator.hasNext()) {
					Map.Entry<String, JsonNode> entry = iterator.next();
					if (entry.getKey().equalsIgnoreCase("1605^HIGHEST EDUCATION LEVEL^99DCT")
					        && !entry.getValue().getTextValue().equalsIgnoreCase("")) {
						valueCoded = handleObsValues(entry.getValue().getTextValue().replace("^", "_"));
						if (valueCoded != null && p != null) {
							Obs o = new Obs();
							o.setConcept(cs.getConcept(1605));
							o.setDateCreated(queueData.getDateCreated());
							o.setCreator(queueData.getCreator());
							o.setObsDatetime(queueData.getDateCreated());
							o.setPerson(p);
							o.setValueCoded(cs.getConcept(valueCoded));
							Context.getObsService().saveObs(o, null);
							
						}
					}
					
					if (entry.getKey().equalsIgnoreCase("1972^OCCUPATION^99DCT")
					        && !entry.getValue().getTextValue().equalsIgnoreCase("")) {
						valueCoded = handleObsValues(entry.getValue().getTextValue().replace("^", "_"));
						if (valueCoded != null && p != null) {
							Obs o = new Obs();
							o.setConcept(cs.getConcept(1972));
							o.setDateCreated(queueData.getDateCreated());
							o.setCreator(queueData.getCreator());
							o.setObsDatetime(queueData.getDateCreated());
							o.setPerson(p);
							o.setValueCoded(cs.getConcept(valueCoded));
							Context.getObsService().saveObs(o, null);
							
						}
					}
					
					if (entry.getKey().equalsIgnoreCase("1054^CIVIL STATUS^99DCT")
					        && !entry.getValue().getTextValue().equalsIgnoreCase("")) {
						valueCoded = handleObsValues(entry.getValue().getTextValue().replace("^", "_"));
						if (valueCoded != null && p != null) {
							Obs o = new Obs();
							o.setConcept(cs.getConcept(1054));
							o.setDateCreated(queueData.getDateCreated());
							o.setCreator(queueData.getCreator());
							o.setObsDatetime(queueData.getDateCreated());
							o.setPerson(p);
							o.setValueCoded(cs.getConcept(valueCoded));
							Context.getObsService().saveObs(o, null);
							
						}
					}
				}
			}
		}
		
	}
	
	private Integer handleObsValues(String obsValue) {
		ArrayNode arrNodeValues = JsonNodeFactory.instance.arrayNode();
		Integer conceptValue = null;
		if (obsValue != null) {
			for (String s : obsValue.split("_")) {
				arrNodeValues.add(s);
			}
			if (arrNodeValues != null) {
				conceptValue = Integer.parseInt(arrNodeValues.get(0).getTextValue());
			}
		}
		return conceptValue;
	}
}
