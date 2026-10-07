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
package net.objecthunter.exp4j.function;

import net.objecthunter.exp4j.internal.BuiltinFunction;
import net.objecthunter.exp4j.internal.Builtins;

/**
 * Class representing the builtin functions available for use in expressions
 */
public class Functions {

    private static final int INDEX_SIN = 0;
    private static final int INDEX_COS = 1;
    private static final int INDEX_TAN = 2;
    private static final int INDEX_CSC = 3;
    private static final int INDEX_SEC = 4;
    private static final int INDEX_COT = 5;
    private static final int INDEX_SINH = 6;
    private static final int INDEX_COSH = 7;
    private static final int INDEX_TANH = 8;
    private static final int INDEX_CSCH = 9;
    private static final int INDEX_SECH = 10;
    private static final int INDEX_COTH = 11;
    private static final int INDEX_ASIN = 12;
    private static final int INDEX_ACOS = 13;
    private static final int INDEX_ATAN = 14;
    private static final int INDEX_SQRT = 15;
    private static final int INDEX_CBRT = 16;
    private static final int INDEX_ABS = 17;
    private static final int INDEX_CEIL = 18;
    private static final int INDEX_FLOOR = 19;
    private static final int INDEX_POW = 20;
    private static final int INDEX_EXP = 21;
    private static final int INDEX_EXPM1 = 22;
    private static final int INDEX_LOG10 = 23;
    private static final int INDEX_LOG2 = 24;
    private static final int INDEX_LOG = 25;
    private static final int INDEX_LOG1P = 26;
    private static final int INDEX_LOGB = 27;
    private static final int INDEX_SGN = 28;
    private static final int INDEX_TO_RADIAN = 29;
    private static final int INDEX_TO_DEGREE = 30;

    private static final Function[] BUILT_IN_FUNCTIONS = new Function[31];

    static {
        BUILT_IN_FUNCTIONS[INDEX_SIN] = new BuiltinFunction("sin", 1, Builtins.SIN);
        BUILT_IN_FUNCTIONS[INDEX_COS] = new BuiltinFunction("cos", 1, Builtins.COS);
        BUILT_IN_FUNCTIONS[INDEX_TAN] = new BuiltinFunction("tan", 1, Builtins.TAN);
        BUILT_IN_FUNCTIONS[INDEX_CSC] = new BuiltinFunction("csc", 1, Builtins.CSC);
        BUILT_IN_FUNCTIONS[INDEX_SEC] = new BuiltinFunction("sec", 1, Builtins.SEC);
        BUILT_IN_FUNCTIONS[INDEX_COT] = new BuiltinFunction("cot", 1, Builtins.COT);
        BUILT_IN_FUNCTIONS[INDEX_SINH] = new BuiltinFunction("sinh", 1, Builtins.SINH);
        BUILT_IN_FUNCTIONS[INDEX_COSH] = new BuiltinFunction("cosh", 1, Builtins.COSH);
        BUILT_IN_FUNCTIONS[INDEX_TANH] = new BuiltinFunction("tanh", 1, Builtins.TANH);
        BUILT_IN_FUNCTIONS[INDEX_CSCH] = new BuiltinFunction("csch", 1, Builtins.CSCH);
        BUILT_IN_FUNCTIONS[INDEX_SECH] = new BuiltinFunction("sech", 1, Builtins.SECH);
        BUILT_IN_FUNCTIONS[INDEX_COTH] = new BuiltinFunction("coth", 1, Builtins.COTH);
        BUILT_IN_FUNCTIONS[INDEX_ASIN] = new BuiltinFunction("asin", 1, Builtins.ASIN);
        BUILT_IN_FUNCTIONS[INDEX_ACOS] = new BuiltinFunction("acos", 1, Builtins.ACOS);
        BUILT_IN_FUNCTIONS[INDEX_ATAN] = new BuiltinFunction("atan", 1, Builtins.ATAN);
        BUILT_IN_FUNCTIONS[INDEX_SQRT] = new BuiltinFunction("sqrt", 1, Builtins.SQRT);
        BUILT_IN_FUNCTIONS[INDEX_CBRT] = new BuiltinFunction("cbrt", 1, Builtins.CBRT);
        BUILT_IN_FUNCTIONS[INDEX_ABS] = new BuiltinFunction("abs", 1, Builtins.ABS);
        BUILT_IN_FUNCTIONS[INDEX_CEIL] = new BuiltinFunction("ceil", 1, Builtins.CEIL);
        BUILT_IN_FUNCTIONS[INDEX_FLOOR] = new BuiltinFunction("floor", 1, Builtins.FLOOR);
        BUILT_IN_FUNCTIONS[INDEX_EXP] = new BuiltinFunction("exp", 1, Builtins.EXP);
        BUILT_IN_FUNCTIONS[INDEX_EXPM1] = new BuiltinFunction("expm1", 1, Builtins.EXPM1);
        BUILT_IN_FUNCTIONS[INDEX_LOG10] = new BuiltinFunction("log10", 1, Builtins.LOG10);
        BUILT_IN_FUNCTIONS[INDEX_LOG2] = new BuiltinFunction("log2", 1, Builtins.LOG2);
        BUILT_IN_FUNCTIONS[INDEX_LOG] = new BuiltinFunction("log", 1, Builtins.LOG);
        BUILT_IN_FUNCTIONS[INDEX_LOG1P] = new BuiltinFunction("log1p", 1, Builtins.LOG1P);
        BUILT_IN_FUNCTIONS[INDEX_SGN] = new BuiltinFunction("signum", 1, Builtins.SIGNUM);
        BUILT_IN_FUNCTIONS[INDEX_TO_RADIAN] = new BuiltinFunction("toradian", 1, Builtins.TO_RADIAN);
        BUILT_IN_FUNCTIONS[INDEX_TO_DEGREE] = new BuiltinFunction("todegree", 1, Builtins.TO_DEGREE);
        BUILT_IN_FUNCTIONS[INDEX_POW] = new BuiltinFunction("pow", 2, Builtins.POWER);
        BUILT_IN_FUNCTIONS[INDEX_LOGB] = new BuiltinFunction("logb", 2, Builtins.LOGB);
    }

    /**
     * Get the builtin function for a given name
     *
     * @param name te name of the function
     * @return a Function instance
     */
    public static Function getBuiltinFunction(final String name) {

        switch (name) {
            case "sin":
                return BUILT_IN_FUNCTIONS[INDEX_SIN];
            case "cos":
                return BUILT_IN_FUNCTIONS[INDEX_COS];
            case "tan":
                return BUILT_IN_FUNCTIONS[INDEX_TAN];
            case "cot":
                return BUILT_IN_FUNCTIONS[INDEX_COT];
            case "asin":
                return BUILT_IN_FUNCTIONS[INDEX_ASIN];
            case "acos":
                return BUILT_IN_FUNCTIONS[INDEX_ACOS];
            case "atan":
                return BUILT_IN_FUNCTIONS[INDEX_ATAN];
            case "sinh":
                return BUILT_IN_FUNCTIONS[INDEX_SINH];
            case "cosh":
                return BUILT_IN_FUNCTIONS[INDEX_COSH];
            case "tanh":
                return BUILT_IN_FUNCTIONS[INDEX_TANH];
            case "abs":
                return BUILT_IN_FUNCTIONS[INDEX_ABS];
            case "log":
                return BUILT_IN_FUNCTIONS[INDEX_LOG];
            case "log10":
                return BUILT_IN_FUNCTIONS[INDEX_LOG10];
            case "log2":
                return BUILT_IN_FUNCTIONS[INDEX_LOG2];
            case "log1p":
                return BUILT_IN_FUNCTIONS[INDEX_LOG1P];
            case "ceil":
                return BUILT_IN_FUNCTIONS[INDEX_CEIL];
            case "floor":
                return BUILT_IN_FUNCTIONS[INDEX_FLOOR];
            case "sqrt":
                return BUILT_IN_FUNCTIONS[INDEX_SQRT];
            case "cbrt":
                return BUILT_IN_FUNCTIONS[INDEX_CBRT];
            case "pow":
                return BUILT_IN_FUNCTIONS[INDEX_POW];
            case "exp":
                return BUILT_IN_FUNCTIONS[INDEX_EXP];
            case "expm1":
                return BUILT_IN_FUNCTIONS[INDEX_EXPM1];
            case "signum":
                return BUILT_IN_FUNCTIONS[INDEX_SGN];
            case "csc":
                return BUILT_IN_FUNCTIONS[INDEX_CSC];
            case "sec":
                return BUILT_IN_FUNCTIONS[INDEX_SEC];
            case "csch":
                return BUILT_IN_FUNCTIONS[INDEX_CSCH];
            case "sech":
                return BUILT_IN_FUNCTIONS[INDEX_SECH];
            case "coth":
                return BUILT_IN_FUNCTIONS[INDEX_COTH];
            case "toradian":
                return BUILT_IN_FUNCTIONS[INDEX_TO_RADIAN];
            case "todegree":
                return BUILT_IN_FUNCTIONS[INDEX_TO_DEGREE];
            default:
                return null;
        }
    }

}
