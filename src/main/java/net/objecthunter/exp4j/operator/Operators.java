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
package net.objecthunter.exp4j.operator;

import net.objecthunter.exp4j.internal.BuiltinOperator;
import net.objecthunter.exp4j.internal.Builtins;

public abstract class Operators {
    private static final int INDEX_ADDITION = 0;
    private static final int INDEX_SUBTRACTION = 1;
    private static final int INDEX_MULTIPLICATION = 2;
    private static final int INDEX_DIVISION = 3;
    private static final int INDEX_POWER = 4;
    private static final int INDEX_MODULO = 5;
    private static final int INDEX_UNARY_MINUS = 6;
    private static final int INDEX_UNARY_PLUS = 7;

    private static final Operator[] BUILT_IN_OPERATORS = new Operator[8];

    static {
        BUILT_IN_OPERATORS[INDEX_ADDITION] = new BuiltinOperator("+", 2, true, Operator.PRECEDENCE_ADDITION, Builtins.ADD);
        BUILT_IN_OPERATORS[INDEX_SUBTRACTION] = new BuiltinOperator("-", 2, true, Operator.PRECEDENCE_ADDITION, Builtins.SUBTRACT);
        BUILT_IN_OPERATORS[INDEX_UNARY_MINUS] = new BuiltinOperator("-", 1, false, Operator.PRECEDENCE_UNARY_MINUS, Builtins.NEGATE);
        BUILT_IN_OPERATORS[INDEX_UNARY_PLUS] = new BuiltinOperator("+", 1, false, Operator.PRECEDENCE_UNARY_PLUS, Builtins.IDENTITY);
        BUILT_IN_OPERATORS[INDEX_MULTIPLICATION] = new BuiltinOperator("*", 2, true, Operator.PRECEDENCE_MULTIPLICATION, Builtins.MULTIPLY);
        BUILT_IN_OPERATORS[INDEX_DIVISION] = new BuiltinOperator("/", 2, true, Operator.PRECEDENCE_DIVISION, Builtins.DIVIDE);
        BUILT_IN_OPERATORS[INDEX_POWER] = new BuiltinOperator("^", 2, false, Operator.PRECEDENCE_POWER, Builtins.POWER);
        BUILT_IN_OPERATORS[INDEX_MODULO] = new BuiltinOperator("%", 2, true, Operator.PRECEDENCE_MODULO, Builtins.MODULO);
    }

    public static Operator getBuiltinOperator(final char symbol, final int numArguments) {
        switch (symbol) {
            case '+':
                if (numArguments != 1) {
                    return BUILT_IN_OPERATORS[INDEX_ADDITION];
                }

                return BUILT_IN_OPERATORS[INDEX_UNARY_PLUS];
            case '-':
                if (numArguments != 1) {
                    return BUILT_IN_OPERATORS[INDEX_SUBTRACTION];
                }

                return BUILT_IN_OPERATORS[INDEX_UNARY_MINUS];
            case '*':
                return BUILT_IN_OPERATORS[INDEX_MULTIPLICATION];
            case '÷':
            case '/':
                return BUILT_IN_OPERATORS[INDEX_DIVISION];
            case '^':
                return BUILT_IN_OPERATORS[INDEX_POWER];
            case '%':
                return BUILT_IN_OPERATORS[INDEX_MODULO];
            default:
                return null;
        }
    }

}
