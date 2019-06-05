/*
 * twemproxy - A fast and lightweight proxy for memcached protocol.
 * Copyright (C) 2011 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _NC_MBUF_H_
#define _NC_MBUF_H_

#include <nc_core.h>

typedef void (*mbuf_copy_t)(struct mbuf *, void *);
//带都节点的单链表 类似stl的
struct mbuf {
    uint32_t           magic;   /* mbuf magic (const) */
    STAILQ_ENTRY(mbuf) next;    /* next mbuf  /* next mbuf 下一块mbuf，代码里所有的mbuf几乎都是以单向链表的形式存储的*/
    uint8_t            *pos;    /* read marker 表示这块mbuf已经读到那个字节了*/
    uint8_t            *last;   /* write marker 表示这块mbuf已经写到哪个字节*/
    
    //data
    uint8_t            *start;   /* start of buffer (const) 表示这块mbuf的起始位置 char×类型*/
    uint8_t            *end;    /* end of buffer (const) 表示这块mbuf的结束位置*/
};
/*mhdr是mbuf单向队列的队列头部*/
STAILQ_HEAD(mhdr, mbuf);
/**
 * 1.mbuf的每一块可以通过配置规定其大小 ，可以说每一块mbuf的大小都是一个固定值，为此在生成时mbuf会去申请一个固定大小的内存，如果这个大小是mbuf_chunk_size，那么end = start + mbuf_chunk_size - sizeof(struct mbuf)，为此start，end，以及magic都是定值。

2.mbuf在申请后一般不会被释放，在使用完后会被放入static struct mhdr free_mbufq这个队列中，一旦要使用mbuf时首先从free_mbufq中取出未使用的mbuf，如果这个队列为空时，它才会去向系统申请新的mbuf。
 * /
#define MBUF_MAGIC      0xdeadbeef
#define MBUF_MIN_SIZE   512
#define MBUF_MAX_SIZE   16777216
#define MBUF_SIZE       16384
#define MBUF_HSIZE      sizeof(struct mbuf)

static inline bool
mbuf_empty(struct mbuf *mbuf)
{
    return mbuf->pos == mbuf->last ? true : false;
}

static inline bool
mbuf_full(struct mbuf *mbuf)
{
    return mbuf->last == mbuf->end ? true : false;
}

void mbuf_init(struct instance *nci);
void mbuf_deinit(void);
struct mbuf *mbuf_get(void);
void mbuf_put(struct mbuf *mbuf);
void mbuf_rewind(struct mbuf *mbuf);
uint32_t mbuf_length(struct mbuf *mbuf);
uint32_t mbuf_size(struct mbuf *mbuf);
size_t mbuf_data_size(void);
void mbuf_insert(struct mhdr *mhdr, struct mbuf *mbuf);
void mbuf_remove(struct mhdr *mhdr, struct mbuf *mbuf);
void mbuf_copy(struct mbuf *mbuf, uint8_t *pos, size_t n);
struct mbuf *mbuf_split(struct mhdr *h, uint8_t *pos, mbuf_copy_t cb, void *cbarg);

#endif
