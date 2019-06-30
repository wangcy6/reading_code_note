# Redis-Twemproxy Agent

### Intro
A simple nodejs application which will connect to Redis-Sentinel and monitor for the master-change event.
It will then update TwemProxy (nutcracker) and restart it.

The basic idea behind it, is so that you have redundancy in your redis shards, when your master dies, a slave is promoted to Master by Redis Sentinel, and then this agent updates your TwemProxy config to point to the new master.

```
			TwemProxy
		__________|__________
		|					|
	Master1				Master N
Slave1 	SlaveN		Slave 1	Slave N
		
			Redis Sentinel
```

A more detailed explanation can be found on [an article on my site](http://www.jambr.co.uk/Article/redis-twemproxy-agent). 

### Caveats
Your master names in Redis-Sentinel (sentinel.conf) should match the names of the master nodes in your twemproxy config.
I have included examples of sentinel and twemproxy configs in the conf directory

### History
This originally started as a [gist](https://gist.github.com/eveiga/5039007) posted on [a twemproxy ticket about doing HA redis](https://github.com/twitter/twemproxy/issues/67).
It was then created in GitHub at https://github.com/matschaffer/redis_twemproxy_agent

This fork contains the following changes:
```
	-	Full yaml parsing of the config file rather than string replacements
	-	Full logging to a specific log file in cli.js
	-	Automatic complete master update upon connection to Redis-Sentinel
	-	An example init.d script, using forever, to run on boot.  Albeit it currently runs as root, haven't got round to securing this yet.
```
