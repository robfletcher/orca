/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.orca.webhook.tasks

import com.google.common.base.Strings
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.apache.commons.lang3.StringUtils
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

import javax.annotation.Nonnull
import java.time.Duration
import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorWebhookTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = TimeUnit.SECONDS.toMillis(1)
  long timeout = TimeUnit.HOURS.toMillis(1)
  private static final String JSON_PATH_NOT_FOUND_ERR_FMT = "Unable to parse %s: JSON property '%s' not found in response body"

  WebhookService webhookService
  WebhookProperties webhookProperties

  @Autowired
  MonitorWebhookTask(WebhookService webhookService, WebhookProperties webhookProperties) {
    this.webhookService = webhookService
    this.webhookProperties = webhookProperties
  }

  @Override
  long getDynamicBackoffPeriod(StageExecution stage, Duration taskDuration) {
    if (taskDuration.toMillis() > TimeUnit.MINUTES.toMillis(1)) {
      // task has been running > 1min, drop retry interval to every 15 sec
      return Math.max(backoffPeriod, TimeUnit.SECONDS.toMillis(15))
    }

    return backoffPeriod
  }

  @Override
  TaskResult execute(StageExecution stage) {
    WebhookStage.StageData stageData = stage.mapTo(WebhookStage.StageData)

    if (StringUtils.isBlank(stageData.statusEndpoint) && !stageData.monitorOnly) {
      throw new IllegalStateException("Missing required parameter: statusEndpoint = ${stageData.statusEndpoint}")
    }

    if (stageData.monitorOnly && StringUtils.isAllBlank(stageData.statusEndpoint, stageData.url)) {
      throw new IllegalStateException("Missing required parameter. Either webhook url or statusEndpoint are required")
    }

    // Preserve the responses we got from createWebhookTask, but reset the monitor subkey as we will overwrite it new data
    def originalResponse = stage.context.getOrDefault("webhook", [:])
    originalResponse["monitor"] = [:]
    originalResponse = [webhook: originalResponse]

    def response
    try {
      response = webhookService.getWebhookStatus(stage)
      log.debug(
        "Received status code {} from status endpoint {} in execution {} in stage {}",
        response.statusCode,
        stageData.statusEndpoint,
        stage.execution.id,
        stage.id
      )
    } catch (HttpStatusCodeException e) {
      def statusCode = e.getStatusCode()
      def statusValue = statusCode.value()

      boolean shouldRetry = statusCode.is5xxServerError() ||
                            statusValue in webhookProperties.defaultRetryStatusCodes ||
                            ((stageData.retryStatusCodes != null) && (stageData.retryStatusCodes.contains(statusValue)))

      if (shouldRetry) {
        log.warn("Failed to get webhook status from ${stageData.statusEndpoint} with statusCode=${statusValue}, will retry", e)
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      String errorMessage = "an exception occurred in webhook monitor to ${stageData.statusEndpoint}: ${e}"
      log.error(errorMessage, e)
      Map<String, ?> outputs = originalResponse
      outputs.webhook.monitor << [error: errorMessage]
      if (e.getResponseHeaders() != null && !e.getResponseHeaders().isEmpty()) {
        outputs.webhook.monitor << [headers: e.getResponseHeaders().toSingleValueMap()]
      }
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
    } catch (Exception e) {
      if (e instanceof UnknownHostException || e.cause instanceof UnknownHostException) {
        log.warn("name resolution failure in webhook for pipeline ${stage.execution.id} to ${stageData.statusEndpoint}, will retry.", e)
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }
      if (e instanceof SocketTimeoutException || e.cause instanceof SocketTimeoutException) {
        log.warn("Socket timeout when polling ${stageData.statusEndpoint}, will retry.", e)
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      String errorMessage = "an exception occurred in webhook monitor to ${stageData.statusEndpoint}: ${e}"
      log.error(errorMessage, e)
      Map<String, ?> outputs = originalResponse
      outputs.webhook.monitor << [error: errorMessage]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
    }

    def result
    def responsePayload = originalResponse
    responsePayload.webhook.monitor =  [
          body: response.body,
          statusCode: response.statusCode,
          statusCodeValue: response.statusCode.value()
        ]

    if (!response.getHeaders().isEmpty()) {
      responsePayload.webhook.monitor << [headers: response.getHeaders().toSingleValueMap()]
    }

    if (Strings.isNullOrEmpty(stageData.statusJsonPath)) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(responsePayload).build()
    }

    try {
      result = JsonPath.read(response.body, stageData.statusJsonPath)
    } catch (PathNotFoundException e) {
      responsePayload.webhook.monitor << [error: String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "status", stageData.statusJsonPath)]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
    }
    if (!(result instanceof String || result instanceof Number || result instanceof Boolean)) {
      responsePayload.webhook.monitor << [error: "The json path '${stageData.statusJsonPath}' did not resolve to a single value", resolvedValue: result]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
    }

    if (stageData.progressJsonPath) {
      def progress
      try {
        progress = JsonPath.read(response.body, stageData.progressJsonPath)
      } catch (PathNotFoundException e) {
        responsePayload.webhook.monitor << [error: String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "progress", stageData.statusJsonPath)]
        return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
      }
      if (!(progress instanceof String)) {
        responsePayload.webhook.monitor << [error: "The json path '${stageData.progressJsonPath}' did not resolve to a String value", resolvedValue: progress]
        return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
      }
      if (progress) {
        responsePayload.webhook.monitor << [progressMessage: progress]
      }
    }

    def statusMap = createStatusMap(stageData.successStatuses, stageData.canceledStatuses, stageData.terminalStatuses)

    if (result instanceof Number) {
      def status = result == 100 ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING
      responsePayload.webhook.monitor << [percentComplete: result]
      return TaskResult.builder(status).context(responsePayload).build()
    } else if (statusMap.containsKey(result.toString().toUpperCase())) {
      return TaskResult.builder(statusMap[result.toString().toUpperCase()]).context(responsePayload).build()
    }

    return TaskResult.builder(ExecutionStatus.RUNNING).context(response ? responsePayload : originalResponse).build()
  }

  @Override void onCancel(@Nonnull StageExecution stage) {
    webhookService.cancelWebhook(stage)
  }

  private static Map<String, ExecutionStatus> createStatusMap(String successStatuses, String canceledStatuses, String terminalStatuses) {
    def statusMap = [:]
    statusMap << mapStatuses(successStatuses, ExecutionStatus.SUCCEEDED)
    if (canceledStatuses) {
      statusMap << mapStatuses(canceledStatuses, ExecutionStatus.CANCELED)
    }
    if (terminalStatuses) {
      statusMap << mapStatuses(terminalStatuses, ExecutionStatus.TERMINAL)
    }
    return statusMap
  }

  private static Map<String, ExecutionStatus> mapStatuses(String statuses, ExecutionStatus status) {
    statuses.split(",").collectEntries { [(it.trim().toUpperCase()): status] }
  }
}
