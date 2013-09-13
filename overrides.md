# Overrides

The JavaApp can monitor JMX enabled applications in different scenarios. Here are a few examples: 



## Indexer monitoring multiple remote hosts


	*.group0.hosts=app0.local,app1.local,app2.local
	*.group0.ports=10000



## Indexer monitoring multiple local java processes

It is common for multiple java processes to use ports witin a port range 

	*.group0.ports=9010-9090,10000-11000
	*.group0.label=DataSynapseEngines

This configuration will find all java processes within the port ranges and collect jmx data


##  Uncommon JMX Url syntaxes 

	*.group0.jmxUrls=  coherence urls 
	*.grouo0.label=Coherence

using the custom jboss JMX Url syntax 

	*.group1.jmxUrls=jboss blah blah
	*.group1.label=JBOSSAS



## Grouping you applications 
