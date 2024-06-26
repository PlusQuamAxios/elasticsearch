/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.xpack.inference.services.elasticsearch;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.core.Nullable;
import org.elasticsearch.inference.Model;
import org.elasticsearch.inference.TaskSettings;
import org.elasticsearch.inference.TaskType;
import org.elasticsearch.xpack.core.ml.action.CreateTrainedModelAssignmentAction;
import org.elasticsearch.xpack.core.ml.action.StartTrainedModelDeploymentAction;
import org.elasticsearch.xpack.core.ml.utils.ExceptionsHelper;

import java.util.Map;

import static org.elasticsearch.xpack.core.ml.inference.assignment.AllocationStatus.State.STARTED;

public class CustomElandModel extends ElasticsearchModel {

    public static CustomElandModel build(
        String inferenceEntityId,
        TaskType taskType,
        String service,
        CustomElandInternalServiceSettings serviceSettings,
        @Nullable TaskSettings taskSettings
    ) {
        return taskSettings == null
            ? new CustomElandModel(inferenceEntityId, taskType, service, serviceSettings)
            : new CustomElandModel(inferenceEntityId, taskType, service, serviceSettings, taskSettings);
    }

    public CustomElandModel(
        String inferenceEntityId,
        TaskType taskType,
        String service,
        CustomElandInternalServiceSettings serviceSettings
    ) {
        super(inferenceEntityId, taskType, service, serviceSettings);
    }

    private CustomElandModel(
        String inferenceEntityId,
        TaskType taskType,
        String service,
        CustomElandInternalServiceSettings serviceSettings,
        TaskSettings taskSettings
    ) {
        super(inferenceEntityId, taskType, service, serviceSettings, taskSettings);
    }

    @Override
    public CustomElandInternalServiceSettings getServiceSettings() {
        return (CustomElandInternalServiceSettings) super.getServiceSettings();
    }

    @Override
    StartTrainedModelDeploymentAction.Request getStartTrainedModelDeploymentActionRequest() {
        var startRequest = new StartTrainedModelDeploymentAction.Request(
            this.getServiceSettings().getModelId(),
            this.getInferenceEntityId()
        );
        startRequest.setNumberOfAllocations(this.getServiceSettings().getNumAllocations());
        startRequest.setThreadsPerAllocation(this.getServiceSettings().getNumThreads());
        startRequest.setWaitForState(STARTED);

        return startRequest;
    }

    @Override
    ActionListener<CreateTrainedModelAssignmentAction.Response> getCreateTrainedModelAssignmentActionListener(
        Model model,
        ActionListener<Boolean> listener
    ) {

        return new ActionListener<>() {
            @Override
            public void onResponse(CreateTrainedModelAssignmentAction.Response response) {
                listener.onResponse(Boolean.TRUE);
            }

            @Override
            public void onFailure(Exception e) {
                if (ExceptionsHelper.unwrapCause(e) instanceof ResourceNotFoundException) {
                    listener.onFailure(
                        new ResourceNotFoundException(
                            "Could not start the TextEmbeddingService service as the "
                                + "custom eland model [{0}] for this platform cannot be found."
                                + " Custom models need to be loaded into the cluster with eland before they can be started.",
                            getServiceSettings().getModelId()
                        )
                    );
                    return;
                }
                listener.onFailure(e);
            }
        };
    }

    public static TaskSettings taskSettingsFromMap(TaskType taskType, Map<String, Object> taskSettingsMap) {
        if (TaskType.RERANK.equals(taskType)) {
            return CustomElandRerankTaskSettings.defaultsFromMap(taskSettingsMap);
        }

        return null;
    }
}
