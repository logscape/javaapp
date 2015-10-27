
import groovy.transform.Synchronized
import java.util.*
import java.util.concurrent.*



pout = pout == null ? System.out : pout
perr = perr == null ? System.err : perr


def stdOut = pout
def stdErr = perr
myPout = pout
myPerr = perr

def propertyMissing(String name) {}

@Synchronized
def log( line, port ){
        dt = new Date()
        timestamp = String.format('%1$te-%1$tb-%1$ty %tT',dt)
        myPout <<  "" + timestamp + " "  + "port:" + port + " " + line  + "\n"
}


@Synchronized
def errorLog( line, port ){
        dt = new Date()
        timestamp = String.format('%1$te-%1$tb-%1$ty %tT',dt)
        myPerr <<  "" + timestamp + " "  + "port:" + port + " " + line  + "\n"
}


def ranges(jmxPortList){
    
    range = [] 

    jmxPortList.split(",").each{ elem  -> 
        range.add (  elem.split("-").collect { it -> it.toInteger()  }  ) 

    }
    return range 
}


def inRange(port,range) {
    def portRanges  = ranges(range)

    portRanges.each {    r -> 
        if  (port >  r[0]  && port < r[1] )
        {
           
            
            return 1 
        }

    }

    return 0

}

def labelPort(mappings,port){
    def portLabels = mappings.keySet()
    retLabel = None
    portLabels.each { label ->


    def portRanges  = ranges(mappings[label])

    portRanges.each {    r -> 
		if  (port >  r[0]  && port < r[1] )
		{
		    retLabel = label
		}

	    }

    }

    return retLabel

}

def isUnix() {

	if (System.properties['os.name'].toLowerCase().contains('indows')){
		return false
	}
	
	return true

}

def isWin() {

	if (System.properties['os.name'].toLowerCase().contains('indows')){
		return true 
	}
	
	return false 

}
def mapPort(mappings) { 
	def connections = null
	portLabels = null
	if ( isWin() ){
		portLabels =  winMapPort(mappings)
	}else{
		portLabels =  unixMapPort(mappings)
	}
	
	return portLabels

} 

def winMapPort(mappings){

    def portRange = ""
    def portLabels = [:]    

	def cmd = [
	'netstat',
	' -ano',
	'''   '''.stripMargin() ]
	println cmd
	cmd.execute().text.split("\n").each { line ->
		elems = line.split()
		if (elems.length == 5){
			
			address = elems[1]
			port = address.split(":")[1]
			pid = elems[4]

		}else{
			port=-1
			pid="-1/-1"
		}
		

        try{ 
			
			
				port = port.toInteger()
				pid =  pid.split("/")[0].toInteger()
				
			if ( port >  0){				
				label = labelPort(mappings,port) 
			}
		 
		if (label!=null)
		{ 
			if ( !portLabels.containsKey(pid)){
				portLabels[pid] = [:]
			}
			portLabels[pid][label]=port 
		}

        }catch(ClassCastException e){    
            	perr <<  "Skipping ..." +  port  + "," + pid  << "\n"
        }catch(NumberFormatException e){
            	// swallow this exception
        }catch(Exception e){

            	errorLog ( e ,port)  
				e.printStackTrace(myPerr)
		
        }
    

    }

    return portLabels
}

def unixMapPort(mappings){
    
    def portRange = ""
    def portLabels = [:]    

    def cmd = [
    'bash',
    '-c',
    ''' netstat -lap | grep java | grep 'LISTEN ' | awk '{print $4,$7}' | sed -e 's/\\[\\:\\:\\]\\://' | sort '''.stripMargin() ]


    def connections = [:] 

    cmd.execute().text.split("\n").each{ line -> 
        (port,pid) = line.split()

        try{ 
            	port = port.toInteger()
           	pid =  pid.split("/")[0].toInteger()
	    	label = labelPort(mappings,port) 

		 
		if (label!=null)
		{ 
			if ( !portLabels.containsKey(pid)){
				portLabels[pid] = [:]
			}
			portLabels[pid][label]=port 
		}

        }catch(ClassCastException e){    
            	perr <<  "Skipping ..." +  port  + "," + pid  << "\n"
        }catch(NumberFormatException e){
            	// swallow this exception
        }catch(Exception e){

            	errorLog ( e )  
        }
    

    }

    return portLabels
}

/*
portranges 
    jmx: 9010-9050
    profiling:10000-11000    

    for port in portrange
        pid
        port
        label = getLabel (port,portLabels)
        application [pid]["port"][ label ] = port
    
    
    return application
    
    application { 
        12076: { jmx:9020  profile:101000 }
        11345: { jmx:9010  profile:102000 }
    }
*/

@Synchronized
def profileStart(port) {
	println "Starting profiling " + port

	if (isUnix()) {
		command =["bash","-c","./start.sh localhost "+ port +"  " ]
	}else { 
		command =["cmd",""," start.bat localhost "+ port +"  " ]
	}
	command.execute() 
}

@Synchronized
def profileOutFile(port) {

	def outFile
	
	if (isUnix()) { 
		outFile = "/tmp/profile." + port + ".out" 
		command = [ "bash", "-c", "./file.sh localhost "+port+" " + outFile + "  "]
	}else{
		outFile = "c:\\temp\\profile." + port + ".out" 
		command = [ "cmd", "", " file.bat localhost "+port+" " + outFile + "  "]
	}
	//println " using file: /tmp/profile." + port + ".out"	
	println " using file:" + outFile
	println command
	command.execute() 
}

@Synchronized
def profileStop(port) {
	println "Stopping profiling" + port 
	if (isUnix()){ 
		command=["bash","-c","./finish.sh localhost "+port+"  "]
	}else {
		command=["cmd"," ","finish.bat localhost "+port+"  "]
	}
	command.execute() 

}

@Synchronized
def notContains(s,terms)
{
	terms.each{ term -> 
		if (s.contains(term)){
			return false
		}
	}
	return true
}
@Synchronized
def profileDataOut(portMapping)
{	
	def jmxPort = portMapping["jmx"]
	def jipPort = portMapping["profiling"]
	def folder
	def fName = "profile." + jipPort + ".out.txt"
	
	if (isUnix()){
		folder =  "/tmp"
		f = folder + "/" + fName
	}else{
		folder = "c:\\temp"
		f = folder + "\\" + fName
	}
	
	new File( f ).eachLine { line -> 
		if (line.contains(":")  && notContains(line,["File","Date","Frame"]) )
		{	 
		   log(line,jmxPort)
		}

	}


}

def pause(secs)
{
	Thread.getCurrentThread.sleep(secs*1000)
}

def out(pid,portMapping)
{
	if (portMapping[pid]["profiling"]) {
		def port = portMapping[pid]["profiling"]
		profileStart( port )
		// sleep 30s
		Thread.sleep(10 * 1000)
		//pause(10)
		profileOutFile(port) 
		profileStop (port) 
//				pause(5)
		this.sleep( 10 * 1000 ) 
//		pause(5)
		profileDataOut(portMapping[pid])
	}

}


def outWin(pid)
{

	def port = portMapping[pid]["profiling"]
	profileStart( port )
	// sleep 30s
	Thread.sleep(10 * 1000)
	//pause(10)
	profileOutFile(port) 
	profileStop (port) 
//				pause(5)
	this.sleep( 10 * 1000 ) 
//		pause(5)
	profileDataOut(portMapping[pid])

}


def arguments(defaults){
	def parameters = defaults
	args.each{ argument -> 
		if ( argument.contains("=") )
		{
			(key,value)= argument.split("=")
			parameters[key] = value.replaceAll('"','') 
		}
	}
	return parameters 
}

parameters = arguments( ["jmxPortRanges":"8980-9790", "threadFilter":"RUNNABLE,BLOCKABLE","jipPortRanges":"20000-21100"] ) 
jmxPortRanges = jmxPortRanges == null ? parameters["jmxPortRanges"]  : jmxPortRanges
jipPortRanges = jipPortRanges == null ? parameters["jipPortRanges"] : jipPortRanges 
/*
portMapping =   mapPort( [ 
        "jmx":"9000-9090"
        ,"profiling":"9100-11000"
    ] 
)
*/

portMapping = mapPort ( [
		"jmx":jmxPortRanges
		,"profiling":jipPortRanges]
)

println portMapping  



println "Creatintg pool"
def pool =  Executors.newFixedThreadPool(5)

portMapping.each { pid,ports -> 
	
	jmxport = portMapping[pid]["jmx"]

	pool.submit( { 
		try { 
			println "Running:" + Thread.currentThread().getName()
			log("\n[thread] start ",0)
			out(pid,portMapping)
		}
		catch(Exception e){
			e.printStackTrace();
			perr << "Error! Skipping [ pid:" + pid +"port:" + port +"  ]"
			perr << e
			
		}
	} as Runnable )
	
	
}



pool.shutdown() 
pool.awaitTermination(60L,TimeUnit.SECONDS)


return None 
