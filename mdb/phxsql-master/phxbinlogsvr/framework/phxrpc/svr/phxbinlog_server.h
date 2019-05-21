/*
	Tencent is pleased to support the open source community by making PhxSQL available.
	Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
	Licensed under the GNU General Public License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

	https://opensource.org/licenses/GPL-2.0

	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
*/

#pragma once

#include <stdarg.h>
#include "phxrpc/rpc.h"


namespace phxbinlog {


class Option;


}


namespace phxbinlogsvr {


class PhxBinLogSvrHandler;
class PhxBinLogClientFactoryInterface;


}


namespace phxrpc {


class BaseTcpStream;
class BaseRequest;
class BaseResponse;


}


class PhxbinlogServerConfig;

class Server {
  public:
    Server();
    virtual ~Server();
    void Run();

  private:
    virtual void InitMonitor();
    virtual phxbinlogsvr::PhxBinLogClientFactoryInterface * GetPhxBinLogClientFactory();
    void InitConfig();

    void BeforeServerRun();
    void AfterServerRun();

    PhxbinlogServerConfig *GetServerConfig();
    phxbinlogsvr::PhxBinLogSvrHandler *GetSvrHandler();

    static void Dispatch(const phxrpc::BaseRequest &req,
                         phxrpc::BaseResponse *const resp,
                         phxrpc::DispatcherArgs_t *const args);

  protected:
    typedef void (*OpenLogFunc)(const char *, const int &log_level, const char *log_path,
                                const uint32_t &log_file_max_size);
    typedef void (*LogFunc)(int log_level, const char *format, va_list args);

    void InitLog(LogFunc log_func, OpenLogFunc openlog_func);

    PhxbinlogServerConfig *config_;
    phxbinlogsvr::PhxBinLogSvrHandler *svr_handler_;
    phxbinlog::Option *phxbin_option_;
};

