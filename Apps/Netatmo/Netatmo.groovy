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
import com.hubitat.app.ChildDeviceWrapper

@Field String VERSION = "1.2.3"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

private getApiUrl() { "https://api.netatmo.com" }
private getVendorAuthPath() { "/oauth2/authorize" }
private getVendorTokenPath(){ "${apiUrl}/oauth2/token" }
private getScope() { "read_home write_home read_camera write_camera access_camera read_presence write_presence access_presence read_doorbell write_doorbell access_doorbell read_station write_station read_thermostat write_thermostat read_smokedetector write_smokedetector read_homecoach write_homecoach read_june write_june access_velux read_velux write_velux read_muller write_muller read_smarther write_smarther read_magellan write_magellan" }
private getClientId() { settings.clientId }
private getClientSecret() { settings.clientSecret }
private getServerUrl() { getFullApiServerUrl() }
private getCallbackUrl() { getServerUrl() + "/oauth/callback?access_token=${state.accessToken}" }
private getBuildRedirectUrl() { getServerUrl() + "/oauth/initialize?access_token=${state.accessToken}" }
private getWebhookUrl() { "${serverUrl}/webhook?access_token=${state.accessToken}" }

definition(
  name: "Netatmo",
  namespace: "syepes",
  author: "Sebastian YEPES",
  description: "Netatmo Product Integrations",
  category: "",
  oauth: true,
  singleInstance: true,
  iconUrl: "https://github.com/CopyCat73/SmartThings-Dev/raw/master/NetatmoSecurity.png",
  iconX2Url: "https://github.com/CopyCat73/SmartThings-Dev/raw/master/NetatmoSecurity@2x.png"
)

preferences {
  page(name: "Credentials", title: "OAuth2 Credentials", content: "authPage", install: false)
  page(name: "Settings", title: "Netatmo security settings", content: "Settings", install: false)
}

mappings {
  path("/oauth/callback") {action: [GET: "callback"]}
  path("/webhook") {action: [GET: "webhook", POST: "webhook"]}
}


def installed() {
  logger("debug", "installed(${VERSION})")
  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION, status:'Current version']
  }
  state.initialSetup = false
  initialize()
}

def uninstalled() {
  logger("debug", "uninstalled()")

  removeChildDevices(getChildDevices())
  if (state.webhookInstalled) {
    dropWebhook()
  }
  unschedule()
}

def updated() {
  logger("debug", "updated() - settings: ${settings.inspect()}")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    if (state.driverInfo == null || state.driverInfo.isEmpty()) {
      state.driverInfo = [ver:VERSION, status:'Current version']
    }
  }

  initialize()
}

def initialize() {
  logger("debug", "initialize() - settings: ${settings.inspect()}")

  // Pull the latest device info into state
  getSecurityDevicesAndPersonList()

  settings.devices.each { deviceId ->
    Map detail = state?.deviceDetail[deviceId]

    try {
      switch(detail?.type) {
        case 'NDB':
          logger("info", "Creating device: Doorbell (${detail?.type}) - ${detail.name}/${detail.homeName}")
          createChildDevice("Netatmo - Doorbell", detail)
        break
        case 'NOC':
          logger("info", "Creating device: Camera Presence (${detail?.type}) - ${detail.name}/${detail.homeName}")
          createChildDevice("Netatmo - Presence", detail)
        break
        case 'NACamera':
          logger("info", "Creating device: Camera Welcome (${detail?.type}) - ${detail.name}/${detail.homeName}")
          createChildDevice("Netatmo - Welcome", detail)
        break
        case 'NACamDoorTag':
          logger("info", "Creating device: Door and Window Sensor (${detail?.type}) - ${detail.name}/${detail.cameraName}/${detail.homeName}")
          createChildDevice("Netatmo - Sensor", detail)
        break
        case 'NSD':
          logger("info", "Creating device: Smart Smoke Alarm (${detail?.type}) - ${detail.name}/${detail.homeName}")
          createChildDevice("Netatmo - Smoke Alarm", detail)
        break
        default:
          logger("warn", "initialize() - Unsupported Device (${detail?.type}) - ${detail}")
        break
      }
    } catch (Exception e) {
      logger("error", "initialize() - Device creation Exception: ${e.inspect()}")
    }
  }

  settings.people.each { personId ->
    Map detail = state?.personDetail[personId]

    try {
      logger("info", "Creating device: Person - ${detail.name}")
      createChildDevice("Netatmo - Person", detail)
    } catch (Exception e) {
      logger("error", "initialize() - Device creation Exception: ${e.inspect()}")
    }
  }

  // Cleanup any other devices that need to go away
  def allInstalled = [settings.devices, settings.people].findAll().join()
  if (allInstalled.size()>0) {
    def delete = getChildDevices().findAll{ !allInstalled.contains(it.deviceNetworkId) }
    logger("info", "Removing devices: ${delete.inspect()}")
    delete.each { deleteChildDevice(it.deviceNetworkId) }
  } else {
    logger("info", "Removing all devices")
    removeChildDevices(getChildDevices())
  }
  
  if (installWebhook && !state.webhookInstalled) {
    addWebhook()
  } else if (!installWebhook && state.webhookInstalled) {
    dropWebhook()
    state.webhookInstalled = false
  }

  schedule("0 0 12 */7 * ?", updateCheck)

  // Do the initial checkState
  checkState()
  if (['5', '10', '15', '30'].contains(stateCheckInterval) ) {
    schedule("0 */${stateCheckInterval} * ? * *", checkState)
  } else {
    schedule("0 0 */${stateCheckInterval} ? * *", checkState)
  }
}

def checkState() {
  logger("debug", "checkState()")

  getSecurityDevicesAndPersonList()
  def cd_devices = getChildDevices()

  settings.devices.each { deviceId ->
    def data = state?.deviceState[deviceId]
    if (data) {
      // Cameras
      def child = cd_devices?.find { it.deviceNetworkId == deviceId && data['type']?.matches("^(NOC|NDB|NACamera)\$") }
      child?.sendEvent(name:'switch', value: data['status'] =~ /^(connected|on)/ ? 'on' : 'off')
      child?.sendEvent(name:'status', value: data['status'])
      child?.sendEvent(name:'sd_status', value: data['sd_status'])
      child?.sendEvent(name:'alim_status', value: data['alim_status'])
      if (data['light_mode_status'] != 'N/A') { child?.sendEvent(name:'light_mode', value: data['light_mode_status']) }
      if (data['quick_display_zone'] != 0) { child?.sendEvent(name:'quick_display_zone', value: data['quick_display_zone']) }
      if (data['max_peers_reached'] != 'N/A') { child?.sendEvent(name:'max_peers_reached', value: data['max_peers_reached']) }
      if (data['websocket_connected'] != 'N/A') { child?.sendEvent(name:'websocket_connected', value: data['websocket_connected']) }

      // Sensor
      child = cd_devices?.find { it.deviceNetworkId == deviceId && data['type']?.matches("^(NACamDoorTag)\$") }
      child?.sendEvent(name:'contact', value: data['status'])
      child?.sendEvent(name:'battery', value: data['battery_percent'])
      child?.sendEvent(name:'rf', value: data['rf'])
      child?.sendEvent(name:'last_activity', value: data['last_activity'])

      // Smoke
      child = cd_devices?.find { it.deviceNetworkId == deviceId && data['type']?.matches("^(NSD)\$") }
      child?.sendEvent(name:'switch', value: data['status'] =~ /^(connected|on)/ ? 'on' : 'off')
      child?.sendEvent(name:'status', value: data['status'])
      child?.sendEvent(name:'battery', value: data['battery_percent'])

    } else {
      logger("warn", "checkState() - Could not find device ${deviceId} state")
    }
  }

  settings.people.each { personId ->
    Map data = state?.personDetail[personId]
    if (data) {
      def child = cd_devices?.find { it.deviceNetworkId == personId }
      if (data['out_of_sight']) {
        child?.away()
        child?.sendEvent(name:'last_seen', value: data['last_seen'])
      } else {
        child?.seen()
        child?.sendEvent(name:'last_seen', value: data['last_seen'])
      }
    } else {
      logger("warn", "checkState() - status (out_of_sight) error on person ${personId}")
    }
  }
}

def authPage() {
  logger("debug", "authPage()")

  String description
  Boolean uninstallAllowed = false
  Boolean oauthTokenProvided = false

  if (!state.initialSetup) {
    state.initialSetup = true
    state.webhookInstalled = false
  }

  if (!state.accessToken) {
    try{
      logger("debug", "authPage() - Creating access token")
      state.accessToken = createAccessToken()
    } catch(Exception e){
      logger("error", "authPage() - ${e.message}")
      return "${e.message}"
    }
  }

  def redirectUrl = getBuildRedirectUrl()
  logger("debug", "authPage() - redirectUrl: ${redirectUrl}")

  if (state.authToken) {
    description = "Tap 'Next' to proceed"
    uninstallAllowed = true
    oauthTokenProvided = true
  } else {
    description = "Click to enter Credentials"
  }

  if (!oauthTokenProvided) {
    logger("debug", "authPage() - Showing the login page")
    return dynamicPage(name: "Credentials", title: "Authorize Connection", nextPage:"Settings", uninstall: uninstallAllowed, install: false) {
      section("Enter Netatmo Application Details") {
        paragraph "Get these details after creating a new application on https://dev.netatmo.com/apps/createanapp"
        input name: "clientId", title: "Client ID", type: "text", required: true
        input name: "clientSecret", title: "Client Secret", type: "text", required: true, submitOnChange: true
      }
      section() {
        paragraph "Click below to login to Netatmo and authorize Hubitat access"
        href url: oauthInitUrl(), title: "Connect to netatmo:", description: description, external:true, required:false
      }
    }

  } else {
    logger("debug", "authPage() - Showing the device page")
    return dynamicPage(name: "Credentials", title: "Connected", nextPage:"Settings", uninstall: uninstallAllowed, install: false) {
      section() {
        input name: "Devices", style: "embedded", title: "Netatmo is now connected to Hubitat!", description: description, required: false
      }
    }
  }
}

def Settings() {
  logger("debug", "Settings()")

  Map tmpList = getSecurityDevicesAndPersonList()
  Map devices = tmpList[0]
  Map persons = tmpList[1]

  dynamicPage(name: "Settings", title: "Settings", install: true) {
    section("Security Devices") {
      input name: "devices", title: "Select device(s) to track", type: "enum", required: false, multiple: true, options: devices
    }
    section("People") {
      input name: "people", title: "Select person(s) to track (Creates presence device)", type: "enum", required: false, multiple: true, options: persons
    }
    section("Preferences") {
      input name: "installWebhook", title: "Enable WebHook", description: "Activate webhook (Enables motion detect and person tracking)", type: "bool", defaultValue: true, required: true
    }
    section("Logging") {
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 2, required: true
    }
  }
}

def oauthInitUrl() {
  try {
    state.oauthInitState = UUID.randomUUID().toString()
    logger("debug", "oauthInitUrl() - Set oauthInitState: ${state?.oauthInitState}")

    Map oauthParams = [
      response_type: "code",
      client_id: getClientId(),
      client_secret: getClientSecret(),
      state: state.oauthInitState,
      redirect_uri: getCallbackUrl(),
      scope: getScope()
    ]
    Map params = [
      uri: getApiUrl(),
      path: getVendorAuthPath(),
      contentType: "application/json; charset=utf-8",
      requestContentType: "application/json; charset=utf-8",
      queryString: toQueryString(oauthParams)
    ]

    logger("debug", "oauthInitUrl() - URL: ${getApiUrl() + getVendorAuthPath()}, PARAMS: ${oauthParams.inspect()}")
    logger("debug", "oauthInitUrl() - Request: ${getApiUrl() + getVendorAuthPath()}?${toQueryString(oauthParams)}")

    return "${getApiUrl() + getVendorAuthPath()}?${toQueryString(oauthParams)}"
  } catch(Exception e){
    logger("error", "oauthInitUrl() - Request Exception: ${e.inspect()}")
  }
}

def callback() {
  logger("debug", "callback() - params: ${params.inspect()}")

  def code = params.code
  def oauthState = params.state

  if (oauthState == state.oauthInitState) {
    Map tokenParams = [
      client_secret: getClientSecret(),
      client_id : getClientId(),
      grant_type: "authorization_code",
      redirect_uri: getCallbackUrl(),
      code: code,
      scope: getScope()
    ]
    Map params = [
      uri: getVendorTokenPath(),
      contentType: 'application/x-www-form-urlencoded',
      body: tokenParams
    ]

    try {
      logger("debug", "callback() - URL: ${getVendorTokenPath()}, PARAMS: ${params.inspect()}")
      logger("debug", "callback() - Request: ${getVendorTokenPath()}?${toQueryString(tokenParams)}")
      httpPost(params) { resp ->
        logger("trace", "callback() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

        def slurper = new JsonSlurper()
        resp?.getData()?.each { key, value ->
          def data = slurper.parseText(key)
          state.refreshToken = data.refresh_token
          state.authToken = data.access_token
          state.tokenExpires = now() + (data.expires_in * 1000)
        }
      }
    } catch (e) {
      logger("error", "callback() - Request Exception: ${e.inspect()}")
    }

    // Handle success and failure here, and render stuff accordingly
    if (state.authToken) {
      success()
    } else {
      fail()
    }

  } else {
    logger("error", "callback() - Failed oauthState(${oauthState}) != state.oauthInitState(${state?.oauthInitState})")
  }
}

def webhook() {
  logger("trace", "webhook() - request: ${request.inspect()}")
  List resp = []
  resp << [name: "result" , value: "ok"]

  try {
    def payload = request.JSON
    if (payload?.message) { logger("info", "${payload?.message}") }
    logger("debug", "webhook() - requestSource: ${request.requestSource}, payload: ${payload.inspect()}")

    def cd_devices = getChildDevices()
    logger("debug", "webhook() - cd_devices: ${cd_devices.inspect()}")


    // Smart Indoor Camera (welcome) Sensors
    if (payload?.push_type?.startsWith('NACamera-tag')) {
      String moduleID = payload?.module_id
      ChildDeviceWrapper cd_module = cd_devices?.find { it.deviceNetworkId == moduleID }

      switch (payload?.event_type) {
        case 'tag_big_move':
        case 'tag_small_move':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Movement detected by ${cd_module}")
          cd_module?.motion(payload?.event_type?.split('_')[1])
        break
        case 'tag_open':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Open detected by ${cd_module}")
          if(logDescText) {
            log.info "${app.name} ${cd_module} is open"
          } else {
            logger("info", "${cd_module} is open")
          }
          cd_module?.sendEvent(name:'contact', value: "open")
        break
        case 'module_connect':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Module connected by ${cd_module}")
          if(logDescText) {
            log.info "${app.name} ${cd_module} is connected"
          } else {
            logger("info", "${cd_module} is connected")
          }
          cd_module?.sendEvent(name:'contact', value: "connected")
        break
        case 'module_disconnect':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Module disconnected by ${cd_module}")
          if(logDescText) {
            log.info "${app.name} ${cd_module} is disconnected"
          } else {
            logger("info", "${cd_module} is disconnected")
          }
          cd_module?.sendEvent(name:'contact', value: "disconnection")
        break
        case 'tag_uninstalled':
        break
        default:
          logger("warn", "webhook() - Unhandled by ${cd_module} - event_type: ${payload?.event_type} - payload: ${payload}")
        break
      }

      return resp
    }

    // Smart Indoor Camera (welcome)
    if (payload?.push_type?.startsWith('NACamera')) {
      String cameraID = payload?.camera_id
      ChildDeviceWrapper cd_camera = cd_devices?.find { it.deviceNetworkId == cameraID }

      if (!cd_camera) {
        logger("warn", "webhook() - Local Camera: ${cameraID} (${payload?.home_name}) not found")
      }

      String personName = 'Unknown'
      if (payload?.event_type == 'person') {
        def cd_person = cd_devices?.find { c ->
          payload?.persons?.find { p ->
            if (p.id == c.deviceNetworkId) { personName = c.label; return c } else { false }
          }
        }

        if (!cd_person) {
          if (!payload?.message?.contains('Unknown')) {
            logger("warn", "webhook() - Local Person: ${payload?.persons?.collect{ it?.id }} / ${payload?.persons?.collect{ it?.face_url }} (${payload?.home_name}) not found")
          }
        } else {
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Person detected (${personName}) by ${cd_camera}")
          cd_person?.seen(payload?.snapshot_url)
          cd_person?.contactSensorClose()
        }
      }

      switch (payload?.event_type) {
        case 'on':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera switched on by ${cd_camera}")
          cd_camera?.on()
        break
        case 'off':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera switched off by ${cd_camera}")
          cd_camera?.off()
        break
        case 'connection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera connection on by ${cd_camera}")
          if(logDescText) {
            log.info "${app.name} ${cd_camera} is connected"
          } else {
            logger("info", "${cd_camera} is connected")
          }
          cd_camera?.sendEvent(name:'switch', value: 'on')
          cd_camera?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'disconnection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera disconnection by ${cd_camera}")
          if(logDescText) {
            log.warn "${app.name} ${cd_camera} is disconnected"
          } else {
            logger("warn", "${cd_camera} is disconnected")
          }
          cd_camera?.sendEvent(name:'switch', value: 'off')
          cd_camera?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'movement':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Movement detected by ${cd_camera}")
          if (payload?.snapshot_url) {
            cd_camera?.motion(payload?.snapshot_url, personName)
          } else {
            cd_camera?.motion(null, "${personName}")
          }
        break
        case 'person':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Person detected (${personName}) by ${cd_camera}")
          if (payload?.snapshot_url) {
            cd_camera?.motion(payload?.snapshot_url, personName)
          } else {
            cd_camera?.motion(null, "${personName}")
          }
        break
        case 'alarm_started':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Alarm detected (${personName}) by ${cd_camera}")
          if (payload?.snapshot_url) {
            cd_camera?.alarm(payload?.snapshot_url)
          } else {
            cd_camera?.alarm()
          }
        break
        default:
          logger("warn", "webhook() - Unhandled by ${cd_camera} - event_type: ${payload?.event_type} - payload: ${payload}")
        break
      }
    }


    // Smart Outdoor Camera (presence)
    if (payload?.push_type?.startsWith('NOC')) {
      String cameraID = payload?.camera_id
      ChildDeviceWrapper cd_camera = cd_devices?.find { it.deviceNetworkId == cameraID }

      if (!cd_camera) {
        logger("warn", "webhook() - Local Camera: ${cameraID} (${payload?.home_name}) not found")
      }

      switch (payload?.event_type) {
        case 'on':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera switched on by ${cd_camera}")
          cd_camera?.on()
        break
        case 'off':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera switched off by ${cd_camera}")
          cd_camera?.off()
        break
        case 'connection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera connection on by ${cd_camera}")
          if(logDescText) {
            log.info "${app.name} ${cd_camera} is connected"
          } else {
            logger("info", "${cd_camera} is connected")
          }
          cd_camera?.sendEvent(name:'switch', value: 'on')
          cd_camera?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'disconnection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera disconnection by ${cd_camera}")
          if(logDescText) {
            log.info "${app.name} ${cd_camera} is disconnected"
          } else {
            logger("info", "${cd_camera} is disconnected")
          }
          cd_camera?.sendEvent(name:'switch', value: 'off')
          cd_camera?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'light_mode':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Camera light is ${payload?.sub_type} by ${cd_camera}")
          cd_camera?.sendEvent(name:'switch_light', value: payload?.sub_type)
        break
        case 'animal':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Animal detected by ${cd_camera}")
          cd_camera?.animal(payload?.snapshot_url)
        break
        case 'human':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Human detected by ${cd_camera}")
          cd_camera?.human(payload?.snapshot_url)
        break
        case 'vehicle':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Car detected by ${cd_camera}")
          cd_camera?.vehicle(payload?.snapshot_url)
        break
        case 'movement':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Movement detected by ${cd_camera}")
          cd_camera?.motion(payload?.snapshot_url)
        break
        default:
          logger("warn", "webhook() - Unhandled by ${cd_camera} - event_type: ${payload?.event_type} - payload: ${payload}")
        break
      }
    }

    // Doorbell
    if (payload?.push_type?.startsWith('NDB')) {
      String doorbellID = payload?.device_id
      ChildDeviceWrapper cd_doorbell = cd_devices?.find { it.deviceNetworkId == doorbellID }

      if (!cd_doorbell) {
        logger("warn", "webhook() - Local Doorbell: ${doorbellID} (${payload?.home_name}) not found")
        return resp
      }

      switch (payload?.event_type) {
        case 'on':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Doorbell switched on by ${cd_doorbell}")
          cd_doorbell?.on()
        break
        case 'off':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Doorbell switched off by ${cd_doorbell}")
          cd_doorbell?.off()
        break
        case 'connection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Doorbell connection on by ${cd_doorbell}")
          if(logDescText) {
            log.info "${app.name} ${cd_doorbell} is connected"
          } else {
            logger("info", "${cd_doorbell} is connected")
          }
          cd_doorbell?.sendEvent(name:'switch', value: 'on')
          cd_doorbell?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'disconnection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Doorbell disconnection by ${cd_doorbell}")
          if(logDescText) {
            log.info "${app.name} ${cd_doorbell} is disconnected"
          } else {
            logger("info", "${cd_doorbell} is disconnected")
          }
          cd_doorbell?.sendEvent(name:'switch', value: 'off')
          cd_doorbell?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'incoming_call':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Doorbell Incoming Call by ${cd_doorbell}")
          cd_doorbell?.ring('incoming', payload?.snapshot_url)
        break
        case 'accepted_call':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Doorbell Incoming Call on by ${cd_doorbell}")
          cd_doorbell?.ring('accepted', payload?.snapshot_url)
        break
        case 'missed_call':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Doorbell Missed Call on by ${cd_doorbell}")
          cd_doorbell?.ring('missed', payload?.snapshot_url)
        break
        case 'human':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Human detected by ${cd_doorbell}")
          cd_doorbell?.human(payload?.snapshot_url)
        break
        case 'movement':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Movement detected by ${cd_doorbell}")
          cd_doorbell?.motion(payload?.snapshot_url)
        break
        default:
          logger("warn", "webhook() - Unhandled by ${cd_doorbell} - event_type: ${payload?.event_type} - payload: ${payload}")
        break
      }
    }

    // Smoke
    if (payload?.push_type?.startsWith('NSD')) {
      String deviceID = payload?.device_id
      ChildDeviceWrapper cd_device = cd_devices?.find { it.deviceNetworkId == deviceID }

      if (!cd_device) {
        logger("warn", "webhook() - Local Smoke Alarm: ${deviceID} (${payload?.home_name}) not found")
      }

      switch (payload?.event_type) {
        case 'connection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Smoke Alarm connection on by ${cd_device}")
          if(logDescText) {
            log.info "${app.name} ${cd_device} is connected"
          } else {
            logger("info", "${cd_device} is connected")
          }
          cd_device?.sendEvent(name:'switch', value: 'on')
          cd_device?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'disconnection':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Smoke Alarm disconnection by ${cd_device}")
          if(logDescText) {
            log.info "${app.name} ${cd_device} is disconnected"
          } else {
            logger("info", "${cd_device} is disconnected")
          }
          cd_device?.sendEvent(name:'switch', value: 'off')
          cd_device?.sendEvent(name:'status', value: payload?.event_type)
        break
        case 'hush':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Smoke Alarm hushed by ${cd_device}")
          if(logDescText) {
            log.info "${app.name} ${cd_device} Alarm hushed for 15 min"
          } else {
            logger("info", "${cd_device} Alarm hushed for 15 min")
          }
        break
        case 'smoke':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Alarm ${payload?.sub_type == 0 ? 'cleared' : 'detected'} by ${cd_device}")
          cd_device?.smoke(payload?.sub_type)
        break
        case 'battery_status':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Battery level (${payload?.sub_type}) by ${cd_device}")
          cd_device?.battery(payload?.sub_type)
        break
        case 'wifi_status':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Wifi Status (${payload?.sub_type == 0 ? 'error' : 'ok'}) by ${cd_device}")
          if(logDescText) {
            log.info "${app.name} ${cd_device} Wifi ${payload?.sub_type == 0 ? 'error' : 'ok'}"
          } else {
            logger("info", "${cd_device} Wifi ${payload?.sub_type == 0 ? 'error' : 'ok'}")
          }
        break
        case 'sound_test':
          logger("debug", "webhook() - event_type: ${payload?.event_type} - Sound Test (${payload?.sub_type == 0 ? 'ok' : 'error'}) by ${cd_device}")
          if(logDescText) {
            log.info "${app.name} ${cd_device} Sound Test ${payload?.sub_type == 0 ? 'ok' : 'error'}"
          } else {
            logger("info", "${cd_device} Sound Test ${payload?.sub_type == 0 ? 'ok' : 'error'}")
          }
        break
        default:
          logger("warn", "webhook() - Unhandled by ${cd_device} - event_type: ${payload?.event_type} - payload: ${payload}")
        break
      }
    }

  } catch(Exception e){
    logger("debug", "webhook() - Request Exception: ${e.inspect()}")
  }

  return resp
}

private success() {
  logger("info", "OAuth authentication flow succeeded")

  String message = """
  <p>We have located your netatmo account</p>
  <p>Close this page and install the application again. you will not be prompted for credentials next time.</p>
  """
  connectionStatus(message)
}

private fail() {
  logger("warn", "OAuth authentication flow failed")

  String message = """
  <p>The connection could not be established with netatmo!</p>
  <p>Close this page and attempt install the application again.</p>
  """
  connectionStatus(message)
}

private connectionStatus(message, redirectUrl = null) {
  logger("debug", "connectionStatus()")

  String redirectHtml = ""
  if (redirectUrl) {
    redirectHtml = """
      <meta http-equiv="refresh" content="3; url=${redirectUrl}" />
    """
  }

  String html = """
    <!DOCTYPE html>
    <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>Netatmo Connection</title>
      <style type="text/css">
        * {
          box-sizing: border-box;
        }
        @font-face {
          font-family: 'Swiss 721 W01 Thin';
          src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
          src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
            url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
            url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
            url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
          font-weight: normal;
          font-style: normal;
        }
        @font-face {
          font-family: 'Swiss 721 W01 Light';
          src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
          src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
            url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
            url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
            url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
          font-weight: normal;
          font-style: normal;
        }
        .container {
          width: 100%;
          padding: 40px;
          /*background: #eee;*/
          text-align: center;
        }
        svg {
          vertical-align: middle;
        }
        img {
          vertical-align: middle;
        }
        img:nth-child(2) {
          margin: 0 30px;
        }
        p {
          font-size: 2.2em;
          font-family: 'Swiss 721 W01 Thin';
          text-align: center;
          color: #666666;
          margin-bottom: 0;
        }
        span {
          font-family: 'Swiss 721 W01 Light';
        }
      </style>
    </head>
    <body>
      <div class="container">
        <svg width="280px" height="150px" xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 613.82 111.751"><g><path fill="#193B6A" d="M581.925 39.672c-15.989 0-28.996 13.007-28.996 28.996v11.859c0 15.989 13.007 28.996 28.996 28.996h.014c15.989 0 28.996-13.007 28.996-28.996V68.668c.001-15.988-13.007-28.996-29.01-28.996zm18.687 40.855c0 10.296-8.376 18.672-18.672 18.672h-.014c-10.296 0-18.672-8.376-18.672-18.672V68.668c0-10.296 8.376-18.672 18.687-18.672 10.296 0 18.672 8.376 18.672 18.672v11.859h-.001zM243.969 39.685c-15.78.454-28.144 13.809-28.144 29.596v11.246c0 15.989 13.007 28.996 29.011 28.996h3.83a28.992 28.992 0 0020.748-8.741l2.146-2.198a2.092 2.092 0 00-.035-2.956l-4.395-4.292a2.09 2.09 0 00-2.956.035l-2.146 2.197c-3.544 3.631-8.29 5.63-13.377 5.63h-3.83c-10.312 0-18.672-8.36-18.672-18.672v-.267h45.6a2.084 2.084 0 002.084-2.084v-9.508c-.001-16.27-13.473-29.454-29.864-28.982zm19.539 30.251h-37.36v-1.268c0-10.296 8.376-18.672 18.688-18.672 10.296 0 18.673 8.376 18.673 18.672v1.268h-.001zM200.955 20.08h-6.22a2.084 2.084 0 00-2.084 2.084v66.669l-54.072-68.192a1.517 1.517 0 00-1.178-.561h-4.854a2.084 2.084 0 00-2.084 2.084v85.109a2.09 2.09 0 002.084 2.084h6.22a2.084 2.084 0 002.084-2.084v-66.98l54.072 68.504v-.001c.291.357.72.561 1.178.561h4.854a2.084 2.084 0 002.084-2.084v-85.11a2.083 2.083 0 00-2.084-2.083zM430.543 40.304h-14.292V26.899a2.09 2.09 0 00-2.09-2.09h-6.143a2.09 2.09 0 00-2.09 2.09v13.405h-6.758a2.09 2.09 0 00-2.09 2.09v6.143a2.09 2.09 0 002.09 2.09h6.758v40.824c0 9.777 7.926 17.703 17.703 17.703h6.913a2.09 2.09 0 002.09-2.09v-6.143a2.09 2.09 0 00-2.09-2.09h-6.913a7.38 7.38 0 01-7.38-7.38V50.628h14.292a2.09 2.09 0 002.09-2.09v-6.143a2.09 2.09 0 00-2.09-2.091zM313.938 40.304h-14.292V26.899a2.09 2.09 0 00-2.09-2.09h-6.143a2.09 2.09 0 00-2.09 2.09v13.405h-6.758a2.09 2.09 0 00-2.09 2.09v6.143a2.09 2.09 0 002.09 2.09h6.758v40.824c0 9.777 7.926 17.703 17.703 17.703h6.912a2.09 2.09 0 002.09-2.09v-6.143a2.09 2.09 0 00-2.09-2.09h-6.912a7.38 7.38 0 01-7.38-7.38V50.628h14.292a2.09 2.09 0 002.09-2.09v-6.143a2.09 2.09 0 00-2.09-2.091zM514.884 39.683c-8.491-.152-16.069 3.999-20.848 10.385-4.68-6.299-12.177-10.389-20.616-10.389a25.52 25.52 0 00-15.331 5.097v-2.382a2.09 2.09 0 00-2.09-2.09h-6.143a2.09 2.09 0 00-2.09 2.09v64.67a2.09 2.09 0 002.09 2.09h6.143a2.09 2.09 0 002.09-2.09v-41.73c0-8.643 7.189-15.638 15.915-15.321 8.304.302 14.76 7.383 14.76 15.693v41.358a2.09 2.09 0 002.09 2.09h6.143a2.09 2.09 0 002.09-2.09v-41.73c0-8.642 7.188-15.638 15.914-15.321 8.304.302 14.761 7.383 14.761 15.693v41.359a2.09 2.09 0 002.09 2.09h6.143a2.09 2.09 0 002.09-2.09v-41.73c.001-13.992-11.257-25.402-25.201-25.652zM359.84 39.672h-2.818a28.992 28.992 0 00-20.748 8.741l-2.146 2.198a2.09 2.09 0 00.036 2.956l4.395 4.291a2.09 2.09 0 002.956-.036l2.145-2.198c3.544-3.63 8.289-5.629 13.376-5.629h2.803c9.112 0 15 4.613 15 11.753v5.005H350.73c-11.697 0-23.537 7.222-23.537 21.026 0 14.939 12.201 21.744 23.537 21.744 9.646 0 18.027-4.257 24.109-9.711v7.253a2.09 2.09 0 002.09 2.09h6.143a2.09 2.09 0 002.09-2.09V61.748c.002-10.632-7.923-22.076-25.322-22.076zm-9.109 59.527c-12.261 0-13.214-8.741-13.214-11.42 0-3.975 2.783-10.702 13.214-10.702h24.109v4.94c-.119 3.438-9.701 17.182-24.109 17.182z"></path> <defs><path id="a" d="M3.088 41.122v62.335c0 4.884 5.603 7.645 9.476 4.669l37.246-28.62a5.886 5.886 0 017.168-.005l37.249 28.536c3.874 2.968 9.469.206 9.469-4.674v-62.24c0-1.832-.853-3.56-2.307-4.674L56.972 2.421a5.888 5.888 0 00-7.162 0L5.395 36.448a5.886 5.886 0 00-2.307 4.674z"></path></defs> <clipPath id="b"><use overflow="visible" xlink:href="#a"></use></clipPath> <linearGradient id="c" x1="-9.87" x2="31.427" y1="92.084" y2="50.788" gradientTransform="translate(0 4)" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#ff7911"></stop> <stop offset=".565" stop-color="#fe7711"></stop> <stop offset=".769" stop-color="#fb700f"></stop> <stop offset=".913" stop-color="#f5650c"></stop> <stop offset="1" stop-color="#f05909"></stop></linearGradient> <path fill="url(#c)" d="M3.643 38.641a5.88 5.88 0 00-.555 2.48v66.281c0 3.272 3.753 5.121 6.348 3.128l43.956-33.777L3.643 38.641z" clip-path="url(#b)"></path> <linearGradient id="d" x1="-13.714" x2="30.214" y1="89.133" y2="52.273" gradientTransform="translate(0 4)" gradientUnits="userSpaceOnUse"><stop offset=".5" stop-color="#ff8d15" stop-opacity="0"></stop> <stop offset=".67" stop-color="#fe8a14" stop-opacity=".34"></stop> <stop offset=".772" stop-color="#fa8212" stop-opacity=".543"></stop> <stop offset=".855" stop-color="#f3740f" stop-opacity=".711"></stop> <stop offset=".929" stop-color="#ea600a" stop-opacity=".858"></stop> <stop offset=".996" stop-color="#de4603" stop-opacity=".992"></stop> <stop offset="1" stop-color="#dd4403"></stop></linearGradient> <path fill="url(#d)" d="M3.643 38.641a5.88 5.88 0 00-.555 2.48v66.281c0 3.272 3.753 5.121 6.348 3.128l43.956-33.777L3.643 38.641z" clip-path="url(#b)"></path> <path fill="#FF8F15" d="M3.643 38.641l49.749 38.112 49.749-38.112a5.891 5.891 0 00-1.752-2.193L55.991 1.67a4.275 4.275 0 00-5.199 0l-31.91 24.446L5.394 36.448a5.87 5.87 0 00-1.751 2.193z" clip-path="url(#b)"></path> <path fill="#FFA618" d="M53.391 76.753l3.579 2.742.002.001 34.275 26.258 6.105 4.677c2.595 1.988 6.343.138 6.343-3.131V41.122c0-.87-.198-1.713-.555-2.481L53.391 76.753z" clip-path="url(#b)"></path> <linearGradient id="e" x1="78.969" x2="115.549" y1="87.97" y2="61.036" gradientTransform="translate(0 4)" gradientUnits="userSpaceOnUse"><stop offset=".436" stop-color="#ff8f15"></stop> <stop offset=".741" stop-color="#ff9916" stop-opacity=".46"></stop> <stop offset="1" stop-color="#ffa618" stop-opacity="0"></stop></linearGradient> <path fill="url(#e)" d="M53.391 76.753l3.579 2.742.002.001 34.275 26.258 6.105 4.677c2.595 1.988 6.343.138 6.343-3.131V41.122c0-.87-.198-1.713-.555-2.481L53.391 76.753z" clip-path="url(#b)"></path></g></svg>
        <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="Connected device icon" />
        <img src="https://cdn.shopify.com/s/files/1/2575/8806/t/20/assets/logo-image-file.png" alt="Hubitat logo" /><br/>
        ${message}
      </div>
    </body>
    </html>
  """
  render contentType: 'text/html', data: html
}

def refreshToken() {
  logger("debug", "refreshToken()")

  Map oauthParams = [
    client_secret: getClientSecret(),
    client_id: getClientId(),
    grant_type: "refresh_token",
    refresh_token: state.refreshToken
  ]
  Map params = [
    uri: getVendorTokenPath(),
    contentType: 'application/x-www-form-urlencoded',
    body: oauthParams,
  ]

  // OAuth Step 2: Request access token with our client Secret and OAuth "Code"
  try {
    logger("debug", "refreshToken() - URL: ${getVendorTokenPath()}, PARAMS: ${params.inspect()}")
    logger("debug", "refreshToken() - Request: ${getVendorTokenPath()}?${toQueryString(oauthParams)}")
    httpPost(params) { resp ->
      logger("trace", "refreshToken() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      def slurper = new JsonSlurper();

      resp?.getData()?.each {key, value ->
        def data = slurper.parseText(key)
        state.refreshToken = data.refresh_token
        state.netatmoAccessToken = data.access_token
        state.tokenExpires = now() + (data.expires_in * 1000)
        logger("info", "Refresh token success")
        return true
      }
    }
  } catch (Exception e) {
    logger("error", "refreshToken() - Request Exception: ${e.inspect()}")
    return false
  }

  // We didn't get an access token
  if ( !state.netatmoAccessToken ) {
    return false
  }
}

Map getSecurityDevicesAndPersonList() {
  logger("debug", "getSecurityDevicesAndPersonList()")

  Map deviceList = [:]
  Map personList = [:]
  Map combinedList = [:]
  state.deviceDetail = [:]
  state.deviceState = [:]
  state.personDetail = [:]

  apiGet("/api/gethomedata",[:]) { resp ->
    logger("trace", "getSecurityDevicesAndPersonList() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

    if (resp) {
      state.response = resp.data.body
      resp.data.body.homes.each { home ->
        home.cameras.each { camera ->
          String key = camera.id
          String cameraName = "${camera.name} (${home.name})"
          String cameraAKey = camera?.containsKey('vpn_url') ? camera.vpn_url.split('/').getAt(5) : 'N/A'
          deviceList[key] = cameraName
          state.deviceDetail[key] = ["id" : key]
          state.deviceDetail[key] << ["name" : cameraName]
          state.deviceDetail[key] << ["type" : camera.type]
          state.deviceDetail[key] << ["homeID" : home.id]
          state.deviceDetail[key] << ["homeName" : home.name]
          state.deviceDetail[key] << ["vpn_url" : camera.vpn_url]
          state.deviceDetail[key] << ["access_key" : cameraAKey]
          state.deviceState[key] = ["type": camera.type]
          state.deviceState[key] << ["status": camera.status]
          state.deviceState[key] << ["sd_status": camera.sd_status]
          state.deviceState[key] << ["alim_status": camera.alim_status]
          state.deviceState[key] << ["last_setup": camera.last_setup]
          state.deviceState[key] << ["is_local": camera?.containsKey('is_local') ? camera.is_local : 'N/A']
          state.deviceState[key] << ["light_mode_status": camera?.containsKey('light_mode_status') ? camera.light_mode_status : 'N/A']
          state.deviceState[key] << ["quick_display_zone": camera?.containsKey('quick_display_zone') ? camera.quick_display_zone : 0]
          state.deviceState[key] << ["max_peers_reached": camera?.containsKey('max_peers_reached') ? camera.max_peers_reached : 'N/A']
          state.deviceState[key] << ["websocket_connected": camera?.containsKey('websocket_connected') ? camera.websocket_connected : 'N/A']

          camera?.modules?.each { module ->
            String key_mod = module.id
            String moduleName = "${module.name} (${home.name}/${camera.name})"
            deviceList[key_mod] = moduleName
            state.deviceDetail[key_mod] = ["id" : key_mod]
            state.deviceDetail[key_mod] << ["name" : moduleName]
            state.deviceDetail[key_mod] << ["type" : module.type]
            state.deviceDetail[key_mod] << ["category" : module.category]
            state.deviceDetail[key_mod] << ["homeID" : home.id]
            state.deviceDetail[key_mod] << ["homeName" : home.name]
            state.deviceDetail[key_mod] << ["cameraID" : camera.id]
            state.deviceDetail[key_mod] << ["cameraName" : camera.name]
            state.deviceState[key_mod] = ["status": (module.status == 'no_news' ? 'disconnection' : module.status)]
            state.deviceState[key_mod] << ["type": module.type]
            state.deviceState[key_mod] << ["monitoring": module.monitoring]
            state.deviceState[key_mod] << ["rf": module.rf]
            state.deviceState[key_mod] << ["battery_percent": module.battery_percent]
            state.deviceState[key_mod] << ["last_activity": module.last_activity]
          }
        }
        home.persons.each { person ->
          if (person.pseudo) {
            String key = person.id
            String personName = person.pseudo
            personList[key] = personName
            state.personDetail[key] = ["id" : key]
            state.personDetail[key] << ["name" : personName]
            state.personDetail[key] << ["homeID" : home.id]
            state.personDetail[key] << ["homeName" : home.name]
            state.personDetail[key] << ["last_seen" : person.last_seen]
            state.personDetail[key] << ["out_of_sight" : person.out_of_sight]
          }
        }
        home.smokedetectors.each { smokedetector ->
          String key = smokedetector.id
          String smokedetectorName = "${smokedetector.name} (${home.name})"
          deviceList[key] = smokedetectorName
          state.deviceDetail[key] = ["id" : key]
          state.deviceDetail[key] << ["name" : smokedetectorName]
          state.deviceDetail[key] << ["type" : smokedetector.type]
          state.deviceDetail[key] << ["homeID" : home.id]
          state.deviceDetail[key] << ["homeName" : home.name]
          state.deviceState[key] = ["type": smokedetector.type]
          state.deviceState[key] << ["last_setup": smokedetector.last_setup]
        }
      }
    }
  }

  // Merge data
  logger("debug", "getSecurityDevicesAndPersonList() - deviceList: ${deviceList.sort()}, personList: ${personList.sort()}")
  combinedList[0] = deviceList.sort() { it.value.toLowerCase() }
  combinedList[1] = personList.sort() { it.value.toLowerCase() }
  return combinedList
}

def setAway(homeID) {
  logger("debug", "setAway(${homeID})")
  setAway(homeID, null)
}

def setAway(homeID, personID) {
  logger("debug", "setAway(${homeID}, ${personID})")

  Map query = ["home_id": homeID]
  if (personID) {
    query <<["person_id": personID]
  }

  apiGet("/api/setpersonsaway",query) { resp ->
    logger("trace", "setAway(${homeID}, ${personID}) - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

    if (resp) {
      if (resp.data.status == "ok") {
        logger("debug", "setAway(${homeID}, ${personID}) - Succesful")
      }
    }
  }
}

def addWebhook() {
  logger("debug", "addWebhook()")
  apiGet("/api/addwebhook", ["url": getWebhookUrl(), "app_type": "app_camera"]) { resp ->
    if (resp) {
      if (resp.data.status == "ok") {
        logger("debug", "addWebhook() - Succesful")
        state.webhookInstalled = true
      }
    }
  }
}

def dropWebhook() {
  logger("debug", "dropWebhook()")
  apiGet("/api/dropwebhook", ["app_type": "app_camera"]) { resp ->
    if (resp) {
      if (resp.data.status == "ok") {
        logger("debug", "dropWebhook() - Succesful")
      }
    }
  }
}

private removeChildDevices(delete) {
  logger("debug", "removeChildDevices() - Removing ${delete.size()} devices")
  delete.each { deleteChildDevice(it.deviceNetworkId) }
}

private createChildDevice(String typeName, Map detail) {
  try {
    def hub = location.hubs[0]
    def cd = getChildDevice(detail?.id)

    if(!cd) {
      logger("debug", "createChildDevice() - Creating child (typeName: ${typeName}, detail: ${detail.inspect()}")
      cd = addChildDevice("syepes", typeName, detail?.id, hub.id, [name: detail?.id, label: detail?.name, isComponent: false])
      cd.setHome(detail?.homeID, detail?.homeName)

      if ( detail?.type?.matches("^(NOC|NDB|NACamera)\$")) {
        cd.setAKey(detail?.access_key)
      }

      if (detail?.type == 'NACamDoorTag') {
        cd.setCamera(detail?.cameraID, detail?.cameraName)
      }
    } else {
      logger("debug", "createChildDevice() - Device ${detail?.name}/${detail?.id} already exists")
    }
  } catch (e) {
    logger("error", "createChildDevice() - Device creation Exception: ${e.inspect()}")
  }
}

private apiGet(String path, Closure callback) {
  apiGet(path, [:], callback);
}

private apiGet(String path, Map query, Closure callback) {
  logger("debug", "apiGet()")

  if (!state.netatmoAccessToken || now() >= state.tokenExpires) {
    refreshToken()
  }

  if (!state.accessToken) {
    try{
      logger("debug", "apiGet() - Creating access token")
      state.accessToken = createAccessToken()
    } catch(Exception e){
      logger("error", "apiGet() - ${e.message}")
      return "${e.message}"
    }
  }

  query['access_token'] = state.netatmoAccessToken
  Map params = [
    uri: getApiUrl(),
    path: path,
    'query': query
  ]

  try {
    logger("debug", "apiGet() - URL: ${getApiUrl() + path}, PARAMS: ${query.inspect()}")
    logger("debug", "apiGet() - Request: ${getApiUrl() + path}?${toQueryString(query)}")
    httpGet(params) { resp ->
      logger("trace", "apiGet() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      callback.call(resp)
    }
  } catch (Exception e) {
    logger("error", "apiGet() - Request Exception: ${e.inspect()}")
    if(refreshToken()) {
      logger("warn", "apiGet() - Trying request again after refreshing token")
      httpGet(params)	{ resp ->
        callback.call(resp)
      }
    } else {
      logger("warn", "apiGet() - Token refresh failed")
    }
  }
}

private String toQueryString(Map m) {
  return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
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
      log."${level}" "${app.name} ${msg}"
    }
  }
}

def updateCheck() {
  def params = [uri: "https://raw.githubusercontent.com/syepes/Hubitat/master/Apps/Netatmo/Netatmo.groovy"]
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
