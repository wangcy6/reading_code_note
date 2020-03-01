Evernote SDK for C++
=========================================

Evernote API version 1.25

Overview
--------
This SDK contains wrapper code used to call the Evernote Cloud API from C++ apps.

Prerequisites
-------------
In order to use the code in this SDK, you need to obtain an API key from http://dev.evernote.com/documentation/cloud. You'll also find full API documentation on that page.

In order to run the sample code, you need a user account on the sandbox service where you will do your development. Sign up for an account at https://sandbox.evernote.com/Registration.action 







# autotools系列工具自动生成 Makefile 

http://www.gnu.org/software/automake/manual/automake.html#Hello-World

```c++
代码目录：
cat src/main.c
```

1) 运行autoscan命令，自动生成configure.scan文件

2) 将configure.scan 文件重命名为configure.ac，并修改configure.ac文件

3) 在project目录下新建Makefile.am文件，src目录新建 Makefile.am文件

4) 在project目录下新建NEWS、 README、 ChangeLog 、AUTHORS文件

5) 将/usr/share/automake-1.X/目录下的depcomp拷贝到本目录下

6) 运行aclocal命令

7) **运行autoconf命令生成configure可执行文件**

8) **运行automake命令, 生成Makefile.in文件**

9) **运行configure, 生成Makfefile文件**



~~~shell

configure.ac
AC_INIT([evernote-sdk-cpp], [1.0], [wang_cyi@163.com])
AM_INIT_AUTOMAKE
AC_CONFIG_FILES([
 Makefile
 src/Makefile
])
AC_OUTPUT


Makefile.am
SUBDIRS = src

src/Makefile.am
AUTOMAKE_OPTIONS=foreign
bin_PROGRAMS=money
money_SOURCES=main.cpp

cp /usr/share/automake-1.15/depcomp .
autoconf
automake
./configure

~~~





# 三  https://github.com/apache/thrift









http://www.gnu.org/software/automake/manual/automake.html#Modernize-AM_005fINIT_005fAUTOMAKE-invocation

https://www.worldhello.net/2010/04/07/954.html

https://www.ibm.com/developerworks/cn/linux/l-makefile/index.html

https://www.jianshu.com/p/2f5e586c3402

http://www.gnu.org/software/automake/manual/automake.html#Hello-World





##
https://github.com/roop/NotekeeperOpen/blob/master/src/cloud/evernote/evernotesync/evernoteaccess.cpp
https://dev.twsiyuan.com/2017/06/evernote-sdk-for-golang.html
https://dev.yinxiang.com/doc/articles/core_concepts.php
https://dev.yinxiang.com/doc/start/python.php
https://www.jianshu.com/p/bda26798f3b3

https://dev.yinxiang.com/doc/start/python.php
https://github.com/evernote/evernote-sdk-python3