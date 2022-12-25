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

@Field String VERSION = "1.0.3"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

@Field static Map deviceData = [:]
@Field static List sendQueue = []
@Field static boolean asyncInProgress = false
@Field static final asyncLock = new Object[0]

metadata {
  definition (name: "LokiZWaveLogger", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Loki/LokiZWaveLogger.groovy") {
    capability "Initialize"
  }
  attribute "status", "string"
  attribute "queueSize", "number"
  command "disconnect"
  command "connect"
  command "cleanQueue"

  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }

    input name: "loki_url", title: "Loki URL", type: "text", defaultValue: "https://logs-prod-eu-west-0.grafana.net/loki/api/v1/push", required: true
    input name: "loki_user", title: "Loki User", type: "text", defaultValue: "", required: false
    input name: "loki_password", title: "Loki Password/Token", type: "password", defaultValue: "", required: false
    input name: "queueMaxSize", title: "Queue Max Size", type: "number", defaultValue: 5000, required: true
    input name: "deviceDetails", title: "Device Details", description: "Collects additional device details (Additional authentication is required if the <b>Hub Login Security</b> is Enabled)", type: "enum", options:[[0:"Disabled"], [1:"Enabled"]], defaultValue: 0, required: true
    input name: "he_usr", title: "HE User", type: "text", description: "Only if <b>Hub Login Security</b> is Enabled", defaultValue: "", required: false
    input name: "he_pwd", title: "HE Password", type: "password", description: "Only if <b>Hub Login Security</b> is Enabled", defaultValue: "", required: false
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")

  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION]
  }

  if (state.deviceInfo == null) {
    state.deviceInfo = [:]
  }

  updated()
}

void updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }

  initialize()
  if (deviceDetails.toInteger()) { deviceInventory() }
}

/*
{ "seqNo":1,
  "name":"Lamp",
  "id":"09",
  "imeReport":{
    "0":[ 0 ],
    "1":[ 0, 5 ],
    "2":[ 28, 0, 0, 0, 2 ],
    "3":[ -90, -35, 127, 127, 127 ],
    "4":[ 1 ],
    "5":[ 1 ]
  },
  "time":"2020-11-23 20:30:16.477",
  "type":"zwaveRx"
}
*/

void parse(String description) {
  def descData = new JsonSlurper().parseText(description)
  // Don't log our own messages, we will get into a loop
  if("${device.id}" != "${descData.id}") {
    if(loki_url != null) {
      logger("trace", "parse() - descData: ${descData?.inspect()}")

      try {
        def hub = location.hubs[0]
        def dateFormat = "yyyy-MM-dd HH:mm:ss.SSS"
        def date = Date.parse(dateFormat, descData.time)
        String ts = date.time * 1000 * 1000
        Map ime = decodeIME(descData.imeReport)
        if (ime.isEmpty()) {
          logger("warn", "Skipping event")
          return
        }

        Map labels = [hub_name: hub?.name?.trim(),
                      hub_ip: hub?.localIP?.trim(),
                      hub_fw: (hub?.firmwareVersionString?.trim() ?: 'None'),
                      event_type: (descData?.type?.trim() ?: 'None'),
                      event_source: 'dev',
                      level: 'info',
                      device_id: 'None',
                      device_id_net: descData.id,
                      device_name: (descData?.name?.trim() ?: 'None'),
                      device_type: 'None',
                      device_driver: 'None',
                      device_driver_type: 'None',
                      routeChanged: (ime?.routeChanged?.trim() ?: 'None'),
                      speed: (ime?.speed?.trim() ?: 'None')
        ]

        if (deviceDetails.toInteger()) {
          if(deviceData?.size() == 0) {
            deviceInventory()
          }
          if(deviceData?.size() > 0 && deviceData?.containsKey(descData.id)) {
            labels['device_id'] = deviceData[descData.id].id
            labels['device_driver'] = deviceData[descData.id].deviceTypeName
            labels['device_driver_type'] = deviceData[descData.id].type
          }
        }

        String line = "seqNo: ${descData.seqNo}, device_name: ${labels.device_name}, routeChanged: ${labels.routeChanged}, transmissionTime: ${ime.transmissionTime}, repeaters: [${ime.repeaters}], speed: ${ime.speed}, rssi: [${ime.get('rssi','None')}], AckChannel: ${ime.get('AckChannel','None')}, TransmitChannel: ${ime.get('TransmitChannel','None')}"
        String json = '''{"stream": '''+ groovy.json.JsonOutput.toJson(labels) +''', "values": [["'''+ts+'''", "'''+line+'''"]]}'''
        pushLog(json)

      } catch(e) {
        logger("error", "parse() - ${e}, descData: ${descData}")
      }

    } else {
      logger("warn", "parse() - Destination Loki URL has not been set")
    }
  }
}

// {"seqNo":164,"name":"Switch - Washing Machine","id":"37","imeReport":{"0":[0],"1":[0,65],"2":[32,37,0,0,2],"3":[-103,127,-55,127,127],"4":[1],"5":[1]},"time":"2020-09-14 22:00:33.751","type":"zwaveRx"}
Map decodeIME(report) {
  logger("trace", "decodeIME() - report: ${report}")

  Map ime = [:]
  try {
    report?.each{ k, v ->
      switch(k.toInteger()) {
        case 0:
          ime['routeChanged'] = v.getAt(0) == 1 ? 'true' : 'false'
        break
        case 1:
          def n = ((v?.getAt(0) & 0xff) << 2) | (v?.getAt(1) & 0xff)
          ime['transmissionTime'] = "${n}ms"
        break
        case 2:
          if (v.getAt(0) == 0) {
            ime['repeaters'] = "None"
          } else {
            String n = ''
            for (i = 0; i<4; i++) {
              if (v.getAt(i) != 0){
                n += n ? ','+ Integer.toHexString(v.getAt(i)) : Integer.toHexString(v.getAt(i))
              }
            }
            ime['repeaters'] = n
          }

          if(v.getAt(4) == 0) {
            ime['speed'] = "Unknown"
          } else if(v.getAt(4) == 1) {
            ime['speed'] = "9.6 kbs"
          } else if(v.getAt(4) == 2) {
            ime['speed'] = "40 kbs"
          } else if(v.getAt(4) == 3) {
            ime['speed'] = "100 kbs"
          }

        break
        case 3:
          String n = ''
          v.each { i ->
            if( (i & 0xff) == 127 ) {
              n += n ? ',N/A' : 'N/A'
            } else if( (i & 0xff) == 126 ) {
              n += n ? ',MAX' : 'MAX'
            } else if( (i & 0xff) == 125 ) {
              n += n ? ',MIN' : 'MIN'
            } else {
              n += n ? ",${i} dBm" : "${i} dBm"
            }
          }
          ime['rssi'] = n
        break
        case 4:
          ime['AckChannel'] = v.getAt(0)
        break
        case 5:
          ime['TransmitChannel'] = v.getAt(0)
        break
        default:
        break
      }
    }
  } catch(e) {
    logger("error", "decodeIME() - Decoding IME: ${e}, report: ${report}")
    ime = [:]
  }

  logger("debug", "decodeIME() - ime: ${ime}")
  return ime
}

void connect() {
  logger("debug", "connect()")

  try {
    interfaces.webSocket.close()
    interfaces.webSocket.connect("http://localhost:8080/zwaveLogsocket", pingInterval: 30, headers: [ "Connection": "keep-alive, Upgrade", "Upgrade": "websocket", "Pragma": "no-cache", "Cache-Control": "no-cache"])
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
  if (deviceDetails.toInteger()) { schedule("0 0 * ? * *", deviceInventory) }
}

void webSocketStatus(String status) {
  logger("debug", "webSocketStatus() - status: ${status}")

  if(status.startsWith("status: open")) {
    sendEvent(name: "status", value: "open", displayed: true)
    return
  } else if(status.startsWith("status: closing")) {
    sendEvent(name: "status", value: "closing", displayed: true)
    return
  } else if(status.startsWith("failure")) {
    logger("warn", "Reconnecting to WebSocket (${status})")
    sendEvent(name: "status", value: "failed", displayed: true)
    // Wait and reconnect
    runIn(5, connect)
  } else {
    logger("warn", "Reconnecting to WebSocket (${status})")
    sendEvent(name: "status", value: "lost", displayed: true)
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
      uri: "${loki_url}",
      requestContentType: 'application/json',
      contentType: 'application/json',
      headers: ['Content-type':'application/json'],
      body : evt
    ]
    if (loki_user != null && loki_password != null) {
      String auth = "Basic "+ "${loki_user}:${loki_password}".bytes.encodeBase64().toString()
      postParams['headers'] << ['Authorization':auth]
    }

    sendEvent(name: "queueSize", value: sendQueue?.size(), displayed: true)
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
      logger("trace", "logResponse() - API: ${loki_url}, Response: ${hubResponse.status}, Payload: ${payload}")
      sendQueue = []
      sendEvent(name: "queueSize", value: 0, displayed: true)

    } else { // Failed
      String errData = hubResponse?.getErrorData()
      String errMsg = hubResponse?.getErrorMessage()
      logger("warn", "Failed Sending Logs - QueueSize: ${sendQueue?.size()}, Response: ${hubResponse?.status}, Error: ${errData} ${errMfg}")
      logger("trace", "logResponse() - API: ${loki_url}, Response: ${hubResponse.status}, Headers: ${hubResponse?.headers}, Payload: ${payload}")
      if (sendQueue?.size() >= queueMaxSize) {
        logger("error", "Maximum Queue size reached: ${sendQueue?.size()} >= ${queueMaxSize}, all current logs have been droped")
        sendQueue = []
        sendEvent(name: "queueSize", value: 0, displayed: true)
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
      body: [username: he_usr, password: he_pwd]
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
        inv?.each { deviceData[it.deviceNetworkId] = it }
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
