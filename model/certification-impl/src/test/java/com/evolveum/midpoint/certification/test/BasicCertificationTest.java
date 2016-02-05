/*
 * Copyright (c) 2010-2015 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.certification.test;

import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectPaging;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.OrderDirection;
import com.evolveum.midpoint.prism.query.RefFilter;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.schema.GetOperationOptions;
import com.evolveum.midpoint.schema.SelectorOptions;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.CertCampaignTypeUtil;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.test.util.TestUtil;
import com.evolveum.midpoint.util.exception.SecurityViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationDecisionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationDefinitionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationResponseType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationStageType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static com.evolveum.midpoint.schema.util.CertCampaignTypeUtil.getOrderBy;
import static com.evolveum.midpoint.test.IntegrationTestTools.display;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.CLOSED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.CREATED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.IN_REMEDIATION;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.IN_REVIEW_STAGE;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCampaignStateType.REVIEW_STAGE_DONE;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType.F_ACTIVATION;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.AccessCertificationCaseType.F_TARGET_REF;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationStatusType.ENABLED;
import static com.evolveum.midpoint.xml.ns._public.common.common_3.ActivationType.F_ADMINISTRATIVE_STATUS;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;

/**
 * Very simple certification test.
 * Tests just the basic functionality, along with security features.
 *
 * @author mederly
 */
@ContextConfiguration(locations = {"classpath:ctx-model-test-main.xml"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class BasicCertificationTest extends AbstractCertificationTest {

    protected static final File CERT_DEF_USER_ASSIGNMENT_BASIC_FILE = new File(COMMON_DIR, "certification-of-eroot-user-assignments.xml");
    protected static final String CERT_DEF_USER_ASSIGNMENT_BASIC_OID = "33333333-0000-0000-0000-000000000001";

    protected AccessCertificationDefinitionType certificationDefinition;
    protected AccessCertificationDefinitionType roleInducementCertDefinition;

    private String campaignOid;
    private String roleInducementCampaignOid;

    @Override
    public void initSystem(Task initTask, OperationResult initResult) throws Exception {
        super.initSystem(initTask, initResult);

        certificationDefinition = repoAddObjectFromFile(CERT_DEF_USER_ASSIGNMENT_BASIC_FILE,
                AccessCertificationDefinitionType.class, initResult).asObjectable();
    }

    /*
     *  "Foreign" campaign - generates a few cases, just to test authorizations.
     */
    @Test
    public void test001CreateForeignCampaign() throws Exception {
        final String TEST_NAME = "test001CreateForeignCampaign";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(RoleInducementCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        roleInducementCertDefinition = repoAddObjectFromFile(ROLE_INDUCEMENT_CERT_DEF_FILE,
                AccessCertificationDefinitionType.class, result).asObjectable();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationCampaignType campaign =
                certificationService.createCampaign(roleInducementCertDefinition.getOid(), task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        assertNotNull("Created campaign is null", campaign);

        roleInducementCampaignOid = campaign.getOid();

        campaign = getCampaignWithCases(roleInducementCampaignOid);
        display("campaign", campaign);
        assertEquals("Unexpected certification cases", 0, campaign.getCase().size());
        assertStateAndStage(campaign, CREATED, 0);
        assertEquals("Unexpected # of stages", 2, campaign.getStageDefinition().size());
        assertDefinitionAndOwner(campaign, roleInducementCertDefinition);
        assertNull("Unexpected start time", campaign.getStart());
        assertNull("Unexpected end time", campaign.getEnd());
    }

    @Test
    public void test002OpenFirstForeignStage() throws Exception {
        final String TEST_NAME = "test002OpenFirstForeignStage";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(RoleInducementCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationService.openNextStage(roleInducementCampaignOid, 1, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(roleInducementCampaignOid);
        display("campaign in stage 1", campaign);

        assertStateAndStage(campaign, IN_REVIEW_STAGE, 1);
        assertDefinitionAndOwner(campaign, roleInducementCertDefinition);
        assertApproximateTime("start time", new Date(), campaign.getStart());
        assertNull("Unexpected end time", campaign.getEnd());
        assertEquals("wrong # of defined stages", 2, campaign.getStageDefinition().size());
        assertEquals("wrong # of stages", 1, campaign.getStage().size());
        AccessCertificationStageType stage = campaign.getStage().get(0);
        assertEquals("wrong stage #", 1, stage.getNumber());
        assertApproximateTime("stage 1 start", new Date(), stage.getStart());
        assertNotNull("stage 1 end", stage.getDeadline());       // too lazy to compute exact datetime
        assertEquals("Wrong number of certification cases", 5, campaign.getCase().size());
    }

    @Test
    public void test005CreateCampaignDenied() throws Exception {
        final String TEST_NAME = "test005CreateCampaignDenied";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        login(getUserFromRepo(USER_ELAINE_OID));            // elaine is a reviewer, not authorized to create campaigns

        // WHEN/THEN
        TestUtil.displayWhen(TEST_NAME);
        try {
            certificationService.createCampaign(certificationDefinition.getOid(), task, result);
            fail("Unexpected success");
        } catch (SecurityViolationException e) {
            System.out.println("Expected security violation exception: " + e.getMessage());
        }
    }

    @Test
    public void test010CreateCampaignAllowed() throws Exception {
        final String TEST_NAME = "test010CreateCampaignAllowed";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        login(getUserFromRepo(USER_BOB_OID));

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationCampaignType campaign =
                certificationService.createCampaign(certificationDefinition.getOid(), task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        assertNotNull("Created campaign is null", campaign);

        campaignOid = campaign.getOid();

        campaign = getObject(AccessCertificationCampaignType.class, campaignOid).asObjectable();
        display("campaign", campaign);
        assertEquals("Unexpected certification cases", 0, campaign.getCase().size());
        assertStateAndStage(campaign, CREATED, 0);
        assertDefinitionAndOwner(campaign, certificationDefinition, USER_BOB_OID);
        assertNull("Unexpected start time", campaign.getStart());
        assertNull("Unexpected end time", campaign.getEnd());
    }

    protected void assertStateAndStage(AccessCertificationCampaignType campaign, AccessCertificationCampaignStateType state, int stage) {
        assertEquals("Unexpected campaign state", state, campaign.getState());
        assertEquals("Unexpected stage number", stage, campaign.getStageNumber());
    }

    @Test
    public void test012SearchAllCasesDenied() throws Exception {
        final String TEST_NAME = "test012SearchAllCasesDenied";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ELAINE_OID));

        searchWithNoCasesExpected(TEST_NAME);
    }

    @Test
    public void test013SearchAllCasesAllowed() throws Exception {
        final String TEST_NAME = "test013SearchAllCasesAllowed";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_BOB_OID));

        searchWithNoCasesExpected(TEST_NAME);
    }

    protected void searchWithNoCasesExpected(String TEST_NAME) throws Exception {
        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        List<AccessCertificationCaseType> caseList = modelService.searchContainers(
                AccessCertificationCaseType.class, CertCampaignTypeUtil.createCasesForCampaignQuery(campaignOid, prismContext),
                null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("caseList", caseList);
        assertEquals("Unexpected cases in caseList", 0, caseList.size());
    }


    @Test
    public void test020OpenFirstStageDenied() throws Exception {
        final String TEST_NAME = "test020OpenFirstStageDenied";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        login(getUserFromRepo(USER_ELAINE_OID));

        // WHEN+THEN
        TestUtil.displayWhen(TEST_NAME);
        try {
            certificationService.openNextStage(campaignOid, 1, task, result);
            fail("Unexpected success");
        } catch (SecurityViolationException e) {
            System.out.println("Got expected denial exception: " + e.getMessage());
        }
    }

    @Test
    public void test021OpenFirstStageAllowed() throws Exception {
        final String TEST_NAME = "test021OpenFirstStageAllowed";
        TestUtil.displayTestTile(this, TEST_NAME);

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();
        login(getUserFromRepo(USER_BOB_OID));

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationService.openNextStage(campaignOid, 1, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 1", campaign);

        assertStateAndStage(campaign, IN_REVIEW_STAGE, 1);
        assertDefinitionAndOwner(campaign, certificationDefinition, USER_BOB_OID);
        assertApproximateTime("start time", new Date(), campaign.getStart());
        assertNull("Unexpected end time", campaign.getEnd());
        assertEquals("wrong # of stages", 1, campaign.getStage().size());
        AccessCertificationStageType stage = campaign.getStage().get(0);
        assertEquals("wrong stage #", 1, stage.getNumber());
        assertApproximateTime("stage 1 start", new Date(), stage.getStart());
        assertNotNull("stage 1 end", stage.getDeadline());       // too lazy to compute exact datetime
        checkAllCases(campaign.getCase(), campaignOid);
    }

//    protected void assertDefinitionAndOwner(AccessCertificationCampaignType campaign, PrismObject<? extends ObjectType> certificationDefinition) {
//        assertEquals("Unexpected ownerRef", ObjectTypeUtil.createObjectRef(USER_ADMINISTRATOR_OID, ObjectTypes.USER), campaign.getOwnerRef());
//        assertEquals("Unexpected definitionRef",
//                ObjectTypeUtil.createObjectRef(CERT_DEF_USER_ASSIGNMENT_BASIC_OID, ObjectTypes.ACCESS_CERTIFICATION_DEFINITION),
//                campaign.getDefinitionRef());
//    }

    @Test
    public void test030SearchAllCasesDenied() throws Exception {
        final String TEST_NAME = "test030SearchCasesDenied";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ELAINE_OID));

        searchWithNoCasesExpected(TEST_NAME);
    }

    @Test
    public void test032SearchAllCasesAllowed() throws Exception {
        final String TEST_NAME = "test032SearchAllCasesAllowed";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_BOB_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        List<AccessCertificationCaseType> caseList = modelService.searchContainers(
                AccessCertificationCaseType.class, null, null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("caseList", caseList);
        checkAllCases(caseList, campaignOid);
    }

    @Test
    public void test040SearchCasesFilteredSortedPaged() throws Exception {
        final String TEST_NAME = "test040SearchCasesFilteredSortedPaged";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_BOB_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        Collection<SelectorOptions<GetOperationOptions>> resolveNames =
                SelectorOptions.createCollection(GetOperationOptions.createResolveNames());
        ObjectFilter filter = RefFilter.createReferenceEqual(new ItemPath(AccessCertificationCaseType.F_OBJECT_REF),
                AccessCertificationCaseType.class, prismContext, ObjectTypeUtil.createObjectRef(userAdministrator).asReferenceValue());
        ObjectPaging paging = ObjectPaging.createPaging(2, 2, getOrderBy(F_TARGET_REF), OrderDirection.DESCENDING);
        ObjectQuery query = ObjectQuery.createObjectQuery(filter, paging);
        List<AccessCertificationCaseType> caseList = modelService.searchContainers(
                AccessCertificationCaseType.class, query, resolveNames, task, result);

        // THEN
        // Cases for administrator are (ordered by name, descending):
        //  - Superuser
        //  - ERoot
        //  - COO
        //  - CEO
        // so paging (2, 2) should return the last two
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("caseList", caseList);
        assertEquals("Wrong number of certification cases", 2, caseList.size());
        checkCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, userAdministrator, campaignOid);
        checkCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, userAdministrator, campaignOid);
        assertEquals("Wrong target OID in case #0", ROLE_COO_OID, caseList.get(0).getTargetRef().getOid());
        assertEquals("Wrong target OID in case #1", ROLE_CEO_OID, caseList.get(1).getTargetRef().getOid());
    }

    @Test
    public void test050SearchDecisionsAdministrator() throws Exception {
        final String TEST_NAME = "test050SearchDecisionsAdministrator";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        List<AccessCertificationCaseType> caseList =
                certificationService.searchDecisionsToReview(
                        CertCampaignTypeUtil.createCasesForCampaignQuery(campaignOid, prismContext),
                        false, null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("caseList", caseList);
        assertEquals("Wrong number of certification cases", 7, caseList.size());
        checkAllCases(caseList, campaignOid);
    }

    @Test
    public void test052SearchDecisionsByTenantRef() throws Exception {
        final String TEST_NAME = "test052SearchDecisionsByTenantRef";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        ObjectQuery query = QueryBuilder.queryFor(AccessCertificationCaseType.class, prismContext)
                .item(AccessCertificationCaseType.F_TENANT_REF).ref(ORG_GOVERNOR_OFFICE_OID)
                .and().ownerId(campaignOid)
                .build();
        List<AccessCertificationCaseType> caseList =
                certificationService.searchDecisionsToReview(
                        query, false, null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("caseList", caseList);
        assertEquals("Wrong number of certification cases", 1, caseList.size());
        checkCase(caseList, USER_JACK_OID, ROLE_CEO_OID, userJack, campaignOid, ORG_GOVERNOR_OFFICE_OID, ORG_SCUMM_BAR_OID, ENABLED);
    }

    @Test
    public void test054SearchDecisionsByOrgRef() throws Exception {
        final String TEST_NAME = "test054SearchDecisionsByOrgRef";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        ObjectQuery query = QueryBuilder.queryFor(AccessCertificationCaseType.class, prismContext)
                .item(AccessCertificationCaseType.F_ORG_REF).ref(ORG_SCUMM_BAR_OID)
                .and().ownerId(campaignOid)
                .build();
        List<AccessCertificationCaseType> caseList =
                certificationService.searchDecisionsToReview(query, false, null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("caseList", caseList);
        assertEquals("Wrong number of certification cases", 1, caseList.size());
        checkCase(caseList, USER_JACK_OID, ROLE_CEO_OID, userJack, campaignOid, ORG_GOVERNOR_OFFICE_OID, ORG_SCUMM_BAR_OID, ENABLED);
    }

    @Test
    public void test056SearchDecisionsByAdminStatus() throws Exception {
        final String TEST_NAME = "test056SearchDecisionsByAdminStatus";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        ObjectQuery query = QueryBuilder.queryFor(AccessCertificationCaseType.class, prismContext)
                .item(F_ACTIVATION, F_ADMINISTRATIVE_STATUS).eq(ENABLED)
                .and().ownerId(campaignOid)
                .build();
        List<AccessCertificationCaseType> caseList =
                certificationService.searchDecisionsToReview(query, false, null, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        display("caseList", caseList);
        assertEquals("Wrong number of certification cases", 1, caseList.size());
        checkCase(caseList, USER_JACK_OID, ROLE_CEO_OID, userJack, campaignOid, ORG_GOVERNOR_OFFICE_OID, ORG_SCUMM_BAR_OID, ENABLED);
    }

    @Test
    public void test100RecordDecision() throws Exception {
        final String TEST_NAME = "test100RecordDecision";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        AccessCertificationCaseType superuserCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_SUPERUSER_OID);

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationDecisionType decision = new AccessCertificationDecisionType(prismContext);
        decision.setResponse(AccessCertificationResponseType.ACCEPT);
        decision.setComment("no comment");
        decision.setStageNumber(0);     // will be replaced by current stage number
        ObjectReferenceType administratorRef = ObjectTypeUtil.createObjectRef(USER_ADMINISTRATOR_OID, ObjectTypes.USER);
        long id = superuserCase.asPrismContainerValue().getId();
        certificationService.recordDecision(campaignOid, id, decision, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        caseList = queryHelper.searchCases(campaignOid, null, null, result);
        display("caseList", caseList);
        checkAllCases(caseList, campaignOid);

        superuserCase = findCase(caseList, USER_ADMINISTRATOR_OID, ROLE_SUPERUSER_OID);
        assertEquals("changed case ID", Long.valueOf(id), superuserCase.asPrismContainerValue().getId());
        assertEquals("wrong # of decisions", 1, superuserCase.getDecision().size());
        AccessCertificationDecisionType storedDecision = superuserCase.getDecision().get(0);
        assertEquals("wrong response", AccessCertificationResponseType.ACCEPT, storedDecision.getResponse());
        assertEquals("wrong comment", "no comment", storedDecision.getComment());
        assertEquals("wrong reviewerRef", administratorRef, storedDecision.getReviewerRef());
        assertEquals("wrong stage number", 1, storedDecision.getStageNumber());
        assertApproximateTime("timestamp", new Date(), storedDecision.getTimestamp());
        assertEquals("wrong current response", AccessCertificationResponseType.ACCEPT, superuserCase.getCurrentStageOutcome());
    }

    @Test
    public void test105RecordAcceptJackCeo() throws Exception {
        final String TEST_NAME = "test105RecordAcceptJackCeo";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        AccessCertificationCaseType ceoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationDecisionType decision = new AccessCertificationDecisionType(prismContext);
        decision.setResponse(AccessCertificationResponseType.ACCEPT);
        decision.setComment("ok");
        decision.setStageNumber(1);
        // reviewerRef will be taken from the current user
        long id = ceoCase.asPrismContainerValue().getId();
        certificationService.recordDecision(campaignOid, id, decision, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        caseList = queryHelper.searchCases(campaignOid, null, null, result);
        display("caseList", caseList);
        checkAllCases(caseList, campaignOid);

        ceoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        assertEquals("changed case ID", Long.valueOf(id), ceoCase.asPrismContainerValue().getId());
        assertEquals("wrong # of decisions", 1, ceoCase.getDecision().size());
        AccessCertificationDecisionType storedDecision = ceoCase.getDecision().get(0);
        assertEquals("wrong response", AccessCertificationResponseType.ACCEPT, storedDecision.getResponse());
        assertEquals("wrong comment", "ok", storedDecision.getComment());
        assertEquals("wrong reviewerRef", ObjectTypeUtil.createObjectRef(USER_ADMINISTRATOR_OID, ObjectTypes.USER), storedDecision.getReviewerRef());
        assertEquals("wrong stage number", 1, storedDecision.getStageNumber());
        assertApproximateTime("timestamp", new Date(), storedDecision.getTimestamp());
        assertEquals("wrong current response", AccessCertificationResponseType.ACCEPT, ceoCase.getCurrentStageOutcome());
    }

    @Test
    public void test110RecordRevokeJackCeo() throws Exception {
        final String TEST_NAME = "test110RecordRevokeJackCeo";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ADMINISTRATOR_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        AccessCertificationCaseType ceoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        AccessCertificationDecisionType decision = new AccessCertificationDecisionType(prismContext);
        decision.setResponse(AccessCertificationResponseType.REVOKE);
        decision.setComment("no way");
        decision.setStageNumber(1);
        // reviewerRef will be taken from the current user
        long id = ceoCase.asPrismContainerValue().getId();
        certificationService.recordDecision(campaignOid, id, decision, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        caseList = queryHelper.searchCases(campaignOid, null, null, result);
        display("caseList", caseList);
        checkAllCases(caseList, campaignOid);

        ceoCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        display("CEO case", ceoCase.asPrismContainerValue());
        assertEquals("changed case ID", Long.valueOf(id), ceoCase.asPrismContainerValue().getId());
        assertEquals("wrong # of decisions", 1, ceoCase.getDecision().size());
        AccessCertificationDecisionType storedDecision = ceoCase.getDecision().get(0);
        assertEquals("wrong response", AccessCertificationResponseType.REVOKE, storedDecision.getResponse());
        assertEquals("wrong comment", "no way", storedDecision.getComment());
        assertEquals("wrong reviewerRef", ObjectTypeUtil.createObjectRef(USER_ADMINISTRATOR_OID, ObjectTypes.USER), storedDecision.getReviewerRef());
        assertEquals("wrong stage number", 1, storedDecision.getStageNumber());
        assertApproximateTime("timestamp", new Date(), storedDecision.getTimestamp());
        assertEquals("wrong current response", AccessCertificationResponseType.REVOKE, ceoCase.getCurrentStageOutcome());
    }

    protected void checkAllCases(Collection<AccessCertificationCaseType> caseList, String campaignOid) {
        assertEquals("Wrong number of certification cases", 7, caseList.size());
        checkCase(caseList, USER_ADMINISTRATOR_OID, ROLE_SUPERUSER_OID, userAdministrator, campaignOid);
        checkCase(caseList, USER_ADMINISTRATOR_OID, ROLE_COO_OID, userAdministrator, campaignOid);
        checkCase(caseList, USER_ADMINISTRATOR_OID, ROLE_CEO_OID, userAdministrator, campaignOid);
        checkCase(caseList, USER_ADMINISTRATOR_OID, ORG_EROOT_OID, userAdministrator, campaignOid);
        checkCase(caseList, USER_JACK_OID, ROLE_CEO_OID, userJack, campaignOid, ORG_GOVERNOR_OFFICE_OID, ORG_SCUMM_BAR_OID, ENABLED);
        checkCase(caseList, USER_JACK_OID, ORG_EROOT_OID, userJack, campaignOid);
    }

    @Test
    public void test150CloseFirstStageDeny() throws Exception {
        final String TEST_NAME = "test150CloseFirstStageDeny";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ELAINE_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN+THEN
        TestUtil.displayWhen(TEST_NAME);
        try {
            certificationService.closeCurrentStage(campaignOid, 1, task, result);
            fail("Unexpected success");
        } catch (SecurityViolationException e) {
            System.out.println("Got expected deny exception: " + e.getMessage());
        }
    }

    @Test
    public void test151CloseCampaignDeny() throws Exception {
        final String TEST_NAME = "test151CloseCampaignDeny";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ELAINE_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN+THEN
        TestUtil.displayWhen(TEST_NAME);
        try {
            certificationService.closeCampaign(campaignOid, task, result);
            fail("Unexpected success");
        } catch (SecurityViolationException e) {
            System.out.println("Got expected deny exception: " + e.getMessage());
        }
    }

    @Test
    public void test152CloseFirstStageAllow() throws Exception {
        final String TEST_NAME = "test152CloseFirstStageAllow";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_BOB_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationService.closeCurrentStage(campaignOid, 1, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign in stage 1", campaign);

        assertStateAndStage(campaign, REVIEW_STAGE_DONE, 1);
        assertDefinitionAndOwner(campaign, certificationDefinition, USER_BOB_OID);
        assertNull("Unexpected end time", campaign.getEnd());
        assertEquals("wrong # of stages", 1, campaign.getStage().size());
        AccessCertificationStageType stage = campaign.getStage().get(0);
        assertEquals("wrong stage #", 1, stage.getNumber());
        assertApproximateTime("stage 1 start", new Date(), stage.getStart());
        //assertApproximateTime("stage 1 end", new Date(), stage.getStart());       // TODO when implemented
        checkAllCases(campaign.getCase(), campaignOid);
    }

    @Test
    public void test200StartRemediationDeny() throws Exception {
        final String TEST_NAME = "test200StartRemediationDeny";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_ELAINE_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN+THEN
        TestUtil.displayWhen(TEST_NAME);
        try {
            certificationService.startRemediation(campaignOid, task, result);
        } catch (SecurityViolationException e) {
            System.out.println("Got expected deny exception: " + e.getMessage());
        }
    }

    @Test
    public void test205StartRemediationAllow() throws Exception {
        final String TEST_NAME = "test205StartRemediationAllow";
        TestUtil.displayTestTile(this, TEST_NAME);
        login(getUserFromRepo(USER_BOB_OID));

        // GIVEN
        Task task = taskManager.createTaskInstance(BasicCertificationTest.class.getName() + "." + TEST_NAME);
        task.setOwner(userAdministrator.asPrismObject());
        OperationResult result = task.getResult();

        // WHEN
        TestUtil.displayWhen(TEST_NAME);
        certificationService.startRemediation(campaignOid, task, result);

        // THEN
        TestUtil.displayThen(TEST_NAME);
        result.computeStatus();
        TestUtil.assertInProgressOrSuccess(result);

        AccessCertificationCampaignType campaign = getCampaignWithCases(campaignOid);
        display("campaign after remediation start", campaign);
        assertTrue("wrong campaign state: " + campaign.getState(), campaign.getState() == CLOSED || campaign.getState() == IN_REMEDIATION);

        RefFilter taskFilter = RefFilter.createReferenceEqual(new ItemPath(TaskType.F_OBJECT_REF), TaskType.class, prismContext, ObjectTypeUtil.createObjectRef(campaign).asReferenceValue());
        List<PrismObject<TaskType>> tasks = taskManager.searchObjects(TaskType.class, ObjectQuery.createObjectQuery(taskFilter), null, result);
        assertEquals("unexpected number of related tasks", 1, tasks.size());
        waitForTaskFinish(tasks.get(0).getOid(), true);

        campaign = getCampaignWithCases(campaignOid);
        assertEquals("wrong campaign state", CLOSED, campaign.getState());
        assertEquals("wrong campaign stage", 2, campaign.getStageNumber());
        assertDefinitionAndOwner(campaign, certificationDefinition, USER_BOB_OID);
        // TODO assertApproximateTime("end time", new Date(), campaign.getEnd());
        assertEquals("wrong # of stages", 1, campaign.getStage().size());
        //assertApproximateTime("stage 1 end", new Date(), stage.getStart());       // TODO when implemented

        List<AccessCertificationCaseType> caseList = queryHelper.searchCases(campaignOid, null, null, result);
        AccessCertificationCaseType jackCase = findCase(caseList, USER_JACK_OID, ROLE_CEO_OID);
        assertApproximateTime("ceoDummyCase.remediedTimestamp", new Date(), jackCase.getRemediedTimestamp());

        userJack = getUser(USER_JACK_OID).asObjectable();
        display("jack", userJack);
        assertEquals("wrong # of jack's assignments", 2, userJack.getAssignment().size());
        assertEquals("wrong target OID", ORG_EROOT_OID, userJack.getAssignment().get(0).getTargetRef().getOid());
    }


}
