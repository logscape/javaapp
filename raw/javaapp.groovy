import org.codehaus.groovy.runtime.StackTraceUtils
import java.text.SimpleDateFormat
import javax.management.ObjectName
import javax.management.remote.JMXConnectorFactory as JmxConnectorFactory
import javax.management.remote.JMXServiceURL as JmxServiceUrl
import javax.management.remote.JMXConnector
import javax.management.openmbean.CompositeDataSupport
import javax.management.openmbean.TabularData
import java.lang.NumberFormatException
import groovy.json.JsonBuilder
//http://groovy.codehaus.org/Groovy+and+JMX
/*def bundleId = null
for(def arg:args){
	if (arg.contains("bundleId")){
		bundleId=arg.split("=")[1] 
	}
}*/
pout = pout == null ? System.out : pout
perr = perr == null ? System.err : perr

def stdOut = pout
def stdErr = perr
Logger logger = new Logger()
logger.setOutStream(pout)
logger.setErrStream(perr)

def propertyMissing(String name) {}



def scriptName = "settings.groovy"
def pathToLib = cwd == null ? "lib/" : cwd + "/deployed-bundles/"+bundleId+"/lib/"
def parent = getClass().getClassLoader()
def loader = new GroovyClassLoader(parent)
//def Settings = loader.parseClass(new File(pathToLib + scriptName))

def JBossServiceUrl(ipAddress,port){
	ipAddress="LCYLS00008.sbetenv.ads"
	port="9999"
	return "service:jmx:remoting-jmx://"+ipAddress+":" + "9999"
}


class StrUtils{
	def static  flatten(map){
		def values=[]
		for(def k:map.keySet()){
			if(map[k] instanceof LinkedHashMap){
				values.add(flatten(map[k])) 
			}else{
				values.add(""+k+"=\""+map[k]+"\"")
			}
		}	
		return values.join(", ") 
	}
}

def linuxScan(portRange,serviceUrl,jmxAuth){


	def cmd = [
	'bash',
	'-c',
	''' netstat -lap | grep java | grep 'LISTEN ' | awk '{print $4,$7}' | sed -e 's/\\[\\:\\:\\]\\://' | sort '''.stripMargin() ]


	def connections = [:] 
	
	counter = 0 
	cmd.execute().text.split("\n").each{ line -> 
		(port,pid) = line.split()
		if (port.contains(":") ) { port = port.split(":")[1] }
		try{ 
			port = port.toInteger()
			pid =  pid.split("/")[0].toInteger()
			cred=getAuth ( jmxAuth, counter ) 
			if  ( port > portRange[0]  && port < portRange[1] ) 
			{
				//c = connector( jmxUrl("127.0.0.1",port ))
				c = connect( serviceUrl("127.0.0.1",port ), cred)
				connections[pid] = [
					"port": port
					,"connection":c.MBeanServerConnection 
					,"connector":c
				]



			//	pout <<   "Connected to ... " +  jmxUrl("127.0.0.1",port)  << "\n"
			}

		}catch(ClassCastException e){	
			perr <<  "Skipping ..." +  port  + "," + pid  << "\n"

		}catch(NumberFormatException e){

			// swallow this exception
		}catch(Exception e){

			errorLog ( e )  
			e.printStackTrace(perr)
		}
	

	}

	return connections
}

class Logger{
	private static verbose=false;
	private static Logger instance = null;
	private static pout;
	private static perr;
	private Logger(){}

	def setVerboseTrue(){
		verbose=true;
	}
	
	def getInstance(){
		if(instance==null){
			instance = new Logger();
		}
		return instance;
	}
	def setOutStream(def out){
		pout=out		
	}
	def setErrStream(def err){
		perr=err
	} 
	def log( line ){
		def dt = new Date()
		SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss zzz"); 
		//def timestamp = String.format('%1$te-%1$tb-%1$ty %tT',dt) 
		def timestamp=dateFormatter.format(dt)
		pout <<  "" + timestamp + "\t" + line  + "\n" 
	}

	def error( line ,exception=null) { 
		def dt = new Date()
		def timestamp = String.format('%1$te-%1$tb-%1$ty %tT',dt) 
		perr <<  "" + timestamp + "\t" + line  + "\n" 
	}

	def exception(line,exception=null){
		this.error("Exception: ["+line+"]") 

		if(exception!=null){
			exception.printStackTrace(perr) 
		}
	}
	def verbose(line){
		if(verbose){
			error(line);
		}
	}
}

def getAuth(auth,index) {
	if (auth == null) { return null } 
	
	elems = auth.split(",")
	if (index > elems.size()  - 1 ) { return null } 
	
	cred = elems[index] 
	if (cred.contains(":")) {
		credArgs =cred.split(":")
		
		return [ (JMXConnector.CREDENTIALS):credArgs ]  	
	}

	return null
}


class Port{
	def port
	def Port(p){
		port =  p
	}

	def isValid(portIntValue){
		if (isRange()){
			def (portBegin,portEnd) = rangeValues() 
			return (  (portIntValue > portBegin) && (portIntValue < portEnd) ) 
		}else if (isInteger()){
			return (intValue() == portIntValue) 
		}
		return false
	}

	def isInteger() {return (port.contains("-") == true) ? false : port.isInteger()  }

	def isRange() {
		if (port.contains("-")) {
			def (v1,v2)=port.split("-");
			return  ( v1.isInteger() &&  v2.isInteger() )
		}
		return false
	}
	def intValue() {
		return port.toInteger()
	} 

	def rangeValues() {
		def (v1,v2) = port.split("-") as List
		def portBegin = v1.toInteger()
		def portEnd = v2.toInteger()
		if (portEnd < portBegin ) {
			throw new Exception("Invalid port-range: " + port ) 
		}
		return[portBegin,portEnd]
	}

	def contains(p) {
		def (portBegin,portEnd) =  rangeValues() 
		if ((p > portBegin) && (p < portEnd )){
			return true
		}

		return false 
	}

	public String toString(){
		return ""+port
	}
}
class JmxUrlBuilder { 
	def scanner
	def ns  
	def JmxUrlBuilder(scanner){
		this.scanner = scanner 
	}

	def generateUrls( urlConfig) {
		def urls = [] 
		def hosts = urlConfig.hosts.split(",")  as List
		def rmiName =(urlConfig.rmi==null)? "jmxrmi" : urlConfig.rmi.name
		def jndiPort=""
		def jndiHost=""
		def jndiUrl=null
		if (urlConfig.jndi !=null){
			jndiPort= (urlConfig.jndi.port==null)? "" : ":"+urlConfig.jndi.port
			jndiHost= (urlConfig.jndi.host==null)? "" : urlConfig.jndi.host
		}
		jndiUrl = (urlConfig.jndi == null) ? "/jndi" : jndiHost + jndiPort+"/jndi"  
		for (host in hosts) {
			def port = (urlConfig.port != null) ? ":" + urlConfig.port   : "" 
			def address = host + port 
			def rmiPart = "rmi://"+address + "/"+rmiName
			def jmxUrl =  "service:jmx:rmi://" + jndiUrl + "/"  +   rmiPart  
			urls.add(jmxUrl) 
		}
		return urls
	}

	def generate() {return generate(this.ns)}

	def generate(ns){
		def ports = ns.ports.split(",") as List  
		def hosts  =  (ns.hosts != null) ?  ns.hosts :  "127.0.0.1"	
		def jmxUrls =  []
		def jmxPorts = [] 
		for (def port:ports){
			def p = new Port(port) 
			if (p.isRange()) {
				if (ns.hosts == null && scanner.contains(p) ){
					jmxPorts.addAll(scanner.get(p))
				}
			} 

			if (p.isInteger()) {
				jmxPorts.add(p.intValue());
			} 
			/*		
			if (ns.hosts != null) { 
				jmxPorts.add(p.intValue()) ; 
				continue 
			} 
			if (scanner.contains(p)){				
				jmxPorts.addAll( scanner.get(p)) 
			} */
		}
		for (port in jmxPorts){
			def urls =   generateUrls([
						"hosts":hosts
						,"port":port 
						,"jndi":ns.jndi
						,"rmi":ns.rmi

			])
			jmxUrls.addAll(urls)  
		}	
		return jmxUrls  
	}


}
class PortScanner {
	def info = [:]
	def ports = []
	def scannedPorts = [] 
	def logger= new Logger() 
	def add(p){
		ports.add(p) 
	} 

	def isValidPort(port) {

		for(def p:ports){
			if (p.isValid(port)) { return true } 
			 
		}
		return false
	}
	def contains(p){
		
		if (p.isInteger()){
			return scannedPorts.contains(p.intValue())
		}
		if(p.isRange()){
			def (portBegin,portEnd)=p.rangeValues()
			for(def port:scannedPorts){
				if ((port.intValue() >= portBegin) && (port.intValue()  <= portEnd) ){
					return true
				}
			}
		}
		return false

	}
	def get(p){
		def orderedPortList = [] 
		if (p.isInteger()){
			orderedPortList = scannedPorts.contains(p.intValue()) ? [p.intValue()] : null 
		}
		if(p.isRange()){
			def (portBegin,portEnd)=p.rangeValues()
			for(def port:scannedPorts){
				if ((port >= portBegin) && (port  <= portEnd) ){
					orderedPortList.add(port)
				}
			}
		}
		return orderedPortList

	}

	def scan() {

		def getUnixPorts = {  
		/*Parsing of output of netstat -anop on *Nixes*/
			def ports = [] 
			def cmd = "netstat -anop" 
			def cmdOut = cmd.execute().text 
			def lines = cmdOut.split("\n") 
			
			for (def line: lines){
				if(line.toLowerCase().contains("tcp") == false ) { continue }
				if(line.toLowerCase().contains("listen") == false) {continue}
				def tokens = line.split() as List 
				def port
				try{ 
					if (line.toLowerCase().contains("tcp ")){
						// if it is ipv4
						port = tokens[3].split(":")[1]
					}else if (line.toLowerCase().contains("tcp6")){
							
						port = tokens[3].split(":")[3]
					}
				}catch(Exception e){
					this.logger.error("Portscanner: Could not parse [" + line + "]") 
					this.logger.error(line) 
				
					continue 
				}				

				def pid = -1 
				if (tokens[6].split('/')[0].isInteger()){
					pid = tokens[6].split('/')[0].toInteger() 
				}else{

					continue 	
				}
				
				if (port.isInteger() == false) { continue } 

				if (isValidPort(port.toInteger())){ 
					scannedPorts.add(port.toInteger())
					info[port] = [:]
					info[port]["pid"] = pid 
				}else{
					
				}

			} 
			scannedPorts.sort() 
			return scannedPorts 
		}


		def getWinPorts = {  
			def ports = [] 
			def cmd = "netstat -ano" 
			def cmdOut = cmd.execute().text 
			def lines = cmdOut.split("\r\n") 
			for (def line: lines){
				if(line.contains("TCP") != true ) { continue }
				def tokens = line.split() as List 
				def port = tokens[1].split(":")[1]
				def pid = -1 
				if (tokens[4].isInteger()){
					pid = tokens[4].toInteger() 
				}
				if (port.isInteger() == false) { continue } 
				if (isValidPort(port.toInteger())){ 
					scannedPorts.add(port.toInteger())
					info[port] = [:]
					info[port]["pid"] = pid 
					

				}

			} 
			scannedPorts.sort() 
			return scannedPorts 
		}

		if (System.properties['os.name'].toLowerCase().contains('windows')){
			return getWinPorts() 	
		}else if (System.properties['os.name'].toLowerCase().contains('linux')){
			return getUnixPorts()
		}else{
			errorLog("Undetect OS, using default: netstat -ano")
			return getWinPorts()
			
		}
		
	}
}


class Service{
	def settings
	def jmxUrls = [] 
	def context = [:]
	def scanner
	def connections=[] 
	def queryResults=[]
	def logger 
	def queryResultSet
	def Service(settings){
		this.settings = settings;
		this.scanner = new PortScanner(); 
		this.logger=new Logger();
	}
	def connectAll(jmxUrls,cred){
		def connections = []
		def env=null
		for(def jmxUrl:jmxUrls){
			try{
	
				if(cred != null){
					env=[:]
					env.put(JMXConnector.CREDENTIALS,cred.split(":"))
				}			
				connections.add(JmxConnectorFactory.connect( new JmxServiceUrl(jmxUrl),env))
			}catch(Exception e){
				this.logger.error("Could not connect to ["+jmxUrl+"]\n Exception: "+e) 
			}
		}
		return connections
	}
	def initVerboseLogging(){
		if (settings.global.keySet().contains("verbose")){
			def verbose=settings.global["verbose"].asBoolean();
			if(verbose){
				this.logger.setVerboseTrue();
			}
		}
	}
	
	def init(){
		//this.queryResultSet= QueryResultSet.getNewInstance() 
		this.queryResultSet= new QueryResultSet()
		initVerboseLogging();
		def keys = settings.config.keySet()
		for (def ns:keys){
			if (settings.isReserved(ns)){
				continue;
			}
			def ports = settings.config[ns].ports 	
			//DO not process ports if jmxUrls is defined
			if (settings.config[ns].containsKey("jmxUrls") == true){
				continue; 
			}
			if(ports==null  ){

				this.logger.error("["+ns+"] ports property is undefined");
			}else{
				ports.split(",").each { p -> scanner.add(new Port(p)) } 
			}
		}
		scanner.scan() 
		def urlBuilder = new JmxUrlBuilder(scanner)
		def cred=null
		// check for ports setting 
		for (def ns:keys){
			settings.config[ns].jmxurls = [] 
			if(settings.isReserved(ns)){
				continue
			}
		
			if(settings.config[ns].containsKey("cred")){
				cred=settings.config[ns].cred
			}
		
			if(settings.config[ns].containsKey("jmxUrls")){
				def urls=settings.config[ns]["jmxUrls"].split(";")
				for(def u:urls){
					settings.config[ns].jmxurls.add(u) 
				}
			}
			if (settings.config[ns].containsKey("ports")){
				def nsSettings  = settings.config[ns]
				settings.config[ns].jmxurls.addAll(urlBuilder.generate(nsSettings))
			}
			jmxUrls.addAll(settings.config[ns].jmxurls) 
			settings.config[ns].jmxconnections =  connectAll(settings.config[ns].jmxurls,cred)
			connections.addAll( settings.config[ns].jmxconnections ) 
		}
	}

	def query(c,queryString){
		def resultMap = [:]
		return resultMap;
	}	
	//TODO: Invoke List  e.g invoke(c,objectName,attributeList) 
	def getObjectName(q){
		return q.split("@")[0]
	}
	def getAttributes(q){
		return q.substring(q.indexOf("@")+ 1 ,q.length()).split("@")
	}
	def mbeanInvoke(conns,invokeBeforeOperations,ns){
		def prefix=""
		def objectName=getObjectName(invokeBeforeOperations) 
               	def ops=getAttributes(invokeBeforeOperations) 
		for(def c:conns){
			invoke(c,objectName,ops,prefix) 
		}
	}
	def getAttributes(c,objectName,attributeList,columnNamePrefix){
		def resultList=[]
		if (objectName.contains("*")){
			def names=c.MBeanServerConnection.queryNames(new ObjectName(objectName),null) 
			def objectNameResultSet = [] 
			for(def n:names){
				try{
					objectNameResultSet.addAll(getAttributes(c,n.toString(),attributeList,columnNamePrefix))
				}catch(javax.management.AttributeNotFoundException e){
					this.logger.exception("Error on object:" + objectName + "," + n ,e);
				}
			}
			resultList.addAll(objectNameResultSet)
		}else{
			def resultSet=[]
			resultList.add([get(c,objectName,attributeList,columnNamePrefix)])
		
		}


		return resultList 
	}


	def getPathFromObjectName(objectName,attrib){
		for(def el:objectName.split(",")){
			if (el.toLowerCase().contains("name")){
				return el.split("=")[1] + "." + attrib 
			}
		}
		return attrib 
	}

	def invoke(c,objectName,ops,columnNamePrefix){
		def bean=new GroovyMBean(c.MBeanServerConnection,objectName)
		for(def op:ops){
			//TODO: Find out why c.MBeanServerConnection is wrong! 
			def mbsc = c.getMBeanServerConnection() 
			//mbsc.invoke(new ObjectName(objectName),"resetPeakThreadCount",null,null)
			mbsc.invoke(new ObjectName(objectName),op,null,null)
		}
	}

	def get(c,objectName,attributeList,columnNamePrefix=null){
		def resultMap=[:] 
		def bean
		def prefix=""
		if (columnNamePrefix != null){
			prefix=columnNamePrefix	
		}
		try{
			bean =  new GroovyMBean(c.MBeanServerConnection,objectName)
		}catch(javax.management.InstanceNotFoundException f){
			this.logger.error("[" + c +"]; Instance["+objectName+"] Not Found" );
			throw f
		}catch(Exception e){
			this.logger.error("Could not connect to"+objectName+"  using["+c+"]")
			this.logger.error(e)

			throw e
		}

		resultMap=[:]
		resultMap["objectName"]=objectName
		this.queryResultSet.addAttribute(objectName,"objectName",objectName)
		this.queryResultSet.addAttribute(objectName,"host",c.getAddress().toString().split("rmi://")[2].split("/")[0].split(":")[0])

		for(def attrib:attributeList){
			def value
			def p
			try{
				value=bean.getProperty(attrib)
			}catch(Exception e){
				this.logger.error("Could not get property [" + attrib  + "] on objectName: ["+ objectName +"] on connection: [" +c+"" )

				this.logger.error(e)
				continue

			}
			if (value instanceof CompositeDataSupport){
				// This is hack. Scraping the name from objectName
				def path=getPathFromObjectName(objectName,attrib) 
				p=this.getDictionaryFromComposite(path,value)
			}else if (value instanceof TabularData) {
				// This is hack. Scraping the name from objectName
				def path=getPathFromObjectName(objectName,attrib) 
				p=this.getDictionaryFromComposite(path,value)
			}else{
				this.queryResultSet.addAttribute(objectName,attrib,value) 
				
				p=value
			}
			resultMap[prefix+attrib]=p
		}


		// add system fields 
		this.queryResultSet.next() 
		return resultMap
	}
	def addSystemFields(){

	}

	def batchQuery(c,queries,columnNamePrefix=null){
		def resultList=[] 	// query result list 
		for(def queryString:queries){
			//Query Example: java.lang:type=GarbageCollector,name=*@LastGcInfo@CollectionCount@CollectionTime
			def objectName=queryString.split("@")[0]
			
                	def attributeList=queryString.substring(queryString.indexOf("@")+ 1 ,queryString.length()).split("@")
			resultList.addAll(getAttributes(c,objectName,attributeList,columnNamePrefix)) 
		}
		return resultList
	}

	def getGroupResults(conns,queries,columnNamePrefix=null,group){
		def resultMap=[:]
		this.queryResultSet.setGroup(group)
		for(def c:conns){ 
			resultMap[c]=batchQuery(c,queries,columnNamePrefix) 
		}
		return resultMap
	}
	def execute(ns,queryString){
		def resultMap=[:]
		for(def c:settings.config[ns].jmxconnections){
		}
		return resultMap;
	} 

	def collectData(queryString,isInvocable=false) {  return startOn(queryString,isInvocable) }

	/*
	def startOn(queryString,isInvocable=false){
		def keys = settings.config.keySet()

		def nsResults = [] 
		for(def ns:keys){
			println "startOn:ns" + ns 
			println "startOn:execute" +  settings.config[ns].jmxconnections 
			def rs =  execute(settings.config[ns].jmxconnections , queryString, isInvocable )   
			
			queryResults.addAll(rs) 
			settings.config[ns].results = rs 
			nsResults.add(settings.config[ns]) 
		}


		return settings.config 
	} */



	def shutdown() {
	}

	def getDictionaryFromTable(padding,tbl){
		def map=[:] 
		for(def k:tbl.keySet()){
			for(def v:k){
			}
			def valueType=tbl.get(k.toArray()).getClass().getName()
			if( valueType=="javax.management.openmbean.CompositeDataSupport"){
				map[k]=getDictionaryFromComposite(padding+".",tbl.get(k.toArray()))
			}
		}

		return map
	}
	

	def getDictionaryFromComposite(path,property){
		def map=[:] 
		def data=property.getCompositeType()
		map["path"]=path
		for(def key:data.keySet()){
			
			if ((String)property[key].getClass().getName()=="javax.management.openmbean.TabularDataSupport"){
				map[key]= this.getDictionaryFromTable(path+"."+key,property[key])
			}

			else if ((String)property[key].getClass().getName()=="javax.management.openmbean.CompositeDataSupport"){
				map[key]=this.getDictionaryFromComposite(path+"."+key,property[key])
			} else{
				map[key]=""+property[key]
				/* trace 
				
				println path
				println key
				println map[key] */
				this.queryResultSet.addAttribute("",key,map[key],path+"."+key)
			}
			
		}
		return map 
	} 





}


def settings =  Settings.newInstance()
def arguments  =  args  as List 

settings.allRequires(["ports"])
settings.addReserved("jndi")
settings.addReserved("rmi")

//check for debug true for external execution

bindings=settings.getBindingFromArguments(arguments) 

if (bindings.containsKey("devMode") == true){
	settings.init(bindings) 
	println "javaapp:: devmode:: init"
}else{

	settings.init(_bindings) 
}

// Add default group

settings.config["default"]=[:]
settings.config["default"]["label"]="default"
if (settings.global.containsKey("hosts")){
	settings.config["default"]["hosts"]=settings.global["hosts"] 
} 

if (settings.global.containsKey("ports")){
	settings.config["default"]["ports"]=settings.global["ports"] 
} 



if (settings.global.containsKey("label")){
	settings.config["default"]["ports"]=settings.global["label"]
}

if (settings.global.containsKey("query")){
	settings.config["default"]["query"]=settings.global["query"]
}


def service = new Service(settings) 
try{	
	service.init()
}catch(Exception e){
	logger.error(e.getMessage()) 
	e.printStackTrace(perr)
}
//def queryString=settings.global["query"].replaceAll("_"," ");
def queryString=settings.global["query"]
def format=settings.global["format"] 
if (Format==null){ format=Printer.FORMAT_JSON  }
if (format=="csv") {format=Printer.FORMAT_CSV } 
if (format=="json") { format=Printer.FORMAT_JSON} 
def invokeQueryString=""
if (queryString==null){
	throw new Exception("Required parameter ['query'] does not exist")
}


invokeQueryString=settings.global["invoke"]
if (invokeQueryString!=null){
	invokeQueryString=invokeQueryString.replaceAll("_"," ")
}else{
	invokeQueryString=""
}

def joinQuery=null
if (settings.global.containsKey("joinQuery"))
{
	joinQuery=settings.global["joinQuery"]
}

//TODO def invokeBefore=settings.getProperty("invokeBefore",null) 
def invokeBefore=null
def invokeBeforeOperations=null
if (settings.global.containsKey("invokeBefore")){
	invokeBeforeOperations=settings.global["invokeBefore"]
}

def invokeAfter=null
def invokeAfterOperations=settings.global["invokeAfter"] 
if (settings.global.containsKey("invokeAfter")){
	invokeAfterOperations=settings.global["invokeAfter"]
}

def pollInterval=0
def pollCount=1
if (settings.global.containsKey("pollInterval") && settings.global.containsKey("pollCount") ) {
	pollInterval=settings.global["pollInterval"].toInteger()
	pollCount=settings.global["pollCount"].toInteger()
}
def counter=0

def queries=queryString.split(";") 
def resultSet=[:] 

def printer=new Printer(format) 
while(counter<pollCount){

	resultSet=[:] 
	service.queryResultSet=QueryResultSet.getNewInstance() 
	try{
		for(def ns:settings.config.keySet()){
			if (ns=="default"){
				continue 
			}
			resultSet[ns]=[:]
			def connections=settings.config[ns].jmxconnections
			// invoke befores
			if(invokeBeforeOperations!=null){
				for(def q:invokeBeforeOperations.split(";")){
					service.mbeanInvoke(connections,q,ns) 
				}
			} 
			resultSet[ns]["results"]=service.getGroupResults(connections,queries,ns) 
			//TODO: service.invoke(connections,invokeAfterOperations,ns) 
			if(joinQuery){
				def resultSet2=[:]
				resultSet2[ns]=[:]
				resultSet2[ns]["results"]=service.getGroupResults(connections,[joinQuery],"__",resultSet[ns].label)
				resultSet[ns]=joinResultSet(resultSet[ns],resultSet2[ns]) 
			}

			if(invokeAfterOperations!=null){
				for(def q:invokeAfterOperations.split(";")){
					service.mbeanInvoke(connections,q,ns) 
				}
			}
			resultSet[ns]["label"] = settings.config[ns]["label"] 
		}
		printer.print(resultSet) 
		//printer.render2(service.queryResultSet,format);
	}catch(Exception e){
		logger.exception("[Fatal]",e)

	}	
	counter=counter+1
	Thread.sleep(pollInterval*1000)
}
service.shutdown() 

def joinToChildValue(v,rs2){
  return v
}

def joinResultSet(rs1,rs2){
	def rs=[:]
	rs=rs1 

	def left=rs1["results"]
	def right=rs2["results"]
//	println ""
	for(def c: left.keySet()){
//		print left[c] 
//		println "\nleft c size==" + left[c].size()
		for(def i=0;i<left[c].size();i++){
//			println left[c][i]
			for(def field: right[c][0][0].keySet()){
	  
				if (field=="objectName") { continue } 
//				println "" 
//				println "............"
//				println "field: "  + field 
//				println "i=" + i 
//				println "left c i==="  +  left[c][i]
//				println "left c i 0==="  +  left[c][i][0]
//				println "left c i 0 field ("+field+")===" +  left[c][i][0][field]

//				println "right c 0==="  +  right[c][0]
//				println "right c 0 0==="  +  right[c][0][0]
//				println "right c 0 0 field ("+field+")===" +  right[c][0][0][field]
//				println "#################" 
				left[c][i][0][field] = right[c][0][0][field] 
//				println "left c i 0 field ("+field+")===" +  left[c][i][0][field]
//				println "#################" 
			}
		}
	}
	ret=[:]
	ret["results"] = left
	return ret 
	System.exit(0) 
/*
	for(def c:rs1.keySet()){
		rs2.remove("objectName")
		//replace common names e.g name
		//rs2[c][0][0]["_id"]=rs2[c][0][0]["Name"]
		//rs2[c][0][0].remove("Name")	
		for(def i=0;i<rs1[c].size();i++){
			
			for(def j=0;j<rs1[c][i].size();j++){
        def v= rs[c][i][j];
        for (def childKey: v.keySet()){
          if (v[childKey] instanceof LinkedHashMap){
            rs[c][i][j][childKey].putAll(rs2[c][0][0])
          }
        }



				rs[c][i][j].putAll(rs2[c][0][0])
			}
		}
		
	}*/
	return rs
}


class Printer{
	def logger;
	def rowSet=[];	
	def currentFormat=-1;

	public static final FORMAT_JSON=0
	public static final FORMAT_CSV=1 
	def Printer(format=FORMAT_CSV){
		this.currentFormat=format
		this.logger=new Logger(); 
		//this.rowSet=new RowSet();
	}

	def json(k,v){
	    return "\""+k+"\"" + ": " + "\""+v+"\"";
	}

	def items(map,attribs=[:]){
		def ret=[]
		map.putAll(attribs)
		def row=[]
		map.keySet().each{ k -> 
			if(map[k] instanceof LinkedHashMap){
				items(map[k],attribs)
			}else{
				row.add(json(k,map[k]))
			}
		}
		this.rowSet.add(row) 
		return ret
	}
	def items2(map,attribs=[:]){
		def elems=[]
		map.putAll(attribs)
		
		for(def k:map.keySet()){
			if (map[k] instanceof LinkedHashMap){
				// collect all columns with prefix . and cascade to children 
				map.keySet().each { it ->
					if (it[0] == '.'){
						attribs[it] = map[it] 
					}
				}
				//rowSet.add(items(map[k],attribs)) 
				elems.addAll(items(map[k],attribs)) 

			}else{
				elems.add(json(k,map[k]))
			}
			
		}
		this.rowSet.add([elems])
		return elems
	}

	def render(format){
		for(def r:this.rowSet){
			if (r.size() <  1){
				continue
			}
			logger.log("{ " + r.join(", ") + " } ")
		}
	}
	def render3(rows){
		rows.each { r ->  println r } 
	}
	
	def render2(queryResultSet,format){
		//println  "#" + queryResultSet.columnId.keySet().collect{"$it"}.join("\t") 
		queryResultSet.records.each { record ->  println  record.collect{"$it"}.join("\t")  } 
	}
	def print(resultSet){


		
		for(def g:resultSet.keySet()){
			def systemAttributes=[:] 
			def results=resultSet[g]["results"]
			systemAttributes["namespace"]=g
			systemAttributes["label"]=resultSet[g]["label"] 
			for(def c:results.keySet()){
				def rs=results[c] 	
				systemAttributes["host"]=c.getAddress().toString().split("rmi://")[2].split("/")[0].split(":")[0]
				//TODO: Detect if __Name exists 
				systemAttributes["__Name"]=results[c]["__Name"][0][0] 
						

				rs.each { query ->
					this.rowSet=[] 
					query.each { attribs -> 
						def rows=items(attribs,systemAttributes) 
						render3(rows) 
					}

					render(Printer.FORMAT_JSON) 
				}
			


			}	
		}
	}

}
