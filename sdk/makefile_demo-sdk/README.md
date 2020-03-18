# autoconf 和 automake 生成 Makefile 文件



1) 运行autoscan命令



2) 将configure.scan 文件重命名为configure.in，并修改configure.ac文件



3) 在project目录下新建Makefile.am文件，并在src录下也新建makefile.am文件



4) 在project目录下新建NEWS、 README、 ChangeLog 、AUTHORS文件





5) 运行`libtoolize`生成一些libtool 的文件这些文件跟平台适应性有关系。





6) 运行aclocal,autoheader命令





7) 运行autoconf命令

8) 运行automake -a命令

9) 运行./confiugre脚本



~~~she
autoscan
touch NEWS README ChangeLog AUTHORS
libtoolize
aclocal
autoheader
autoconf
automake -a
./configure
make
~~~





# 代码提交工具

## 一. 文件共享利器——Samba

### 安装配置

~~~shell
#停止firewall
systemctl stop firewalld.service

# 然后添加用户，因为 passdb backend = tdbsam，所以使用pdbedit来增加用户
useradd sharefile
id  sharefile
pdbedit -a -u sharefile # 添加user1账号，并定义其密码

pdbedit -L # 列出所有的账

chown -Rf sharefile:sharefile /home/code
restorecon -Rv /home/code
semanage fcontext -a -t samba_share_t /home/code

yum install -y samba

# 修改配置：
cd /etc/samba
[global]
	workgroup = WORKGROUP
	server string = Samba Server Version %v
	log file = /etc/samba/logs/%m.log
	max log size = 10000
	security = user
	passdb backend = tdbsam
	load printers = yes
	cups options = raw
[share]
	comment = Do not arbitrarily modify the database file
	path = /home/code
	public = yes
	writable = yes
	read only = no
	guest ok = Yes
	guest only = Yes

# 检查配置：
testparm

# 启动samba服务
systemctl restart smb
service smb restart

Started Samba SMB Daemon.

sudo /etc/init.d/samba restart
设置密码
# 使用下面的方法配置用户名和密码，username替换为用户名
smbpasswd -a username
输入password

 
检查错误日志
systemctl status smb.service
journalctl -xe



访问samba服务器

Linux平台
smbclient //IP/共享名  -U 用户名
# 如下面的例子
[root@localhost]# smbclient //192.168.127.88/myshare/  -U user1
smbclient //11.12.123.1234/share/  -U sharefile


smbclient //65.49.221.20/share/  -U sharefile

另外可以使用mount在本地挂载，方法如下：

mount -t cifs //192.168.127.88/myshare /mnt -o username=user1,password=123456


https://www.linuxprobe.com/chapter-12.html

https://www.jianshu.com/p/c4579605a737
从ubuntu 12.10 开始cifs-utils 已取代了smbfs
https://www.jianshu.com/p/f98bc0396f1a
~~~