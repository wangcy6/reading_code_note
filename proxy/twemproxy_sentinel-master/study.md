

make 

https://redis.io/topics/sentinel
ZooKeeper的脑裂的出现和解决方案
https://github.com/redis/hiredis/tree/master/examples

------------------------------------------------------------------
~~~
int ts_sentinel_subscribe(ts_args **tsArgs) {

  signal(SIGPIPE, SIG_IGN);
  struct event_base *base = event_base_new();

  redisAsyncContext *c = redisAsyncConnect((*tsArgs)->server->host, (*tsArgs)->server->port);
  if (c->err) {
    syslog(LOG_CRIT, "error: %s\n", c->errstr);
    return 1;
  }

  redisEnableKeepAlive(&c->c);

  redisLibeventAttach(c, base);
  char subscribeCmd[72];
  sprintf(subscribeCmd,"SUBSCRIBE %s", (*tsArgs)->nc_channel_name);
  redisAsyncCommand(c, ts_sentinel_publish_message, (*tsArgs), subscribeCmd);
  redisAsyncSetDisconnectCallback(c, ts_sentinel_disconnect);
  syslog(LOG_INFO, "twemproxy sentinel listenting to sentinel on channel: %s\n", subscribeCmd);
  event_base_dispatch(base);

  return 0;
}

~~~




-----------------------------------------------------------