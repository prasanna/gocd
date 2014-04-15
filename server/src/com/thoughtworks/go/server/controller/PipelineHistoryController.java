/*************************GO-LICENSE-START*********************************
 * Copyright 2014 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *************************GO-LICENSE-END***********************************/

package com.thoughtworks.go.server.controller;

import java.util.HashMap;
import java.util.Map;
import javax.naming.NamingException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.thoughtworks.go.config.CaseInsensitiveString;
import com.thoughtworks.go.config.PipelineConfig;
import com.thoughtworks.go.domain.PipelinePauseInfo;
import com.thoughtworks.go.i18n.Localizer;
import com.thoughtworks.go.util.json.JsonHelper;
import com.thoughtworks.go.util.json.JsonMap;
import com.thoughtworks.go.presentation.pipelinehistory.PipelineInstanceModels;
import static com.thoughtworks.go.server.controller.actions.JsonAction.jsonFound;
import static com.thoughtworks.go.server.controller.actions.JsonAction.jsonNotAcceptable;

import com.thoughtworks.go.server.presentation.models.PipelineHistoryJsonPresentationModel;
import com.thoughtworks.go.server.service.GoConfigService;
import com.thoughtworks.go.server.service.PipelineHistoryService;
import com.thoughtworks.go.server.service.PipelinePauseService;
import com.thoughtworks.go.server.service.PipelineScheduleQueue;
import com.thoughtworks.go.server.service.SchedulingCheckerService;
import com.thoughtworks.go.server.service.SecurityService;
import com.thoughtworks.go.server.service.result.ServerHealthStateOperationResult;
import com.thoughtworks.go.server.util.Pagination;
import com.thoughtworks.go.server.util.UserHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PipelineHistoryController {
    private PipelineHistoryService pipelineHistoryService;
    private GoConfigService goConfigService;
    private PipelineScheduleQueue pipelineScheduleQueue;
    private SchedulingCheckerService schedulingCheckerService;
    private SecurityService securityService;
    private Localizer localizer;
    private PipelinePauseService pipelinePauseService;

    protected PipelineHistoryController() {
    }

    @Autowired
    PipelineHistoryController(PipelineHistoryService pipelineHistoryService,
                              GoConfigService goConfigService,
                              PipelineScheduleQueue pipelineScheduleQueue,
                              SchedulingCheckerService schedulingCheckerService, SecurityService securityService,
                              Localizer localizer, PipelinePauseService pipelinePauseService) {
        this.pipelineHistoryService = pipelineHistoryService;
        this.goConfigService = goConfigService;
        this.pipelineScheduleQueue = pipelineScheduleQueue;
        this.schedulingCheckerService = schedulingCheckerService;
        this.securityService = securityService;
        this.localizer = localizer;
        this.pipelinePauseService = pipelinePauseService;
    }

    @RequestMapping(value = "/pipeline/history", method = RequestMethod.GET)
    public ModelAndView list(@RequestParam("pipelineName")String pipelineName) throws Exception {
        Map model = new HashMap();
        model.put("pipelineName", pipelineName);
        model.put("l", localizer);
        return new ModelAndView("pipeline/pipeline_history", model);
    }

    @RequestMapping(value = "/**/pipelineHistory.json", method = RequestMethod.GET)
    public ModelAndView list(@RequestParam("pipelineName")String pipelineName,
                             @RequestParam(value = "perPage", required = false)Integer perPageParam,
                             @RequestParam(value = "start", required = false)Integer startParam,
                             HttpServletResponse response, HttpServletRequest request) throws NamingException {
        PipelineConfig pipelineConfig = goConfigService.pipelineConfigNamed(new CaseInsensitiveString(pipelineName));
        String username = CaseInsensitiveString.str(UserHelper.getUserName().getUsername());

        Pagination pagination = null;
        try {
            pagination = Pagination.pageStartingAt(startParam, pipelineHistoryService.totalCount(pipelineName), perPageParam);
        } catch (Exception e) {
            JsonMap json = new JsonMap();
            JsonHelper.addDeveloperErrorMessage(json, e);
            return jsonNotAcceptable(json).respond(response);
        }

        PipelinePauseInfo pauseInfo = pipelinePauseService.pipelinePauseInfo(pipelineName);
        boolean hasBuildCauseInBuffer = pipelineScheduleQueue.hasBuildCause(CaseInsensitiveString.str(pipelineConfig.name()));
        PipelineInstanceModels pipelineHistory = pipelineHistoryService.load(pipelineName, pagination, username, true);

        boolean hasForcedBuildCause = pipelineScheduleQueue.hasForcedBuildCause(pipelineName);

        PipelineHistoryJsonPresentationModel historyJsonPresenter = new PipelineHistoryJsonPresentationModel(
                pauseInfo,
                pipelineHistory,
                pipelineConfig,
                pagination, canForce(pipelineConfig, username),
                hasForcedBuildCause, hasBuildCauseInBuffer, canPause(pipelineConfig, username));
        return jsonFound(historyJsonPresenter).respond(response);
    }

    private boolean canPause(PipelineConfig pipelineConfig, String username) {
        return securityService.hasOperatePermissionForPipeline(new CaseInsensitiveString(username), CaseInsensitiveString.str(pipelineConfig.name()));
    }

    private boolean canForce(PipelineConfig pipelineConfig, String username) {
        return schedulingCheckerService.canManuallyTrigger(pipelineConfig, username,
                new ServerHealthStateOperationResult());
    }

}