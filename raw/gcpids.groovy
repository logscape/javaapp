import javax.management.ObjectName
import javax.management.remote.JMXConnectorFactory as JmxFactory
import javax.management.remote.JMXServiceURL as JmxUrl
import javax.management.openmbean.CompositeDataSupport
import java.lang.NumberFormatException
//http://groovy.codehaus.org/Groovy+and+JMX


pout = pout == null ? System.out : pout
perr = perr == null ? System.err : perr

def stdOut = pout
def stdErr = perr

def propertyMissing(String name) {}
 
def jmxUrl(ipAddress,port){
	return "service:jmx:rmi://"+ ipAddress +"/jndi/rmi://"+ipAddress+":"+ port +"/jmxrmi"
}


def connect(jmxUrl){
	return JmxFactory.connect( new JmxUrl(jmxUrl))
}

def getGcLogFile(pid){
	def cmd = [
		'bash'
		,'-c'
		," ps aux | grep " +  pid  + "   ".stripMargin() ] 
		
	psOut=cmd.execute().text 

	return psOut.split().findAll{  it.contains('-Xloggc')  }.collect{ it ->  it.split(":")[1] }[0]
}
def gcLogDiscover(portRange){

	def cmd = [
	'bash',
	'-c',
	''' netstat -lap | grep java | grep 'LISTEN ' | awk '{print $4,$7}' | sed -e 's/\\[\\:\\:\\]\\://' | sort '''.stripMargin() ]



	cmd.execute().text.split("\n").each{ line -> 
		(port,pid) = line.split()

		try{ 
			port = port.toInteger()
			pid =  pid.split("/")[0].toInteger()

			if  ( port > portRange[0]  && port < portRange[1] ) 
			{
				log( pid + "\t" + getGcLogFile(pid) ) 
			}

		}catch(ClassCastException e){	
			perr <<  "Skipping ..." +  port  + "," + pid  << "\n"

		}catch(NumberFormatException e){

			// swallow this exception
		}catch(Exception e){

			errorLog ( e )  
		}
	

	}
	return gcLogFile
}
def log( line ){
	dt = new Date()
	timestamp = String.format('%1$te-%1$tb-%1$ty %tT',dt) 
	pout <<  "" + timestamp + "\t" + line  + "\n" 
}

def errorLog( line ) 
{
	dt = new Date()
	timestamp = String.format('%1$te-%1$tb-%1$ty %tT',dt) 
	perr <<  "" + timestamp + "\t" + line  + "\n" 
}

def execute( jmxConnections, jmxQuery)
{
	if (!jmxQuery.contains("@")) {
		throw new Exception("No attribute list specified for java query" );
	}

	// dequeue the list 
	jmxQueryElems = jmxQuery.split("@").reverse() as List
	beanName = jmxQueryElems.pop() 
	beanPropertyList = jmxQueryElems.reverse() 

	jmxConnections.each { k,v -> 
		conn = v["connection"]
		try{
			bean = new GroovyMBean(conn,beanName)

			beanPropertyList.each {  name ->
				property = bean.getProperty(name) 

				if (property instanceof CompositeDataSupport)
				{
					keyValuePairs = [] 
					property.getCompositeType().keySet().each{ key -> 
						keyValuePairs.add (  key + ":" +  property.get(key)  ) 

					
					}
					logline =   keyValuePairs.join("\t")

				}else{
					logline =  bean.getProperty( name ) 		
				}
				log( beanName +  "." + name + ":\t" + logline +  "\t" + "pid:" + k) 
			}
		}catch(javax.management.InstanceNotFoundException e) {
			perr << "Could not find ["+ jmxQuery +"]. Failed on port="+ v["port"] +",pid="+v["pid"]+" "  << "\n"

		}
	}	
}

def ranges(jmxPortList){
	
	range = [] 

	jmxPortList.split(",").each{ elem  -> 
		range.add (  elem.split("-").collect { it -> it.toInteger()  }  ) 

	}
	return range 
}
//pout <<  "args:" + args + " "  << "\n"
jmxPortRanges = jmxPortRanges == null ? "8980-8990" : jmxPortRanges
def portRanges = ranges( jmxPortRanges ) // [8900,9100]
def jmxConnections = [] 

portRanges.each { portRange -> 
	jmxConnections = gcLogDiscover(portRange) 
}

