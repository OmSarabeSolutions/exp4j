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

/**
 * Single implementation of the math behind every built-in function and operator.
 * <p>
 * Both the {@code Function}/{@code Operator} objects returned by {@code Functions} and
 * {@code Operators} and the compiled evaluator in {@code Expression} delegate here, so
 * the semantics of a built-in are defined exactly once. This package is not exported
 * by the module and is not part of the public API.
 */
public final class Builtins {

    /* unary codes */
    public static final int SIN = 0;
    public static final int COS = 1;
    public static final int TAN = 2;
    public static final int CSC = 3;
    public static final int SEC = 4;
    public static final int COT = 5;
    public static final int SINH = 6;
    public static final int COSH = 7;
    public static final int TANH = 8;
    public static final int CSCH = 9;
    public static final int SECH = 10;
    public static final int COTH = 11;
    public static final int ASIN = 12;
    public static final int ACOS = 13;
    public static final int ATAN = 14;
    public static final int SQRT = 15;
    public static final int CBRT = 16;
    public static final int ABS = 17;
    public static final int CEIL = 18;
    public static final int FLOOR = 19;
    public static final int EXP = 20;
    public static final int EXPM1 = 21;
    public static final int LOG10 = 22;
    public static final int LOG2 = 23;
    public static final int LOG = 24;
    public static final int LOG1P = 25;
    public static final int SIGNUM = 26;
    public static final int TO_RADIAN = 27;
    public static final int TO_DEGREE = 28;
    public static final int NEGATE = 29;
    public static final int IDENTITY = 30;

    /* binary codes */
    public static final int ADD = 0;
    public static final int SUBTRACT = 1;
    public static final int MULTIPLY = 2;
    public static final int DIVIDE = 3;
    public static final int POWER = 4;
    public static final int MODULO = 5;
    public static final int LOGB = 6;

    private Builtins() {
    }

    public static double unary(final int code, final double a) {
        switch (code) {
            case SIN:
                return Math.sin(a);
            case COS:
                return Math.cos(a);
            case TAN:
                return Math.tan(a);
            case CSC:
                return csc(a);
            case SEC:
                return sec(a);
            case COT:
                return cot(a);
            case SINH:
                return Math.sinh(a);
            case COSH:
                return Math.cosh(a);
            case TANH:
                return Math.tanh(a);
            case CSCH:
                return csch(a);
            case SECH:
                return 1d / Math.cosh(a);
            case COTH:
                return Math.cosh(a) / Math.sinh(a);
            case ASIN:
                return Math.asin(a);
            case ACOS:
                return Math.acos(a);
            case ATAN:
                return Math.atan(a);
            case SQRT:
                return Math.sqrt(a);
            case CBRT:
                return Math.cbrt(a);
            case ABS:
                return Math.abs(a);
            case CEIL:
                return Math.ceil(a);
            case FLOOR:
                return Math.floor(a);
            case EXP:
                return Math.exp(a);
            case EXPM1:
                return Math.expm1(a);
            case LOG10:
                return Math.log10(a);
            case LOG2:
                return Math.log(a) / Math.log(2d);
            case LOG:
                return Math.log(a);
            case LOG1P:
                return Math.log1p(a);
            case SIGNUM:
                return signum(a);
            case TO_RADIAN:
                return Math.toRadians(a);
            case TO_DEGREE:
                return Math.toDegrees(a);
            case NEGATE:
                return -a;
            case IDENTITY:
                return a;
            default:
                throw unknown(code);
        }
    }

    public static double binary(final int code, final double a, final double b) {
        switch (code) {
            case ADD:
                return a + b;
            case SUBTRACT:
                return a - b;
            case MULTIPLY:
                return a * b;
            case DIVIDE:
                return divide(a, b);
            case POWER:
                return Math.pow(a, b);
            case MODULO:
                return modulo(a, b);
            case LOGB:
                return Math.log(b) / Math.log(a);
            default:
                throw unknown(code);
        }
    }

    /* Multi-statement cases live in their own methods so that unary() stays below the JIT's
       inlining size limit (FreqInlineSize, 325 bytes of bytecode); inlining it into the
       evaluator lets the JIT fold the code dispatch, which is the main evaluation cost. */

    private static double csc(final double a) {
        final double sin = Math.sin(a);
        if (sin == 0d) {
            throw new ArithmeticException("Division by zero in cosecant!");
        }
        return 1d / sin;
    }

    private static double sec(final double a) {
        final double cos = Math.cos(a);
        if (cos == 0d) {
            throw new ArithmeticException("Division by zero in secant!");
        }
        return 1d / cos;
    }

    private static double cot(final double a) {
        final double tan = Math.tan(a);
        if (tan == 0d) {
            throw new ArithmeticException("Division by zero in cotangent!");
        }
        return 1d / tan;
    }

    private static double csch(final double a) {
        //this would throw an ArithmeticException later as sinh(0) = 0
        if (a == 0d) {
            return 0;
        }
        return 1d / Math.sinh(a);
    }

    private static double signum(final double a) {
        if (a > 0) {
            return 1;
        } else if (a < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    private static double divide(final double a, final double b) {
        if (b == 0d) {
            throw new ArithmeticException("Division by zero!");
        }
        return a / b;
    }

    private static double modulo(final double a, final double b) {
        if (b == 0d) {
            throw new ArithmeticException("Division by zero!");
        }
        return a % b;
    }

    private static IllegalArgumentException unknown(final int code) {
        return new IllegalArgumentException("Unknown built-in " + code);
    }
}
