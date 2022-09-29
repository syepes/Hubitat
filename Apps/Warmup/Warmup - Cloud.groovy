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

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[1]

private getApiUrl() { "https://api.warmup.com" }

definition(
  name: "Warmup - Cloud",
  namespace: "syepes",
  author: "Sebastian YEPES",
  description: "Warmup - Cloud",
  category: "",
  oauth: false,
  singleInstance: true,
  iconUrl: "https://play-lh.googleusercontent.com/e5FtKP1UOhN-yaZ9HXDObGKxCnyeTLEkqTs9ViwLsZZaTZZhrQtn9jGpLZBW3-0H22E0=w200-h200",
  iconX2Url: "https://play-lh.googleusercontent.com/e5FtKP1UOhN-yaZ9HXDObGKxCnyeTLEkqTs9ViwLsZZaTZZhrQtn9jGpLZBW3-0H22E0=w400-h400"
)

preferences {
  page(name: "credentials", title: "Credentials")
  page(name: "login", title: "Login")
  page(name: "config", title: "Config")
}

/* Preferences */
Map credentials() {
  logger("debug", "credentials()")

  return dynamicPage(name: "credentials", title: "Login Credentials", nextPage:"login", install: false, uninstall: false) {
    section("Warmup - Cloud") {
      input name: "user", title: "User", type: "text", defaultValue: "", required: true
      input name: "password", title: "Password", type: "password", defaultValue: "", required: true
    }
  }
}

Map login() {
  logger("debug", "login()")

  if (getToken()) {
    logger("debug", "login() - Successful")
    return dynamicPage(name: "login", title: "Warmup - Cloud", nextPage: "config", install: false, uninstall: false) {
      section() {
        paragraph "Successfully connected to Warmup - Cloud. Click Next"
      }
    }
  } else {
    logger("debug", "login() - Failed")
    return dynamicPage(name: "login", title: "Warmup - Cloud", nextPage: "credentials", install: false, uninstall: false) {
      section() {
        paragraph "Unable to connect to Warmup - Cloud, double check your credentials. Click Next"
      }
    }
  }
}

Map config() {
  logger("debug", "config()")

  Map rooms = [:]
  Map locations = getLocationList()
  locations?.each { k, v ->
    rooms << getRoomList(k)
  }
  logger("debug", "config() - locations: ${locations}")
  logger("debug", "config() - rooms: ${rooms}")

  return dynamicPage(name: "config", title: "Config", install: true, uninstall: true) {
    section("Devices") {
      input name: "rooms", title: "Select Device(s) to track / Room (Location)", type: "enum", required: false, multiple: true, options: rooms
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

  settings.rooms.each { roomId ->
    Map rd = state?.roomDetail[roomId]
    String locId = rd?.locId
    if(rd && locId) {
      Map ld = state?.locationDetail[locId]
      logger("info", "Creating Location: ${ld?.name}")
      ChildDeviceWrapper location = createLocation(ld)
      if (location) {
        logger("info", "Creating Room: ${rd.roomName}")
        ChildDeviceWrapper room = location.addRoom(rd)
        if (room) {
          room.setStates(rd)
        }
      }
    }
  }

  // Cleanup any other devices that need to go away
  List allInstalled = [settings.rooms].findAll().flatten()
  if (allInstalled.size()>0) {
    List groups = getChildDevices().collect{ it.getChildDevices().collect{ it.deviceNetworkId?.split('-')?.getAt(0) } }.flatten()
    List device = getChildDevices().collect{ it.getChildDevices().collect{ it.deviceNetworkId?.split('-')?.getAt(1) } }.flatten()
    List delete = device.findAll{ !allInstalled.contains(it) }

    getChildDevices()?.each {
      if (groups.contains(it?.deviceNetworkId)) {
        delete.each { roomID ->
          logger("info", "Removing Device: ${it.deviceNetworkId}-${roomID}")
          it.deleteChildDevice(it.deviceNetworkId +"-"+ roomID)
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

void checkState() {
  logger("debug", "checkState()")
  getDevicesStatus()

  List<ChildDeviceWrapper> locations = getChildDevices()
  locations.each { location ->
    Map ld = state.locationDetail[location?.deviceNetworkId]
    if (ld) {
      location.setDetails(ld)
    }
    List<ChildDeviceWrapper> rooms = location.getChildDevices()
    rooms.each { room ->
      String roomId = room.currentState("id")?.value
      if (logDescText) {
        log.info "${app.name} checkState() - Location: ${location} / Room: ${room} (${roomId})"
      } else {
        logger("debug", "checkState() - Location: ${location} / Room: ${room} (${roomId})")
      }
      if (roomId) {
        Map rd = state.roomDetail[roomId]
        if (rd) {
          logger("trace", "checkState() - Room States: ${rd}")
          room.setStates(rd)
        } else {
          logger("warn", "checkState() - Device not found: ${room} (${roomId})")
        }
      }
    }
  }
}

private void getDevicesStatus() {
  logger("debug", "getDevicesStatus()")
  Map locations = getLocationList()
  locations?.each { k, v ->
    getRoomList(k)
  }
}

private boolean getToken() {
  logger("debug", "getToken()")
  if (state.authToken) { return true }

  Map authParams = [
    request: [
      appId: 'WARMUP-APP-V001',
      method: 'userLogin',
      email: user,
      password: password
    ]
  ]

  Map params = [
    uri: getApiUrl(),
    path: '/apps/app/v1',
    headers: ['APP-Token':'M=;He<Xtg"$}4N%5k{$:PD+WA"]D<;#PriteY|VTuA>_iyhs+vA"4lic{6-LqNM:','User-Agent':'WARMUP_APP'],
    requestContentType: 'application/json; charset=utf-8',
    contentType: 'application/json; charset=utf-8',
    body: authParams,
  ]

  try {
    boolean rc = false
    logger("trace", "getToken() - URL: ${getApiUrl()}/apps/app/v1, PARAMS: ${params.inspect()}")
    logger("trace", "getToken() - Request: ${getApiUrl()}/apps/app/v1")

    httpPost(params) { resp ->
      logger("trace", "getToken() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")

      if (resp && resp.getStatus() == 200 && resp?.getData()?.status?.result == "success") {
        def data = resp?.getData()
        state.authToken = data.response.token
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

private Map getLocationList() {
  logger("debug", "getLocationList()")
  Map locationList = [:]
  state.locationDetail = [:]

  Map body = [request: [method: "getLocations"]]

  apiPost("/apps/app/v1",body) { resp ->
    logger("trace", "getLocationList() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp.getStatus() == 200 && resp?.getData()?.status?.result == "success") {
      resp.data.response?.locations?.each { location ->
        String keyDevice = location.id
        locationList[keyDevice] = location.name
        state.locationDetail[keyDevice] = ["id" : location.id]
        state.locationDetail[keyDevice] << ["name" : location.name]
        location.each { k, v ->
          state.locationDetail[keyDevice] << ["${k}" : v]
        }
      }
    } else {
      logger("warn", "getLocationList() - Failed retrieving Locations")
    }
  }
  logger("trace", "getLocationList() - ${locationList}")
  return locationList
}

private Map getRoomList(String locationID) {
  logger("debug", "getRoomList(${locationID})")
  Map roomList = [:]
  state.roomDetail = [:]

  Map body = [request: [method: "getRooms",
                        locId: locationID]
              ]

  apiPost("/apps/app/v1",body) { resp ->
    logger("trace", "getRoomList(${locationID}) - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
    if (resp && resp.getStatus() == 200 && resp?.getData()?.status?.result == "success") {
      String locId = resp.data.response.locId

      resp.data.response?.rooms?.each { room ->
        String keyDevice = room.roomId
        roomList[keyDevice] = room.roomName
        state.roomDetail[keyDevice] = ["id" : room.roomId]
        state.roomDetail[keyDevice] << ["name" : room.roomName]
        state.roomDetail[keyDevice] << ["locId" : resp.data.response.locId]
        state.roomDetail[keyDevice] << ["locMode" : resp.data.response.locMode]

        if (state.locationDetail?.containsKey(locId)) {
          String locName = state.locationDetail[locId].find{ it.key == "name"}?.value
          state.roomDetail[keyDevice] << ["locName" : locName]
          roomList[keyDevice] = "${room.roomName} (${locName})"
        }
        room.each { k, v ->
          if (k ==~ /.+Temp/) {
            state.roomDetail[keyDevice] << ["${k}" : String.format("%2.1f",(v as BigDecimal) / 10)]
          } else {
            state.roomDetail[keyDevice] << ["${k}" : v]
          }
        }
      }

      // Extract additional data
      resp.data.message?.getRooms?.result?.data?.user?.location?.rooms?.each { room_usr ->
        keyDevice = room_usr?.id
        if (keyDevice && state.roomDetail?.containsKey(keyDevice)) {
          String roomType = room_usr?.roomType
          if (roomType) {
            state.roomDetail[keyDevice]["roomType"] = roomType
          }
          String floorType = room_usr?.floorType
          if (floorType) {
            state.roomDetail[keyDevice]["floorType"] = floorType
          }
        }
      }
    } else {
      logger("warn", "getRoomList(${locationID}) - Failed retrieving Rooms")
    }
  }
  logger("trace", "getRoomList(${locationID}) - ${roomList}")
  return roomList
}

private ChildDeviceWrapper createLocation(Map detail) {
  try {
    def hub = location.hubs[0]
    ChildDeviceWrapper cd = getChildDevice("${detail?.id}")
    if(!cd) {
      logger("debug", "createLocation() - Creating Location (${detail.inspect()}")
      ChildDeviceWrapper cdh = addChildDevice("syepes", "Warmup - Cloud - Location", "${detail?.id}", hub.id, [name: "${detail?.name}", label: "${detail?.name}", isComponent: false])
      cdh.setDetails(detail)
      return cdh
    } else {
      logger("debug", "createLocation() - Location: ${detail?.name} (${detail?.id}) already exists")
      cd.setDetails(detail)
      return cd
    }
  } catch (e) {
    logger("error", "createLocation() - Location creation Exception: ${e.inspect()}")
    return null
  }
}

private removeChildDevices(delete) {
  logger("debug", "removeChildDevices() - Removing ${delete.size()} devices")
  delete.each { deleteChildDevice(it.deviceNetworkId) }
}

def apiPost(Map query, Closure callback) {
  apiPost('/apps/app/v1', query, callback);
}

def apiPost(String path, Closure callback) {
  apiPost(path, [:], callback);
}

def apiPost(String path, Map query, Closure callback) {
  logger("debug", "apiPost()")

  if (!state.authToken) {
    getToken()
  }

  if (!state.authToken) {
    try{
      logger("debug", "apiPost() - Creating access token")
      getToken()
    } catch(Exception e){
      logger("error", "apiPost() - ${e.message}")
      return "${e.message}"
    }
  }

  if (query) {
    query['account'] = [email: "${user}",
                        token: "${state.authToken}"]
  }

  Map params = [
    uri: getApiUrl(),
    path: path,
    headers: ['APP-Token':'M=;He<Xtg"$}4N%5k{$:PD+WA"]D<;#PriteY|VTuA>_iyhs+vA"4lic{6-LqNM:','User-Agent':'WARMUP_APP'],
    requestContentType: 'application/json; charset=utf-8',
    contentType: 'application/json; charset=utf-8',
    timeout: 30,
    body: query,
  ]

  try {
    logger("trace", "apiPost() - URL: ${getApiUrl() + path}, PARAMS: ${params.inspect()}")
    logger("trace", "apiPost() - Request: ${getApiUrl() + path}")
    httpPostJson(params) { resp ->
      logger("trace", "apiPost() - respStatus: ${resp?.getStatus()}, respHeaders: ${resp?.getAllHeaders()?.inspect()}, respData: ${resp?.getData()}")
      callback.call(resp)
    }
  } catch (Exception e) {
    logger("debug", "apiPost() - Request Exception: ${e.inspect()}")
    if(getToken()) {
      logger("warn", "apiPost() - Trying request again after refreshing token")
      httpPost(params) { resp ->
        callback.call(resp)
      }
    } else {
      logger("warn", "apiPost() - Token refresh failed")
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
