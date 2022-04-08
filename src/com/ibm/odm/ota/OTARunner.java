/*
* Copyright IBM Corp. 1987, 2018
* 
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
* 
* http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
* 
**/
package com.ibm.odm.ota;

import java.util.List;
import java.util.logging.Logger;

import org.eclipse.emf.ecore.EClass;

import ilog.rules.teamserver.brm.IlrBaseline;
import ilog.rules.teamserver.brm.IlrBranch;
import ilog.rules.teamserver.brm.IlrRuleProject;
import ilog.rules.teamserver.model.IlrApplicationException;
import ilog.rules.teamserver.model.IlrDefaultSearchCriteria;
import ilog.rules.teamserver.model.IlrElementDetails;
import ilog.rules.teamserver.model.IlrSession;
import ilog.rules.teamserver.model.IlrSessionHelper;

/**
 * Removing elements from the recycle bin
 * 
 * @author pberland@us.ibm.com
 *
 */
public class OTARunner {

	private String username;
	private String password;
	private String url;
	private String datasource;

	private static final String DEFAULT_USERNAME = "rtsAdmin";
	private static final String DEFAULT_PASSWORD = "rtsAdmin";
	private static final String DEFAULT_URL = "http://localhost:9090/decisioncenter";

	private static Logger logger = Logger.getLogger(OTARunner.class.getCanonicalName());

	private OTARunner(String[] args) throws OTAException {
		getParams(args);
	}

	/**
	 * Gets the parameters from the execution arguments. The parameter values are
	 * parsed and validated just in time, in the place they are used.
	 * 
	 * @param args
	 * @throws OTAException
	 */
	private void getParams(String[] args) throws OTAException {
		if (args.length == 0) {
			datasource = null;
			url = DEFAULT_URL;
			username = DEFAULT_USERNAME;
			password = DEFAULT_PASSWORD;
		} else {
			url = getArg("url", args);
			datasource = getArg("datasource", args);
			username = getArg("username", args);
			password = getArg("password", args);
		}
	}

	private String getArg(String key, String[] args) throws OTAException {
		for (String arg : args) {
			String[] kvp = arg.split("=");
			if (kvp[0].equals(key)) {
				return (kvp.length == 1 || kvp[1].trim().isEmpty()) ? null : kvp[1];
			}
		}
		throw new OTAException("Argument " + key + " not found", null);
	}

	@SuppressWarnings("deprecation")
	private void run() throws OTAException {
		DCConnection.startSession(url, username, password, datasource);
		IlrSession session = DCConnection.getSession();

		try {
			// Get the recycle bin baseline for the target project and set it as the current
			// baseline to operate on.
			//
			String ruleProjectName = "pq-pricing-rules";
			IlrRuleProject ruleProject = Helper.getProjectNamed(ruleProjectName);
			IlrBranch currentBaseline = IlrSessionHelper.getCurrentBaseline(session, ruleProject);
			IlrBaseline recycleBinBaseline = IlrSessionHelper.getRecyclebinBaseline(session, currentBaseline);
			session.setWorkingBaseline(recycleBinBaseline);

			EClass eClass = session.getBrmPackage().getProjectElement();
			IlrDefaultSearchCriteria criteria = new IlrDefaultSearchCriteria(eClass);
			List<IlrElementDetails> elements = session.findElementDetails(criteria);
			//
			// Take, for example, the first element of the list and delete it.
			//
			if (elements.size() > 0) {
				IlrElementDetails element = elements.get(0);
				String elementName = element.getName();
				//
				// Yes, this is a deprecated method, but there is no alternative here.
				//
				session.removeFromBaseline(element, recycleBinBaseline);
				logger.info("Removed element " + elementName + " from recycle bin");
			}
		} catch (IlrApplicationException e) {
			throw new OTAException("Remove from baseline", e);
		}

		DCConnection.endSession();
	}

	public static void main(String[] args) {
		try {
			OTARunner runner = new OTARunner(args);
			runner.run();
		} catch (OTAException e) {
			logger.severe(e.getStackTraceString());
		}
	}
}
