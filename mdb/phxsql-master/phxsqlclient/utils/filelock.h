/*
	Tencent is pleased to support the open source community by making PhxSQL available.
	Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
	Licensed under the GNU General Public License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
	
	https://opensource.org/licenses/GPL-2.0
	
	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
*/

#pragma once

#include <unistd.h>
#include <fcntl.h>
#include <inttypes.h>
#include <sys/select.h>
#include <sys/time.h>
#include <stdio.h>

namespace phxsql {

/*!
 *
 *\brief  ��fcntl() �����ķ�װ��ʵ���ļ���,Ҳ�������ڽ��̼�ͬ��.
 *
 *\as FileLock  �ο�  testiFileLock.cpp
 *
 */

class FileLock {
 protected:
    /*!
     *\brief  �򿪵��ļ���fd.
     */
    int m_iFd;

 protected:

    /*!
     *\brief ��fcntl�ķ�װ.
     *\param iCmd int ,�����֣���μ�man fcntl.
     *\param iType int ,�����ͣ���μ�man fcntl.
     *\param iOffset int ,���iWhere�ļ���ƫ����.
     *\param iLen int ,��ס���ֽ���.
     *\param iWhere int ,����λ�ã�SEEK_XXX.
     *\retval true:�ɹ���false:ʧ��.
     *
     */
    bool Fcntl(int iCmd, int iType, int iOffset, int iLen, int iWhence);

    /*!
     *\brief ��fcntl�ķ�װ������ָ��ʱ�仹δȡ��������ʱ����ʧ�ܣ�ǰ��5�������μ�ǰ��.
     *\param sec int ,ָ��ʱ��s.
     *
     */
    bool FcntlTimeOut(int iCmd, int iType, int iOffset, int iLen, int iWhence, int sec);

    /*!
     *
     *\brief Fcntl()���ļ��汾.
     *
     */
    bool Fcntl(int iCmd, int iType, uint64_t iOffset, uint64_t iLen, int iWhence);

    /*!
     *
     *\brief FcntlTimeOut()���ļ��汾.
     *
     */
    bool FcntlTimeOut(int iCmd, int iType, uint64_t iOffset, uint64_t iLen, int iWhence, int sec);
 public:

    /*!
     *\brief ���캯��.
     *
     */
    FileLock();

    /*!
     *\brief  �����������رմ򿪵��ļ�������.
     *
     */
    virtual ~FileLock();

    /*!
     *\brief ʹ��һ��ָ�����ļ���.
     *\param sPath const char *,�ļ���·��.
     *\retval  true:�ɹ���false:ʧ��.
     *
     */
    bool Open(const char* sPath);

    /*!
     *\brief �ر����ļ�,�������ϵ��ļ������ᱻ�ͷ�.
     */
    void Close();

    /*!
     *\brief �Ӷ���.���û�м����ɹ������߱��ź��жϣ�����������false.
     *\param iOffset int, ����ƫ����.
     *\param iLen int,�����ֽ���.
     *\param iWhence int, ������ʼλ��.
     *\retval true:�ɹ���false:ʧ��.
     *
     *\note return false when interupt by singal.
     */
    inline bool ReadLock(int iOffset, int iLen = 1, int iWhence = SEEK_SET) {
        return Fcntl(F_SETLK, F_RDLCK, iOffset, iLen, iWhence);
    }

    /*!
     *\brief ReadLock��ס���ļ��汾.
     *
     */
    bool ReadLock(uint64_t iOffset, uint64_t iLen = 1LL, int iWhence = SEEK_SET);

    /*!
     *\brief ��д��.���û�м����ɹ������߱��ź��жϣ�����������false.
     *\param iOffset int , ����ƫ����.
     *\param iLen int, �����ֽ���.
     *\param iWhence  int������ʼλ��.
     *\retval �true:�ɹ�,false:ʧ��.
     *\note return false when interupt by singal.
     */
    inline bool WriteLock(int iOffset, int iLen = 1, int iWhence = SEEK_SET) {
        return Fcntl(F_SETLK, F_WRLCK, iOffset, iLen, iWhence);
    }

    /*!
     *\brief WriteLock���ļ��汾.
     *
     */
    bool WriteLock(uint64_t iOffset, uint64_t iLen = 1LL, int iWhence = SEEK_SET);

    /*!
     *\brief ���õȴ��Ķ���.һ����ö��������߱��ź��жϲŷ���.
     *\param iOffset int ,����ƫ����.
     *\param iLen  int, �����ֽ���.
     *\param iWhence  int ,SEEK_XXX,������ʼλ��.
     *\retval  true:�ɹ�,false:ʧ��.
     *\note return false when interupt by singal.
     */
    inline bool ReadLockW(int iOffset, int iLen = 1, int iWhence = SEEK_SET) {
        return Fcntl(F_SETLKW, F_RDLCK, iOffset, iLen, iWhence);
    }

    /*!
     *
     *\brief ReadLockW()���ļ��汾.
     */
    bool ReadLockW(uint64_t iOffset, uint64_t iLen = 1LL, int iWhence = SEEK_SET);

    /*!
     *\brief ���õȴ���д��.һ�����д�������߱��ź��жϲŷ���.
     *\param iOffset int, ����ƫ����.
     *\param iLen int ,�����ֽ���.
     *\param iWhence  int, ������ʼλ��.
     *\retval �ɹ�����true��ʧ�ܷ���false
     *\note return false when interupt by singal.
     */
    inline bool WriteLockW(int iOffset, int iLen = 1, int iWhence = SEEK_SET) {
        return Fcntl(F_SETLKW, F_WRLCK, iOffset, iLen, iWhence);
    }

    /*!
     *\brief WriteLockW()���ļ��汾.
     */
    bool WriteLockW(uint64_t iOffset, uint64_t iLen = 1LL, int iWhence = SEEK_SET);

    /*!
     *\brief ָ���ȴ�ʱ��Ķ���.
     *\param iOffset int ,����ƫ����.
     *\param sec int ,�ȴ���ʱ����.
     *\param iLen int, �����ֽ���.
     *\param iWhence int , ������ʼλ��.
     *\retval �ɹ�����true��ʧ�ܷ���false.
     *\note return false when interupt by singal.
     */
    inline bool ReadLockTimeOut(int iOffset, int sec, int iLen = 1, int iWhence = SEEK_SET) {
        return FcntlTimeOut(F_SETLKW, F_RDLCK, iOffset, iLen, iWhence, sec);
    }

    /*!
     *\brief ReadLockTimeOut()���ļ��汾.
     */
    bool ReadLockTimeOut(uint64_t iOffset, int sec, uint64_t iLen = 1LL, int iWhence = SEEK_SET);

    /*!
     *\brief  ָ�� ʱ��ȴ���д��.
     *\param iOffset int , ����ƫ����.
     *\param sec  int , �ȴ���ʱ����.
     *\param iLen  int, �����ֽ���.
     *\param iWhence int , ������ʼλ��.
     *\retval �ɹ�����true��ʧ�ܷ���false.
     *\note return false when interupt by singal.
     */
    inline bool WriteLockTimeOut(int iOffset, int sec, int iLen = 1, int iWhence = SEEK_SET) {
        return FcntlTimeOut(F_SETLKW, F_WRLCK, iOffset, iLen, iWhence, sec);
    }

    /*!
     *\brief WriteLockTimeOut()���ļ��汾.
     *
     */
    bool WriteLockTimeOut(uint64_t iOffset, int sec, uint64_t iLen = 1LL, int iWhence = SEEK_SET);

    /*!
     *\brief ����.
     *\param iOffset int, ����ƫ����.
     *\param iLen int , �����ֽ���.
     *\param iWhence int , ������ʼλ��.
     *\retval �ɹ�����true��ʧ�ܷ���false.
     *\note return false when interupt by singal.
     */
    inline bool Unlock(int iOffset, int iLen = 1, int iWhence = SEEK_SET) {
        return Fcntl(F_SETLK, F_UNLCK, iOffset, iLen, iWhence);
    }

    /*!
     *\brief Unlock()���ļ��汾.
     *
     */
    bool Unlock(uint64_t iOffset, uint64_t iLen = 1LL, int iWhence = SEEK_SET);

    /*!
     *\brief ������ļ��Ƿ�opened.
     *
     */
    inline bool IsOpened() {
        return m_iFd != -1;
    }
};

}
