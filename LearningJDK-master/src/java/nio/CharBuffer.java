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

// -- This file was mechanically generated: Do not edit! -- //

package java.nio;

import java.io.IOException;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * A char buffer.
 *
 * This class defines four categories of operations upon char buffers:
 * 1.Absolute and relative get and put methods that read and write single chars;
 * 2.Relative bulk get methods that transfer contiguous sequences of chars from this buffer into an array; and
 * 3.Relative bulk put methods that transfer contiguous sequences of chars from a char array, a string, or some other char buffer into this buffer; and
 * 4.A method for compacting a char buffer.
 *
 * Char buffers can be created either by allocation, which allocates space for the buffer's content,
 * by wrapping an existing char array or string into a buffer,
 * or by creating a view of an existing byte buffer.
 *
 * Like a byte buffer, a char buffer is either direct or non-direct.
 * A char buffer created via the wrap methods of this class will be non-direct.
 * A char buffer created as a view of a byte buffer will be direct if, and only if, the byte buffer itself is direct.
 * Whether or not a char buffer is direct may be determined by invoking the isDirect method.
 *
 * This class implements the CharSequence interface so that character buffers may be used wherever character sequences are accepted,
 * for example in the regular-expression package java.util.regex.
 *
 * Methods in this class that do not otherwise have a value to return are specified to return the buffer upon which they are invoked.
 * This allows method invocations to be chained. The sequence of statements
 *
 *     cb.put("text/");
 *     cb.put(subtype);
 *     cb.put("; charset=");
 *     cb.put(enc);
 * can, for example, be replaced by the single statement
 *     cb.put("text/").put(subtype).put("; charset=").put(enc);
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

/*
 * 包装了字符序列的字符缓冲区
 *
 * 常见的非直接缓冲区子类是HeapCharBuffer和StringCharBuffer
 *
 * 以下是所有CharBuffer的6组实现
 *
 * 非直接缓冲区
 *              CharBuffer
 *        ┌─────────┴────────┐
 * StringCharBuffer   HeapCharBuffer
 *                          |
 *                    HeapCharBufferR
 *
 *
 * 直接缓冲区（缓冲区字节序与本地相同）
 * CharBuffer        DirectBuffer
 *     └──────┬──────────┘ │
 *    DirectCharBufferU    │
 *            ├────────────┘
 *    DirectCharBufferRU
 *
 * 直接缓冲区（缓冲区字节序与本地不同）
 * CharBuffer        DirectBuffer
 *     └──────┬──────────┘ │
 *    DirectCharBufferS    │
 *            ├────────────┘
 *    DirectCharBufferRS
 *
 *
 * ByteBuffer转CharBuffer
 *                      CharBuffer
 *           ┌─────────────┘└─────────────┐
 * ByteBufferAsCharBufferB    ByteBufferAsCharBufferL
 *           |                            |
 * ByteBufferAsCharBufferRB   ByteBufferAsCharBufferRL
 */
public abstract class CharBuffer extends Buffer implements Comparable<CharBuffer>, Appendable, CharSequence, Readable {
    
    // These fields are declared here rather than in Heap-X-Buffer in order to reduce the number of virtual method invocations needed to access these values,
    // which is especially costly when coding small buffers.
    final char[] hb;    // Non-null only for heap buffers（并非用在所有子类）
    
    final int offset;   // 寻址偏移量，用于StringCharBuffer/HeapCharBuffer/DirectCharBufferU/DirectCharBufferS这四组实现
    boolean isReadOnly; // 该缓冲区是否只读
    
    
    
    /*▼ 构造方法 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    // Creates a new buffer with the given mark, position, limit, capacity, backing array, and array offset
    CharBuffer(int mark, int pos, int lim, int cap, char[] hb, int offset) {
        super(mark, pos, lim, cap);
        this.hb = hb;
        this.offset = offset;
    }
    
    // Creates a new buffer with the given mark, position, limit, and capacity
    CharBuffer(int mark, int pos, int lim, int cap) { // package-private
        this(mark, pos, lim, cap, null, 0);
    }
    
    /*▲ 构造方法 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 缓冲区属性 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Tells whether or not this char buffer is direct.
     *
     * @return {@code true} if, and only if, this buffer is direct
     */
    // true：该缓冲区是直接缓冲区
    public abstract boolean isDirect();
    
    /*▲ 缓冲区属性 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 标记操作 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * {@inheritDoc}
     */
    // 在当前游标position处设置新的mark（备忘）
    @Override
    public final CharBuffer mark() {
        super.mark();
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    // 设置新的游标position
    @Override
    public final CharBuffer position(int newPosition) {
        super.position(newPosition);
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    // 设置新的上界limit
    @Override
    public final CharBuffer limit(int newLimit) {
        super.limit(newLimit);
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    // 将当前游标position回退到mark（备忘）位置
    @Override
    public final CharBuffer reset() {
        super.reset();
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    // 清理缓冲区，重置标记
    @Override
    public final CharBuffer clear() {
        super.clear();
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    // 修改标记，可以切换缓冲区读/写模式
    @Override
    public final CharBuffer flip() {
        super.flip();
        return this;
    }
    
    /**
     * {@inheritDoc}
     */
    // 丢弃备忘，游标归零
    @Override
    public final CharBuffer rewind() {
        super.rewind();
        return this;
    }
    
    /*▲ 标记操作 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 创建新缓冲区，新旧缓冲区共享内部的存储容器 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Creates a new char buffer whose content is a shared subsequence of this buffer's content.
     *
     * <p> The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     * <p> The new buffer's position will be zero, its capacity and its limit
     * will be the number of chars remaining in this buffer, its mark will be
     * undefined, and its byte order will be identical to that of this buffer.
     *
     * The new buffer will be direct if, and only if, this buffer is direct, and
     * it will be read-only if, and only if, this buffer is read-only.  </p>
     *
     * @return The new char buffer
     */
    // 切片，截取旧缓冲区的【活跃区域】，作为新缓冲区的【原始区域】。两个缓冲区标记独立
    @Override
    public abstract CharBuffer slice();
    
    /**
     * Creates a new char buffer that shares this buffer's content.
     *
     * <p> The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer, and vice
     * versa; the two buffers' position, limit, and mark values will be
     * independent.
     *
     * <p> The new buffer's capacity, limit, position,
     *
     * mark values, and byte order will be identical to those of this buffer.
     *
     * The new buffer will be direct if, and only if, this buffer is direct, and
     * it will be read-only if, and only if, this buffer is read-only.  </p>
     *
     * @return The new char buffer
     */
    // 副本，新缓冲区共享旧缓冲区的【原始区域】，且新旧缓冲区【活跃区域】一致。两个缓冲区标记独立。
    @Override
    public abstract CharBuffer duplicate();
    
    /**
     * Creates a new, read-only char buffer that shares this buffer's
     * content.
     *
     * <p> The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer; the new
     * buffer itself, however, will be read-only and will not allow the shared
     * content to be modified.  The two buffers' position, limit, and mark
     * values will be independent.
     *
     * <p> The new buffer's capacity, limit, position,
     *
     *
     *
     *
     * mark values, and byte order will be identical to those of this buffer.
     *
     *
     * <p> If this buffer is itself read-only then this method behaves in
     * exactly the same way as the {@link #duplicate duplicate} method.  </p>
     *
     * @return The new, read-only char buffer
     */
    // 只读副本，新缓冲区共享旧缓冲区的【原始区域】，且新旧缓冲区【活跃区域】一致。两个缓冲区标记独立。
    public abstract CharBuffer asReadOnlyBuffer();
    
    /**
     * Creates a new character buffer that represents the specified subsequence
     * of this buffer, relative to the current position.
     *
     * <p> The new buffer will share this buffer's content; that is, if the
     * content of this buffer is mutable then modifications to one buffer will
     * cause the other to be modified.  The new buffer's capacity will be that
     * of this buffer, its position will be
     * {@code position()}&nbsp;+&nbsp;{@code start}, and its limit will be
     * {@code position()}&nbsp;+&nbsp;{@code end}.  The new buffer will be
     * direct if, and only if, this buffer is direct, and it will be read-only
     * if, and only if, this buffer is read-only.  </p>
     *
     * @param start The index, relative to the current position, of the first
     *              character in the subsequence; must be non-negative and no larger
     *              than {@code remaining()}
     * @param end   The index, relative to the current position, of the character
     *              following the last character in the subsequence; must be no
     *              smaller than {@code start} and no larger than
     *              {@code remaining()}
     *
     * @return The new character buffer
     *
     * @throws IndexOutOfBoundsException If the preconditions on {@code start} and {@code end}
     *                                   do not hold
     */
    // 副本，新缓冲区的【活跃区域】取自旧缓冲区【活跃区域】的[start，end)部分
    public abstract CharBuffer subSequence(int start, int end);
    
    /*▲ 创建新缓冲区，新旧缓冲区共享内部的存储容器 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ get ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Relative <i>get</i> method.  Reads the char at this buffer's
     * current position, and then increments the position.
     *
     * @return The char at the buffer's current position
     *
     * @throws BufferUnderflowException If the buffer's current position is not smaller than its limit
     */
    // 读取position处（可能需要加offset）的char，然后递增position。
    public abstract char get();
    
    /**
     * Absolute <i>get</i> method.  Reads the char at the given
     * index.
     *
     * @param index The index from which the char will be read
     *
     * @return The char at the given index
     *
     * @throws IndexOutOfBoundsException If {@code index} is negative
     *                                   or not smaller than the buffer's limit
     */
    // 读取index处（可能需要加offset）的char（有越界检查）
    public abstract char get(int index);
    
    /**
     * Absolute <i>get</i> method.  Reads the char at the given
     * index without any validation of the index.
     *
     * @param index The index from which the char will be read
     *
     * @return The char at the given index
     */
    // 返回index处的字符，不经过越界检查
    abstract char getUnchecked(int index);
    
    /**
     * Relative bulk <i>get</i> method.
     *
     * <p> This method transfers chars from this buffer into the given
     * destination array.  If there are fewer chars remaining in the
     * buffer than are required to satisfy the request, that is, if
     * {@code length}&nbsp;{@code >}&nbsp;{@code remaining()}, then no
     * chars are transferred and a {@link BufferUnderflowException} is
     * thrown.
     *
     * <p> Otherwise, this method copies {@code length} chars from this
     * buffer into the given array, starting at the current position of this
     * buffer and at the given offset in the array.  The position of this
     * buffer is then incremented by {@code length}.
     *
     * <p> In other words, an invocation of this method of the form
     * <code>src.get(dst,&nbsp;off,&nbsp;len)</code> has exactly the same effect as
     * the loop
     *
     * <pre>{@code
     *     for (int i = off; i < off + len; i++)
     *         dst[i] = src.get();
     * }</pre>
     *
     * except that it first checks that there are sufficient chars in
     * this buffer and it is potentially much more efficient.
     *
     * @param dst    The array into which chars are to be written
     * @param offset The offset within the array of the first char to be
     *               written; must be non-negative and no larger than
     *               {@code dst.length}
     * @param length The maximum number of chars to be written to the given
     *               array; must be non-negative and no larger than
     *               {@code dst.length - offset}
     *
     * @return This buffer
     *
     * @throws BufferUnderflowException  If there are fewer than {@code length} chars
     *                                   remaining in this buffer
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length}
     *                                   parameters do not hold
     */
    // 复制当前缓存区的length个元素到dst数组offset索引处
    public CharBuffer get(char[] dst, int offset, int length) {
        checkBounds(offset, length, dst.length);
        if(length > remaining())
            throw new BufferUnderflowException();
        int end = offset + length;
        for(int i = offset; i < end; i++)
            dst[i] = get();
        return this;
    }
    
    /**
     * Relative bulk <i>get</i> method.
     *
     * <p> This method transfers chars from this buffer into the given
     * destination array.  An invocation of this method of the form
     * {@code src.get(a)} behaves in exactly the same way as the invocation
     *
     * <pre>
     *     src.get(a, 0, a.length) </pre>
     *
     * @param dst The destination array
     *
     * @return This buffer
     *
     * @throws BufferUnderflowException If there are fewer than {@code length} chars
     *                                  remaining in this buffer
     */
    // 复制原缓存区的内容到dst数组（复制dst数组能容纳的所有内容，不考虑偏移量offset）
    public CharBuffer get(char[] dst) {
        return get(dst, 0, dst.length);
    }
    
    /*▲ get ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ put ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Relative <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes the given char into this buffer at the current
     * position, and then increments the position. </p>
     *
     * @param c The char to be written
     *
     * @return This buffer
     *
     * @throws BufferOverflowException If this buffer's current position is not smaller than its limit
     * @throws ReadOnlyBufferException If this buffer is read-only
     */
    // 向position处（可能需要加offset）写入char，并将position递增
    public abstract CharBuffer put(char c);
    
    /**
     * Absolute <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes the given char into this buffer at the given
     * index. </p>
     *
     * @param index The index at which the char will be written
     * @param c     The char value to be written
     *
     * @return This buffer
     *
     * @throws IndexOutOfBoundsException If {@code index} is negative
     *                                   or not smaller than the buffer's limit
     * @throws ReadOnlyBufferException   If this buffer is read-only
     */
    // 向index处（可能需要加offset）写入char
    public abstract CharBuffer put(int index, char c);
    
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the chars remaining in the given source
     * buffer into this buffer.  If there are more chars remaining in the
     * source buffer than in this buffer, that is, if
     * {@code src.remaining()}&nbsp;{@code >}&nbsp;{@code remaining()},
     * then no chars are transferred and a {@link
     * BufferOverflowException} is thrown.
     *
     * <p> Otherwise, this method copies
     * <i>n</i>&nbsp;=&nbsp;{@code src.remaining()} chars from the given
     * buffer into this buffer, starting at each buffer's current position.
     * The positions of both buffers are then incremented by <i>n</i>.
     *
     * <p> In other words, an invocation of this method of the form
     * {@code dst.put(src)} has exactly the same effect as the loop
     *
     * <pre>
     *     while (src.hasRemaining())
     *         dst.put(src.get()); </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.
     *
     * @param src The source buffer from which chars are to be read;
     *            must not be this buffer
     *
     * @return This buffer
     *
     * @throws BufferOverflowException  If there is insufficient space in this buffer
     *                                  for the remaining chars in the source buffer
     * @throws IllegalArgumentException If the source buffer is this buffer
     * @throws ReadOnlyBufferException  If this buffer is read-only
     */
    // 将源缓冲区src的内容全部写入到当前缓冲区
    public CharBuffer put(CharBuffer src) {
        if(src == this)
            throw createSameBufferException();
        if(isReadOnly())
            throw new ReadOnlyBufferException();
        int n = src.remaining();
        if(n > remaining())
            throw new BufferOverflowException();
        for(int i = 0; i < n; i++)
            put(src.get());
        return this;
    }
    
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the entire content of the given source
     * char array into this buffer.  An invocation of this method of the
     * form {@code dst.put(a)} behaves in exactly the same way as the
     * invocation
     *
     * <pre>
     *     dst.put(a, 0, a.length) </pre>
     *
     * @param src The source array
     *
     * @return This buffer
     *
     * @throws BufferOverflowException If there is insufficient space in this buffer
     * @throws ReadOnlyBufferException If this buffer is read-only
     */
    // 将字符数组src的全部内容写入此缓冲区
    public final CharBuffer put(char[] src) {
        return put(src, 0, src.length);
    }
    
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the entire content of the given source string
     * into this buffer.  An invocation of this method of the form
     * {@code dst.put(s)} behaves in exactly the same way as the invocation
     *
     * <pre>
     *     dst.put(s, 0, s.length()) </pre>
     *
     * @param src The source string
     *
     * @return This buffer
     *
     * @throws BufferOverflowException If there is insufficient space in this buffer
     * @throws ReadOnlyBufferException If this buffer is read-only
     */
    // 将字符串src的全部内容写入此缓冲区
    public final CharBuffer put(String src) {
        return put(src, 0, src.length());
    }
    
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers chars into this buffer from the given
     * source array.  If there are more chars to be copied from the array
     * than remain in this buffer, that is, if
     * {@code length}&nbsp;{@code >}&nbsp;{@code remaining()}, then no
     * chars are transferred and a {@link BufferOverflowException} is
     * thrown.
     *
     * <p> Otherwise, this method copies {@code length} chars from the
     * given array into this buffer, starting at the given offset in the array
     * and at the current position of this buffer.  The position of this buffer
     * is then incremented by {@code length}.
     *
     * <p> In other words, an invocation of this method of the form
     * <code>dst.put(src,&nbsp;off,&nbsp;len)</code> has exactly the same effect as
     * the loop
     *
     * <pre>{@code
     *     for (int i = off; i < off + len; i++)
     *         dst.put(a[i]);
     * }</pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.
     *
     * @param src    The array from which chars are to be read
     * @param offset The offset within the array of the first char to be read;
     *               must be non-negative and no larger than {@code array.length}
     * @param length The number of chars to be read from the given array;
     *               must be non-negative and no larger than
     *               {@code array.length - offset}
     *
     * @return This buffer
     *
     * @throws BufferOverflowException   If there is insufficient space in this buffer
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length}
     *                                   parameters do not hold
     * @throws ReadOnlyBufferException   If this buffer is read-only
     */
    // 从源字符数组src的offset处开始，复制length个元素，写入到当前缓冲区（具体行为由子类实现）
    public CharBuffer put(char[] src, int offset, int length) {
        checkBounds(offset, length, src.length);
        if(length > remaining())
            throw new BufferOverflowException();
        int end = offset + length;
        for(int i = offset; i < end; i++)
            this.put(src[i]);
        return this;
    }
    
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers chars from the given string into this
     * buffer.  If there are more chars to be copied from the string than
     * remain in this buffer, that is, if
     * <code>end&nbsp;-&nbsp;start</code>&nbsp;{@code >}&nbsp;{@code remaining()},
     * then no chars are transferred and a {@link
     * BufferOverflowException} is thrown.
     *
     * <p> Otherwise, this method copies
     * <i>n</i>&nbsp;=&nbsp;{@code end}&nbsp;-&nbsp;{@code start} chars
     * from the given string into this buffer, starting at the given
     * {@code start} index and at the current position of this buffer.  The
     * position of this buffer is then incremented by <i>n</i>.
     *
     * <p> In other words, an invocation of this method of the form
     * <code>dst.put(src,&nbsp;start,&nbsp;end)</code> has exactly the same effect
     * as the loop
     *
     * <pre>{@code
     *     for (int i = start; i < end; i++)
     *         dst.put(src.charAt(i));
     * }</pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient.
     *
     * @param src   The string from which chars are to be read
     * @param start The offset within the string of the first char to be read;
     *              must be non-negative and no larger than
     *              {@code string.length()}
     * @param end   The offset within the string of the last char to be read,
     *              plus one; must be non-negative and no larger than
     *              {@code string.length()}
     *
     * @return This buffer
     *
     * @throws BufferOverflowException   If there is insufficient space in this buffer
     * @throws IndexOutOfBoundsException If the preconditions on the {@code start} and {@code end}
     *                                   parameters do not hold
     * @throws ReadOnlyBufferException   If this buffer is read-only
     */
    // 将字符串src的部分[start, end)内容写入此缓冲区
    public CharBuffer put(String src, int start, int end) {
        checkBounds(start, end - start, src.length());
        if(isReadOnly())
            throw new ReadOnlyBufferException();
        if(end - start > remaining())
            throw new BufferOverflowException();
        for(int i = start; i < end; i++)
            this.put(src.charAt(i));
        return this;
    }
    
    /*▲ put ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ wrap/非直接缓冲区 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Wraps a char array into a buffer.
     *
     * <p> The new buffer will be backed by the given char array;
     * that is, modifications to the buffer will cause the array to be modified
     * and vice versa.  The new buffer's capacity will be
     * {@code array.length}, its position will be {@code offset}, its limit
     * will be {@code offset + length}, its mark will be undefined, and its
     * byte order will be the {@link ByteOrder#nativeOrder native order} of the underlying
     * hardware.
     *
     * Its {@link #array backing array} will be the given array, and
     * its {@link #arrayOffset array offset} will be zero.  </p>
     *
     * @param array  The array that will back the new buffer
     * @param offset The offset of the subarray to be used; must be non-negative and
     *               no larger than {@code array.length}.  The new buffer's position
     *               will be set to this value.
     * @param length The length of the subarray to be used;
     *               must be non-negative and no larger than
     *               {@code array.length - offset}.
     *               The new buffer's limit will be set to {@code offset + length}.
     *
     * @return The new char buffer
     *
     * @throws IndexOutOfBoundsException If the preconditions on the {@code offset} and {@code length}
     *                                   parameters do not hold
     */
    // 包装一个字符数组到buffer（包装一部分）
    public static CharBuffer wrap(char[] array, int offset, int length) {
        try {
            return new HeapCharBuffer(array, offset, length);
        } catch(IllegalArgumentException x) {
            throw new IndexOutOfBoundsException();
        }
    }
    
    /**
     * Wraps a char array into a buffer.
     *
     * <p> The new buffer will be backed by the given char array;
     * that is, modifications to the buffer will cause the array to be modified
     * and vice versa.  The new buffer's capacity and limit will be
     * {@code array.length}, its position will be zero, its mark will be
     * undefined, and its byte order will be the {@link ByteOrder#nativeOrder native order} of the underlying
     * hardware.
     *
     * Its {@link #array backing array} will be the given array, and its
     * {@link #arrayOffset array offset} will be zero.  </p>
     *
     * @param array The array that will back this buffer
     *
     * @return The new char buffer
     */
    // 包装一个字符数组到buffer（包装一部分）
    public static CharBuffer wrap(char[] array) {
        return wrap(array, 0, array.length);
    }
    
    /**
     * Wraps a character sequence into a buffer.
     *
     * <p> The content of the new, read-only buffer will be the content of the
     * given character sequence.  The buffer's capacity will be
     * {@code csq.length()}, its position will be {@code start}, its limit
     * will be {@code end}, and its mark will be undefined.  </p>
     *
     * @param csq   The character sequence from which the new character buffer is to
     *              be created
     * @param start The index of the first character to be used;
     *              must be non-negative and no larger than {@code csq.length()}.
     *              The new buffer's position will be set to this value.
     * @param end   The index of the character following the last character to be
     *              used; must be no smaller than {@code start} and no larger
     *              than {@code csq.length()}.
     *              The new buffer's limit will be set to this value.
     *
     * @return The new character buffer
     *
     * @throws IndexOutOfBoundsException If the preconditions on the {@code start} and {@code end}
     *                                   parameters do not hold
     */
    // 包装一个CharSequence到buffer（包装一部分）
    public static CharBuffer wrap(CharSequence csq, int start, int end) {
        try {
            return new StringCharBuffer(csq, start, end);
        } catch(IllegalArgumentException x) {
            throw new IndexOutOfBoundsException();
        }
    }
    
    /**
     * Wraps a character sequence into a buffer.
     *
     * <p> The content of the new, read-only buffer will be the content of the
     * given character sequence.  The new buffer's capacity and limit will be
     * {@code csq.length()}, its position will be zero, and its mark will be
     * undefined.  </p>
     *
     * @param csq The character sequence from which the new character buffer is to
     *            be created
     *
     * @return The new character buffer
     */
    // 包装一个CharSequence到buffer（包装全部）
    public static CharBuffer wrap(CharSequence csq) {
        return wrap(csq, 0, csq.length());
    }
    
    /*▲ wrap/非直接缓冲区 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ append ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Appends the specified character sequence  to this
     * buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> An invocation of this method of the form {@code dst.append(csq)}
     * behaves in exactly the same way as the invocation
     *
     * <pre>
     *     dst.put(csq.toString()) </pre>
     *
     * <p> Depending on the specification of {@code toString} for the
     * character sequence {@code csq}, the entire sequence may not be
     * appended.  For instance, invoking the {@link CharBuffer#toString()
     * toString} method of a character buffer will return a subsequence whose
     * content depends upon the buffer's position and limit.
     *
     * @param csq The character sequence to append.  If {@code csq} is
     *            {@code null}, then the four characters {@code "null"} are
     *            appended to this character buffer.
     *
     * @return This buffer
     *
     * @throws BufferOverflowException If there is insufficient space in this buffer
     * @throws ReadOnlyBufferException If this buffer is read-only
     * @since 1.5
     */
    // 向buffer中添加CharSequence（添加全部）
    public CharBuffer append(CharSequence csq) {
        if(csq == null)
            return put("null");
        else
            return put(csq.toString());
    }
    
    /**
     * Appends a subsequence of the  specified character sequence  to this
     * buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> An invocation of this method of the form {@code dst.append(csq, start,
     * end)} when {@code csq} is not {@code null}, behaves in exactly the
     * same way as the invocation
     *
     * <pre>
     *     dst.put(csq.subSequence(start, end).toString()) </pre>
     *
     * @param csq The character sequence from which a subsequence will be
     *            appended.  If {@code csq} is {@code null}, then characters
     *            will be appended as if {@code csq} contained the four
     *            characters {@code "null"}.
     *
     * @return This buffer
     *
     * @throws BufferOverflowException   If there is insufficient space in this buffer
     * @throws IndexOutOfBoundsException If {@code start} or {@code end} are negative, {@code start}
     *                                   is greater than {@code end}, or {@code end} is greater than
     *                                   {@code csq.length()}
     * @throws ReadOnlyBufferException   If this buffer is read-only
     * @since 1.5
     */
    // 向buffer中添加CharSequence（添加一部分）
    public CharBuffer append(CharSequence csq, int start, int end) {
        CharSequence cs = (csq == null ? "null" : csq);
        return put(cs.subSequence(start, end).toString());
    }
    
    /**
     * Appends the specified char  to this
     * buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> An invocation of this method of the form {@code dst.append(c)}
     * behaves in exactly the same way as the invocation
     *
     * <pre>
     *     dst.put(c) </pre>
     *
     * @param c The 16-bit char to append
     *
     * @return This buffer
     *
     * @throws BufferOverflowException If there is insufficient space in this buffer
     * @throws ReadOnlyBufferException If this buffer is read-only
     * @since 1.5
     */
    // 向buffer中添加一个字符
    public CharBuffer append(char c) {
        return put(c);
    }
    
    /*▲ append ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 压缩 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Compacts this buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> The chars between the buffer's current position and its limit,
     * if any, are copied to the beginning of the buffer.  That is, the
     * char at index <i>p</i>&nbsp;=&nbsp;{@code position()} is copied
     * to index zero, the char at index <i>p</i>&nbsp;+&nbsp;1 is copied
     * to index one, and so forth until the char at index
     * {@code limit()}&nbsp;-&nbsp;1 is copied to index
     * <i>n</i>&nbsp;=&nbsp;{@code limit()}&nbsp;-&nbsp;{@code 1}&nbsp;-&nbsp;<i>p</i>.
     * The buffer's position is then set to <i>n+1</i> and its limit is set to
     * its capacity.  The mark, if defined, is discarded.
     *
     * <p> The buffer's position is set to the number of chars copied,
     * rather than to zero, so that an invocation of this method can be
     * followed immediately by an invocation of another relative <i>put</i>
     * method. </p>
     *
     * @return This buffer
     *
     * @throws ReadOnlyBufferException If this buffer is read-only
     */
    // 压缩缓冲区，将当前未读完的数据挪到容器起始处，可用于读模式到写模式的切换，但又不丢失之前读入的数据。
    public abstract CharBuffer compact();
    
    /*▲ 压缩 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 字节顺序 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Retrieves this buffer's byte order.
     *
     * <p> The byte order of a char buffer created by allocation or by
     * wrapping an existing {@code char} array is the {@link
     * ByteOrder#nativeOrder native order} of the underlying
     * hardware.  The byte order of a char buffer created as a <a
     * href="ByteBuffer.html#views">view</a> of a byte buffer is that of the
     * byte buffer at the moment that the view is created.  </p>
     *
     * @return This buffer's byte order
     */
    // 返回该缓冲区的字节序（大端还是小端）
    public abstract ByteOrder order();
    
    /* The order or null if the buffer does not cover a memory region, such as StringCharBuffer */
    // 返回‘char’的字节顺序（大端还是小端），在StringCharBuffer中换回null，其他缓冲区中由实现而定。
    abstract ByteOrder charRegionOrder();
    
    /*▲ 字节顺序 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 比较 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Tells whether or not this buffer is equal to another object.
     *
     * <p> Two char buffers are equal if, and only if,
     * <ol>
     * <li><p> They have the same element type,  </p></li>
     * <li><p> They have the same number of remaining elements, and
     * </p></li>
     * <li><p> The two sequences of remaining elements, considered
     * independently of their starting positions, are pointwise equal.
     * </p></li>
     * </ol>
     *
     * <p> A char buffer is not equal to any other type of object.  </p>
     *
     * @param ob The object to which this buffer is to be compared
     *
     * @return {@code true} if, and only if, this buffer is equal to the
     * given object
     */
    public boolean equals(Object ob) {
        if(this == ob)
            return true;
        if(!(ob instanceof CharBuffer))
            return false;
        CharBuffer that = (CharBuffer) ob;
        if(this.remaining() != that.remaining())
            return false;
        return BufferMismatch.mismatch(this, this.position(), that, that.position(), this.remaining()) < 0;
    }
    
    // 比较字符x和字符y，返回x-y的结果
    private static int compare(char x, char y) {
        return Character.compare(x, y);
    }
    
    /**
     * Compares this buffer to another.
     *
     * <p> Two char buffers are compared by comparing their sequences of
     * remaining elements lexicographically, without regard to the starting
     * position of each sequence within its corresponding buffer.
     *
     * Pairs of {@code char} elements are compared as if by invoking {@link Character#compare(char, char)}.
     *
     * <p> A char buffer is not comparable to any other type of object.
     *
     * @return A negative integer, zero, or a positive integer as this buffer
     * is less than, equal to, or greater than the given buffer
     */
    public int compareTo(CharBuffer that) {
        int i = BufferMismatch.mismatch(this, this.position(), that, that.position(), Math.min(this.remaining(), that.remaining()));
        if(i >= 0) {
            return compare(this.get(this.position() + i), that.get(that.position() + i));
        }
        return this.remaining() - that.remaining();
    }
    
    /**
     * Finds and returns the relative index of the first mismatch between this
     * buffer and a given buffer.  The index is relative to the
     * {@link #position() position} of each buffer and will be in the range of
     * 0 (inclusive) up to the smaller of the {@link #remaining() remaining}
     * elements in each buffer (exclusive).
     *
     * <p> If the two buffers share a common prefix then the returned index is
     * the length of the common prefix and it follows that there is a mismatch
     * between the two buffers at that index within the respective buffers.
     * If one buffer is a proper prefix of the other then the returned index is
     * the smaller of the remaining elements in each buffer, and it follows that
     * the index is only valid for the buffer with the larger number of
     * remaining elements.
     * Otherwise, there is no mismatch.
     *
     * @param that The byte buffer to be tested for a mismatch with this buffer
     *
     * @return The relative index of the first mismatch between this and the
     * given buffer, otherwise -1 if no mismatch.
     *
     * @since 11
     */
    // 快速比较两个缓冲区内容，并返回失配元素的索引。返回-1代表缓冲区内容相同。
    public int mismatch(CharBuffer that) {
        int length = Math.min(this.remaining(), that.remaining());
        int r = BufferMismatch.mismatch(this, this.position(), that, that.position(), length);
        return (r == -1 && this.remaining() != that.remaining()) ? length : r;
    }
    
    /*▲ 比较 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ CharSequence ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Returns the length of this character buffer.
     *
     * <p> When viewed as a character sequence, the length of a character
     * buffer is simply the number of characters between the position
     * (inclusive) and the limit (exclusive); that is, it is equivalent to
     * {@code remaining()}. </p>
     *
     * @return The length of this character buffer
     */
    // 返回缓冲区长度（在读/写模式下意义不同）
    public final int length() {
        return remaining();
    }
    
    /**
     * Reads the character at the given index relative to the current
     * position.
     *
     * @param index The index of the character to be read, relative to the position;
     *              must be non-negative and smaller than {@code remaining()}
     *
     * @return The character at index
     * <code>position()&nbsp;+&nbsp;index</code>
     *
     * @throws IndexOutOfBoundsException If the preconditions on {@code index} do not hold
     */
    // 返回position+index处的char，position位置不变
    public final char charAt(int index) {
        return get(position() + checkIndex(index, 1));
    }
    
    // 将当前字符序列转为流序列，序列中每个元素是char的编码值
    @Override
    public IntStream chars() {
        return StreamSupport.intStream(() -> new CharBufferSpliterator(this), Buffer.SPLITERATOR_CHARACTERISTICS, false);
    }
    
    /**
     * Returns a string containing the characters in this buffer.
     *
     * <p> The first character of the resulting string will be the character at
     * this buffer's position, while the last character will be the character
     * at index {@code limit()}&nbsp;-&nbsp;1.  Invoking this method does not
     * change the buffer's position. </p>
     *
     * @return The specified string
     */
    // 将当前字符序列转为字符串输出
    public String toString() {
        return toString(position(), limit());
    }
    
    /*▲ CharSequence ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ Readable ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Attempts to read characters into the specified character buffer.
     * The buffer is used as a repository of characters as-is: the only
     * changes made are the results of a put operation. No flipping or
     * rewinding of the buffer is performed.
     *
     * @param target the buffer to read characters into
     *
     * @return The number of characters added to the buffer, or
     * -1 if this source of characters is at its end
     *
     * @throws IOException             if an I/O error occurs
     * @throws NullPointerException    if target is null
     * @throws ReadOnlyBufferException if target is a read only buffer
     * @since 1.5
     */
    // 读取此缓冲区内容，并写入目标缓冲区target（如果放不下，则只写入放得下的部分）
    public int read(CharBuffer target) throws IOException {
        // 确定可传输的字节数（还剩多少字节未被读取）
        int targetRemaining = target.remaining();
        int remaining = remaining();
        if(remaining == 0)
            return -1;
        int n = Math.min(remaining, targetRemaining);
        int limit = limit();
        // Set source limit to prevent target overflow
        if(targetRemaining < remaining)
            limit(position() + n);
        try {
            if(n > 0)
                target.put(this);
        } finally {
            limit(limit); // restore real limit
        }
        return n;
    }
    
    /*▲ Readable ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ Buffer ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Tells whether or not this buffer is backed by an accessible char
     * array.
     *
     * <p> If this method returns {@code true} then the {@link #array() array}
     * and {@link #arrayOffset() arrayOffset} methods may safely be invoked.
     * </p>
     *
     * @return {@code true} if, and only if, this buffer
     * is backed by an array and is not read-only
     */
    // true：此buffer由可访问的数组实现
    public final boolean hasArray() {
        return (hb != null) && !isReadOnly;
    }
    
    /**
     * Returns the char array that backs this
     * buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Modifications to this buffer's content will cause the returned
     * array's content to be modified, and vice versa.
     *
     * <p> Invoke the {@link #hasArray hasArray} method before invoking this
     * method in order to ensure that this buffer has an accessible backing
     * array.  </p>
     *
     * @return The array that backs this buffer
     *
     * @throws ReadOnlyBufferException       If this buffer is backed by an array but is read-only
     * @throws UnsupportedOperationException If this buffer is not backed by an accessible array
     */
    // 返回该buffer内部的非只读数组
    public final char[] array() {
        if(hb == null)
            throw new UnsupportedOperationException();
        if(isReadOnly)
            throw new ReadOnlyBufferException();
        return hb;
    }
    
    // 返回内部存储结构的引用（一般用于非直接缓存区）
    @Override
    Object base() {
        return hb;
    }
    
    /**
     * Returns the offset within this buffer's backing array of the first element of the buffer  (optional operation).
     * If this buffer is backed by an array then buffer position p corresponds to array index p + arrayOffset().
     *
     * Invoke the hasArray method before invoking this method in order to ensure that this buffer has an accessible backing array.
     *
     * @return The offset within this buffer's array of the first element of the buffer
     *
     * @throws ReadOnlyBufferException       If this buffer is backed by an array but is read-only
     * @throws UnsupportedOperationException If this buffer is not backed by an accessible array
     */
    // 返回此缓冲区中的第一个元素在缓冲区的底层实现数组中的偏移量（可选操作）
    public final int arrayOffset() {
        if(hb == null)
            throw new UnsupportedOperationException();
        if(isReadOnly)
            throw new ReadOnlyBufferException();
        return offset;
    }
    
    /*▲ Buffer ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /**
     * Allocates a new char buffer.
     *
     * <p> The new buffer's position will be zero, its limit will be its capacity, its mark will be undefined, each of its elements will be
     * initialized to zero, and its byte order will be the {@link ByteOrder#nativeOrder native order} of the underlying hardware.
     *
     * It will have a {@link #array backing array}, and its {@link #arrayOffset array offset} will be zero.
     *
     * @param capacity The new buffer's capacity, in chars
     *
     * @return The new char buffer
     *
     * @throws IllegalArgumentException If the {@code capacity} is a negative integer
     */
    // 分配非直接缓冲区HeapCharBuffer：将缓冲区建立在JVM的内存中
    public static CharBuffer allocate(int capacity) {
        if(capacity < 0)
            throw createCapacityException(capacity);
        return new HeapCharBuffer(capacity, capacity);
    }
    
    // 将缓冲区子串转换为字符串返回
    abstract String toString(int start, int end);
    
    /**
     * Returns the current hash code of this buffer.
     *
     * <p> The hash code of a char buffer depends only upon its remaining
     * elements; that is, upon the elements from {@code position()} up to, and
     * including, the element at {@code limit()}&nbsp;-&nbsp;{@code 1}.
     *
     * <p> Because buffer hash codes are content-dependent, it is inadvisable
     * to use buffers as keys in hash maps or similar data structures unless it
     * is known that their contents will not change.  </p>
     *
     * @return The current hash code of this buffer
     */
    public int hashCode() {
        int h = 1;
        int p = position();
        for(int i = limit() - 1; i >= p; i--)
            h = 31 * h + (int) get(i);
        return h;
    }
}
