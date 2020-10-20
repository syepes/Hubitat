/**
 *  Copyright (C) Sebastian YEPES
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.transform.Field
import groovy.json.JsonSlurper

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

@Field static Map deviceData = [:]
@Field static List sendQueue = []
@Field static boolean asyncInProgress = false
@Field static final asyncLock = new Object[0]

metadata {
  definition (name: "LokiLogLogger", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Loki/LokiLogLogger.groovy") {
    capability "Initialize"
  }
  command "disconnect"
  command "connect"
  command "cleanQueue"

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }

    input name: "ip", title: "FQDN or IP Address", type: "text", defaultValue: "192.168.1.1", required: true
    input name: "port", title: "API Port", type: "number", defaultValue: 3100, required: true
    input name: "queueMaxSize", title: "Queue Max Size", type: "number", defaultValue: 5000, required: true
    input name: "deviceDetails", title: "Device Details", description: "Collects additional device details (Additional authentication is required if the <b>Hub Login Security</b> is Enabled)", type: "enum", options:[[0:"Disabled"], [1:"Enabled"]], defaultValue: 0, required: true
    input name: "he_usr", title: "HE User", type: "text", description: "Only if <b>Hub Login Security</b> is Enabled", defaultValue: "", required: false
    input name: "he_pwd", title: "HE Password", type: "password", description: "Only if <b>Hub Login Security</b> is Enabled", defaultValue: "", required: false
  }
}

void installed() {
  logger("debug", "installed(${VERSION})")
  updated()
}

void updated() {
  logger("debug", "updated()")
  initialize()
  if (deviceDetails.toInteger()) { deviceInventory() }
}

void parse(String description) {
  def descData = new JsonSlurper().parseText(description)
  // Don't log our own messages, we will get into a loop
  if("${device.id}" != "${descData.id}") {
    if(ip != null && port != null) {
      logger("trace", "parse() - descData: ${descData?.inspect()}")

      try {
        def hub = location.hubs[0]
        def dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        def date = Date.parse(dateFormat, descData.time)
        String ts = date.format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", location.timeZone)
        String line = (descData?.msg?.trim() ?: null)
        if (line == null) {
          logger("warn", "Skipping event without message")
          return
        }

        Map labels = [hub_name: hub?.name?.trim(),
                      hub_ip: hub?.localIP?.trim(),
                      hub_fw: (hub?.firmwareVersionString.trim() ?: 'None'),
                      event_type: 'logs',
                      event_source: (descData?.type?.trim() ?: 'None'),
                      level: (descData?.level?.trim() ?: 'None'),
                      device_id: descData.id,
                      device_id_net: 'None',
                      device_type: (descData?.type?.trim() ?: 'None'),
                      device_name: (descData?.name?.trim() ?: 'None'),
                      device_driver: 'None',
                      device_driver_type: 'None'
        ]

        if (deviceDetails.toInteger()) {
          if(deviceData?.size() > 0 && deviceData?.containsKey(descData.id)) {
            labels['device_id_net'] = deviceData[descData.id].deviceNetworkId
            labels['device_driver'] = deviceData[descData.id].deviceTypeName
            labels['device_driver_type'] = deviceData[descData.id].type
          }
        }

        String json = '''{"labels": "{hub_name=\\"'''+labels.hub_name+'''\\",hub_ip=\\"'''+labels.hub_ip+'''\\",hub_fw=\\"'''+labels.hub_fw+'''\\",event_type=\\"'''+labels.event_type+'''\\",event_source=\\"'''+labels.event_source+'''\\",level=\\"'''+labels.level+'''\\",device_id=\\"'''+labels.device_id+'''\\",device_id_net=\\"'''+labels.device_id_net+'''\\",device_name=\\"'''+labels.device_name+'''\\",device_driver=\\"'''+labels.device_driver+'''\\",device_driver_type=\\"'''+labels.device_driver_type+'''\\"}", "entries": [{"ts": "'''+ts+'''", "line": "'''+line+'''"}]}'''
        pushLog(json)

      } catch(e) {
        logger("error", "parse() - ${e}, descData: ${descData}")
      }

    } else {
      logger("warn", "parse() - Destination IP not set")
    }
  }
}

void connect() {
  logger("debug", "connect()")

  try {
    interfaces.webSocket.close()
    interfaces.webSocket.connect("http://localhost:8080/logsocket", pingInterval: 30, headers: [ "Connection": "keep-alive, Upgrade", "Upgrade": "websocket", "Pragma": "no-cache", "Cache-Control": "no-cache"])
    pauseExecution(1000)
  } catch(e) {
    logger("error", "connect() - ${e}")
  }
}

void disconnect() {
  logger("debug", "disconnect()")
  interfaces.webSocket.close()
}

void uninstalled() {
  logger("debug", "uninstalled()")
  unschedule()
  disconnect()
}

void initialize() {
  logger("debug", "initialize()")
  unschedule()
  runIn(5, "connect")
  schedule("0 0 12 */7 * ?", updateCheck)
  if (deviceDetails.toInteger()) { schedule("0 0 * ? * *", deviceInventory) }
}

void webSocketStatus(String status) {
  logger("debug", "webSocketStatus() - status: ${status}")

  if(status.startsWith("failure")) {
    logger("warn", "Reconnecting to WebSocket")
    // Wait and reconnect
    runIn(5, connect)
  }
}

@groovy.transform.Synchronized("asyncLock")
void pushLog(String evt) {
  logger("debug", "pushLog() - ${evt}")

  sendQueue << evt
  runIn(1, pushLogFromQueue)
}

@groovy.transform.Synchronized("asyncLock")
void pushLogFromQueue() {
  logger("debug", "pushLogFromQueue() - sendQueue: ${sendQueue.size()} / asyncInProgress: ${asyncInProgress}")

  if (sendQueue.size() == 0){ return }
  if (asyncInProgress){ return }
  asyncInProgress = true

  String json = ''
  Map evt = [:]
  try {
    json = '''{"streams": ['''+sendQueue.join(",")+''']}'''
    evt = new JsonSlurper().parseText(json)

  } catch (e) {
    logger("error", "pushLogFromQueue() - Building streams: ${e}, json: ${json}")
    sendQueue = []
    asyncInProgress = false
    return
  }

  try {
    Map postParams = [
      uri: "http://${ip}:${port}/api/prom/push",
      requestContentType: 'application/json',
      contentType: 'application/json',
      headers: ['Content-type':'application/json'],
      body : evt
    ]
    asynchttpPost('logResponse', postParams, [data: sendQueue])

  } catch (e) {
    logger("error", "pushLogFromQueue() - Sending Logs: ${e}")
    asyncInProgress = false
  }

  asyncInProgress = false
}

@groovy.transform.Synchronized("asyncLock")
void logResponse(hubResponse, payload) {
  try {
    if (hubResponse?.status < 300) { // OK
      logger("info", "Sent Logs: ${sendQueue.size()}")
      logger("trace", "logResponse() - API: http://${ip}:${port}/api/prom/push, Response: ${hubResponse.status}, Payload: ${payload}")
      sendQueue = []

    } else { // Failed
      logger("warn", "Failed Sending Logs - Response: ${hubResponse?.status}, QueueSize: ${sendQueue?.size()}")
      logger("trace", "logResponse() - API: http://${ip}:${port}/api/prom/push, Response: ${hubResponse.status}, Headers: ${hubResponse?.headers}, Payload: ${payload}")
      if (sendQueue?.size() >= queueMaxSize) {
        logger("error", "Maximum Queue size reached: ${sendQueue?.size()} >= ${queueMaxSize}, all current logs have been droped")
        sendQueue = []
      }
    }
    asyncInProgress = false

  } catch (e) {
    logger("error", "logResponse() - Response: ${hubResponse.status}, Payload: ${payload}, Error: ${e}")
    asyncInProgress = false
  }
}

@groovy.transform.Synchronized("asyncLock")
void cleanQueue() {
  logger("debug", "cleanQueue() - sendQueue: ${sendQueue.size()} / asyncInProgress: ${asyncInProgress}")

  if (sendQueue.size() == 0){ return }
  if (asyncInProgress){ return }
  asyncInProgress = true

  try {
    logger("info", "Queue has been reseted")
    sendQueue = []
    asyncInProgress = false

  } catch (e) {
    logger("error", "cleanQueue() - Error: ${e}")
    asyncInProgress = false
  }
}

private void loginHE() {
  if (state.authToken != "") { return }

  try {
    state.authToken = ''
    Map params = [
      uri: 'http://localhost:8080',
      path: '/login',
      ignoreSSLIssues:  true,
      requestContentType: 'application/x-www-form-urlencoded',
      body: [username: "syepes", password: '***REMOVED***']
    ]

    httpPost(params) { resp ->
      if (resp.getStatus() == 302) {
        resp.getHeaders('Set-Cookie').each {
          state.authToken = state.authToken + it.value.split(';')[0] + ';'
        }
      } else {
        state.authToken = ''
      }
    }
  } catch (e) {
    logger("error", "loginHE() - Error: ${e}")
  }
}

void deviceInventory() {
  if (he_usr != "" && he_pwd != "") { loginHE() }

  try {
    Map headers = ['Content-Type': 'application/json;charset=UTF-8', 'Host': 'localhost']
    if (he_usr != "" && he_pwd != "") { headers['Cookie'] = state.authToken }

    Map params = [
      uri: 'http://localhost:8080',
      path: '/device/list/all/data',
      contentType: "application/json; charset=utf-8",
      requestContentType: "application/json; charset=utf-8",
      headers: headers
    ]

    deviceData = [:]
    httpGet(params) { resp ->
      logger("trace", "deviceInventory() - Status: ${resp?.getStatus()} / Data: ${resp?.getData()}")

      if (resp.getStatus() == 200) {
        def inv = resp.getData()
        inv?.each { deviceData[it.id] = it }
        logger("info", "Device inventory: ${deviceData?.size()}")
      }
    }
  } catch (e) {
    if (he_usr != "" && he_pwd != "") { state.authToken = '' }
    logger("error", "deviceInventory() - Error: ${e}")
  }
}

/**
 * @param level Level to log at, see LOG_LEVELS for options
 * @param msg Message to log
 */
private logger(level, msg) {
  if (level && msg) {
    Integer levelIdx = LOG_LEVELS.indexOf(level)
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
    if (setLevelIdx < 0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx <= setLevelIdx) {
      log."${level}" "${device.displayName} ${msg}"
    }
  }
}

def updateCheck() {
  Map params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Loki/LokiLogLogger.groovy"]
  asynchttpGet("updateCheckHandler", params)
}

private updateCheckHandler(resp, data) {
  if (resp?.getStatus() == 200) {
    Integer ver_online = (resp?.getData() =~ /(?m).*String VERSION = "(\S*)".*/).with { hasGroup() ? it[0][1]?.replaceAll('[vV]', '')?.replaceAll('\\.', '').toInteger() : null }
    if (ver_online == null) { logger("error", "updateCheck() - Unable to extract version from source file") }

    Integer ver_cur = state.driverInfo?.ver?.replaceAll('[vV]', '')?.replaceAll('\\.', '').toInteger()

    if (ver_online > ver_cur) {
      logger("info", "New version(${ver_online})")
      state.driverInfo.status = "New version (${ver_online})"
    } else if (ver_online == ver_cur) {
      logger("info", "Current version")
      state.driverInfo.status = 'Current version'
    }

  } else {
    logger("error", "updateCheck() - Unable to download source file")
  }
}
