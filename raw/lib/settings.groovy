public class Settings{
	def config=[:]
	def global=[:]
	def reservedWords=[]
	def binding 
	def addReserved(name){
		reservedWords.add(name)
	}
	def isReserved(name){
		for(def w:reservedWords){
			if (name.contains(w)==true){
				return true
			}

		}
		return false
	}
	def getParentNS(ns){
		def parts=ns.split('\\.') as List
		parts.pop() 
		def parentNS=parts.join(".") 
		return parentNS 
	}
	def requires(ns,properties){

		properties.each{ p ->
			if (config[ns].keySet().contains(p) != true) {
				throw new Exception("Requires the property: " +  p ) 
			}
		}
	}

	def allRequires(properties){
		config.keySet().each{ns->
			requires(ns,properties)
		}
	}

	def setDefaults(ns,defaults){

		defaults.each{ k,v -> 
			if (!config[ns].keySet().contains(k)){
				
				config[ns][k] = v.isInteger()  ?  v.toInteger() : v  
			}
		}
	} 

	def updateConfig = {  namespace,k,v -> 
		if(isReserved(namespace)){
			def pns = getParentNS(namespace)
			def parts=namespace.split("\\.") as List 
			def var=parts.pop() 
			if(config[pns].keySet().contains(var)==false){
				config[pns][var]=[:]
			}
			config[pns][var][k]=v
		}
		if (!config.keySet().contains(namespace)){
			config[namespace] = [:] 
		}
		config[namespace][k] = v  
	}

	def getBindingFromArguments(args){
		def map=[:]
		for(def arg:args){
			def index=arg.indexOf("=") 
			def k=arg.substring(0,index)
			def v=arg.substring(index+1,arg.size()) 
			map[k]=v
		}
		return map
	}
	def init(binding){
		if(binding==null){
		      binding=[:]
		}
		for(def var:binding.keySet()){
			def newVar="_."+var
			def parts = newVar.split("\\.") as List
			def p = parts.pop()
			def namespace = parts.size() > 0 ? parts.join(".") : "_"
			def value=binding[var]
			updateConfig(namespace,p,value)	
		}
		global=config["_"]
		config.remove("_")
		for (ns in config.keySet() ){ 
			if  (isReserved(ns)){
				continue
			}
			setDefaults(ns,global) 
		}

	}
}
/*
def bindings = [:]
def settings = new Settings()
settings.addReserved(["reserved"])
params="a=b reserved=v0  a.reserved.k1=v1 a.reserved.k2=v2"
for(def p:params.split()){
	def (fn,value)=p.split("=")
	bindings[fn]=value
}
settings.init(bindings)
println settings.config
println settings.global*/
