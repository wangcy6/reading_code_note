/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package java.util.function;

import java.util.Objects;

/**
 * Represents an operation on a single {@code int}-valued operand that produces
 * an {@code int}-valued result.  This is the primitive type specialization of
 * {@link UnaryOperator} for {@code int}.
 *
 * <p>This is a <a href="package-summary.html">functional interface</a>
 * whose functional method is {@link #applyAsInt(int)}.
 *
 * @see UnaryOperator
 * @since 1.8
 */
/*
 * 函数式接口：IntUnaryOperator
 *
 * int一元操作
 *
 * 参数：int
 * 返回：int
 */
@FunctionalInterface
public interface IntUnaryOperator {
    
    /**
     * Applies this operator to the given operand.
     *
     * @param operand the operand
     *
     * @return the operator result
     */
    int applyAsInt(int operand);
    
    /**
     * Returns a unary operator that always returns its input argument.
     *
     * @return a unary operator that always returns its input argument
     */
    // 标识转换
    static java.util.function.IntUnaryOperator identity() {
        return t -> t;
    }
    
    /**
     * Returns a composed operator that first applies the {@code before}
     * operator to its input, and then applies this operator to the result.
     * If evaluation of either operator throws an exception, it is relayed to
     * the caller of the composed operator.
     *
     * @param before the operator to apply before this operator is applied
     *
     * @return a composed operator that first applies the {@code before}
     * operator and then applies this operator
     *
     * @throws NullPointerException if before is null
     * @see #andThen(java.util.function.IntUnaryOperator)
     */
    // f1.compose(f2)：先执行f2，再执行f1
    default java.util.function.IntUnaryOperator compose(java.util.function.IntUnaryOperator before) {
        Objects.requireNonNull(before);
        return (int v) -> applyAsInt(before.applyAsInt(v));
    }
    
    /**
     * Returns a composed operator that first applies this operator to
     * its input, and then applies the {@code after} operator to the result.
     * If evaluation of either operator throws an exception, it is relayed to
     * the caller of the composed operator.
     *
     * @param after the operator to apply after this operator is applied
     *
     * @return a composed operator that first applies this operator and then
     * applies the {@code after} operator
     *
     * @throws NullPointerException if after is null
     * @see #compose(java.util.function.IntUnaryOperator)
     */
    // f1.andThen(f2)：先执行f1，再执行f2
    default java.util.function.IntUnaryOperator andThen(java.util.function.IntUnaryOperator after) {
        Objects.requireNonNull(after);
        return (int t) -> after.applyAsInt(applyAsInt(t));
    }
}
