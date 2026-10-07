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

    /**
     * Name of the {@code static double name(double)} method implementing each unary code, indexed by code.
     * Used by the bytecode generator to call the implementation directly.
     */
    public static final String[] UNARY_METHODS = {
            "sin",   // SIN
            "cos",   // COS
            "tan",   // TAN
            "csc",   // CSC
            "sec",   // SEC
            "cot",   // COT
            "sinh",   // SINH
            "cosh",   // COSH
            "tanh",   // TANH
            "csch",   // CSCH
            "sech",   // SECH
            "coth",   // COTH
            "asin",   // ASIN
            "acos",   // ACOS
            "atan",   // ATAN
            "sqrt",   // SQRT
            "cbrt",   // CBRT
            "abs",   // ABS
            "ceil",   // CEIL
            "floor",   // FLOOR
            "exp",   // EXP
            "expm1",   // EXPM1
            "log10",   // LOG10
            "log2",   // LOG2
            "log",   // LOG
            "log1p",   // LOG1P
            "signum",   // SIGNUM
            "toRadian",   // TO_RADIAN
            "toDegree",   // TO_DEGREE
            "negate",   // NEGATE
            "identity",   // IDENTITY
    };

    /**
     * Name of the {@code static double name(double, double)} method implementing each binary code, indexed by code.
     */
    public static final String[] BINARY_METHODS = {
            "add",   // ADD
            "subtract",   // SUBTRACT
            "multiply",   // MULTIPLY
            "divide",   // DIVIDE
            "power",   // POWER
            "modulo",   // MODULO
            "logb",   // LOGB
    };

    private Builtins() {
    }

    /*
     * Dispatch by code, used by the interpreter and by BuiltinFunction/BuiltinOperator.apply().
     * Every case only calls the implementing method below, which keeps this switch small.
     */

    public static double unary(final double a, final int code) {
        switch (code) {
            case SIN:
                return sin(a);
            case COS:
                return cos(a);
            case TAN:
                return tan(a);
            case CSC:
                return csc(a);
            case SEC:
                return sec(a);
            case COT:
                return cot(a);
            case SINH:
                return sinh(a);
            case COSH:
                return cosh(a);
            case TANH:
                return tanh(a);
            case CSCH:
                return csch(a);
            case SECH:
                return sech(a);
            case COTH:
                return coth(a);
            case ASIN:
                return asin(a);
            case ACOS:
                return acos(a);
            case ATAN:
                return atan(a);
            case SQRT:
                return sqrt(a);
            case CBRT:
                return cbrt(a);
            case ABS:
                return abs(a);
            case CEIL:
                return ceil(a);
            case FLOOR:
                return floor(a);
            case EXP:
                return exp(a);
            case EXPM1:
                return expm1(a);
            case LOG10:
                return log10(a);
            case LOG2:
                return log2(a);
            case LOG:
                return log(a);
            case LOG1P:
                return log1p(a);
            case SIGNUM:
                return signum(a);
            case TO_RADIAN:
                return toRadian(a);
            case TO_DEGREE:
                return toDegree(a);
            case NEGATE:
                return negate(a);
            case IDENTITY:
                return identity(a);
            default:
                throw unknown(code);
        }
    }

    public static double binary(final double a, final double b, final int code) {
        switch (code) {
            case ADD:
                return add(a, b);
            case SUBTRACT:
                return subtract(a, b);
            case MULTIPLY:
                return multiply(a, b);
            case DIVIDE:
                return divide(a, b);
            case POWER:
                return power(a, b);
            case MODULO:
                return modulo(a, b);
            case LOGB:
                return logb(a, b);
            default:
                throw unknown(code);
        }
    }

    /*
     * The implementations: the single definition of each built-in. They are deliberately tiny
     * so that the JIT inlines them at every call site, including from generated bytecode.
     */

    public static double sin(final double a) {
        return Math.sin(a);
    }

    public static double cos(final double a) {
        return Math.cos(a);
    }

    public static double tan(final double a) {
        return Math.tan(a);
    }

    public static double csc(final double a) {
        final double sin = Math.sin(a);
        if (sin == 0d) {
            throw new ArithmeticException("Division by zero in cosecant!");
        }
        return 1d / sin;
    }

    public static double sec(final double a) {
        final double cos = Math.cos(a);
        if (cos == 0d) {
            throw new ArithmeticException("Division by zero in secant!");
        }
        return 1d / cos;
    }

    public static double cot(final double a) {
        final double tan = Math.tan(a);
        if (tan == 0d) {
            throw new ArithmeticException("Division by zero in cotangent!");
        }
        return 1d / tan;
    }

    public static double sinh(final double a) {
        return Math.sinh(a);
    }

    public static double cosh(final double a) {
        return Math.cosh(a);
    }

    public static double tanh(final double a) {
        return Math.tanh(a);
    }

    public static double csch(final double a) {
        //this would throw an ArithmeticException later as sinh(0) = 0
        if (a == 0d) {
            return 0;
        }
        return 1d / Math.sinh(a);
    }

    public static double sech(final double a) {
        return 1d / Math.cosh(a);
    }

    public static double coth(final double a) {
        return Math.cosh(a) / Math.sinh(a);
    }

    public static double asin(final double a) {
        return Math.asin(a);
    }

    public static double acos(final double a) {
        return Math.acos(a);
    }

    public static double atan(final double a) {
        return Math.atan(a);
    }

    public static double sqrt(final double a) {
        return Math.sqrt(a);
    }

    public static double cbrt(final double a) {
        return Math.cbrt(a);
    }

    public static double abs(final double a) {
        return Math.abs(a);
    }

    public static double ceil(final double a) {
        return Math.ceil(a);
    }

    public static double floor(final double a) {
        return Math.floor(a);
    }

    public static double exp(final double a) {
        return Math.exp(a);
    }

    public static double expm1(final double a) {
        return Math.expm1(a);
    }

    public static double log10(final double a) {
        return Math.log10(a);
    }

    public static double log2(final double a) {
        return Math.log(a) / Math.log(2d);
    }

    public static double log(final double a) {
        return Math.log(a);
    }

    public static double log1p(final double a) {
        return Math.log1p(a);
    }

    public static double signum(final double a) {
        if (a > 0) {
            return 1;
        } else if (a < 0) {
            return -1;
        } else {
            return 0;
        }
    }

    public static double toRadian(final double a) {
        return Math.toRadians(a);
    }

    public static double toDegree(final double a) {
        return Math.toDegrees(a);
    }

    public static double negate(final double a) {
        return -a;
    }

    public static double identity(final double a) {
        return a;
    }

    public static double add(final double a, final double b) {
        return a + b;
    }

    public static double subtract(final double a, final double b) {
        return a - b;
    }

    public static double multiply(final double a, final double b) {
        return a * b;
    }

    public static double divide(final double a, final double b) {
        if (b == 0d) {
            throw new ArithmeticException("Division by zero!");
        }
        return a / b;
    }

    public static double power(final double a, final double b) {
        return Math.pow(a, b);
    }

    public static double modulo(final double a, final double b) {
        if (b == 0d) {
            throw new ArithmeticException("Division by zero!");
        }
        return a % b;
    }

    public static double logb(final double a, final double b) {
        return Math.log(b) / Math.log(a);
    }

    private static IllegalArgumentException unknown(final int code) {
        return new IllegalArgumentException("Unknown built-in " + code);
    }
}
