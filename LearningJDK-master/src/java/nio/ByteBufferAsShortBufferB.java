/*
 * Copyright (c) 2000, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.nio;

// ByteBuffer转为ShortBuffer，使用可读写的缓冲区。采用大端字节序，其他部分与ByteBufferAsShortBufferL相同
class ByteBufferAsShortBufferB extends ShortBuffer {
    
    protected final ByteBuffer bb;
    
    
    /*▼ 构造方法 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    ByteBufferAsShortBufferB(ByteBuffer bb) {   // package-private
        super(-1, 0, bb.remaining() >> 1, bb.remaining() >> 1);
        this.bb = bb;
        // enforce limit == capacity
        int cap = this.capacity();
        this.limit(cap);
        int pos = this.position();
        assert (pos <= cap);
        address = bb.address;
    }
    
    ByteBufferAsShortBufferB(ByteBuffer bb, int mark, int pos, int lim, int cap, long addr) {
        super(mark, pos, lim, cap);
        this.bb = bb;
        address = addr;
        assert address >= bb.address;
    }
    
    /*▲ 构造方法 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 可读写 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public boolean isReadOnly() {
        return false;
    }
    
    public boolean isDirect() {
        return bb.isDirect();
    }
    
    /*▲ 可读写 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 创建新缓冲区，新旧缓冲区共享内部的存储容器 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public ShortBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        long addr = byteOffset(pos);
        return new ByteBufferAsShortBufferB(bb, -1, 0, rem, rem, addr);
    }
    
    public ShortBuffer duplicate() {
        return new ByteBufferAsShortBufferB(bb, this.markValue(), this.position(), this.limit(), this.capacity(), address);
    }
    
    public ShortBuffer asReadOnlyBuffer() {
        return new ByteBufferAsShortBufferRB(bb, this.markValue(), this.position(), this.limit(), this.capacity(), address);
    }
    
    /*▲ 创建新缓冲区，新旧缓冲区共享内部的存储容器 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ get/读取 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public short get() {
        short x = UNSAFE.getShortUnaligned(bb.hb, byteOffset(nextGetIndex()), true);
        return (x);
    }
    
    public short get(int i) {
        short x = UNSAFE.getShortUnaligned(bb.hb, byteOffset(checkIndex(i)), true);
        return (x);
    }
    
    /*▲ get/读取 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ put/写入 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public ShortBuffer put(short x) {
        short y = (x);
        UNSAFE.putShortUnaligned(bb.hb, byteOffset(nextPutIndex()), y, true);
        return this;
    }
    
    public ShortBuffer put(int i, short x) {
        short y = (x);
        UNSAFE.putShortUnaligned(bb.hb, byteOffset(checkIndex(i)), y, true);
        return this;
    }
    
    /*▲ put/写入 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 压缩 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public ShortBuffer compact() {
        int pos = position();
        int lim = limit();
        assert (pos <= lim);
        int rem = (pos <= lim ? lim - pos : 0);
        
        ByteBuffer db = bb.duplicate();
        db.limit(ix(lim));
        db.position(ix(0));
        ByteBuffer sb = db.slice();
        sb.position(pos << 1);
        sb.compact();
        position(rem);
        limit(capacity());
        discardMark();
        return this;
    }
    
    /*▲ 压缩 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 字节顺序 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public ByteOrder order() {
        return ByteOrder.BIG_ENDIAN;
    }
    
    /*▲ 字节顺序 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    protected long byteOffset(long i) {
        return (i << 1) + address;
    }
    
    @Override
    Object base() {
        return bb.hb;
    }
    
    private int ix(int i) {
        int off = (int) (address - bb.address);
        return (i << 1) + off;
    }
}
