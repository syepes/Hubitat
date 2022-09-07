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
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

private getApiUrl() { "https://app.velux-active.com" }
private getVendorTokenPath(){ "${apiUrl}/oauth2/token" }
private getClientId() { "5931426da127d981e76bdd3f" }
private getClientSecret() { "6ae2d89d15e767ae5c56b456b452d319" }

definition(
  name: "Netatmo - Velux",
  namespace: "syepes",
  author: "Sebastian YEPES",
  description: "Netatmo Velux Product Integrations",
  category: "",
  oauth: false,
  singleInstance: true,
  iconUrl: "https://pbs.twimg.com/profile_images/1214898820577935360/cY4FJMQR_200x200.jpg",
  iconX2Url: "https://pbs.twimg.com/profile_images/1214898820577935360/cY4FJMQR_400x400.jpg"
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
    section("Netatmo - Velux") {
      input name: "clientId", title: "Client ID", type: "text", defaultValue: getClientId(), required: true
      input name: "clientSecret", title: "Client Secret", type: "text", defaultValue: getClientSecret(), required: true
      input name: "user", title: "Netatmo User", type: "text", defaultValue: "", required: true
      input name: "password", title: "Netatmo Password", type: "password", defaultValue: "", required: true
    }
  }
}

Map login() {
  logger("debug", "login()")

  if (getToken()) {
    logger("debug", "login() - Successful")
    return dynamicPage(name: "login", title: "Netatmo", nextPage: "config", install: false, uninstall:false) {
      section() {
        paragraph "Successfully connected to Netatmo Velux. Click Next"
      }
    }
  } else {
    logger("debug", "login() - Failed")
    return dynamicPage(name: "login", title: "Netatmo", nextPage: "credentials", install: false, uninstall:false) {
      section() {
        paragraph "Unable to connect to Netatmo Velux, double check your credentials. Click Next"
      }
    }
  }
}

Map config() {
  logger("debug", "config()")
  Map devices = getHomeDevicesList()

  return dynamicPage(name: "config", title: "Config", install: true, uninstall:true) {\
    section("Devices") {
      input name: "devices", title: "Select Device(s) to track / Home -> Room -> Module", type: "enum", required: false, multiple: true, options: devices
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

  // Pull the latest device info into state
  getHomeStatus()

  settings.devices.each { moduleId ->
    Map md = state?.moduleDetail[moduleId]
    String homeId = md?.homeID
    Map hd = state?.homeDetail[homeId]

    if (md && hd) {
      ChildDeviceWrapper home = createHome(hd)
      if (home) {
        if (md?.type ==~ /NXG|NXB|NXD/) {
          logger("info", "Creating Home: ${hd.name} (${hd.city}) / Module: ${md.name} (${md.type})")
          home.addModule(md)
        }

        if (md?.roomID) {
          String roomId = md?.roomID
          Map rd = state?.roomDetail[roomId]
          if (rd) {
            ChildDeviceWrapper room = home.addRoom(rd)
            if (room) {
              logger("info", "Creating Home: ${hd.name} (${hd.city}) / Room: ${rd.name} (${rd.type}) / Module: ${md.name} (${md.type})")
              room.addModule(md)
            }
          }
        }
      }
    }
  }

  // Cleanup any other devices that need to go away
  List allInstalled = [settings.devices].findAll().join()
  if (allInstalled.size()>0) {
    List homes = getChildDevices().collect{ it.getChildDevices().collect{ it.getChildDevices().collect{ it.deviceNetworkId?.split('-',3)?.getAt(0) } } }?.flatten()?.unique()
    List rooms = getChildDevices().collect{ it.getChildDevices().collect{ it.getChildDevices().collect{ it.deviceNetworkId?.split('-',3)?.getAt(1) } } }?.flatten()?.unique()
    List modules = getChildDevices().collect{ it.getChildDevices().collect{
      if (it.getChildDevices()) {
        it.getChildDevices().collect{ it.deviceNetworkId?.split('-',3)?.getAt(2) }
      } else {
        it.deviceNetworkId?.split('-',2)?.getAt(1)
      }
    } }?.flatten()?.unique()
    List delete = modules.findAll{ !allInstalled.contains(it) }

    getChildDevices()?.each { hd ->
      if (homes.contains(hd?.deviceNetworkId)) {
        hd?.getChildDevices()?.each { rd ->
          String devIDH = rd?.deviceNetworkId?.split('-',2)?.getAt(1)
          if (delete.contains(devIDH)) {
            logger("info", "Removing Home Device: ${hd.deviceNetworkId}-${devIDH}")
            hd.deleteChildDevice(hd.deviceNetworkId +"-"+ devIDH)
          }

          rd?.getChildDevices()?.each { md ->
            String devIDR = md?.deviceNetworkId?.split('-',3)?.getAt(2)
            if (delete.contains(devIDR)) {
              logger("info", "Removing Room Device: ${rd.deviceNetworkId}-${devIDR}")
              rd.deleteChildDevice(rd.deviceNetworkId +"-"+ devIDR)
            }
          }
        }
      } else {
        logger("info", "Removing Home: ${hd.deviceNetworkId}")
        deleteChildDevice(hd?.deviceNetworkId)
      }
    }
  } else {
    logger("info", "Removing all devices")
    removeChildDevices(getChildDevices())
  }

  // Do the initial checkState
  checkState()

  unschedule()
  if (stateCheckInterval.toInteger()) {
    if (['2' ,'5', '10', '15', '30'].contains(stateCheckInterval) ) {
      schedule("0 */${stateCheckInterval} * ? * *", checkState)
    } else {
      schedule("0 0 */${stateCheckInterval} ? * *", checkState)
    }
  }
}

void checkState(String home_id=null) {
  logger("debug", "checkState()")

  getHomeStatus(home_id)

  List<ChildDeviceWrapper> homes = getChildDevices()
  homes?.each { home ->
    List<ChildDeviceWrapper> rooms = home.getChildDevices()
    rooms.each { room ->
      // Modules attachede directly to a Home
      Map hms = state.moduleState[home.deviceNetworkId][room.deviceNetworkId.split('-')?.getAt(1)]
      if (hms) {
        logger("debug", "checkState() - Home: ${home} / Module: ${room}")
        logger("trace", "checkState() - HomeModule States: ${hms}")
        room.setStates(hms)
      }

      Map rs = state.roomState[home.deviceNetworkId][room.deviceNetworkId.split('-')?.getAt(1)]
      if (rs) {
        logger("trace", "checkState() - Room States: ${rs}")
        room.setStates(rs)
      }
      List<ChildDeviceWrapper> modules = room.getChildDevices()
      modules.each { module ->
        if (logDescText) {
          log.info "${app.name} checkState() - Home: ${home} / Room: ${room} / Module: ${module}"
        } else {
          logger("debug", "checkState() - Home: ${home} / Room: ${room} / Module: ${module}")
        }
        Map ms = state.moduleState[home.deviceNetworkId][module.deviceNetworkId.split('-')?.getAt(2)]
        if (ms) {
          logger("trace", "checkState() - RoomModule States: ${ms}")
          module.setStates(ms)
        }
      }
    }
  }
}

boolean getToken() {
  logger("debug", "getToken()")

  if (state.authToken) { return true }

  Map oauthParams = [
    client_secret: getClientSecret(),
    client_id: getClientId(),
    grant_type: 'password',
    user_prefix: 'velux',
    username: user,
    password: password,
  ]
  Map params = [
    uri: getVendorTokenPath(),
    contentType: 'application/x-www-form-urlencoded',
    body: oauthParams,
  ]

  // OAuth Step 2: Request access token with our client Secret and OAuth "Code"
  try {
    boolean rc = false
    logger("trace", "getToken() - URL: ${getVendorTokenPath()}, PARAMS: ${params.inspect()}")
    logger("trace", "getToken() - Request: ${getVendorTokenPath()}?${toQueryString(oauthParams)}")
    httpPost(params) { resp ->
      logger("trace", "getToken() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      def slurper = new JsonSlurper()

      if (resp && resp.getStatus() == 200) {
        resp?.getData()?.each {key, value ->
          def data = slurper.parseText(key)
          state.refreshToken = data.refresh_token
          state.authToken = data.access_token
          state.tokenExpires = now() + (data.expires_in * 1000)
        }
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
    state.refreshToken = null
    return false
  }

  // We didn't get an access token
  if (!state.authToken) {
    state.authToken = null
    state.refreshToken = null
    return false
  }
}

boolean refreshToken() {
  logger("debug", "refreshToken()")

  Map oauthParams = [
    client_secret: getClientSecret(),
    client_id: getClientId(),
    grant_type: 'refresh_token',
    refresh_token: state.refreshToken
  ]
  Map params = [
    uri: getVendorTokenPath(),
    contentType: 'application/x-www-form-urlencoded',
    body: oauthParams,
  ]

  try {
    boolean rc = false
    logger("trace", "refreshToken() - URL: ${getVendorTokenPath()}, PARAMS: ${params.inspect()}")
    logger("trace", "refreshToken() - Request: ${getVendorTokenPath()}?${toQueryString(oauthParams)}")
    httpPost(params) { resp ->
      logger("trace", "refreshToken() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      def slurper = new JsonSlurper();

      if (resp && resp.getStatus() == 200) {
        resp?.getData()?.each {key, value ->
          def data = slurper.parseText(key)
          state.refreshToken = data.refresh_token
          state.authToken = data.access_token
          state.tokenExpires = now() + (data.expires_in * 1000)
        }
        logger("info", "Refresh token success")
        rc = true
      } else {
        logger("error", "Refresh token failed")
        rc = false
      }
    }
    return rc
  } catch (Exception e) {
    logger("error", "refreshToken() - Request Exception: ${e.inspect()}")
    state.authToken = null
    state.refreshToken = null
    return false
  }

  // We didn't get an access token
  if (!state.authToken) {
    state.authToken = null
    state.refreshToken = null
    return false
  }
}

Map getHomeDevicesList() {
  logger("debug", "getHomeDevicesList()")

  Map deviceList = [:]
  state.homeDetail = [:]
  state.roomDetail = [:]
  state.moduleDetail = [:]

  apiGet("/api/homesdata",[:]) { resp ->
    logger("trace", "getHomeDevicesList() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

    if (resp) {
      resp.data.body.homes.each { home ->
        String keyHome = home.id
        state.homeDetail[keyHome] = ["id" : keyHome]
        state.homeDetail[keyHome] << ["name" : home.name]
        state.homeDetail[keyHome] << ["country" : home.country]
        state.homeDetail[keyHome] << ["city" : home.city]
        state.homeDetail[keyHome] << ["nb_users" : home.nb_users]
        state.homeDetail[keyHome] << ["place_improved" : home.place_improved]
        state.homeDetail[keyHome] << ["trust_location" : home.trust_location]
        state.homeDetail[keyHome] << ["therm_absence_notification" : home.therm_absence_notification]
        state.homeDetail[keyHome] << ["therm_absence_autoaway" : home.therm_absence_autoaway]

        home.rooms.each { room ->
          String keyRoom = room.id
          state.roomDetail[keyRoom] = ["id" : keyRoom]
          state.roomDetail[keyRoom] << ["name" : room.name]
          state.roomDetail[keyRoom] << ["type" : room.type]
          state.roomDetail[keyRoom] << ["homeID" : home.id]
          state.roomDetail[keyRoom] << ["homeName" : home.name]
          state.roomDetail[keyRoom] << ["module_ids" : room.module_ids]
          state.roomDetail[keyRoom] << ["modules" : room.modules]
        }

        home.modules.each { module ->
          String keyModule = module.id
          if(module?.room_id) {
            String roomName = state.roomDetail[module?.room_id].name
            deviceList[keyModule] = "${home.name} -> ${roomName} -> ${module.name}"
          } else {
            deviceList[keyModule] = "${home.name} -> ${module.name}"
          }

          state.moduleDetail[keyModule] = ["id" : keyModule]
          state.moduleDetail[keyModule] << ["name" : module.name]
          state.moduleDetail[keyModule] << ["type" : module.type]
          state.moduleDetail[keyModule] << ["homeID" : home.id]
          state.moduleDetail[keyModule] << ["homeName" : home.name]
          state.moduleDetail[keyModule] << ["setup_date" : module.setup_date]
          if (module.type == "NXG") {
            state.moduleDetail[keyModule] << ["typeName" : "Gateway"]
            state.moduleDetail[keyModule] << ["reachable" : module.reachable]
            state.moduleDetail[keyModule] << ["modules_bridged" : module.modules_bridged]
            state.moduleDetail[keyModule] << ["pincode_enabled" : module.pincode_enabled]
          }
          if (module.type == "NXB") {
            state.moduleDetail[keyModule] << ["typeName" : "Repeater"]
            state.moduleDetail[keyModule] << ["bridge" : module.bridge]
          }
          if (module.type == "NXS") {
            state.moduleDetail[keyModule] << ["typeName" : "Sensor switch"]
            state.moduleDetail[keyModule] << ["roomID" : module.room_id]
            state.moduleDetail[keyModule] << ["bridge" : module.bridge]
          }
          if (module.type == "NXD") {
            state.moduleDetail[keyModule] << ["typeName" : "Departure switch"]
            state.moduleDetail[keyModule] << ["bridge" : module.bridge]
          }
          if (module.type == "NXO") {
            state.moduleDetail[keyModule] << ["typeName" : module.velux_type.capitalize()]
            state.moduleDetail[keyModule] << ["bridge" : module.bridge]
            state.moduleDetail[keyModule] << ["roomID" : module.room_id]
            state.moduleDetail[keyModule] << ["groupID" : module.group_id]
          }
        }
      }
    }
  }

  // Merge data
  logger("debug", "getHomeDevicesList() - deviceList: ${deviceList.sort()}")
  return deviceList.sort() { it.value.toLowerCase() }
}


void getHomeStatus(String home_id=null) {
  logger("debug", "getHomeStatus(${home_id})")

  state.roomState = [:].withDefault { [:].withDefault { [:] } }
  state.moduleState = [:].withDefault { [:].withDefault { [:] } }

  state?.homeDetail?.keySet()?.each { homeId ->
    if (home_id != null) {
      if (home_id != homeId) { return }
    }

    Map query = ["home_id": homeId]
    apiGet("/api/homestatus",query) { resp ->
      logger("trace", "getHomeStatus(${homeId}) - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

      if (resp) {
        if (resp.data.body?.errors) {
          logger("error", "getHomeStatus(${homeId}) - id: ${resp.data.body?.errors.id}, code: ${resp.data.body?.errors.code}")
          return
        }

        def home = resp.data.body.home
        if (home) {
          home.rooms.each { room ->
            String keyRoom = room.id
            room.each { k, v ->
              state.roomState[homeId][keyRoom] << ["${k}" : v]
            }
          }
          home.modules.each { module ->
            String keyModule = module.id
            module.each { k, v ->
              state.moduleState[homeId][keyModule] << ["${k}" : v]
            }
          }
        } else { logger("warn", "getHomeStatus(${homeId}) - Is empty") }
      }
    }
  }
}

private ChildDeviceWrapper createHome(Map detail) {
  try {
    def hub = location.hubs[0]
    ChildDeviceWrapper cd = getChildDevice("${detail?.id}")
    if(!cd) {
      logger("debug", "createHome() - Creating Home (${detail.inspect()}")
      ChildDeviceWrapper cdh = addChildDevice("syepes", "Netatmo - Velux - Home", "${detail?.id}", hub.id, [name: "${detail?.name}", label: "${detail?.name}", isComponent: false])
      cdh.setDetails(detail)
      return cdh
    } else {
      logger("debug", "createHome() - Home: ${detail?.name} (${detail?.id}) already exists")
      cd.setDetails(detail)
      return cd
    }
  } catch (e) {
    logger("error", "createHome() - Home creation Exception: ${e.inspect()}")
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

  if (!state.authToken || now() >= state.tokenExpires) {
    refreshToken()
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

  query['access_token'] = state.authToken
  Map params = [
    uri: getApiUrl(),
    path: path,
    'query': query,
    timeout: 45
  ]

  try {
    logger("trace", "apiGet() - URL: ${getApiUrl() + path}, PARAMS: ${query.inspect()}")
    logger("trace", "apiGet() - Request: ${getApiUrl() + path}?${toQueryString(query)}")
    httpGet(params) { resp ->
      logger("trace", "apiGet() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      callback.call(resp)
    }
  } catch (Exception e) {
    logger("debug", "apiGet() - Request Exception: ${e.inspect()}")
    if(refreshToken()) {
      logger("info", "apiGet() - Trying request again after refreshing token")
      httpGet(params) { resp ->
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
