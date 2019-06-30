#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <syslog.h>

#include "ts_server.h"
#include "ts_sentinel.h"
#include "ts_args.h"
#include "ts_nc_config.h"

void ts_nc_update_masters_and_restart(ts_args **tsArgs);
static int ts_nc_service_restart(char *service_name);

static redisContext *redis_ctx;

void main(int argc, char **argv) {

  ts_args *tsArgs = ts_args_init();
  
  ts_args_parse(argc, argv, &tsArgs);
  
  //创建日志
  openlog(tsArgs->nc_log_file, LOG_CONS | LOG_PID | LOG_NDELAY, 0);
  //连接reids哨兵
  redis_ctx = ts_sentinel_connect(&tsArgs->server);
  //获取mater主机配置信息
  ts_servers *servers = ts_sentinel_get_masters(&redis_ctx);
  //更新配置信息到磁盘文件
  ts_nc_config_update(&tsArgs, &servers);
  //重启代理
  ts_nc_service_restart(tsArgs->nc_service_name);
  //订阅，为什么发现ip发现变化，就重启服务，直接替换不好，这样出现一个更新，一个使用 使用锁情况。
  ts_sentinel_subscribe(&tsArgs);
}

void ts_nc_update_masters_and_restart(ts_args **tsArgs) {
  
  redisReconnect(redis_ctx); 

  ts_servers *servers = ts_sentinel_get_masters(&redis_ctx);
  
  ts_nc_config_update(tsArgs, &servers);
  //为什么需要重启服务呢，是单线程无锁方式。重新修改配置
  ts_nc_service_restart((*tsArgs)->nc_service_name);
}

static int ts_nc_service_restart(char *service_name) {
  pid_t pid;
  int status;
  
  if ((pid = fork()) == 0) {
    /* the child process */
    execlp("service", "service", service_name, "restart", NULL);
    /* if execl() was successful, this won't be reached */
    syslog(LOG_CRIT, "Cannot Retart Twemproxy\n");
    _exit(1);
  }
  if ((pid > 0) && (waitpid(pid, &status, 0) > 0)) {
    if (WIFEXITED(status) && !WEXITSTATUS(status)) {
      syslog(LOG_NOTICE, "Retarted Twemproxy\n");
      return 0;
    }
  }
  syslog(LOG_CRIT, "Cannot Retart Twemproxy\n");
  return 1;
}
