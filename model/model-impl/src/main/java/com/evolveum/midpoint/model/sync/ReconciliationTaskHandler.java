/**
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted 2011 [name of copyright owner]"
 * 
 */
package com.evolveum.midpoint.model.sync;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.schema.exception.CommunicationException;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.schema.result.OperationConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskHandler;
import com.evolveum.midpoint.task.api.TaskManager;
import com.evolveum.midpoint.task.api.TaskRunResult;
import com.evolveum.midpoint.task.api.TaskRunResult.TaskRunResultStatus;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * The task hander for reconciliation.
 * 
 *  This handler takes care of executing reconciliation "runs". It means that the handler "run" method will
 *  be as scheduled (every few days). The responsibility is to iterate over accounts and/or users and compare the
 *  real state with the assumed IDM state.
 * 
 * @author Radovan Semancik
 *
 */
@Component
public class ReconciliationTaskHandler implements TaskHandler {
	
	public static final String HANDLER_URI = "http://midpoint.evolveum.com/model/sync/reconciliation-handler-1";
	
	@Autowired(required=true)
	private TaskManager taskManager;
	
	@Autowired(required=true)
	private ProvisioningService provisioningService;
	
	private static final transient Trace LOGGER = TraceManager.getTrace(ReconciliationTaskHandler.class);

	@PostConstruct
	private void initialize() {
		taskManager.registerHandler(HANDLER_URI, this);
	}
	
	@Override
	public TaskRunResult run(Task task) {
		LOGGER.trace("ReconciliationCycle.run starting");
		
		long progress = task.getProgress();
		OperationResult opResult = new OperationResult(OperationConstants.RECONCILIATION);
		TaskRunResult runResult = new TaskRunResult();
		runResult.setOperationResult(opResult);
		String resourceOid = task.getObjectOid();
		opResult.addContext("resourceOid", resourceOid);

		try {

			if (resourceOid==null) {
				// This is a user reconciliation
				performUserReconciliation();
				return runResult;
			} else {
				performResourceReconciliation();
			}
		
			
//		} catch (ObjectNotFoundException ex) {
//			LOGGER.error("Reconciliation: Resource does not exist, OID: {}",resourceOid,ex);
//			// This is bad. The resource does not exist. Permanent problem.
//			opResult.recordFatalError("Resource does not exist, OID: "+resourceOid,ex);
//			runResult.setRunResultStatus(TaskRunResultStatus.PERMANENT_ERROR);
//			runResult.setProgress(progress);
//			return runResult;
//		} catch (CommunicationException ex) {
//			LOGGER.error("Reconciliation: Communication error: {}",ex.getMessage(),ex);
//			// Error, but not critical. Just try later.
//			opResult.recordPartialError("Communication error: "+ex.getMessage(),ex);
//			runResult.setRunResultStatus(TaskRunResultStatus.TEMPORARY_ERROR);
//			runResult.setProgress(progress);
//			return runResult;
//		} catch (SchemaException ex) {
//			LOGGER.error("Reconciliation: Error dealing with schema: {}",ex.getMessage(),ex);
//			// Not sure about this. But most likely it is a misconfigured resource or connector
//			// It may be worth to retry. Error is fatal, but may not be permanent.
//			opResult.recordFatalError("Error dealing with schema: "+ex.getMessage(),ex);
//			runResult.setRunResultStatus(TaskRunResultStatus.TEMPORARY_ERROR);
//			runResult.setProgress(progress);
//			return runResult;
		} catch (RuntimeException ex) {
			LOGGER.error("Reconciliation: Internal Error: {}",ex.getMessage(),ex);
			// Can be anything ... but we can't recover from that.
			// It is most likely a programming error. Does not make much sense to retry.
			opResult.recordFatalError("Internal Error: "+ex.getMessage(),ex);
			runResult.setRunResultStatus(TaskRunResultStatus.PERMANENT_ERROR);
			runResult.setProgress(progress);
			return runResult;
		}
		
		opResult.computeStatus("Reconciliation run has failed");
		// This "run" is finished. But the task goes on ...
		runResult.setRunResultStatus(TaskRunResultStatus.FINISHED);
		runResult.setProgress(progress);
		LOGGER.trace("Reconciliation.run stopping");
		return runResult;
	}

	private void performResourceReconciliation() {
		// TODO Auto-generated method stub
		
	}

	private void performUserReconciliation() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Long heartbeat(Task task) {
		// TODO Auto-generated method stub
		return 0L;
	}

	@Override
	public void refreshStatus(Task task) {
		// Do nothing. Everything is fresh already.		
	}

}
