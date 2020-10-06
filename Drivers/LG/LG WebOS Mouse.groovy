/**
 *  Copyright (C) Sebastian YEPES
 *  Original Authors: Sam Lalor, Andrew Stanley-Jones
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

@Field String VERSION = "1.0.0"

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]
@Field List = ["HOME", "BACK", "UP", "DOWN", "LEFT", "RIGHT", "RED", "BLUE", "YELLOW", "GREEN", ]
@Field queue

metadata {
  definition(name: "LG WebOS Mouse", namespace: "syepes", author: "Sebastian YEPES", importUrl: "https://raw.githubusercontent.com/syepes/Hubitat/master/Drivers/LG/LG%20WebOS%20Mouse.groovy") {
    command "setMouseURI", ["string"]
    command "getURI"
    command "close"

    command "click"
    command "sendButton", ["string"]
    command "move", ["number", "number"]
    command "moveAbsolute", ["number", "number"]
    command "scroll", ["number", "number"]
    command "ok"
    command "home"
    command "left"
    command "right"
    command "red"
    command "blue"
    command "yellow"
    command "green"
  }
  preferences {
    section { // General
      input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
    }
  }
}


def updated() {
  logger("debug", "updated()")
}

def getURI() {
  logger("debug", "getURI()")
  if ((now() - (state?.refreshURIAt ?: 15001)) > 15000) {
    parent.getMouseURI()
    state.refreshURIAt = now()
  }
}

def close() {
  logger("debug", "close()")
  state.socketStatus = "closing"
  interfaces.webSocket.close()
  // Clear refreshURIAt to allow getURI to work for testing
  state.refreshURIAt = 0
}

def setMouseURI(String uri) {
  logger("debug", "setMouseURI() - uri: ${uri}")
  if ((uri != state.uri) || (state.socketStatus == "closing")) {
    reconnect(uri)
  }
}

def sendMessage(String msg) {
  logger("debug", "sendMessage() - msg: ${msg}")
  if (state.socketStatus == "open") {
    interfaces.webSocket.sendMessage(msg)
  } else {
    reconnect()
    if (!state.queue) {
      state.queue = []
    }
    state.queue += [ [now(), msg] ]
    logger("debug", "sendMessage() - Queue length: ${state.queue.size()}")
  }
}

def click() {
  logger("debug", "click()")
  sendMessage("type:click\n\n")
}

def sendButton(String name) {
  logger("debug", "sendButton() - name: ${name}")
  sendMessage("type:button\nname:${name}\n\n")
}

def move(x, y) {
  logger("debug", "move(x:${x},y:${y})")
  sendMessage("type:move\ndx:${x}\ndy:${y}\ndrag: 0\n\n")
}

def moveAbsolute(x, y) {
  logger("debug", "moveAbsolute(x:${x},y:${y})")

  // Go to 0,0
  for (int i = 0; i<80; i++) {
    move(-10, -10)
  }

  int dx = x / 10
  int dy = y / 10
  int max = dx > dy ? dx : dy
  for (int i = 0; i<max; i++) {
    move(dx > 0 ? 10 : 0, dy > 0 ? 10 : 0)
    dx = dx > 0 ? dx - 1 : 0
    dy = dy > 0 ? dy - 1 : 0
  }
  move(dx % 10, dy % 10)
}

def scroll(dx, dy) {
  logger("debug", "scroll(dx:${dx},dy:${dy})")
  sendMessage("type:scroll\ndx:${dx}\ndy:${dy}\n\n")
}

def parse(status) {
  logger("debug", "parse(${status})")
}

def reconnect(new_uri = null) {
  logger("debug", "reconnect() - new uri: ${new_uri}, state: ${state.socketStatus}")
  if (!new_uri && (state.socketStatus != "closing")) return
  if (new_uri) state.uri = new_uri

  close()
  try {
    logger("debug", "reconnect() - Pointer Connecting to: ${state.uri}")
    interfaces.webSocket.connect(state.uri)
    state.socketStatus = "opening"
  } catch (e) {
    logger("warn", "reconnect() - Failed to open mouse socket: ${e}")
  }
}

def flushQueue() {
  logger("debug", "flushQueue()")

  if (state.socketStatus == "open" && state.queue) {
    logger("debug", "flushQueue() - Queue length: ${state.queue.size()}")
    def curQueue = state.queue
    state.queue = []
    def flushed = 0
    def skipped = 0
    curQueue.each { it ->
      def queuedAt = it[0]
      def msg = it[1]
      logger("trace", "flushQueue() - Looking at: ${it} age: ${now() - queuedAt}")
      if ((now() - queuedAt) < 15000) {
        logger("trace", "flushQueue() - Sending Queued: ${msg}")
        sendMessage(msg)
        flushed++
      } else {
        skipped++
      }
    }
    logger("debug", "flushQueue() - sent ${flushed} skipped (too old): ${skipped}")
  }
}

def webSocketStatus(String status) {
  logger("debug", "webSocketStatus() - status: ${status}")
  if (status.startsWith("status:")) {
    state.socketStatus = status.replace("status: ", "")
  } else if (status.startsWith("failure:")) {
    state.socketStatus = "closing"
  }
  logger("debug", "webSocketStatus() - New status: ${state.socketStatus}")

  if (state.socketStatus == "open" && state.queue) {
    runInMillis(250, flushQueue)
  }
  if (state.socketStatus == "closing") {
    getURI()
  }
}

def ok() {
  logger("debug", "ok()")
  click()
}

def home() {
  logger("debug", "home()")
  sendButton("HOME")
}

def left() {
  logger("debug", "left()")
  sendButton("LEFT")
}

def right() {
  logger("debug", "right()")
  sendButton("RIGHT")
}

def red() {
  logger("debug", "red()")
  sendButton("RED")
}

def blue() {
  logger("debug", "blue()")
  sendButton("BLUE")
}

def yellow() {
  logger("debug", "yellow()")
  sendButton("YELLOW")
}

def green() {
  logger("debug", "green()")
  sendButton("GREEN")
}

/**
 * @param level Level to log at, see LOG_LEVELS for options
 * @param msg Message to log
 */
private logger(level, msg) {
  if (level && msg) {
    Integer levelIdx = LOG_LEVELS.indexOf(level)
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
    if (setLevelIdx<0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx<= setLevelIdx) {
      log."${level}" "${device.displayName} ${msg}"
    }
  }
}