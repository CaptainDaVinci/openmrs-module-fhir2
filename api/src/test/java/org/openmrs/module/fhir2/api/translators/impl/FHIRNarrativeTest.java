/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.translators.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.narrative.CustomThymeleafNarrativeGenerator;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Person;
import org.hl7.fhir.r4.model.Person.PersonLinkComponent;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.module.fhir2.FhirConstants;

@RunWith(MockitoJUnitRunner.class)
public class FHIRNarrativeTest {
	
	private FhirContext ctx;
	
	@Before
	public void setUpNarrative() {
		ctx = FhirContext.forR4();
		/* ctx.setNarrativeGenerator(new CustomThymeleafNarrativeGenerator("classpath:/overriden-custom-narrative.properties",
		        "classpath:/custom-narrative.properties"));*/
		ctx.setNarrativeGenerator(new CustomThymeleafNarrativeGenerator("classpath:/custom-narrative.properties"));
	}
	
	@Test
	public void printPersonNarrative() {
		Person person = new Person();
		person.setId("1234567789");
		
		HumanName hn = new HumanName();
		hn.addPrefix("Dr.");
		hn.addGiven("John");
		hn.setFamily("Watson");
		hn.addSuffix("Jr.");
		person.setName(Collections.singletonList(hn));
		
		person.setGender(AdministrativeGender.MALE);
		
		Calendar cal = Calendar.getInstance();
		cal.set(1966, 1, 1);
		Date date = cal.getTime();
		person.setBirthDate(date);
		
		Address ad = new Address();
		ad.addLine("#123");
		ad.addLine("New building, pleasant street");
		ad.setCity("Reykjavík");
		ad.setCountry("Iceland");
		ad.setPostalCode("123456");
		person.setAddress(Collections.singletonList(ad));
		
		person.setActive(true);
		
		ContactPoint cp = new ContactPoint();
		cp.setId("54321-54321-54321-54321");
		cp.setValue("1234567890");
		person.setTelecom(Collections.singletonList(cp));
		
		List<PersonLinkComponent> links = new ArrayList<>();
		PersonLinkComponent linkComponent = new PersonLinkComponent();
		String uri = FhirConstants.PATIENT + "/12345-12345-12345-12345";
		Reference patientReference = new Reference();
		patientReference.setDisplay("Patient Full name");
		patientReference.setId(uri);
		linkComponent.setTarget(patientReference);
		links.add(linkComponent);
		person.setLink(links);
		
		String output = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(person);
		System.out.println(output);
	}
}
