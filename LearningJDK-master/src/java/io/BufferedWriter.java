/*
 * Copyright (c) 1996, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.io;

/**
 * Writes text to a character-output stream, buffering characters so as to
 * provide for the efficient writing of single characters, arrays, and strings.
 *
 * <p> The buffer size may be specified, or the default size may be accepted.
 * The default is large enough for most purposes.
 *
 * <p> A newLine() method is provided, which uses the platform's own notion of
 * line separator as defined by the system property {@code line.separator}.
 * Not all platforms use the newline character ('\n') to terminate lines.
 * Calling this method to terminate each output line is therefore preferred to
 * writing a newline character directly.
 *
 * <p> In general, a Writer sends its output immediately to the underlying
 * character or byte stream.  Unless prompt output is required, it is advisable
 * to wrap a BufferedWriter around any Writer whose write() operations may be
 * costly, such as FileWriters and OutputStreamWriters.  For example,
 *
 * <pre>
 * PrintWriter out
 *   = new PrintWriter(new BufferedWriter(new FileWriter("foo.out")));
 * </pre>
 *
 * will buffer the PrintWriter's output to the file.  Without buffering, each
 * invocation of a print() method would cause characters to be converted into
 * bytes that would then be written immediately to the file, which can be very
 * inefficient.
 *
 * @author Mark Reinhold
 * @see PrintWriter
 * @see FileWriter
 * @see OutputStreamWriter
 * @see java.nio.file.Files#newBufferedWriter
 * @since 1.1
 */
// 带有内部缓存区的字符输出流
public class BufferedWriter extends Writer {
    
    private static int defaultCharBufferSize = 8192;
    
    private Writer out; // 最终输出流
    
    private char[] cb;      // 内部缓冲区
    private int nChars;     // 内部缓冲区容量
    private int nextChar;   // 内部缓冲区中下一个可写位置
    
    
    
    /*▼ 构造器 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Creates a buffered character-output stream that uses a default-sized
     * output buffer.
     *
     * @param out A Writer
     */
    public BufferedWriter(Writer out) {
        this(out, defaultCharBufferSize);
    }
    
    /**
     * Creates a new buffered character-output stream that uses an output
     * buffer of the given size.
     *
     * @param out A Writer
     * @param sz  Output-buffer size, a positive integer
     *
     * @throws IllegalArgumentException If {@code sz <= 0}
     */
    public BufferedWriter(Writer out, int sz) {
        super(out);
        
        if(sz<=0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        this.out = out;
        cb = new char[sz];
        nChars = sz;
        nextChar = 0;
    }
    
    /*▲ 构造器 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 写 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Writes a single character.
     *
     * @throws IOException If an I/O error occurs
     */
    // 将指定的字符写入到输出流
    public void write(int c) throws IOException {
        synchronized(lock) {
            ensureOpen();
            
            if(nextChar >= nChars) {
                flushBuffer();
            }
            
            // 向内部缓冲区存入待写字符
            cb[nextChar++] = (char) c;
        }
    }
    
    /**
     * Writes a portion of an array of characters.
     *
     * <p> Ordinarily this method stores characters from the given array into
     * this stream's buffer, flushing the buffer to the underlying stream as
     * needed.  If the requested length is at least as large as the buffer,
     * however, then this method will flush the buffer and write the characters
     * directly to the underlying stream.  Thus redundant
     * {@code BufferedWriter}s will not copy data unnecessarily.
     *
     * @param cbuf A character array
     * @param off  Offset from which to start reading characters
     * @param len  Number of characters to write
     *
     * @throws IndexOutOfBoundsException If {@code off} is negative, or {@code len} is negative,
     *                                   or {@code off + len} is negative or greater than the length
     *                                   of the given array
     * @throws IOException               If an I/O error occurs
     */
    // 将字符数组cbuf中off处起的len个字符写入到输出流
    public void write(char[] cbuf, int off, int len) throws IOException {
        synchronized(lock) {
            ensureOpen();
            
            if((off<0) || (off>cbuf.length) || (len<0) || ((off + len)>cbuf.length) || ((off + len)<0)) {
                throw new IndexOutOfBoundsException();
            } else if(len == 0) {
                return;
            }
            
            // 待写入的字符数量超出了缓冲区容量
            if(len >= nChars) {
                /*
                 * If the request length exceeds the size of the output buffer,
                 * flush the buffer and then write the data directly.
                 * In this way buffered streams will cascade harmlessly.
                 */
                // 刷新缓冲区
                flushBuffer();
                // 直接向包装的输出流写入字符
                out.write(cbuf, off, len);
                return;
            }
            
            int b = off;
            int t = off + len;
            
            // 先尝试向缓冲区存入待写字符，缓冲区满后需要刷新它
            while(b<t) {
                int d = min(nChars - nextChar, t - b);
                System.arraycopy(cbuf, b, cb, nextChar, d);
                b += d;
                nextChar += d;
                if(nextChar >= nChars) {
                    flushBuffer();
                }
            }
        }
    }
    
    /**
     * Writes a portion of a String.
     *
     * @param s   String to be written
     * @param off Offset from which to start reading characters
     * @param len Number of characters to be written
     *
     * @throws IndexOutOfBoundsException If {@code off} is negative,
     *                                   or {@code off + len} is greater than the length
     *                                   of the given string
     * @throws IOException               If an I/O error occurs
     * @implSpec While the specification of this method in the
     * {@linkplain java.io.Writer#write(java.lang.String, int, int) superclass}
     * recommends that an {@link IndexOutOfBoundsException} be thrown
     * if {@code len} is negative or {@code off + len} is negative,
     * the implementation in this class does not throw such an exception in
     * these cases but instead simply writes no characters.
     */
    // 将字符串str中off处起的len个字符写入到输出流
    public void write(String s, int off, int len) throws IOException {
        synchronized(lock) {
            ensureOpen();
            
            int b = off;
            int t = off + len;
            
            while(b<t) {
                int d = min(nChars - nextChar, t - b);
                s.getChars(b, b + d, cb, nextChar);
                b += d;
                nextChar += d;
                if(nextChar >= nChars) {
                    flushBuffer();
                }
            }
        }
    }
    
    
    /**
     * Writes a line separator.  The line separator string is defined by the
     * system property {@code line.separator}, and is not necessarily a single
     * newline ('\n') character.
     *
     * @throws IOException If an I/O error occurs
     */
    // 向输出流写入换行标记
    public void newLine() throws IOException {
        write(System.lineSeparator());
    }
    
    /*▲ 写 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /*▼ 杂项 ████████████████████████████████████████████████████████████████████████████████┓ */
    
    /**
     * Flushes the output buffer to the underlying character stream, without
     * flushing the stream itself.  This method is non-private only so that it
     * may be invoked by PrintStream.
     */
    // 刷新当前的缓冲输出流：将内部缓冲区中的字符写入到最终输出流
    void flushBuffer() throws IOException {
        synchronized(lock) {
            ensureOpen();
            
            if(nextChar == 0) {
                return;
            }
            
            out.write(cb, 0, nextChar);
            
            nextChar = 0;
        }
    }
    
    /**
     * Flushes the stream.
     *
     * @throws IOException If an I/O error occurs
     */
    // 刷新输出流，不仅要刷新内部缓冲区，还要刷新包装的输出流
    public void flush() throws IOException {
        synchronized(lock) {
            flushBuffer();
            out.flush();
        }
    }
    
    // 关闭输入流，关闭其会先刷新内部缓冲区
    @SuppressWarnings("try")
    public void close() throws IOException {
        synchronized(lock) {
            if(out == null) {
                return;
            }
            
            try(Writer w = out) {
                flushBuffer();
            } finally {
                out = null;
                cb = null;
            }
        }
    }
    
    /*▲ 杂项 ████████████████████████████████████████████████████████████████████████████████┛ */
    
    
    
    /** Checks to make sure that the stream has not been closed */
    private void ensureOpen() throws IOException {
        if(out == null) {
            throw new IOException("Stream closed");
        }
    }
    
    /**
     * Our own little min method, to avoid loading java.lang.Math if we've run
     * out of file descriptors and we're trying to print a stack trace.
     */
    private int min(int a, int b) {
        return Math.min(a, b);
    }
    
}
