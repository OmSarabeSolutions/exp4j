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
import net.objecthunter.exp4j.internal.BuiltinFunction;
import net.objecthunter.exp4j.internal.BuiltinOperator;
import net.objecthunter.exp4j.internal.Builtins;
import net.objecthunter.exp4j.operator.Operator;
import net.objecthunter.exp4j.tokenizer.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Immutable, allocation-light program compiled from an RPN token array.
 * <p>
 * Built-in operators and functions are evaluated through {@link Builtins} with their
 * operands taken straight from the stack, instead of virtual {@code apply(double...)}
 * calls, which avoids the varargs array allocation and the megamorphic call per token.
 * Variables are read from a slot array instead of a {@link java.util.HashMap}. Custom
 * operators and functions fall back to their {@code apply(double...)} implementation.
 */
final class CompiledExpression implements Program {

    /* opcodes; args[i] holds the constant index, variable slot, Builtins code or custom index */
    static final int OP_CONST = 0;
    static final int OP_VAR = 1;
    static final int OP_UNARY = 2;
    static final int OP_BINARY = 3;
    static final int OP_CUSTOM_OPERATOR = 4;
    static final int OP_CUSTOM_FUNCTION = 5;

    /* package-private so that BytecodeCompiler can translate the program */
    final int[] ops;
    final int[] args;
    final double[] constants;
    final Operator[] operators;
    final Function[] functions;
    final String[] varNames;
    private final int maxStack;

    private CompiledExpression(int[] ops, int[] args, double[] constants, Operator[] operators,
                               Function[] functions, String[] varNames, int maxStack) {
        this.ops = ops;
        this.args = args;
        this.constants = constants;
        this.operators = operators;
        this.functions = functions;
        this.varNames = varNames;
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
            final int consumed;
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
                    if (op instanceof BuiltinOperator builtin) {
                        if (builtin.getCode() == Builtins.IDENTITY && consumed == 1) {
                            // unary plus: emit nothing
                            if (depth < 1) {
                                return null;
                            }
                            continue;
                        }
                        ops[count] = consumed == 1 ? OP_UNARY : OP_BINARY;
                        args[count] = builtin.getCode();
                    } else {
                        ops[count] = OP_CUSTOM_OPERATOR;
                        args[count] = operators.size();
                        operators.add(op);
                    }
                    break;
                }
                case Token.TOKEN_FUNCTION: {
                    final Function f = ((FunctionToken) t).getFunction();
                    consumed = f.getNumArguments();
                    if (f instanceof BuiltinFunction builtin && (consumed == 1 || consumed == 2)) {
                        ops[count] = consumed == 1 ? OP_UNARY : OP_BINARY;
                        args[count] = builtin.getCode();
                    } else {
                        ops[count] = OP_CUSTOM_FUNCTION;
                        args[count] = functions.size();
                        functions.add(f);
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
        final String[] varNames = new String[varSlots.size()];
        for (Map.Entry<String, Integer> e : varSlots.entrySet()) {
            varNames[e.getValue()] = e.getKey();
        }
        return new CompiledExpression(Arrays.copyOf(ops, count), Arrays.copyOf(args, count), consts,
                operators.toArray(new Operator[0]), functions.toArray(new Function[0]), varNames, maxDepth);
    }

    static IllegalArgumentException variableNotSet(final String name) {
        return new IllegalArgumentException("No value has been set for the setVariable '" + name + "'.");
    }

    @Override
    public double evaluate(final double[] values, final boolean[] valueSet) {
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
                        throw variableNotSet(varNames[slot]);
                    }
                    stack[++sp] = values[slot];
                    break;
                }
                case OP_UNARY:
                    stack[sp] = Builtins.unary(stack[sp], args[i]);
                    break;
                case OP_BINARY:
                    stack[sp - 1] = Builtins.binary(stack[sp - 1], stack[sp], args[i]);
                    sp--;
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
                default:
                    throw new IllegalStateException("Unknown opcode " + ops[i]);
            }
        }
        return stack[0];
    }
}
