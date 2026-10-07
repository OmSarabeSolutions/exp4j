/*
 * Copyright 2014 Frank Asseg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.objecthunter.exp4j.internal;

import net.objecthunter.exp4j.operator.Operator;

/**
 * A built-in operator whose math is implemented by {@link Builtins}.
 */
public final class BuiltinOperator extends Operator {

    private final int code;

    /**
     * @param code the {@link Builtins#unary} code if {@code numberOfOperands} is 1,
     *             otherwise the {@link Builtins#binary} code
     */
    public BuiltinOperator(String symbol, int numberOfOperands, boolean leftAssociative, int precedence, int code) {
        super(symbol, numberOfOperands, leftAssociative, precedence);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public double apply(double... args) {
        return getNumOperands() == 1 ? Builtins.unary(args[0], code) : Builtins.binary(args[0], args[1], code);
    }
}
