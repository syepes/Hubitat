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

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

private getApiUrl() { "https://accsmart.panasonic.com" }

definition(
  name: "Panasonic - Comfort Cloud",
  namespace: "syepes",
  author: "Sebastian YEPES",
  description: "Panasonic - Comfort Cloud",
  category: "",
  oauth: false,
  singleInstance: true,
  iconUrl: "https://play-lh.googleusercontent.com/w9UBcPUO6BgrxMunskXdNnGe0qcQTb2sFXz22OOG7nomIhvYgY_LuOHqK0Y8wOZ434M=w200-h200",
  iconX2Url: "https://play-lh.googleusercontent.com/w9UBcPUO6BgrxMunskXdNnGe0qcQTb2sFXz22OOG7nomIhvYgY_LuOHqK0Y8wOZ434M=w400-h400"
)

preferences {
  page(name: "credentials", title: "Credentials")
  page(name: "login", title: "Login")
  page(name: "config", title: "Config")
}

/* Preferences */
Map credentials() {
  logger("debug", "credentials()")

  return dynamicPage(name: "credentials", title: "Login Credentials", nextPage:"login", install: false, uninstall:false) {
    section("Panasonic - Comfort Cloud") {
      input name: "user", title: "User", type: "text", defaultValue: "", required: true
      input name: "password", title: "Password", type: "password", defaultValue: "", required: true
    }
  }
}

Map login() {
  logger("debug", "login()")

  if (getToken()) {
    logger("debug", "login() - Successful")
    return dynamicPage(name: "login", title: "Comfort Cloud", nextPage: "config", install: false, uninstall:false) {
      section() {
        paragraph "Successfully connected to Comfort Cloud. Click Next"
      }
    }
  } else {
    logger("debug", "login() - Failed")
    return dynamicPage(name: "login", title: "Comfort Cloud", nextPage: "credentials", install: false, uninstall:false) {
      section() {
        paragraph "Unable to connect to Comfort Cloud, double check your credentials. Click Next"
      }
    }
  }
}

Map config() {
  logger("debug", "config()")
  Map devices = getDeviceList()

  return dynamicPage(name: "config", title: "Config", install: true, uninstall:true) {
    section("Devices") {
      input name: "devices", title: "Select Device(s) to track / Device (Groups)", type: "enum", required: false, multiple: true, options: devices
    }
    section("Preferences") {
      input name: "stateCheckInterval", title: "State Check", description: "Check interval of the current state", type: "enum", options:[[0:"Disabled"], [2:"2min"], [5:"5min"], [10:"10min"], [15:"15min"], [30:"30min"], [2:"1h"], [3:"3h"], [4:"4h"], [6:"6h"], [8:"8h"], [12: "12h"]], defaultValue: 2, required: true
    }
    section("Logging") {
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
    }
  }
}

def installed() {
  logger("debug", "installed(${VERSION})")
  if (state.driverInfo == null || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    state.driverInfo = [ver:VERSION]
  }
  initialize()
}

def uninstalled() {
  logger("debug", "uninstalled()")

  removeChildDevices(getChildDevices())
  unschedule()
}

def updated() {
  logger("debug", "updated() - settings: ${settings.inspect()} / InstallationState: ${app.getInstallationState()}")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    if (state.driverInfo == null || state.driverInfo.isEmpty()) {
      state.driverInfo = [ver:VERSION]
    }
  }

  initialize()
}

void initialize() {
  logger("debug", "initialize() - settings: ${settings.inspect()}")
  // // Pull the latest device info into state
  getDevicesStatus()

  settings.devices.each { deviceId ->
    Map dd = state?.deviceDetail[deviceId]
    String groupId = dd?.groupId
    Map gd = state?.groupDetail[groupId]

    if (dd && gd) {
      ChildDeviceWrapper group = createGroup(gd)
      if (group) {
        logger("info", "Creating Group: ${gd.groupName} / Device: ${dd.deviceName}")
        ChildDeviceWrapper device = group.addDevice(dd)
        if (device) {
          Map ds = state?.deviceState[deviceId]
          device.setStates(ds)
        }
      }
    }
  }

  // Cleanup any other devices that need to go away
  List allInstalled = [settings.devices].findAll().join()
  if (allInstalled.size()>0) {
    List groups = getChildDevices().collect{ it.getChildDevices().collect{ it.deviceNetworkId?.split('-',2)?.getAt(0) } }?.flatten()?.unique()
    List device = getChildDevices().collect{ it.getChildDevices().collect{ it.deviceNetworkId?.split('-',2)?.getAt(1) } }?.flatten()?.unique()
    List delete = device.findAll{ !allInstalled.contains(it) }

    getChildDevices()?.each {
      if (groups.contains(it?.deviceNetworkId)) {
        delete.each { devID ->
          logger("info", "Removing device: ${it.deviceNetworkId}-${devID}")
          it.deleteChildDevice(it.deviceNetworkId +"-"+ devID)
        }
      } else {
        logger("info", "Removing Group: ${it.deviceNetworkId}")
        deleteChildDevice(it?.deviceNetworkId)
      }
    }

  } else {
    logger("info", "Removing all devices")
    removeChildDevices(getChildDevices())
  }

  unschedule()
  if (stateCheckInterval.toInteger()) {
    if (['2' ,'5', '10', '15', '30'].contains(stateCheckInterval) ) {
      schedule("0 */${stateCheckInterval} * ? * *", checkState)
    } else {
      schedule("0 0 */${stateCheckInterval} ? * *", checkState)
    }
  }
}

void checkState(String deviceGuid=null) {
  logger("debug", "checkState(${deviceGuid})")
  getDevicesStatus(deviceGuid)

  if (deviceGuid) {
    Map ds = state.deviceState[deviceGuid]
    if (ds) {
      Map dd = state.deviceDetail[deviceGuid]
      ChildDeviceWrapper device = getChildDevice("${dd?.groupId}-${deviceGuid}")
      if (cd) {
        logger("debug", "checkState() - Device: ${deviceGuid}")
        logger("trace", "checkState() - Device States: ${ds}")
        device.setStates(ds)
      }
    }
  } else {
    List<ChildDeviceWrapper> groups = getChildDevices()
    groups.each { group ->
      List<ChildDeviceWrapper> devices = group.getChildDevices()
      devices.each { device ->
        def deviceId = device.currentState("id")?.value
        if (logDescText) {
          log.info "${app.name} checkState() - Group: ${group} / Device: ${device} (${deviceId})"
        } else {
          logger("debug", "checkState() - Group: ${group} / Device: ${device} (${deviceId})")
        }

        if (deviceId) {
          Map ds = state.deviceState[deviceId]
          if (ds) {
            logger("trace", "checkState() - Device States: ${ds}")
            device.setStates(ds)
          } else {
            logger("warn", "checkState() - Device not found: ${device} (${deviceId})")
          }
        }
      }
    }
  }
}

void getDevicesStatus(String deviceGuid=null) {
  logger("debug", "getDevicesStatus(${deviceGuid})")

  if (!deviceGuid) {
    state.deviceState = [:].withDefault { [:].withDefault { [:] } }
  }
  if (!state?.deviceState) {
    state.deviceState = [:].withDefault { [:].withDefault { [:] } }
  }

  if (deviceGuid) {
    Map ds = deviceStatus(deviceGuid)
    if (ds) {
      state.deviceState[deviceGuid] = ds
    } else { logger("warn", "getDevicesStatus(${deviceGuid}) - Is empty") }
  } else {
    settings.devices.each { devicesId ->
      Map ds = deviceStatus(devicesId)
      if (ds) {
        state.deviceState[devicesId] = ds
      } else { logger("warn", "getDevicesStatus(${devicesId}) - Is empty") }
    }
  }
}

boolean getToken() {
  logger("debug", "getToken()")
  if (state.authToken) { return true }

  Map authParams = [
    language: 0,
    loginId: user,
    password: password
  ]
  Map params = [
    uri: getApiUrl(),
    path: '/auth/login',
    headers: ['X-APP-TYPE':'1','X-APP-VERSION':'1.19.0','User-Agent':'G-RAC','Connection':'Keep-Alive'],
    requestContentType: 'application/json; charset=utf-8',
    contentType: 'application/json; charset=utf-8',
    body: authParams,
  ]

  try {
    boolean rc = false
    logger("trace", "getToken() - URL: ${getApiUrl()}/auth/login, PARAMS: ${params.inspect()}")
    logger("trace", "getToken() - Request: ${getApiUrl()}/auth/login")

    httpPost(params) { resp ->
      logger("trace", "getToken() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

      if (resp && resp.getStatus() == 200) {
        def data = resp?.getData()
        state.extUsrId = data.extUsrId
        state.clientId = data.clientId
        state.authToken = data.uToken
        logger("info", "Get token success")
        rc = true
      } else {
        logger("error", "Get token failed")
        rc = false
      }
    }
    return rc
  } catch (Exception e) {
    logger("error", "getToken() - Request Exception: ${e.inspect()}")
    state.authToken = null
    return false
  }

  // We didn't get an access token
  if (!state.authToken) {
    state.authToken = null
    return false
  }
}

Map getDeviceList() {
  logger("debug", "getDeviceList()")
  Map deviceList = [:]
  state.groupDetail = [:]
  state.deviceDetail = [:]

  apiGet("/device/group",[:]) { resp ->
    logger("trace", "getDeviceList() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

    if (resp) {
      resp.data.groupList.each { group ->
        String keyGroup = group.groupId
        state.groupDetail[keyGroup] = ["id" : group.groupId]
        state.groupDetail[keyGroup] << ["name" : group.groupName]
        state.groupDetail[keyGroup] << ["groupId" : group.groupId]
        state.groupDetail[keyGroup] << ["groupName" : group.groupName]

        group.deviceList.each { device ->
          String keyDevice = device.deviceGuid
          deviceList[keyDevice] = "${device.deviceName} (${group.groupName})"
          state.deviceDetail[keyDevice] = ["id" : device.deviceGuid]
          state.deviceDetail[keyDevice] << ["name" : device.deviceName]
          state.deviceDetail[keyDevice] << ["groupId" : group.groupId]
          state.deviceDetail[keyDevice] << ["groupName" : group.groupName]
          device.each { k, v ->
            if (k != "parameters") {
              state.deviceDetail[keyDevice] << ["${k}" : v]
            }
          }
        }
      }
    }
  }

  logger("debug", "getDeviceList() - deviceList: ${deviceList}")
  return deviceList
}

Map deviceStatus(String deviceGUID) {
  logger("debug", "deviceStatus(${deviceGUID?.inspect()})")
  apiGet("/deviceStatus/${deviceGUID}",[:]) { resp ->
    logger("trace", "deviceStatus() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp?.getStatus() == 200) {
      return resp?.getData()
    } else {
      return [:]
    }
  }
}

Map deviceStatusCache(String deviceGUID) {
  logger("debug", "deviceStatusCache(${deviceGUID?.inspect()})")
  apiGet("/deviceStatus/now/${deviceGUID}",[:]) { resp ->
    logger("trace", "deviceStatusCache() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp.getStatus() == 200) {
      return resp.getData()
    } else {
      return [:]
    }
  }
}

def deviceControl(Map operation) {
  logger("debug", "deviceControl(${operation?.inspect()})")
  try {
    Map params = [
      uri: getApiUrl(),
      path: '/deviceStatus/control',
      headers: ['X-APP-TYPE':'1','X-APP-VERSION':'1.19.0','User-Agent':'G-RAC','Connection':'Keep-Alive','Accept':'application/json; charset=utf-8','Content-Type':'application/json; charset=utf-8','X-User-Authorization': state.authToken],
      requestContentType: 'application/json; charset=utf-8',
      contentType: 'application/json; charset=utf-8',
      body: operation,
      timeout: 15
    ]

    logger("trace", "deviceControl() - PARAMS: ${params.inspect()}")
    httpPostJson(params) { resp ->
      logger("trace", "deviceControl() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      logger("debug", "deviceControl() - respStatus: ${resp?.getStatus()}, respData: ${resp?.getData()}")
    }
  } catch (Exception e) {
    logger("error", "deviceControl() - Request Exception: ${e.inspect()}")
  }
}

private ChildDeviceWrapper createGroup(Map detail) {
  try {
    def hub = location.hubs[0]
    ChildDeviceWrapper cd = getChildDevice("${detail?.id}")
    if(!cd) {
      logger("debug", "createGroup() - Creating Group (${detail.inspect()}")
      ChildDeviceWrapper cdh = addChildDevice("syepes", "Panasonic - Comfort Cloud - Group", "${detail?.id}", hub.id, [name: "${detail?.name}", label: "${detail?.name}", isComponent: false])
      cdh.setDetails(detail)
      return cdh
    } else {
      logger("debug", "createGroup() - Group: ${detail?.name} (${detail?.id}) already exists")
      cd.setDetails(detail)
      return cd
    }
  } catch (e) {
    logger("error", "createGroup() - Group creation Exception: ${e.inspect()}")
    return null
  }
}

private removeChildDevices(delete) {
  logger("debug", "removeChildDevices() - Removing ${delete.size()} devices")
  delete.each { deleteChildDevice(it.deviceNetworkId) }
}

private apiGet(String path, Closure callback) {
  apiGet(path, [:], callback);
}

private apiGet(String path, Map query, Closure callback) {
  logger("debug", "apiGet()")

  if (!state.authToken) {
    getToken()
  }

  if (!state.authToken) {
    try{
      logger("debug", "apiGet() - Creating access token")
      getToken()
    } catch(Exception e){
      logger("error", "apiGet() - ${e.message}")
      return "${e.message}"
    }
  }

  Map params = [
    uri: getApiUrl(),
    path: path,
    headers: ['X-APP-TYPE':'1','X-APP-VERSION':'1.19.0','User-Agent':'G-RAC','Connection':'Keep-Alive','Accept':'application/json; charset=utf-8','Content-Type':'application/json; charset=utf-8','X-User-Authorization': state.authToken],
    requestContentType: 'application/json; charset=utf-8',
    contentType: 'application/json; charset=utf-8',
    timeout: 30
  ]

  try {
    logger("trace", "apiGet() - URL: ${getApiUrl() + path}, PARAMS: ${params.inspect()}")
    logger("trace", "apiGet() - Request: ${getApiUrl() + path}")
    httpGet(params) { resp ->
      logger("trace", "apiGet() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      callback.call(resp)
    }
  } catch (Exception e) {
    logger("error", "apiGet() - Request Exception: ${e.inspect()}")
    if(getToken()) {
      logger("warn", "apiGet() - Trying request again after refreshing token")
      httpGet(params) { resp ->
        callback.call(resp)
      }
    } else {
      logger("warn", "apiGet() - Token refresh failed")
    }
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
      log."${level}" "${app.name} ${msg}"
    }
  }
}
