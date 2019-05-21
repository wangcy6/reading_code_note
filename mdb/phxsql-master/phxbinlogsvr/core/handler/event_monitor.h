/*
	Tencent is pleased to support the open source community by making PhxSQL available.
	Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
	Licensed under the GNU General Public License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
	
	https://opensource.org/licenses/GPL-2.0
	
	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
*/

#pragma once

#include "phxcomm/thread_base.h"
#include <vector>
#include <string>

namespace phxbinlog {

class Option;
class StorageManager;
class MasterManager;
class EventMonitor : public phxsql::ThreadBase {
 public:
    EventMonitor(const Option *option);
    virtual ~EventMonitor();
    virtual int Process();
 private:
    int CheckRunningStatus();

 private:
    std::vector<std::string> last_check_gtid_;
    StorageManager *storage_manager_;
    MasterManager *master_manager_;
    const Option *option_;
    bool stop_;
};

}
