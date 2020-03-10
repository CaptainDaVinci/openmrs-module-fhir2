/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.dao.impl;

import static org.hibernate.criterion.Restrictions.eq;
import static org.hibernate.criterion.Restrictions.in;
import static org.hibernate.criterion.Restrictions.or;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang3.math.NumberUtils;
import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Criterion;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.openmrs.AllergenType;
import org.openmrs.Allergy;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirGlobalPropertyService;
import org.openmrs.module.fhir2.api.dao.FhirAllergyIntoleranceDao;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FhirAllergyIntoleranceDaoImpl extends BaseDaoImpl implements FhirAllergyIntoleranceDao {
	
	@Inject
	@Named("sessionFactory")
	private SessionFactory sessionFactory;
	
	@Inject
	private FhirGlobalPropertyService globalPropertyService;
	
	private List<String> severityConceptUuids;
	
	@Override
	public Allergy getAllergyIntoleranceByUuid(String uuid) {
		return (Allergy) sessionFactory.getCurrentSession().createCriteria(Allergy.class).add(eq("uuid", uuid))
		        .uniqueResult();
	}
	
	@Override
	public Collection<Allergy> searchForAllergies(ReferenceParam patientReference, TokenOrListParam category,
	        TokenOrListParam allergen, TokenOrListParam severity, TokenOrListParam manifestationCode,
	        TokenOrListParam clinicalStatus) {
		Criteria criteria = sessionFactory.getCurrentSession().createCriteria(Allergy.class);
		handlePatientReference(criteria, patientReference, "patient");
		handleAllergenCategory("allergen.allergenType", category);
		handleAllergen(criteria, allergen);
		handleSeverity(criteria, severity).ifPresent(criteria::add);
		handleManifestation(criteria, manifestationCode);
		handleBoolean("voided", setClinicalStatusTokenValue(clinicalStatus));
		
		return criteria.list();
	}
	
	private void handleManifestation(Criteria criteria, TokenOrListParam code) {
		if (code != null) {
			criteria.createAlias("reactions", "r");
			criteria.createAlias("r.reaction", "c");
			
			handleOrListParamBySystem(code, (system, tokens) -> {
				if (system.isEmpty()) {
					return Optional.of(
					    or(in("c.conceptId", tokensToParams(tokens).map(NumberUtils::toInt).collect(Collectors.toList())),
					        in("c.uuid", tokensToList(tokens))));
				} else {
					if (!containsAlias(criteria, "cm")) {
						criteria.createAlias("c.conceptMappings", "cm").createAlias("cm.conceptReferenceTerm", "crt");
					}
					
					return Optional.of(generateSystemQuery(system, tokensToList(tokens)));
				}
			}).ifPresent(criteria::add);
		}
	}
	
	private void handleAllergen(Criteria criteria, TokenOrListParam code) {
		if (code != null) {
			criteria.createAlias("allergen.codedAllergen", "c");
			
			handleOrListParamBySystem(code, (system, tokens) -> {
				if (system.isEmpty()) {
					return Optional.of(
					    or(in("c.conceptId", tokensToParams(tokens).map(NumberUtils::toInt).collect(Collectors.toList())),
					        in("c.uuid", tokensToList(tokens))));
				} else {
					if (!containsAlias(criteria, "cm")) {
						criteria.createAlias("c.conceptMappings", "cm").createAlias("cm.conceptReferenceTerm", "crt");
					}
					
					return Optional.of(generateSystemQuery(system, tokensToList(tokens)));
				}
			}).ifPresent(criteria::add);
		}
	}
	
	private Optional<Criterion> handleSeverity(Criteria criteria, TokenOrListParam severityParam) {
		if (severityParam == null) {
			return Optional.empty();
		}
		severityConceptUuids = globalPropertyService.getGlobalProperties(FhirConstants.GLOBAL_PROPERTY_MILD,
		    FhirConstants.GLOBAL_PROPERTY_MODERATE, FhirConstants.GLOBAL_PROPERTY_SEVERE,
		    FhirConstants.GLOBAL_PROPERTY_OTHER);
		
		criteria.createAlias("severity", "c");
		
		return handleOrListParam(severityParam, token -> {
			try {
				AllergyIntolerance.AllergyIntoleranceSeverity severity = AllergyIntolerance.AllergyIntoleranceSeverity
				        .fromCode(token.getValue());
				switch (severity) {
					case MILD:
						return Optional.of(eq("c.uuid", severityConceptUuids.get(0)));
					case MODERATE:
						return Optional.of(eq("c.uuid", severityConceptUuids.get(1)));
					case SEVERE:
						return Optional.of(eq("c.uuid", severityConceptUuids.get(2)));
					case NULL:
						return Optional.of(eq("c.uuid", severityConceptUuids.get(3)));
				}
			}
			catch (FHIRException ignored) {}
			return Optional.empty();
		});
	}
	
	private Optional<Criterion> handleAllergenCategory(String propertyName, TokenOrListParam categoryParam) {
		if (categoryParam == null) {
			return Optional.empty();
		}
		
		return handleOrListParam(categoryParam, token -> {
			try {
				AllergyIntolerance.AllergyIntoleranceCategory category = AllergyIntolerance.AllergyIntoleranceCategory
				        .fromCode(token.getValue());
				switch (category) {
					case FOOD:
						return Optional.of(eq(propertyName, AllergenType.FOOD));
					case MEDICATION:
						return Optional.of(eq(propertyName, AllergenType.DRUG));
					case ENVIRONMENT:
						return Optional.of(eq(propertyName, AllergenType.ENVIRONMENT));
					case NULL:
						return Optional.of(eq(propertyName, AllergenType.OTHER));
				}
			}
			catch (FHIRException ignored) {}
			return Optional.empty();
		});
		
	}
	
	public TokenOrListParam setClinicalStatusTokenValue(TokenOrListParam statusParam) {
		if (statusParam != null && !statusParam.getValuesAsQueryTokens().isEmpty()) {
			switch (statusParam.getValuesAsQueryTokens().get(0).getValue()) {
				case "active":
					return statusParam.add("false");
				case "inactive":
					return statusParam.add("true");
			}
		}
		return null;
	}
	
}
