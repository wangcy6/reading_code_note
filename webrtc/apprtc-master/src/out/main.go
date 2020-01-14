// Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
// Use of this source code is governed by a BSD-style license
// that can be found in the LICENSE file in the root of the source
// tree.

package main

import (
	"collider"
	"flag"
	"log"
)

var tls = flag.Bool("tls", true, "whether TLS is used")
var port = flag.Int("port", 443, "The TCP port that the server listens on")
var roomSrv = flag.String("room-server", "http://10.112.178.190:8080", "The origin of the room server")

func main() {
	flag.Parse()

	log.Printf("Starting collider: tls = %t, port = %d, room-server=%s", *tls, *port, *roomSrv)
	//问题 到目前为止我不知道这个是干什么的，请继续看
	c := collider.NewCollider(*roomSrv)
	c.Run(*port, *tls)
}

/**
apprtc是个web房间服务器, 用户从首页创建房间, 进入房间, 开始音视频聊天.
核心代码其实都在js文件中, 不想使用gae for python框架的话, 也可以自己使用别的语言编写web服务器,
然后把html和js文件拿过来稍微改改就ok了.
collider只是实现了很简单的几个信令,
使用websocket通信.
---------------------
作者：云卷云舒么么哒
来源：CSDN
原文：https://blog.csdn.net/gamereborn/article/details/80200461
版权声明：本文为博主原创文章，转载请附上博文链接！
**/
