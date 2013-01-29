package org.openlmis.rnr.service;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openlmis.core.builder.ProcessingPeriodBuilder;
import org.openlmis.core.builder.ProductBuilder;
import org.openlmis.core.builder.SupervisoryNodeBuilder;
import org.openlmis.core.domain.*;
import org.openlmis.core.exception.DataException;
import org.openlmis.core.message.OpenLmisMessage;
import org.openlmis.core.service.*;
import org.openlmis.rnr.builder.RequisitionBuilder;
import org.openlmis.rnr.builder.RnrLineItemBuilder;
import org.openlmis.rnr.domain.Rnr;
import org.openlmis.rnr.domain.RnrColumn;
import org.openlmis.rnr.domain.RnrLineItem;
import org.openlmis.rnr.domain.RnrStatus;
import org.openlmis.rnr.repository.RequisitionRepository;
import org.openlmis.rnr.repository.RnrTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.natpryce.makeiteasy.MakeItEasy.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.*;
import static org.openlmis.core.builder.ProcessingPeriodBuilder.defaultProcessingPeriod;
import static org.openlmis.core.builder.ProcessingPeriodBuilder.id;
import static org.openlmis.core.builder.ProductBuilder.code;
import static org.openlmis.core.builder.ProductBuilder.defaultProduct;
import static org.openlmis.core.domain.Right.*;
import static org.openlmis.rnr.builder.RequisitionBuilder.defaultRnr;
import static org.openlmis.rnr.builder.RequisitionBuilder.status;
import static org.openlmis.rnr.builder.RnrColumnBuilder.*;
import static org.openlmis.rnr.domain.RnrStatus.*;
import static org.openlmis.rnr.service.RequisitionService.*;

@RunWith(MockitoJUnitRunner.class)
public class RequisitionServiceTest {

  private static final Integer HIV = 1;
  private static final Integer FACILITY_ID = 1;
  private static final Integer PROGRAM_ID = 2;
  private static final Integer PERIOD_ID = 10;
  private static final Integer USER_ID = 1;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();
  @Autowired
  private RequisitionService requisitionService;
  @Mock
  private FacilityApprovedProductService facilityApprovedProductService;
  @Mock
  private RequisitionRepository requisitionRepository;
  @Mock
  private RnrTemplateRepository rnrTemplateRepository;
  @Mock
  private SupervisoryNodeService supervisoryNodeService;
  @Mock
  private RoleRightsService roleRightService;
  @Mock
  private ProgramService programService;
  @Mock
  private ProcessingScheduleService processingScheduleService;
  @Mock
  private FacilityService facilityService;

  private Rnr submittedRnr;
  private Rnr initiatedRnr;
  private Rnr authorizedRnr;

  @Before
  public void setup() {
    requisitionService = new RequisitionService(requisitionRepository, rnrTemplateRepository, facilityApprovedProductService,
      supervisoryNodeService, roleRightService, programService, processingScheduleService, facilityService);
    submittedRnr = make(a(RequisitionBuilder.defaultRnr, with(status, SUBMITTED)));
    initiatedRnr = make(a(RequisitionBuilder.defaultRnr, with(status, INITIATED)));
    authorizedRnr = make(a(RequisitionBuilder.defaultRnr, with(status, AUTHORIZED)));
  }

  @Test
  public void shouldInitRequisition() throws Exception {
    Date date = new Date();
    Rnr requisition = createRequisition(PERIOD_ID, null);
    ProcessingPeriod validPeriod = setupForInitRnr(date, requisition, PERIOD_ID);

    List<FacilityApprovedProduct> facilityApprovedProducts = new ArrayList<>();
    ProgramProduct programProduct = new ProgramProduct(null, make(a(defaultProduct)), 10, true);
    facilityApprovedProducts.add(new FacilityApprovedProduct("warehouse", programProduct, 30));
    when(facilityApprovedProductService.getFullSupplyFacilityApprovedProductByFacilityAndProgram(FACILITY_ID, PROGRAM_ID)).thenReturn(facilityApprovedProducts);

    Rnr rnr = requisitionService.initiate(FACILITY_ID, PROGRAM_ID, PERIOD_ID, 1);

    verify(facilityApprovedProductService).getFullSupplyFacilityApprovedProductByFacilityAndProgram(FACILITY_ID, PROGRAM_ID);
    verify(requisitionRepository).insert(rnr);
    assertThat(rnr.getLineItems().size(), is(1));
    assertThat(rnr.getPeriodId(), is(validPeriod.getId()));
  }

  @Test
  public void shouldGetRequisition() throws Exception {
    Rnr rnr = new Rnr();
    when(requisitionRepository.getRequisition(FACILITY_ID, PROGRAM_ID, PERIOD_ID)).thenReturn(rnr);

    assertThat(requisitionService.get(FACILITY_ID, PROGRAM_ID, PERIOD_ID), is(rnr));
  }

  @Test
  public void shouldGetAllPeriodsForInitiatingRequisitionWhenThereIsAtLeastOneExistingRequisitionInThePostSubmitFlow() throws Exception {
    DateTime date1 = new DateTime();
    DateTime date2 = date1.minusMonths(1);
    DateTime date3 = date1.minusMonths(2);
    DateTime date4 = date1.minusMonths(3);

    ProcessingPeriod processingPeriod1 = createProcessingPeriod(10, date1);
    ProcessingPeriod processingPeriod2 = createProcessingPeriod(20, date2);
    ProcessingPeriod processingPeriod3 = createProcessingPeriod(30, date3);
    ProcessingPeriod processingPeriod4 = createProcessingPeriod(40, date4);

    createRequisition(processingPeriod1.getId(), AUTHORIZED);
    Rnr rnr2 = createRequisition(processingPeriod2.getId(), APPROVED);
    createRequisition(processingPeriod3.getId(), INITIATED);

    when(programService.getProgramStartDate(FACILITY_ID, PROGRAM_ID)).thenReturn(date1.toDate());
    when(requisitionRepository.getLastRequisitionToEnterThePostSubmitFlow(FACILITY_ID, PROGRAM_ID)).thenReturn(rnr2);
    when(processingScheduleService.getAllPeriodsAfterDateAndPeriod(FACILITY_ID, PROGRAM_ID, date1.toDate(), processingPeriod2.getId())).
      thenReturn(Arrays.asList(processingPeriod3, processingPeriod4));

    List<ProcessingPeriod> periods = requisitionService.getAllPeriodsForInitiatingRequisition(FACILITY_ID, PROGRAM_ID);

    assertThat(periods.size(), is(2));
    assertThat(periods.get(0), is(processingPeriod3));
    assertThat(periods.get(1), is(processingPeriod4));
  }

  @Test
  public void shouldGetAllPeriodsForInitiatingRequisitionWhenThereIsNoRequisitionInThePostSubmitFlow() throws Exception {
    DateTime date1 = new DateTime();
    DateTime date2 = date1.minusMonths(1);

    ProcessingPeriod processingPeriod1 = createProcessingPeriod(10, date1);
    ProcessingPeriod processingPeriod2 = createProcessingPeriod(20, date2);

    when(programService.getProgramStartDate(FACILITY_ID, PROGRAM_ID)).thenReturn(date1.toDate());
    when(requisitionRepository.getLastRequisitionToEnterThePostSubmitFlow(FACILITY_ID, PROGRAM_ID)).thenReturn(null);
    when(processingScheduleService.getAllPeriodsAfterDateAndPeriod(FACILITY_ID, PROGRAM_ID, date1.toDate(), null)).
      thenReturn(Arrays.asList(processingPeriod1, processingPeriod2));

    List<ProcessingPeriod> periods = requisitionService.getAllPeriodsForInitiatingRequisition(FACILITY_ID, PROGRAM_ID);

    assertThat(periods.size(), is(2));
    assertThat(periods.get(0), is(processingPeriod1));
    assertThat(periods.get(1), is(processingPeriod2));
  }

  private Rnr createRequisition(int periodId, RnrStatus status) {
    return make(a(RequisitionBuilder.defaultRnr,
      with(RequisitionBuilder.periodId, periodId),
      with(RequisitionBuilder.status, status)));
  }

  private ProcessingPeriod createProcessingPeriod(int id, DateTime startDate) {
    ProcessingPeriod processingPeriod = make(a(defaultProcessingPeriod,
      with(ProcessingPeriodBuilder.startDate, startDate.toDate())));
    processingPeriod.setId(id);
    return processingPeriod;
  }

  @Test
  public void shouldNotInitRequisitionIfTemplateNotDefined() {
    when(rnrTemplateRepository.fetchRnrTemplateColumns(PROGRAM_ID)).thenReturn(new ArrayList<RnrColumn>());
    expectedException.expect(DataException.class);
    expectedException.expectMessage(RNR_TEMPLATE_NOT_INITIATED_ERROR);
    Rnr rnr = requisitionService.initiate(FACILITY_ID, HIV, null, 1);
    verify(facilityApprovedProductService, never()).getFullSupplyFacilityApprovedProductByFacilityAndProgram(FACILITY_ID, HIV);
    verify(requisitionRepository, never()).insert(rnr);
  }

  @Test
  public void shouldNotInitRequisitionIfPeriodDoesNotAllowInitiation() throws Exception {
    Date date = new Date();
    Rnr requisition = createRequisition(PERIOD_ID, null);
    ProcessingPeriod validPeriod = setupForInitRnr(date, requisition, 1);

    expectedException.expect(DataException.class);
    expectedException.expectMessage(RNR_PREVIOUS_NOT_FILLED_ERROR);

    requisitionService.initiate(FACILITY_ID, PROGRAM_ID, PERIOD_ID, USER_ID);

    verify(programService).getProgramStartDate(FACILITY_ID, PROGRAM_ID);
    verify(requisitionRepository).getLastRequisitionToEnterThePostSubmitFlow(FACILITY_ID, PROGRAM_ID);
    verify(processingScheduleService).getAllPeriodsAfterDateAndPeriod(FACILITY_ID, PROGRAM_ID, date, validPeriod.getId());
  }

  private ProcessingPeriod setupForInitRnr(Date date, Rnr requisition, Integer validPeriodId) {
    final ProcessingPeriod validPeriod = new ProcessingPeriod();
    validPeriod.setId(validPeriodId);

    when(rnrTemplateRepository.fetchRnrTemplateColumns(PROGRAM_ID)).thenReturn(Arrays.asList(make(a(defaultRnrColumn))));
    when(programService.getProgramStartDate(FACILITY_ID, PROGRAM_ID)).thenReturn(date);
    when(requisitionRepository.getLastRequisitionToEnterThePostSubmitFlow(FACILITY_ID, PROGRAM_ID)).thenReturn(requisition);
    when(processingScheduleService.getAllPeriodsAfterDateAndPeriod(FACILITY_ID, PROGRAM_ID, date, PERIOD_ID)).
      thenReturn(Arrays.asList(validPeriod));
    return validPeriod;
  }

  @Test
  public void shouldReturnMessageWhileSubmittingRnrIfSupervisingNodeNotPresent() {
    Rnr rnr = spy(make(a(defaultRnr)));

    when(requisitionRepository.getById(rnr.getId())).thenReturn(initiatedRnr);
    doReturn(true).when(rnr).validate(false);
    when(supervisoryNodeService.getFor(rnr.getFacilityId(), rnr.getProgramId())).thenReturn(null);

    OpenLmisMessage message = requisitionService.submit(rnr);
    verify(requisitionRepository).update(rnr);
    assertThat(rnr.getStatus(), is(SUBMITTED));
    assertThat(message.getCode(), is("rnr.submitted.without.supervisor"));
  }

  @Test
  public void shouldSubmitValidRnrWithSubmittedDateAndSetMessage() {
    Rnr rnr = spy(make(a(defaultRnr)));
    when(requisitionRepository.getById(rnr.getId())).thenReturn(initiatedRnr);
    doReturn(true).when(rnr).validate(false);
    when(supervisoryNodeService.getFor(rnr.getFacilityId(), rnr.getProgramId())).thenReturn(new SupervisoryNode());
    OpenLmisMessage message = requisitionService.submit(rnr);
    verify(requisitionRepository).update(rnr);

    assertThat(rnr.getSubmittedDate(), is(notNullValue()));
    assertThat(rnr.getStatus(), is(SUBMITTED));
    assertThat(message.getCode(), is("rnr.submitted.success"));
  }

  @Test
  public void shouldAuthorizeAValidRnrAndTagWithSupervisoryNode() throws Exception {
    Rnr rnr = spy(make(a(defaultRnr)));
    when(requisitionRepository.getById(rnr.getId())).thenReturn(submittedRnr);
    when(rnrTemplateRepository.isFormulaValidationRequired(rnr.getProgramId())).thenReturn(true);
    doReturn(true).when(rnr).validate(true);
    when(supervisoryNodeService.getApproverFor(rnr.getFacilityId(), rnr.getProgramId())).thenReturn(new User());
    SupervisoryNode approverNode = new SupervisoryNode();
    when(supervisoryNodeService.getFor(rnr.getFacilityId(), rnr.getProgramId())).thenReturn(approverNode);

    OpenLmisMessage authorize = requisitionService.authorize(rnr);

    verify(rnrTemplateRepository).isFormulaValidationRequired(rnr.getProgramId());
    verify(rnr).validate(true);
    verify(requisitionRepository).update(rnr);
    assertThat(rnr.getStatus(), is(AUTHORIZED));
    assertThat(rnr.getSupervisoryNodeId(), is(approverNode.getId()));
    assertThat(authorize.getCode(), is(RNR_AUTHORIZED_SUCCESSFULLY));

  }

  @Test
  public void shouldNotOverwriteSubmittedDateWhenAuthorizing() {
    Date submittedDate = new Date(1465555522222L);
    submittedRnr.setSubmittedDate(submittedDate);
    Rnr rnrForAuthorizing = spy(make(a(defaultRnr)));
    when(requisitionRepository.getById(rnrForAuthorizing.getId())).thenReturn(submittedRnr);
    SupervisoryNode node = make(a(SupervisoryNodeBuilder.defaultSupervisoryNode));
    when(supervisoryNodeService.getFor(rnrForAuthorizing.getFacilityId(), rnrForAuthorizing.getProgramId())).thenReturn(node);

    requisitionService.authorize(rnrForAuthorizing);

    verify(requisitionRepository).update(rnrForAuthorizing);
    assertThat(rnrForAuthorizing.getSubmittedDate(), is(submittedDate));
  }

  @Test
  public void shouldAuthorizeAValidRnrAndAdviseUserIfRnrDoesNotHaveApprover() throws Exception {
    Rnr rnr = spy(make(a(defaultRnr)));
    when(requisitionRepository.getById(rnr.getId())).thenReturn(submittedRnr);
    when(rnrTemplateRepository.isFormulaValidationRequired(rnr.getProgramId())).thenReturn(true);
    when(supervisoryNodeService.getApproverFor(rnr.getFacilityId(), rnr.getProgramId())).thenReturn(null);
    SupervisoryNode node = make(a(SupervisoryNodeBuilder.defaultSupervisoryNode));
    when(supervisoryNodeService.getFor(rnr.getFacilityId(), rnr.getProgramId())).thenReturn(node);
    doReturn(true).when(rnr).validate(true);

    OpenLmisMessage openLmisMessage = requisitionService.authorize(rnr);

    verify(rnrTemplateRepository).isFormulaValidationRequired(rnr.getProgramId());
    verify(rnr).validate(true);
    verify(requisitionRepository).update(rnr);
    assertThat(rnr.getStatus(), is(AUTHORIZED));
    assertThat(openLmisMessage.getCode(), is(RNR_AUTHORIZED_SUCCESSFULLY_WITHOUT_SUPERVISOR));
  }

  @Test
  public void shouldNotAuthorizeInvalidRnr() throws Exception {
    Rnr rnr = spy(make(a(defaultRnr)));
    when(requisitionRepository.getById(rnr.getId())).thenReturn(submittedRnr);
    when(rnrTemplateRepository.isFormulaValidationRequired(rnr.getProgramId())).thenReturn(true);
    doThrow(new DataException("error-message")).when(rnr).validate(true);

    expectedException.expect(DataException.class);
    expectedException.expectMessage("error-message");
    requisitionService.authorize(rnr);
  }

  @Test
  public void shouldNotAuthorizeRnrIfNotSubmitted() throws Exception {
    Rnr rnr = spy(make(a(defaultRnr)));
    when(requisitionRepository.getById(rnr.getId())).thenReturn(initiatedRnr);

    expectedException.expect(DataException.class);
    expectedException.expectMessage(RNR_AUTHORIZATION_ERROR);

    requisitionService.authorize(rnr);
  }

  @Test
  public void shouldSaveRnrIfStatusIsSubmittedAndUserHasAuthorizeRight() {
    Rnr rnr = spy(make(a(defaultRnr)));
    Integer userId = 1;
    rnr.setModifiedBy(userId);
    rnr.setStatus(SUBMITTED);
    List<Right> listUserRights = Arrays.asList(AUTHORIZE_REQUISITION);
    when(roleRightService.getRights(userId)).thenReturn(listUserRights);
    when(requisitionRepository.getRequisition(rnr.getFacilityId(), rnr.getProgramId(), rnr.getPeriodId())).thenReturn(make(a(defaultRnr)));

    requisitionService.save(rnr);
    verify(requisitionRepository).update(rnr);
  }

  @Test
  public void shouldSaveRnrIfStatusIsAuthorizedAndUserHasApproveRight() {
    Rnr rnr = spy(make(a(defaultRnr)));
    Integer userId = 1;
    rnr.setModifiedBy(userId);
    rnr.setStatus(AUTHORIZED);
    List<Right> listUserRights = Arrays.asList(APPROVE_REQUISITION);
    when(roleRightService.getRights(userId)).thenReturn(listUserRights);
    when(requisitionRepository.getRequisition(rnr.getFacilityId(), rnr.getProgramId(), rnr.getPeriodId())).thenReturn(make(a(defaultRnr)));


    requisitionService.save(rnr);
    verify(requisitionRepository).update(rnr);
  }

  @Test
  public void shouldSaveRnrIfStatusIsInitiatedAndUserHasCreateRight() {
    Rnr rnr = spy(make(a(defaultRnr)));
    Integer userId = 1;
    rnr.setModifiedBy(userId);
    rnr.setStatus(INITIATED);
    List<Right> listUserRights = Arrays.asList(CREATE_REQUISITION);
    when(roleRightService.getRights(userId)).thenReturn(listUserRights);
    when(requisitionRepository.getRequisition(rnr.getFacilityId(), rnr.getProgramId(), rnr.getPeriodId())).thenReturn(make(a(defaultRnr)));
    requisitionService.save(rnr);
    verify(requisitionRepository).update(rnr);
  }

  @Test
  public void shouldNotSaveRnrWithStatusInitiatedIfUserHasOnlyAuthorizeRight() {
    Rnr rnr = spy(make(a(defaultRnr)));
    Integer userId = 1;
    rnr.setModifiedBy(userId);
    rnr.setStatus(INITIATED);
    List<Right> listUserRights = Arrays.asList(AUTHORIZE_REQUISITION);
    when(roleRightService.getRights(userId)).thenReturn(listUserRights);
    expectedException.expect(DataException.class);
    expectedException.expectMessage(RNR_OPERATION_UNAUTHORIZED);
    requisitionService.save(rnr);
  }

  @Test
  public void shouldNotSaveAlreadySubmittedRnrIfUserHasOnlyCreateRequisitionRight() {
    Rnr rnr = spy(make(a(defaultRnr)));
    Integer userId = 1;
    rnr.setModifiedBy(userId);
    rnr.setStatus(SUBMITTED);
    List<Right> listUserRights = Arrays.asList(CREATE_REQUISITION);
    when(roleRightService.getRights(userId)).thenReturn(listUserRights);
    expectedException.expect(DataException.class);
    expectedException.expectMessage(RNR_OPERATION_UNAUTHORIZED);
    requisitionService.save(rnr);
  }

  @Test
  public void shouldFetchAllRequisitionsForFacilitiesAndProgramSupervisedByUserForApproval() throws Exception {
    final RoleAssignment firstAssignment = new RoleAssignment(1, 1, 1, new SupervisoryNode());
    final RoleAssignment secondAssignment = new RoleAssignment(2, 2, 2, new SupervisoryNode());
    final Rnr requisition = make(a(RequisitionBuilder.defaultRnr));
    final List<Rnr> requisitionsForFirstAssignment = new ArrayList<Rnr>() {{
      add(requisition);
    }};
    final List<Rnr> requisitionsForSecondAssignment = new ArrayList<>();
    List<RoleAssignment> roleAssignments = new ArrayList<RoleAssignment>() {{
      add(firstAssignment);
      add(secondAssignment);
    }};
    when(roleRightService.getRoleAssignments(APPROVE_REQUISITION, USER_ID)).thenReturn(roleAssignments);
    when(requisitionRepository.getAuthorizedRequisitions(firstAssignment)).thenReturn(requisitionsForFirstAssignment);
    when(requisitionRepository.getAuthorizedRequisitions(secondAssignment)).thenReturn(requisitionsForSecondAssignment);
    Program expectedProgram = new Program();
    Facility expectedFacility = new Facility();
    ProcessingPeriod expectedPeriod = new ProcessingPeriod();
    when(programService.getById(3)).thenReturn(expectedProgram);
    when(facilityService.getById(3)).thenReturn(expectedFacility);
    when(processingScheduleService.getPeriodById(3)).thenReturn(expectedPeriod);

    List<Rnr> requisitions = requisitionService.listForApproval(USER_ID);

    List<Rnr> expectedRequisitions = new ArrayList<Rnr>() {{
      addAll(requisitionsForFirstAssignment);
      addAll(requisitionsForSecondAssignment);
    }};

    assertThat(requisitions, is(expectedRequisitions));
    assertThat(requisition.getProgram(), is(expectedProgram));
    assertThat(requisition.getFacility(), is(expectedFacility));
    assertThat(requisition.getPeriod(), is(expectedPeriod));
    verify(requisitionRepository, times(1)).getAuthorizedRequisitions(firstAssignment);
    verify(requisitionRepository, times(1)).getAuthorizedRequisitions(secondAssignment);
  }

  @Test
  public void shouldNotApproveAnRnrIfStatusIsNotAuthorized() throws Exception {
    when(requisitionRepository.getById(submittedRnr.getId())).thenReturn(submittedRnr);

    expectedException.expect(DataException.class);
    expectedException.expectMessage(RNR_OPERATION_UNAUTHORIZED);

    requisitionService.approve(submittedRnr);
  }

  @Test
  public void shouldApproveAnRnrAndChangeStatusToApprovedIfThereIsNoFurtherApprovalNeeded() throws Exception {
    authorizedRnr.setSupervisoryNodeId(1);
    when(requisitionRepository.getById(authorizedRnr.getId())).thenReturn(authorizedRnr);

    OpenLmisMessage message = requisitionService.approve(authorizedRnr);
    verify(requisitionRepository).update(authorizedRnr);
    assertThat(authorizedRnr.getStatus(), is(APPROVED));
    assertThat(authorizedRnr.getSupervisoryNodeId(), is(nullValue()));
    assertThat(message.getCode(), is(RNR_APPROVED_SUCCESSFULLY));
  }

  @Test
  public void shouldApproveAnRnrAndKeepStatusInApprovalIfFurtherApprovalNeeded() throws Exception {
    authorizedRnr.setSupervisoryNodeId(1);
    when(requisitionRepository.getById(authorizedRnr.getId())).thenReturn(authorizedRnr);

    SupervisoryNode parentNode = new SupervisoryNode() {{
      setId(2);
    }};
    when(supervisoryNodeService.getParent(1)).thenReturn(parentNode);
    when(supervisoryNodeService.getApproverForGivenSupervisoryNodeAndProgram(parentNode.getId(), authorizedRnr.getProgramId())).thenReturn(new User());

    OpenLmisMessage message = requisitionService.approve(authorizedRnr);

    verify(requisitionRepository).update(authorizedRnr);
    assertThat(authorizedRnr.getStatus(), is(IN_APPROVAL));
    assertThat(authorizedRnr.getSupervisoryNodeId(), is(2));
    assertThat(message.getCode(), is(RNR_APPROVED_SUCCESSFULLY));
  }

  @Test
  public void shouldApproveAnRnrAndKeepStatusInApprovalIfFurtherApprovalNeededAndShouldGiveMessageIfThereIsNoSupervisorAssigned() throws Exception {
    authorizedRnr.setSupervisoryNodeId(1);
    when(requisitionRepository.getById(authorizedRnr.getId())).thenReturn(authorizedRnr);

    SupervisoryNode parentNode = new SupervisoryNode() {{
      setId(2);
    }};
    when(supervisoryNodeService.getParent(1)).thenReturn(parentNode);

    when(supervisoryNodeService.getApproverForGivenSupervisoryNodeAndProgram(parentNode.getId(), authorizedRnr.getProgramId())).thenReturn(null);
    OpenLmisMessage message = requisitionService.approve(authorizedRnr);

    verify(requisitionRepository).update(authorizedRnr);
    assertThat(authorizedRnr.getStatus(), is(IN_APPROVAL));
    assertThat(authorizedRnr.getSupervisoryNodeId(), is(2));
    assertThat(message.getCode(), is(RNR_APPROVED_SUCCESSFULLY_WITHOUT_SUPERVISOR));
  }

  @Test
  public void shouldGetRequisitionForApprovalById() throws Exception {
    Rnr expected = new Rnr();
    final int supervisoryNodeId = 1;
    expected.setSupervisoryNodeId(supervisoryNodeId);
    final int rnrId = 1;
    when(requisitionRepository.getById(rnrId)).thenReturn(expected);

    final RoleAssignment assignment = roleAssignmentWithSupervisoryNodeId(supervisoryNodeId);
    List<RoleAssignment> roleAssignments = new ArrayList<RoleAssignment>() {{
      add(assignment);
    }};

    final int userId = 1;
    when(roleRightService.getRoleAssignments(APPROVE_REQUISITION, userId)).thenReturn(roleAssignments);
    Rnr actual = requisitionService.getRnrForApprovalById(rnrId, userId);

    assertThat(actual, is(expected));
  }

  @Test
  public void shouldThrowExceptionIfUserDoesNotHaveAccessToRequestedRequisition() throws Exception {
    Rnr expected = new Rnr();
    final int supervisoryNodeId = 1;
    expected.setSupervisoryNodeId(supervisoryNodeId);
    final int rnrId = 1;
    when(requisitionRepository.getById(rnrId)).thenReturn(expected);

    List<RoleAssignment> roleAssignments = new ArrayList<RoleAssignment>() {{
    }};

    final int userId = 1;
    when(roleRightService.getRoleAssignments(APPROVE_REQUISITION, userId)).thenReturn(roleAssignments);

    expectedException.expect(DataException.class);
    expectedException.expectMessage(RNR_OPERATION_UNAUTHORIZED);
    requisitionService.getRnrForApprovalById(rnrId, userId);

  }

  private RoleAssignment roleAssignmentWithSupervisoryNodeId(int supervisoryNodeId) {
    final RoleAssignment assignment = new RoleAssignment();
    final SupervisoryNode node = new SupervisoryNode();
    node.setId(supervisoryNodeId);
    assignment.setSupervisoryNode(node);
    return assignment;
  }

  @Test
  public void shouldFillBeginningBalanceOfLineItemsFromPreviousRequisitionIfAvailable() throws Exception {
    Date date = new Date();
    Rnr someRequisition = createRequisition(PERIOD_ID, null);
    ProcessingPeriod validPeriod = setupForInitRnr(date, someRequisition, PERIOD_ID);

    List<FacilityApprovedProduct> facilityApprovedProducts = new ArrayList<>();
    ProgramProduct programProduct = new ProgramProduct(null, make(a(defaultProduct)), 10, true);
    facilityApprovedProducts.add(new FacilityApprovedProduct("warehouse", programProduct, 30));
    ProgramProduct programProduct2 = new ProgramProduct(null, make(a(defaultProduct, with(code, "testCode"))), 10, true);
    facilityApprovedProducts.add(new FacilityApprovedProduct("warehouse", programProduct, 30));

    when(facilityApprovedProductService.getFullSupplyFacilityApprovedProductByFacilityAndProgram(FACILITY_ID, PROGRAM_ID)).thenReturn(facilityApprovedProducts);

    int previousPeriodId = PERIOD_ID - 1;
    ProcessingPeriod previousPeriod = make(a(defaultProcessingPeriod, with(id, previousPeriodId)));
    when(processingScheduleService.getImmediatePreviousPeriod(PERIOD_ID)).thenReturn(previousPeriod);
    when(requisitionRepository.getRequisition(FACILITY_ID, PROGRAM_ID, previousPeriodId)).thenReturn(make(a(defaultRnr)));

    Rnr requisition = requisitionService.initiate(FACILITY_ID, PROGRAM_ID, validPeriod.getId(), USER_ID);

    RnrLineItem firstLineItem = requisition.getLineItems().get(0);
    RnrLineItem secondLineItem = requisition.getLineItems().get(1);

    assertThat(firstLineItem.getBeginningBalance(), is(RnrLineItemBuilder.STOCK_IN_HAND));
    assertThat(firstLineItem.getPreviousStockInHandAvailable(), is(true));
    assertThat(secondLineItem.getBeginningBalance(), is(nullValue()));
    assertThat(secondLineItem.getPreviousStockInHandAvailable(), is(false));

    verify(processingScheduleService).getImmediatePreviousPeriod(PERIOD_ID);
    verify(requisitionRepository).getRequisition(FACILITY_ID, PROGRAM_ID, previousPeriodId);
  }

  @Test
  public void shouldFillNullIfPreviousRnrNotDefined() throws Exception {
    Date date = new Date();
    Rnr someRequisition = createRequisition(PERIOD_ID, null);
    ProcessingPeriod validPeriod = setupForInitRnr(date, someRequisition, PERIOD_ID);

    List<FacilityApprovedProduct> facilityApprovedProducts = new ArrayList<>();
    ProgramProduct programProduct = new ProgramProduct(null, make(a(defaultProduct)), 10, true);
    facilityApprovedProducts.add(new FacilityApprovedProduct("warehouse", programProduct, 30));
    when(facilityApprovedProductService.getFullSupplyFacilityApprovedProductByFacilityAndProgram(FACILITY_ID, PROGRAM_ID)).thenReturn(facilityApprovedProducts);

    int previousPeriodId = PERIOD_ID - 1;
    ProcessingPeriod previousPeriod = make(a(defaultProcessingPeriod, with(id, previousPeriodId)));
    when(processingScheduleService.getImmediatePreviousPeriod(PERIOD_ID)).thenReturn(previousPeriod);
    when(requisitionRepository.getRequisition(FACILITY_ID, PROGRAM_ID, previousPeriodId)).thenReturn(null);

    Rnr requisition = requisitionService.initiate(FACILITY_ID, PROGRAM_ID, validPeriod.getId(), USER_ID);

    assertThat(requisition.getLineItems().get(0).getBeginningBalance(), is(nullValue()));
    verify(processingScheduleService).getImmediatePreviousPeriod(PERIOD_ID);
    verify(requisitionRepository).getRequisition(FACILITY_ID, PROGRAM_ID, previousPeriodId);
  }

  @Test
  public void shouldFillNullInBeginningBalanceIfProductDoesNotExistInPreviousRequisition() throws Exception {
    Date date = new Date();
    Rnr someRequisition = createRequisition(PERIOD_ID, null);
    ProcessingPeriod validPeriod = setupForInitRnr(date, someRequisition, PERIOD_ID);

    List<FacilityApprovedProduct> facilityApprovedProducts = new ArrayList<>();
    ProgramProduct programProduct = new ProgramProduct(null, make(a(defaultProduct, with(ProductBuilder.code, "Non-existent Code"))), 10, true);
    facilityApprovedProducts.add(new FacilityApprovedProduct("warehouse", programProduct, 30));
    when(facilityApprovedProductService.getFullSupplyFacilityApprovedProductByFacilityAndProgram(FACILITY_ID, PROGRAM_ID)).thenReturn(facilityApprovedProducts);

    int previousPeriodId = PERIOD_ID - 1;
    ProcessingPeriod previousPeriod = make(a(defaultProcessingPeriod, with(id, previousPeriodId)));
    when(processingScheduleService.getImmediatePreviousPeriod(PERIOD_ID)).thenReturn(previousPeriod);
    when(requisitionRepository.getRequisition(FACILITY_ID, PROGRAM_ID, previousPeriodId)).thenReturn(make(a(defaultRnr)));

    Rnr requisition = requisitionService.initiate(FACILITY_ID, PROGRAM_ID, validPeriod.getId(), USER_ID);

    assertThat(requisition.getLineItems().get(0).getBeginningBalance(), is(nullValue()));
    verify(processingScheduleService).getImmediatePreviousPeriod(PERIOD_ID);
    verify(requisitionRepository).getRequisition(FACILITY_ID, PROGRAM_ID, previousPeriodId);
  }

  @Test
  public void shouldFillNullInBeginningBalanceIfStockInHandIsNotDisplayed() throws Exception {
    Date date = new Date();
    Rnr someRequisition = createRequisition(PERIOD_ID, null);
    ProcessingPeriod validPeriod = setupForInitRnr(date, someRequisition, PERIOD_ID);

    List<FacilityApprovedProduct> facilityApprovedProducts = new ArrayList<>();
    ProgramProduct programProduct = new ProgramProduct(null, make(a(defaultProduct)), 10, true);
    facilityApprovedProducts.add(new FacilityApprovedProduct("warehouse", programProduct, 30));
    when(facilityApprovedProductService.getFullSupplyFacilityApprovedProductByFacilityAndProgram(FACILITY_ID, PROGRAM_ID)).thenReturn(facilityApprovedProducts);
    when(rnrTemplateRepository.fetchRnrTemplateColumns(PROGRAM_ID)).thenReturn(Arrays.asList(make(a(defaultRnrColumn, with(columnName, "stockInHand"), with(visible, false)))));

    Rnr requisition = requisitionService.initiate(FACILITY_ID, PROGRAM_ID, validPeriod.getId(), USER_ID);

    assertThat(requisition.getLineItems().get(0).getBeginningBalance(), is(nullValue()));
  }

  @Test
  public void shouldNotAlterBeginningBalanceIfPreviousStockInHandAvailableFlagIsSet() throws Exception {
    Rnr savedRequisition = make(a(defaultRnr));
    Rnr requisition = createRequisition(PERIOD_ID, SUBMITTED);
    requisition.setModifiedBy(USER_ID);
    requisition.getLineItems().get(0).setBeginningBalance(3);
    savedRequisition.getLineItems().get(0).setPreviousStockInHandAvailable(true);
    List<Right> listUserRights = Arrays.asList(AUTHORIZE_REQUISITION);
    when(roleRightService.getRights(USER_ID)).thenReturn(listUserRights);
    when(requisitionRepository.getRequisition(requisition.getFacilityId(), requisition.getProgramId(), requisition.getPeriodId()))
      .thenReturn(savedRequisition);

    requisitionService.save(requisition);

    verify(requisitionRepository).getRequisition(requisition.getFacilityId(), requisition.getProgramId(), requisition.getPeriodId());
    verify(requisitionRepository).update(requisition);
    assertThat(requisition.getLineItems().get(0).getBeginningBalance(), is(RnrLineItemBuilder.BEGINNING_BALANCE));
  }
}
