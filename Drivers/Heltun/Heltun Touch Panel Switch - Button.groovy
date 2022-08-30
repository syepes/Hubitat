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

@Field String VERSION = "1.0.1"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
  definition (name: "Heltun Touch Panel Switch - Button", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/Heltun/Heltun%20Touch%20Panel%20Switch%20-%20Button.groovy") {
    capability "Actuator"
    capability "Switch"
    capability "Momentary" // push
    capability "Pushable Button" // pushed
    capability "Holdable Button" // held
    capability "Releasable Button" // released
    capability "Refresh"
    capability "Initialize"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }
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

  initialize()
}

def uninstalled() {
  logger("debug", "uninstalled()")
  unschedule()
}

def updated() {
  logger("debug", "updated()")

  if (!state.driverInfo?.ver || state.driverInfo.isEmpty() || state.driverInfo.ver != VERSION) {
    installed()
  }
  unschedule()
  initialize()
}

def initialize() {
  logger("debug", "initialize()")

  sendEvent(name: "numberOfButtons", value: 1)
  schedule("0 0 12 */7 * ?", updateCheck)
}

def parse(value) {
  logger("debug", "parse() - value: ${value?.inspect()}")
  if (value) {
    sendEvent(value)
  }
}

def refresh() {
  logger("debug", "refresh() - state: ${state.inspect()}")
  parent.componentRefresh(device)
}

def on() {
  logger("debug", "on()")
  parent.componentOn(device)
}

def off() {
  logger("debug", "off()")
  parent.componentOff(device)
}

def push() {
  def btnNumber = device?.deviceNetworkId?.split('-')[1]
  push(btnNumber, 'digital')
}

def push(btnNumber) {
  push(btnNumber, 'digital')
}

def push(btnNumber, btnType) {
  logger("debug", "push(${btnNumber},${btnType})")
  sendEvent(name: "pushed", value: btnNumber, descriptionText: "Button was pushed", type: btnType, isStateChange: true)
  parent.componentPush(device)
}

def hold() {
  def btnNumber = device?.deviceNetworkId?.split('-')[1]
  hold(btnNumber, 'digital')
}

def hold(btnNumber) {
  hold(btnNumber, 'digital')
}

def hold(btnNumber, btnType) {
  logger("debug", "hold(${btnNumber},${btnType})")
  sendEvent(name: "hold", value: btnNumber, descriptionText: "Button was hold", type: btnType, isStateChange: true)
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
