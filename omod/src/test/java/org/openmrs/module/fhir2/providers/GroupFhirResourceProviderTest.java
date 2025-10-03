package org.openmrs.module.fhir2.providers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import ca.uhn.fhir.rest.api.Constants;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PatchTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceAndListParam;
import ca.uhn.fhir.rest.param.ReferenceOrListParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.SimpleBundleProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Group;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.openmrs.Location;
import org.openmrs.api.LocationService;
import org.openmrs.module.cohort.CohortM;
import org.openmrs.module.cohort.CohortType;
import org.openmrs.module.cohort.api.CohortService;
import org.openmrs.module.cohort.api.CohortTypeService;
import org.openmrs.module.fhir2.api.translators.GroupTranslator;

@RunWith(MockitoJUnitRunner.class)
public class GroupFhirResourceProviderTest {
	
	private static final String GROUP_UUID = "5fa2a82f-23e9-41b7-a6b5-6bcae2c5b383";
	
	@Mock
	private CohortService cohortService;
	
	@Mock
	private GroupTranslator groupTranslator;
	
	@Mock
	private CohortTypeService cohortTypeService;
	
	@Mock
	private LocationService locationService;
	
	private GroupFhirResourceProvider provider;
	
	@Before
	public void setup() {
		provider = new GroupFhirResourceProvider();
		provider.setCohortService(cohortService);
		provider.setGroupTranslator(groupTranslator);
		provider.setCohortTypeService(cohortTypeService);
		provider.setLocationService(locationService);
	}
	
	@Test
	public void getResourceType_shouldReturnGroupClass() {
		assertThat(provider.getResourceType(), equalTo(Group.class));
	}
	
	@Test
	public void searchGroups_shouldReturnTranslatedResults() {
		CohortM cohort = new CohortM();
		Group translatedGroup = new Group();
		
		when(cohortService.findMatchingCohortMs("vl", null, null, null, false))
		        .thenReturn(Collections.singletonList(cohort));
		when(groupTranslator.toFhirResource(cohort)).thenReturn(translatedGroup);
		
		IBundleProvider result = provider.searchGroups(new StringParam("vl"), null, null);
		
		assertThat(result, instanceOf(SimpleBundleProvider.class));
		
		List<IBaseResource> resources = result.getResources(0, 10);
		assertThat(resources, contains(translatedGroup));
		
		verify(cohortService).findMatchingCohortMs("vl", null, null, null, false);
		verify(groupTranslator).toFhirResource(cohort);
	}
	
	@Test
	public void searchGroups_shouldHandleNullNameParameter() {
		when(cohortService.findMatchingCohortMs(null, null, null, null, false)).thenReturn(Collections.emptyList());
		
		IBundleProvider result = provider.searchGroups(null, null, null);
		
		assertThat(result.getResources(0, 1), is(Collections.emptyList()));
		
		verify(cohortService).findMatchingCohortMs(null, null, null, null, false);
		verifyNoInteractions(groupTranslator);
	}
	
	@Test
	public void searchGroups_shouldFilterByListTypeAndLocation() {
		CohortType cohortType = new CohortType();
		cohortType.setUuid("type-uuid");
		cohortType.setName("System List");
		Location firstLocation = new Location(1);
		firstLocation.setUuid("first-location");
		Location secondLocation = new Location(2);
		secondLocation.setUuid("second-location");
		
		CohortM cohort = new CohortM();
		Group translatedGroup = new Group();
		
		TokenParam listType = new TokenParam();
		listType.setValue("type-uuid");
		
		ReferenceParam firstReference = new ReferenceParam();
		firstReference.setValue("Location/first-location");
		ReferenceParam secondReference = new ReferenceParam();
		secondReference.setValue("Location/second-location");
		
		ReferenceOrListParam orListParam = new ReferenceOrListParam();
		orListParam.addOr(firstReference);
		orListParam.addOr(secondReference);
		ReferenceAndListParam locationParam = new ReferenceAndListParam();
		locationParam.addAnd(orListParam);
		
		when(cohortTypeService.getCohortTypeByUuid("type-uuid")).thenReturn(cohortType);
		when(locationService.getLocationByUuid("first-location")).thenReturn(firstLocation);
		when(locationService.getLocationByUuid("second-location")).thenReturn(secondLocation);
		when(cohortService.findMatchingCohortMs(eq(null), eq(null), eq(cohortType), anyCollection(), eq(false)))
		        .thenReturn(Collections.singletonList(cohort));
		when(groupTranslator.toFhirResource(cohort)).thenReturn(translatedGroup);
		
		IBundleProvider result = provider.searchGroups(null, listType, locationParam);
		
		assertThat(result.getResources(0, 10), contains(translatedGroup));
		
		verify(cohortTypeService).getCohortTypeByUuid("type-uuid");
		ArgumentCaptor<Collection<Location>> locationCaptor = ArgumentCaptor.forClass(Collection.class);
		verify(cohortService).findMatchingCohortMs(eq(null), eq(null), eq(cohortType), locationCaptor.capture(), eq(false));
		assertThat(locationCaptor.getValue(), containsInAnyOrder(firstLocation, secondLocation));
	}
	
	@Test
	public void getGroupById_shouldReturnTranslatedGroup() {
		CohortM cohort = new CohortM();
		Group translatedGroup = new Group();
		
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(cohort);
		when(groupTranslator.toFhirResource(cohort)).thenReturn(translatedGroup);
		
		Group result = provider.getGroupById(new IdType("Group", GROUP_UUID));
		
		assertThat(result, is(translatedGroup));
		verify(groupTranslator).toFhirResource(cohort);
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void getGroupById_shouldThrowIfCohortNotFound() {
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(null);
		
		provider.getGroupById(new IdType("Group", GROUP_UUID));
	}
	
	@Test
	public void createGroup_shouldPersistTranslatedCohort() {
		Group input = new Group();
		CohortM cohortToSave = new CohortM();
		CohortM savedCohort = new CohortM();
		savedCohort.setUuid(GROUP_UUID);
		Group translatedGroup = new Group();
		
		when(groupTranslator.toOpenmrsType(input)).thenReturn(cohortToSave);
		when(cohortService.saveCohortM(cohortToSave)).thenReturn(savedCohort);
		when(groupTranslator.toFhirResource(savedCohort)).thenReturn(translatedGroup);
		
		MethodOutcome outcome = provider.createGroup(input);
		
		assertThat(outcome.getCreated(), is(true));
		assertThat(outcome.getResource(), is(translatedGroup));
		assertThat(outcome.getId() == null || outcome.getId().isEmpty(), is(true));
		
		verify(groupTranslator).toOpenmrsType(input);
		verify(groupTranslator).toFhirResource(savedCohort);
		verify(cohortService).saveCohortM(cohortToSave);
	}
	
	@Test
	public void updateGroup_shouldPersistExistingCohort() {
		IdType id = new IdType("Group", GROUP_UUID);
		Group input = new Group();
		CohortM existing = new CohortM();
		CohortM updated = new CohortM();
		CohortM saved = new CohortM();
		saved.setUuid(GROUP_UUID);
		Group translatedGroup = new Group();
		
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(existing);
		when(groupTranslator.toOpenmrsType(existing, input)).thenReturn(updated);
		when(cohortService.saveCohortM(updated)).thenReturn(saved);
		when(groupTranslator.toFhirResource(saved)).thenReturn(translatedGroup);
		
		MethodOutcome outcome = provider.updateGroup(id, input);
		
		assertThat(outcome.getCreated(), is(false));
		assertThat(outcome.getResource(), is(translatedGroup));
		assertThat(outcome.getId() == null || outcome.getId().isEmpty(), is(true));
		
		verify(groupTranslator).toOpenmrsType(existing, input);
		verify(cohortService).saveCohortM(updated);
		verify(groupTranslator).toFhirResource(saved);
	}
	
	@Test(expected = InvalidRequestException.class)
	public void updateGroup_shouldThrowWhenIdMissing() {
		provider.updateGroup(null, new Group());
	}
	
	@Test(expected = InvalidRequestException.class)
	public void updateGroup_shouldThrowWhenIdPartMissing() {
		provider.updateGroup(new IdType(), new Group());
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void updateGroup_shouldThrowWhenCohortNotFound() {
		IdType id = new IdType("Group", GROUP_UUID);
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(null);
		
		provider.updateGroup(id, new Group());
	}
	
	@Test
	public void patchGroup_shouldApplyJsonPatchAndPersistChanges() {
		IdType id = new IdType("Group", GROUP_UUID);
		CohortM existing = new CohortM();
		CohortM updated = new CohortM();
		CohortM saved = new CohortM();
		Group translated = new Group();
		translated.setName("Initial name");
		Group savedGroup = new Group();
		
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(existing);
		when(groupTranslator.toFhirResource(existing)).thenReturn(translated);
		when(groupTranslator.toOpenmrsType(eq(existing), Mockito.any(Group.class))).thenReturn(updated);
		when(cohortService.saveCohortM(updated)).thenReturn(saved);
		when(groupTranslator.toFhirResource(saved)).thenReturn(savedGroup);
		
		String patchBody = "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"Updated Group\"}]";
		
		RequestDetails requestDetails = Mockito.mock(RequestDetails.class);
		when(requestDetails.getHeader("Content-Type")).thenReturn(null);
		
		MethodOutcome outcome = provider.patchGroup(id, PatchTypeEnum.JSON_PATCH, patchBody, requestDetails);
		
		assertThat(outcome.getResource(), is(savedGroup));
		ArgumentCaptor<Group> patchedCaptor = ArgumentCaptor.forClass(Group.class);
		verify(groupTranslator).toOpenmrsType(eq(existing), patchedCaptor.capture());
		assertThat(patchedCaptor.getValue().getName(), notNullValue());
		assertThat(patchedCaptor.getValue().getName(), is("Updated Group"));
		verify(cohortService).saveCohortM(updated);
		verify(groupTranslator).toFhirResource(existing);
		verify(groupTranslator).toFhirResource(saved);
		verify(requestDetails).getHeader(Constants.HEADER_CONTENT_TYPE);
	}
	
	@Test
	public void patchGroup_shouldApplyJsonMergePatchWhenContentTypeMatches() {
		IdType id = new IdType("Group", GROUP_UUID);
		CohortM existing = new CohortM();
		CohortM updated = new CohortM();
		CohortM saved = new CohortM();
		Group translated = new Group();
		translated.setName("Initial name");
		Group savedGroup = new Group();
		
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(existing);
		when(groupTranslator.toFhirResource(existing)).thenReturn(translated);
		when(groupTranslator.toOpenmrsType(eq(existing), Mockito.any(Group.class))).thenReturn(updated);
		when(cohortService.saveCohortM(updated)).thenReturn(saved);
		when(groupTranslator.toFhirResource(saved)).thenReturn(savedGroup);
		
		RequestDetails requestDetails = Mockito.mock(RequestDetails.class);
		when(requestDetails.getHeader("Content-Type")).thenReturn("application/merge-patch+json");
		
		String patchBody = "{\"name\":\"Updated Group\"}";
		
		MethodOutcome outcome = provider.patchGroup(id, PatchTypeEnum.JSON_PATCH, patchBody, requestDetails);
		
		assertThat(outcome.getResource(), is(savedGroup));
		ArgumentCaptor<Group> patchedCaptor = ArgumentCaptor.forClass(Group.class);
		verify(groupTranslator).toOpenmrsType(eq(existing), patchedCaptor.capture());
		assertThat(patchedCaptor.getValue().getName(), notNullValue());
		assertThat(patchedCaptor.getValue().getName(), is("Updated Group"));
		verify(cohortService).saveCohortM(updated);
		verify(requestDetails).getHeader(Constants.HEADER_CONTENT_TYPE);
	}
	
	@Test
	public void patchGroup_shouldApplyXmlPatchWhenRequested() {
		IdType id = new IdType("Group", GROUP_UUID);
		CohortM existing = new CohortM();
		CohortM updated = new CohortM();
		CohortM saved = new CohortM();
		Group translated = new Group();
		translated.setName("Initial name");
		Group savedGroup = new Group();
		
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(existing);
		when(groupTranslator.toFhirResource(existing)).thenReturn(translated);
		when(groupTranslator.toOpenmrsType(eq(existing), Mockito.any(Group.class))).thenReturn(updated);
		when(cohortService.saveCohortM(updated)).thenReturn(saved);
		when(groupTranslator.toFhirResource(saved)).thenReturn(savedGroup);
		
		String patchBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
		        + "<diff xmlns=\"urn:ietf:params:xml:ns:xml-patch\" xmlns:f=\"http://hl7.org/fhir\">\n"
		        + "  <replace sel=\"/f:Group/f:name/@value\">Updated Group</replace>\n" + "</diff>";
		
		MethodOutcome outcome = provider.patchGroup(id, PatchTypeEnum.XML_PATCH, patchBody, null);
		
		assertThat(outcome.getResource(), is(savedGroup));
		ArgumentCaptor<Group> patchedCaptor = ArgumentCaptor.forClass(Group.class);
		verify(groupTranslator).toOpenmrsType(eq(existing), patchedCaptor.capture());
		assertThat(patchedCaptor.getValue().getName(), notNullValue());
		assertThat(patchedCaptor.getValue().getName(), is("Updated Group"));
		verify(cohortService).saveCohortM(updated);
	}
	
	@Test
	public void patchGroup_shouldValidateMissingId() {
		try {
			provider.patchGroup(null, PatchTypeEnum.JSON_PATCH, "[]", null);
			fail("Expected InvalidRequestException to be thrown");
		}
		catch (InvalidRequestException ex) {
			verifyNoInteractions(cohortService);
			verifyNoInteractions(groupTranslator);
		}
	}
	
	@Test
	public void patchGroup_shouldThrowWhenCohortNotFound() {
		IdType id = new IdType("Group", GROUP_UUID);
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(null);
		
		try {
			provider.patchGroup(id, PatchTypeEnum.JSON_PATCH, "[]", null);
			fail("Expected ResourceNotFoundException to be thrown");
		}
		catch (ResourceNotFoundException ex) {
			verify(cohortService).getCohortMByUuid(GROUP_UUID);
			verifyNoInteractions(groupTranslator);
		}
	}
	
	@Test
	public void deleteGroup_shouldVoidCohort() {
		CohortM cohort = new CohortM();
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(cohort);
		
		OperationOutcome outcome = provider.deleteGroup(new IdType("Group", GROUP_UUID));
		
		assertThat(outcome, notNullValue());
		verify(cohortService).voidCohortM(cohort, "voided via FHIR request");
	}
	
	@Test(expected = ResourceNotFoundException.class)
	public void deleteGroup_shouldThrowWhenCohortNotFound() {
		when(cohortService.getCohortMByUuid(GROUP_UUID)).thenReturn(null);
		
		provider.deleteGroup(new IdType("Group", GROUP_UUID));
	}
}
