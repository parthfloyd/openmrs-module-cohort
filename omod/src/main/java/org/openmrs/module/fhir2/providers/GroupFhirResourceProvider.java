/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.providers;

import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceOrListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Group;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.module.cohort.CohortM;
import org.openmrs.module.cohort.CohortType;
import org.openmrs.module.cohort.api.CohortService;
import org.openmrs.module.cohort.api.CohortTypeService;
import org.openmrs.module.fhir2.api.translators.GroupTranslator;
import org.openmrs.module.fhir2.providers.util.FhirProviderUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * FHIR resource provider for {@link Group} resources backed by {@link CohortM}.
 */
@Primary
@Component("cohortGroupFhirResourceProvider")
@Setter(AccessLevel.PACKAGE)
@Getter(AccessLevel.PROTECTED)
public class GroupFhirResourceProvider implements IResourceProvider {
	
	@Autowired
	private CohortService cohortService;
	
	@Autowired
	private GroupTranslator groupTranslator;
	
	@Autowired
	private CohortTypeService cohortTypeService;
	
	@Autowired
	private LocationService locationService;
	
	@Override
	public Class<Group> getResourceType() {
		return Group.class;
	}
	
	@Search
	public IBundleProvider searchGroups(@OptionalParam(name = "name") StringParam name,
	        @OptionalParam(name = "list-type") TokenParam listType,
	        @OptionalParam(name = "location") ReferenceAndListParam location) {
		String nameMatch = name != null ? name.getValue() : null;
		CohortType cohortType = resolveCohortType(listType);
		Collection<Location> locations = resolveLocations(location);
		List<CohortM> cohorts = cohortService.findMatchingCohortMs(nameMatch, null, cohortType, locations, false);
		List<Group> results = new ArrayList<>(cohorts.size());
		for (CohortM cohort : cohorts) {
			results.add(groupTranslator.toFhirResource(cohort));
		}
		return new SimpleBundleProvider(results);
	}
	
	@Read
	public Group getGroupById(@IdParam @Nonnull IdType id) {
		CohortM cohort = cohortService.getCohortMByUuid(id.getIdPart());
		if (cohort == null) {
			throw new ResourceNotFoundException("Could not find Group with Id " + id.getIdPart());
		}
		return groupTranslator.toFhirResource(cohort);
	}
	
	@Create
	@SuppressWarnings("unused")
	public MethodOutcome createGroup(@ResourceParam Group group) {
		CohortM cohort = groupTranslator.toOpenmrsType(group);
		CohortM saved = cohortService.saveCohortM(cohort);
		return FhirProviderUtils.buildCreate(groupTranslator.toFhirResource(saved));
	}
	
	@Update
	@SuppressWarnings("unused")
	public MethodOutcome updateGroup(@IdParam IdType id, @ResourceParam Group group) {
		if (id == null || id.getIdPart() == null) {
			throw new InvalidRequestException("id must be specified to update");
		}
		CohortM existing = cohortService.getCohortMByUuid(id.getIdPart());
		if (existing == null) {
			throw new ResourceNotFoundException("Could not find Group with Id " + id.getIdPart());
		}
		CohortM updated = groupTranslator.toOpenmrsType(existing, group);
		CohortM saved = cohortService.saveCohortM(updated);
		return FhirProviderUtils.buildUpdate(groupTranslator.toFhirResource(saved));
	}
	
	@Delete
	@SuppressWarnings("unused")
	public OperationOutcome deleteGroup(@IdParam @Nonnull IdType id) {
		CohortM cohort = cohortService.getCohortMByUuid(id.getIdPart());
		if (cohort == null) {
			throw new ResourceNotFoundException("Could not find Group with Id " + id.getIdPart());
		}
		cohortService.voidCohortM(cohort, "voided via FHIR request");
		return FhirProviderUtils.buildDeleteR4();
	}
	
	private CohortType resolveCohortType(TokenParam listType) {
		if (listType == null || StringUtils.isBlank(listType.getValue())) {
			return null;
		}
		
		String value = listType.getValue();
		CohortType cohortType = cohortTypeService.getCohortTypeByUuid(value);
		if (cohortType == null) {
			cohortType = cohortTypeService.getCohortTypeByName(value);
		}
		return cohortType;
	}
	
	private Collection<Location> resolveLocations(ReferenceAndListParam locationParam) {
		if (locationParam == null) {
			return null;
		}
		
		Collection<Location> locations = new HashSet<>();
		for (ReferenceOrListParam orList : locationParam.getValuesAsQueryTokens()) {
			for (ReferenceParam referenceParam : orList.getValuesAsQueryTokens()) {
				Location location = resolveLocation(referenceParam);
				if (location != null) {
					locations.add(location);
				}
			}
		}
		
		return locations.isEmpty() ? null : locations;
	}
	
	private Location resolveLocation(ReferenceParam referenceParam) {
		if (referenceParam == null) {
			return null;
		}
		
		Location location = null;
		if (StringUtils.isNotBlank(referenceParam.getIdPart())) {
			location = locationService.getLocationByUuid(referenceParam.getIdPart());
		}
		
		if (location == null && StringUtils.isNotBlank(referenceParam.getValue())) {
			location = locationService.getLocation(referenceParam.getValue());
		}
		
		return location;
	}
}
