/*
 * Copyright (c) 2000, 2018, Oracle and/or its affiliates. All rights reserved.
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

import sun.nio.ch.DirectBuffer;

// 只读、直接缓冲区，是DirectFloatBufferU的只读版本
class DirectFloatBufferRU extends DirectFloatBufferU implements DirectBuffer {
    
    /*▼ 构造方法 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    // For duplicates and slices
    DirectFloatBufferRU(DirectBuffer db, int mark, int pos, int lim, int cap, int off) {
        super(db, mark, pos, lim, cap, off);
        this.isReadOnly = true;
    }
    
    /*▲ 构造方法 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 只读/直接 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public boolean isReadOnly() {
        return true;
    }
    
    public boolean isDirect() {
        return true;
    }
    
    /*▲ 只读/直接 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 创建新缓冲区，新旧缓冲区共享内部的存储容器 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public FloatBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        assert (pos<=lim);
        int rem = (pos<=lim ? lim - pos : 0);
        int off = (pos << 2);
        assert (off >= 0);
        return new DirectFloatBufferRU(this, -1, 0, rem, rem, off);
    }
    
    public FloatBuffer duplicate() {
        return new DirectFloatBufferRU(this, this.markValue(), this.position(), this.limit(), this.capacity(), 0);
    }
    
    public FloatBuffer asReadOnlyBuffer() {
        return duplicate();
    }
    
    /*▲ 创建新缓冲区，新旧缓冲区共享内部的存储容器 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 只读缓冲区，禁止写入 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public FloatBuffer put(float x) {
        throw new ReadOnlyBufferException();
    }
    
    public FloatBuffer put(int i, float x) {
        throw new ReadOnlyBufferException();
    }
    
    public FloatBuffer put(FloatBuffer src) {
        throw new ReadOnlyBufferException();
    }
    
    public FloatBuffer put(float[] src, int offset, int length) {
        throw new ReadOnlyBufferException();
    }
    
    /*▲ 只读缓冲区，禁止写入 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 禁止压缩，因为禁止写入，压缩没意义 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public FloatBuffer compact() {
        throw new ReadOnlyBufferException();
    }
    
    /*▲ 禁止压缩，因为禁止写入，压缩没意义 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 字节顺序 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    public ByteOrder order() {
        return ((ByteOrder.nativeOrder() != ByteOrder.BIG_ENDIAN) ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
    }
    
    /*▲ 字节顺序 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    @Override
    Object base() {
        return null;
    }
}
