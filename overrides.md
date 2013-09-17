# Overrides

The JavaApp can monitor JMX enabled applications in different scenarios. In most cases all you will have to do is provide a host and a port and the JavaApp will do the rest.    
Here are a few examples: 




## Indexer monitoring multiple remote hosts


	*.group0.hosts=app0.local,app1.local,app2.local
	*.group0.ports=10000



## Indexer monitoring multiple local java processes

Some java applications may run multiple processes on a single host with each exposing jmx within a predifined port range. The example below will discover all jmx ports in the ranges 9010-9090 and 10000-11000. 

	*.group0.ports=9010-9090,10000-11000
	*.group0.label=DataSynapseEngines

There is no need to explicitely list all the ports if the process being monitored is on the same host as an Indexer of Forwarder

## Group your applications




##  Other Jmx Url types

You can use hardcoded urls if your application use jmx syntax similar to these. 

	*.group0.jmxUrls=service:jmx:rmi://10.28.1.164:16331/jndi/rmi://10.28.1.164:16332/server;service:jmx:rmi://10.28.1.165:16331/jndi/rmi://10.28.1.165:16332/server  
	*.grouo0.label=Coherence

using the custom jboss JMX Url syntax 

	*.group1.jmxUrls=service:jmx:remoting-jmx://localhost:9010
	*.group1.label=JBOSS_AS7

