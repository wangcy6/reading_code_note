/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Constants written into the Object Serialization Stream.
 *
 * @author  unascribed
 * @since 1.1
 */
// 序列化/反序列化过程中用到的一些常量
public interface ObjectStreamConstants {
    
    /**
     * Magic number that is written to the stream header.
     */
    short STREAM_MAGIC   = (short) 0xaced;
    
    /**
     * Version number that is written to the stream header.
     */
    short STREAM_VERSION = 5;
    
    /* Each item in the stream is preceded by a tag */
    
    /**
     * First tag value.
     */
    byte TC_BASE           = 0x70;
    
    /**
     * Null object reference.
     */
    byte TC_NULL           = (byte) 0x70;   // null
    
    /**
     * Reference to an object already written into the stream.
     */
    byte TC_REFERENCE      = (byte) 0x71;   // 对已写入流中的共享对象的引用
    
    /**
     * new Class Descriptor.
     */
    byte TC_CLASSDESC      = (byte) 0x72;   // 普通对象(非null，非共享，非代理)的序列化描述符
    
    /**
     * new Object.
     */
    byte TC_OBJECT         = (byte) 0x73;
    
    /**
     * new String.
     */
    byte TC_STRING         = (byte) 0x74;   // 小字符串：utf8字节数量在0xFFFF范围内
    
    /**
     * new Array.
     */
    byte TC_ARRAY          = (byte) 0x75;
    
    /**
     * Reference to Class.
     */
    byte TC_CLASS          = (byte) 0x76;   // 类对象的序列化描述符
    
    /**
     * Block of optional data. Byte following tag indicates number
     * of bytes in this block data.
     */
    byte TC_BLOCKDATA      = (byte) 0x77;   // 小数据块标记，数据所占字节数量在byte范围内
    
    /**
     * End of optional block data blocks for an object.
     */
    byte TC_ENDBLOCKDATA   = (byte) 0x78;   // 对象的可选数据块的结尾
    
    /**
     * Reset stream context. All handles written into stream are reset.
     */
    byte TC_RESET          = (byte) 0x79;
    
    /**
     * long Block data. The long following the tag indicates the
     * number of bytes in this block data.
     */
    byte TC_BLOCKDATALONG  = (byte) 0x7A;   // 大数据块标记，数据所占字节数量在int范围内
    
    /**
     * Exception during write.
     */
    byte TC_EXCEPTION      = (byte) 0x7B;
    
    /**
     * Long string.
     */
    byte TC_LONGSTRING     = (byte) 0x7C;   // 大字符串：utf8字节数量超出0xFFFF范围
    
    /**
     * new Proxy Class Descriptor.
     */
    byte TC_PROXYCLASSDESC = (byte) 0x7D;   // 代理对象的序列化描述符
    
    /**
     * new Enum constant.
     *
     * @since 1.5
     */
    byte TC_ENUM           = (byte) 0x7E;
    
    /**
     * Last tag value.
     */
    byte TC_MAX            = (byte) 0x7E;
    
    /**
     * First wire handle to be assigned.
     */
    int baseWireHandle     = 0x7e0000;
    
    
    /******************************************************/
    /* Bit masks for ObjectStreamClass flag.*/
    
    /**
     * Bit mask for ObjectStreamClass flag. Indicates a Serializable class
     * defines its own writeObject method.
     */
    byte SC_WRITE_METHOD   = 0x01;
    
    /**
     * Bit mask for ObjectStreamClass flag. Indicates Externalizable data
     * written in Block Data mode.
     * Added for PROTOCOL_VERSION_2.
     *
     * @see #PROTOCOL_VERSION_2
     * @since 1.2
     */
    byte SC_BLOCK_DATA     = 0x08;
    
    /**
     * Bit mask for ObjectStreamClass flag. Indicates class is Serializable.
     */
    byte SC_SERIALIZABLE   = 0x02;
    
    /**
     * Bit mask for ObjectStreamClass flag. Indicates class is Externalizable.
     */
    byte SC_EXTERNALIZABLE = 0x04;
    
    /**
     * Bit mask for ObjectStreamClass flag. Indicates class is an enum type.
     *
     * @since 1.5
     */
    byte SC_ENUM           = 0x10;
    
    
    /* *******************************************************************/
    /* Security permissions */
    
    /**
     * Enable substitution of one object for another during
     * serialization/deserialization.
     *
     * @see java.io.ObjectOutputStream#enableReplaceObject(boolean)
     * @see java.io.ObjectInputStream#enableResolveObject(boolean)
     * @since 1.2
     */
    SerializablePermission SUBSTITUTION_PERMISSION = new SerializablePermission("enableSubstitution");
    
    /**
     * Enable overriding of readObject and writeObject.
     *
     * @see java.io.ObjectOutputStream#writeObjectOverride(Object)
     * @see java.io.ObjectInputStream#readObjectOverride()
     * @since 1.2
     */
    SerializablePermission SUBCLASS_IMPLEMENTATION_PERMISSION = new SerializablePermission("enableSubclassImplementation");
    
    /**
     * Enable setting the process-wide serial filter.
     *
     * @see java.io.ObjectInputFilter.Config#setSerialFilter(ObjectInputFilter)
     * @since 9
     */
    SerializablePermission SERIAL_FILTER_PERMISSION = new SerializablePermission("serialFilter");
    
    /**
     * A Stream Protocol Version. <p>
     *
     * All externalizable data is written in JDK 1.1 external data format after calling this method.
     * This version is needed to write streams containing Externalizable data that can be read by pre-JDK 1.1.6 JVMs.
     *
     * @see java.io.ObjectOutputStream#useProtocolVersion(int)
     * @since 1.2
     */
    int PROTOCOL_VERSION_1 = 1;
    
    /**
     * A Stream Protocol Version. <p>
     *
     * This protocol is written by JVM 1.2.
     *
     * Externalizable data is written in block data mode and is terminated with TC_ENDBLOCKDATA.
     * Externalizable class descriptor flags has SC_BLOCK_DATA enabled.
     * JVM 1.1.6 and greater can read this format change.
     *
     * Enables writing a nonSerializable class descriptor into the stream.
     * The serialVersionUID of a nonSerializable class is set to 0L.
     *
     * @see java.io.ObjectOutputStream#useProtocolVersion(int)
     * @see #SC_BLOCK_DATA
     * @since 1.2
     */
    int PROTOCOL_VERSION_2 = 2;
}
