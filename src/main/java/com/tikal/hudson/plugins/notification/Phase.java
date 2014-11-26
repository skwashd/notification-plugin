/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tikal.hudson.plugins.notification;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.List;

import com.tikal.hudson.plugins.notification.Endpoint;
import com.tikal.hudson.plugins.notification.model.BuildState;
import com.tikal.hudson.plugins.notification.model.JobState;

@SuppressWarnings({ "unchecked", "rawtypes" })
public enum Phase {
	STARTED, COMPLETED, FINISHED;

	public void handle(Run run, TaskListener listener) {
		HudsonNotificationProperty property = (HudsonNotificationProperty) run.getParent().getProperty(HudsonNotificationProperty.class);
		if (property != null) {
			List<Endpoint> targets = property.getEndpoints();
			for (Endpoint target : targets) {
                try {
                    JobState jobState = buildJobState(run.getParent(), run, target);
					target.getProtocol().send(target.getUrl(), target.getFormat().serialize(jobState));
                } catch (IOException e) {
                    e.printStackTrace(listener.error("Failed to notify "+target));
                }
            }
		}
	}
	
	private JobState buildJobState(Job job, Run run, Endpoint target) {
		JobState jobState = new JobState();
		jobState.setName(job.getName());
		jobState.setUrl(job.getUrl());
		BuildState buildState = new BuildState();
		buildState.setNumber(run.number);
		buildState.setUrl(run.getUrl());
		buildState.setPhase(this);
		buildState.setStatus(getStatus(run));

		String log = this.getLog(run, target);
		buildState.setLog(log);

		String rootUrl = Hudson.getInstance().getRootUrl();
		if (rootUrl != null) {
			buildState.setFullUrl(rootUrl + run.getUrl());
		}

		jobState.setBuild(buildState);

		ParametersAction paramsAction = run.getAction(ParametersAction.class);
		if (paramsAction != null && run instanceof AbstractBuild) {
			AbstractBuild build = (AbstractBuild) run;
			EnvVars env = new EnvVars();
			for (ParameterValue value : paramsAction.getParameters())
				if (!value.isSensitive())
					value.buildEnvVars(build, env);
			buildState.setParameters(env);
		}
		
		return jobState;
	}
	
	private String getStatus(Run r) {
		Result result = r.getResult();
		String status = null;
		if (result != null) {
			status = result.toString();
		}
		return status;
	}


	private String getLog(Run run, Endpoint target) {
		String log = new String("");
		Integer loglines = target.getLoglines();
		if (null == loglines) {
			loglines = 0;
		}
		try {
			switch (loglines) {
				// The full log
				case -1:
					log = run.getLog();
					break;
					// No log
				case 0:
					break;
				default:
					List<String> logEntries = run.getLog(loglines);
					for (String entry: logEntries) {
						log += entry + "\n";
					}
			}
		} catch (IOException e) {
			log = "Unable to retrieve log";
		}
		return log;
	}
}
