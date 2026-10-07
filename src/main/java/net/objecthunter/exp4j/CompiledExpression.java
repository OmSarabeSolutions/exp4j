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
package net.objecthunter.exp4j;

import net.objecthunter.exp4j.function.Function;
import net.objecthunter.exp4j.function.Functions;
import net.objecthunter.exp4j.operator.Operator;
import net.objecthunter.exp4j.operator.Operators;
import net.objecthunter.exp4j.tokenizer.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Immutable, allocation-light program compiled from an RPN token array.
 * <p>
 * Built-in operators and functions are dispatched through a single {@code switch}
 * on an opcode instead of virtual {@code apply(double...)} calls, which avoids the
 * varargs array allocation and the megamorphic call per token. Variables are read
 * from a slot array instead of a {@link java.util.HashMap}. Custom operators and
 * functions fall back to their {@code apply(double...)} implementation.
 * <p>
 * The built-in opcodes must stay semantically identical to
 * {@link Functions} and {@link Operators}.
 */
final class CompiledExpression {

    private static final int OP_CONST = 0;
    private static final int OP_VAR = 1;
    private static final int OP_CUSTOM_OPERATOR = 2;
    private static final int OP_CUSTOM_FUNCTION = 3;

    private static final int OP_ADD = 10;
    private static final int OP_SUB = 11;
    private static final int OP_MUL = 12;
    private static final int OP_DIV = 13;
    private static final int OP_POW = 14;
    private static final int OP_MOD = 15;
    private static final int OP_NEG = 16;

    private static final int F_SIN = 100;
    private static final int F_COS = 101;
    private static final int F_TAN = 102;
    private static final int F_COT = 103;
    private static final int F_LOG = 104;
    private static final int F_LOG2 = 105;
    private static final int F_LOG10 = 106;
    private static final int F_LOG1P = 107;
    private static final int F_ABS = 108;
    private static final int F_ACOS = 109;
    private static final int F_ASIN = 110;
    private static final int F_ATAN = 111;
    private static final int F_CBRT = 112;
    private static final int F_FLOOR = 113;
    private static final int F_SINH = 114;
    private static final int F_SQRT = 115;
    private static final int F_TANH = 116;
    private static final int F_COSH = 117;
    private static final int F_CEIL = 118;
    private static final int F_POW = 119;
    private static final int F_EXP = 120;
    private static final int F_EXPM1 = 121;
    private static final int F_SIGNUM = 122;
    private static final int F_CSC = 123;
    private static final int F_SEC = 124;
    private static final int F_CSCH = 125;
    private static final int F_SECH = 126;
    private static final int F_COTH = 127;
    private static final int F_LOGB = 128;
    private static final int F_TORADIAN = 129;
    private static final int F_TODEGREE = 130;

    private final int[] ops;
    private final int[] args;
    private final double[] constants;
    private final Operator[] operators;
    private final Function[] functions;
    private final int maxStack;

    private CompiledExpression(int[] ops, int[] args, double[] constants, Operator[] operators,
                               Function[] functions, int maxStack) {
        this.ops = ops;
        this.args = args;
        this.constants = constants;
        this.operators = operators;
        this.functions = functions;
        this.maxStack = maxStack;
    }

    /**
     * Compile the tokens into a program.
     *
     * @param tokens    the RPN tokens
     * @param varSlots  slot index for every variable name occurring in the tokens
     * @return the program, or {@code null} if the token stream is structurally invalid; the caller must
     * then use the token interpreter so the original error reporting is preserved
     */
    static CompiledExpression compile(Token[] tokens, Map<String, Integer> varSlots) {
        final int n = tokens.length;
        final int[] ops = new int[n];
        final int[] args = new int[n];
        final List<Double> constants = new ArrayList<>();
        final List<Operator> operators = new ArrayList<>();
        final List<Function> functions = new ArrayList<>();
        int count = 0;
        int depth = 0;
        int maxDepth = 0;
        for (Token t : tokens) {
            int consumed;
            switch (t.getType()) {
                case Token.TOKEN_NUMBER:
                    ops[count] = OP_CONST;
                    args[count] = constants.size();
                    constants.add(((NumberToken) t).getValue());
                    consumed = 0;
                    break;
                case Token.TOKEN_VARIABLE:
                    ops[count] = OP_VAR;
                    args[count] = varSlots.get(((VariableToken) t).getName());
                    consumed = 0;
                    break;
                case Token.TOKEN_OPERATOR: {
                    final Operator op = ((OperatorToken) t).getOperator();
                    consumed = op.getNumOperands();
                    if (consumed != 1 && consumed != 2) {
                        // the interpreter silently ignores such operators; keep that behaviour there
                        return null;
                    }
                    final int code = builtinOperatorCode(op);
                    if (code == OP_CUSTOM_OPERATOR) {
                        ops[count] = OP_CUSTOM_OPERATOR;
                        args[count] = operators.size();
                        operators.add(op);
                    } else if (code == -1) {
                        // unary plus: identity, emit nothing
                        if (depth < 1) {
                            return null;
                        }
                        continue;
                    } else {
                        ops[count] = code;
                    }
                    break;
                }
                case Token.TOKEN_FUNCTION: {
                    final Function f = ((FunctionToken) t).getFunction();
                    consumed = f.getNumArguments();
                    final int code = builtinFunctionCode(f);
                    if (code == OP_CUSTOM_FUNCTION) {
                        ops[count] = OP_CUSTOM_FUNCTION;
                        args[count] = functions.size();
                        functions.add(f);
                    } else {
                        ops[count] = code;
                    }
                    break;
                }
                default:
                    return null;
            }
            if (depth < consumed) {
                return null;
            }
            depth = depth - consumed + 1;
            maxDepth = Math.max(maxDepth, depth);
            count++;
        }
        if (depth != 1) {
            return null;
        }
        final double[] consts = new double[constants.size()];
        for (int i = 0; i < consts.length; i++) {
            consts[i] = constants.get(i);
        }
        return new CompiledExpression(java.util.Arrays.copyOf(ops, count), java.util.Arrays.copyOf(args, count),
                consts, operators.toArray(new Operator[0]), functions.toArray(new Function[0]), maxDepth);
    }

    /**
     * @return the opcode of a built-in operator, -1 for the built-in unary plus,
     * or {@link #OP_CUSTOM_OPERATOR} for anything else
     */
    private static int builtinOperatorCode(Operator op) {
        final String symbol = op.getSymbol();
        if (symbol == null || symbol.length() != 1
                || Operators.getBuiltinOperator(symbol.charAt(0), op.getNumOperands()) != op) {
            return OP_CUSTOM_OPERATOR;
        }
        switch (symbol.charAt(0)) {
            case '+':
                return op.getNumOperands() == 1 ? -1 : OP_ADD;
            case '-':
                return op.getNumOperands() == 1 ? OP_NEG : OP_SUB;
            case '*':
                return OP_MUL;
            case '/':
                return OP_DIV;
            case '^':
                return OP_POW;
            case '%':
                return OP_MOD;
            default:
                return OP_CUSTOM_OPERATOR;
        }
    }

    private static int builtinFunctionCode(Function f) {
        final String name = f.getName();
        if (Functions.getBuiltinFunction(name) != f) {
            return OP_CUSTOM_FUNCTION;
        }
        switch (name) {
            case "sin": return F_SIN;
            case "cos": return F_COS;
            case "tan": return F_TAN;
            case "cot": return F_COT;
            case "log": return F_LOG;
            case "log2": return F_LOG2;
            case "log10": return F_LOG10;
            case "log1p": return F_LOG1P;
            case "abs": return F_ABS;
            case "acos": return F_ACOS;
            case "asin": return F_ASIN;
            case "atan": return F_ATAN;
            case "cbrt": return F_CBRT;
            case "floor": return F_FLOOR;
            case "sinh": return F_SINH;
            case "sqrt": return F_SQRT;
            case "tanh": return F_TANH;
            case "cosh": return F_COSH;
            case "ceil": return F_CEIL;
            case "pow": return F_POW;
            case "exp": return F_EXP;
            case "expm1": return F_EXPM1;
            case "signum": return F_SIGNUM;
            case "csc": return F_CSC;
            case "sec": return F_SEC;
            case "csch": return F_CSCH;
            case "sech": return F_SECH;
            case "coth": return F_COTH;
            case "logb": return F_LOGB;
            case "toradian": return F_TORADIAN;
            case "todegree": return F_TODEGREE;
            default: return OP_CUSTOM_FUNCTION;
        }
    }

    double evaluate(final double[] values, final boolean[] valueSet, final String[] names) {
        final int[] ops = this.ops;
        final int[] args = this.args;
        final double[] stack = new double[maxStack];
        int sp = -1;
        for (int i = 0; i < ops.length; i++) {
            switch (ops[i]) {
                case OP_CONST:
                    stack[++sp] = constants[args[i]];
                    break;
                case OP_VAR: {
                    final int slot = args[i];
                    if (!valueSet[slot]) {
                        throw new IllegalArgumentException("No value has been set for the setVariable '" + names[slot] + "'.");
                    }
                    stack[++sp] = values[slot];
                    break;
                }
                case OP_ADD:
                    stack[sp - 1] = stack[sp - 1] + stack[sp];
                    sp--;
                    break;
                case OP_SUB:
                    stack[sp - 1] = stack[sp - 1] - stack[sp];
                    sp--;
                    break;
                case OP_MUL:
                    stack[sp - 1] = stack[sp - 1] * stack[sp];
                    sp--;
                    break;
                case OP_DIV:
                    if (stack[sp] == 0d) {
                        throw new ArithmeticException("Division by zero!");
                    }
                    stack[sp - 1] = stack[sp - 1] / stack[sp];
                    sp--;
                    break;
                case OP_POW:
                    stack[sp - 1] = Math.pow(stack[sp - 1], stack[sp]);
                    sp--;
                    break;
                case OP_MOD:
                    if (stack[sp] == 0d) {
                        throw new ArithmeticException("Division by zero!");
                    }
                    stack[sp - 1] = stack[sp - 1] % stack[sp];
                    sp--;
                    break;
                case OP_NEG:
                    stack[sp] = -stack[sp];
                    break;
                case OP_CUSTOM_OPERATOR: {
                    final Operator op = operators[args[i]];
                    if (op.getNumOperands() == 2) {
                        final double right = stack[sp--];
                        stack[sp] = op.apply(stack[sp], right);
                    } else {
                        stack[sp] = op.apply(stack[sp]);
                    }
                    break;
                }
                case OP_CUSTOM_FUNCTION: {
                    final Function f = functions[args[i]];
                    final int numArguments = f.getNumArguments();
                    final double[] fargs = new double[numArguments];
                    sp -= numArguments;
                    System.arraycopy(stack, sp + 1, fargs, 0, numArguments);
                    stack[++sp] = f.apply(fargs);
                    break;
                }
                case F_SIN:
                    stack[sp] = Math.sin(stack[sp]);
                    break;
                case F_COS:
                    stack[sp] = Math.cos(stack[sp]);
                    break;
                case F_TAN:
                    stack[sp] = Math.tan(stack[sp]);
                    break;
                case F_COT: {
                    final double tan = Math.tan(stack[sp]);
                    if (tan == 0d) {
                        throw new ArithmeticException("Division by zero in cotangent!");
                    }
                    stack[sp] = 1d / tan;
                    break;
                }
                case F_LOG:
                    stack[sp] = Math.log(stack[sp]);
                    break;
                case F_LOG2:
                    stack[sp] = Math.log(stack[sp]) / Math.log(2d);
                    break;
                case F_LOG10:
                    stack[sp] = Math.log10(stack[sp]);
                    break;
                case F_LOG1P:
                    stack[sp] = Math.log1p(stack[sp]);
                    break;
                case F_ABS:
                    stack[sp] = Math.abs(stack[sp]);
                    break;
                case F_ACOS:
                    stack[sp] = Math.acos(stack[sp]);
                    break;
                case F_ASIN:
                    stack[sp] = Math.asin(stack[sp]);
                    break;
                case F_ATAN:
                    stack[sp] = Math.atan(stack[sp]);
                    break;
                case F_CBRT:
                    stack[sp] = Math.cbrt(stack[sp]);
                    break;
                case F_FLOOR:
                    stack[sp] = Math.floor(stack[sp]);
                    break;
                case F_SINH:
                    stack[sp] = Math.sinh(stack[sp]);
                    break;
                case F_SQRT:
                    stack[sp] = Math.sqrt(stack[sp]);
                    break;
                case F_TANH:
                    stack[sp] = Math.tanh(stack[sp]);
                    break;
                case F_COSH:
                    stack[sp] = Math.cosh(stack[sp]);
                    break;
                case F_CEIL:
                    stack[sp] = Math.ceil(stack[sp]);
                    break;
                case F_POW:
                    stack[sp - 1] = Math.pow(stack[sp - 1], stack[sp]);
                    sp--;
                    break;
                case F_EXP:
                    stack[sp] = Math.exp(stack[sp]);
                    break;
                case F_EXPM1:
                    stack[sp] = Math.expm1(stack[sp]);
                    break;
                case F_SIGNUM: {
                    final double a = stack[sp];
                    stack[sp] = a > 0 ? 1 : (a < 0 ? -1 : 0);
                    break;
                }
                case F_CSC: {
                    final double sin = Math.sin(stack[sp]);
                    if (sin == 0d) {
                        throw new ArithmeticException("Division by zero in cosecant!");
                    }
                    stack[sp] = 1d / sin;
                    break;
                }
                case F_SEC: {
                    final double cos = Math.cos(stack[sp]);
                    if (cos == 0d) {
                        throw new ArithmeticException("Division by zero in secant!");
                    }
                    stack[sp] = 1d / cos;
                    break;
                }
                case F_CSCH: {
                    final double a = stack[sp];
                    stack[sp] = a == 0d ? 0 : 1d / Math.sinh(a);
                    break;
                }
                case F_SECH:
                    stack[sp] = 1d / Math.cosh(stack[sp]);
                    break;
                case F_COTH: {
                    final double a = stack[sp];
                    stack[sp] = Math.cosh(a) / Math.sinh(a);
                    break;
                }
                case F_LOGB:
                    stack[sp - 1] = Math.log(stack[sp]) / Math.log(stack[sp - 1]);
                    sp--;
                    break;
                case F_TORADIAN:
                    stack[sp] = Math.toRadians(stack[sp]);
                    break;
                case F_TODEGREE:
                    stack[sp] = Math.toDegrees(stack[sp]);
                    break;
                default:
                    throw new IllegalStateException("Unknown opcode " + ops[i]);
            }
        }
        return stack[0];
    }
}
