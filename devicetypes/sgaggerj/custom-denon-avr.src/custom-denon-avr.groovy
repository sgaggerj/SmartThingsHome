/**
 *  	Denon Network Receiver 
 *    	Based on Denon/Marantz receiver by Kristopher Kubicki and edited version by sbdobrescu
 *    	SmartThings driver to connect your Denon Network Receiver to SmartThings
 *		Tested with AVR-S920W
 */

preferences {
    input("destIp", "text", title: "IP", description: "The device IP")
    input("destPort", "number", title: "Port", description: "The port you wish to connect", defaultValue: 80)
}

metadata {
    definition (name: "Custom Denon AVR", namespace: "sgaggerj", 
        author: "sgaggerj") {
        capability "Actuator"
        capability "Switch" 
        capability "Polling"
        capability "Switch Level"
        capability "Music Player" 
        
        attribute "mute", "string"
        attribute "input", "string"
		attribute "inputChan", "enum"
        
        command "mute"
        command "unmute"
        command "toggleMute"
		command "MP"
		command "G"
        }
        
        simulator {
		// TODO-: define status and reply messages here
	}
    
        	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "generic", width: 6, height: 4) {
           tileAttribute("device.switch", key: "PRIMARY_CONTROL") { 	            
                attributeState "on", label: '${name}', action:"switch.off", backgroundColor: "#79b821", icon:"st.Electronics.electronics16"
            	attributeState "off", label: '${name}', action:"switch.on", backgroundColor: "#ffffff", icon:"st.Electronics.electronics16"
        	}             
            tileAttribute ("level", key: "SLIDER_CONTROL") {
           		attributeState "default", label:'Volume Level: ${name}', action:"setLevel"
            }
            tileAttribute("device.input", key: "SECONDARY_CONTROL") {
            	attributeState ("default", label:'Current Input: ${currentValue}')
        	}
        }        
        standardTile("poll", "device.poll", width: 2, height: 2, decoration: "flat") {
            state "poll", label: "", action: "polling.poll", icon: "st.secondary.refresh", backgroundColor: "#FFFFFF"
        }
        standardTile("mute", "device.mute", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "muted", action:"unmute", backgroundColor: "#ffffff", icon:"st.custom.sonos.muted", nextState:"unmuted"
            state "unmuted", action:"mute", backgroundColor: "#ffffff", icon:"st.custom.sonos.unmuted", nextState:"muted"
        }
        standardTile("MP", "device.switch", width: 2, height: 2, decoration: "flat"){
        	state "Media Player", label: 'Shield', action: "MP", backgroundColor: "#ffffff", icon:"st.Electronics.electronics6"
			}
        standardTile("G", "device.switch", width: 2, height: 2, decoration: "flat"){
        	state "G", label: 'XBox  One', action: "G", icon:"st.Electronics.electronics11"
        	}
        main "switch"
        details(["switch","input","mute","G", "MP","poll"])
    }
}


def parse(String description) {
	log.debug "Parsing '${description}'"
    
 	def map = stringToMap(description)

    
    if(!map.body || map.body == "DQo=") { return }
        log.debug "${map.body} "
	def body = new String(map.body.decodeBase64())
    
	def statusrsp = new XmlSlurper().parseText(body)
	def power = statusrsp.Power.value.text()
    if(power == "ON") { 
    	sendEvent(name: "switch", value: 'on')
    }
    if(power != "" && power != "ON") { 
    	sendEvent(name: "switch", value: 'off')
    }
    

    def muteLevel = statusrsp.Mute.value.text()
    if(muteLevel == "on") { 
    	sendEvent(name: "mute", value: 'muted')
	}
    if(muteLevel != "" && muteLevel != "on") {
	    sendEvent(name: "mute", value: 'unmuted')
    }
    
    def inputCanonical = statusrsp.InputFuncSelect.value.text()
    def inputTmp = []
    //check to see if the VideoSelectLists node is available
    if(!statusrsp.VideoSelectLists.isEmpty()){
    	log.debug "VideoSelectLists is available... parsing"
        statusrsp.VideoSelectLists.value.each {
        	log.debug "$it"
            if(it.@index != "ON" && it.@index != "OFF") {
                inputTmp.push(it.'@index')
                log.debug "Adding Input ${it.@index}"
                if(it.toString().trim() == inputCanonical) {     
                    sendEvent(name: "input", value: it.'@index')
                }
            }
        }
    }
    //if the VideoSelectLists node is not available, let's try the InputFuncList
    else if(!statusrsp.InputFuncList.isEmpty()){
    	log.debug "InputFuncList is available... parsing"
        statusrsp.InputFuncList.value.each {
            if(it != "ON" && it != "OFF") {
                inputTmp.push(it)
                //log.debug "Adding Input ${it}"
                if(it.toString().trim() == inputCanonical) {     
                    sendEvent(name: "input", value: it)
                }
            }
        }
    }
    
    sendEvent(name: "inputChan", value: inputTmp)
    
       if(statusrsp.MasterVolume.value.text()) { 
    	def int volLevel = (int) statusrsp.MasterVolume.value.toFloat() ?: -40.0
        volLevel = (volLevel + 80) * 0.9
        
   		def int curLevel = 36
        try {
        	curLevel = device.currentValue("level")
        } catch(NumberFormatException nfe) { 
        	curLevel = 36
        }
	
        if(curLevel != volLevel) {
    		sendEvent(name: "level", value: volLevel)
        }
    } 
}


def setLevel(val) {
	sendEvent(name: "mute", value: "unmuted")     
    sendEvent(name: "level", value: val)
	def int scaledVal = val * 0.9 - 80
	request("cmd0=PutMasterVolumeSet%2F$scaledVal")
}

def on() {
	sendEvent(name: "switch", value: 'on')
	request('cmd0=PutZone_OnOff%2FON')
}

def off() { 
	sendEvent(name: "switch", value: 'off')
	request('cmd0=PutZone_OnOff%2FOFF')
}

def mute() { 
	sendEvent(name: "mute", value: "muted")
	request('cmd0=PutVolumeMute%2FON')
}

def unmute() { 
	sendEvent(name: "mute", value: "unmuted")
	request('cmd0=PutVolumeMute%2FOFF')
}

def toggleMute(){
    if(device.currentValue("mute") == "muted") { unmute() }
	else { mute() }
}
def MP() {
	log.debug "Setting input to Shield"
    request("cmd0=PutZone_InputFunction%2FMPLAY")
}

def G() {
	log.debug "Setting input to XBox One"
    request("cmd0=PutZone_InputFunction%2FGAME")
}

def poll() { 
	refresh()
}

def refresh() {

    def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex" 

    def hubAction = new physicalgraph.device.HubAction(
   	 		'method': 'GET',
    		'path': "/goform/formMainZone_MainZoneXml.xml",
            'headers': [ HOST: "$destIp:$destPort" ] 
		) 
        
    hubAction
}

def request(body) { 

    def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex" 

    def hubAction = new physicalgraph.device.HubAction(
   	 		'method': 'POST',
    		'path': "/MainZone/index.put.asp",
        	'body': body,
        	'headers': [ HOST: "$destIp:$destPort" ]
		) 
              
    hubAction
}


private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}