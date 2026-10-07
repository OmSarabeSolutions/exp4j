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
import net.objecthunter.exp4j.tokenizer.*;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class Expression {

    private final Token[] tokens;

    private final Set<String> userFunctionNames;

    /* slot index of each distinct variable name occurring in the tokens */
    private final Map<String, Integer> varSlots;

    private final String[] varNames;

    /* false for slot names clashing with a function, so setVariable() still rejects them */
    private final boolean[] varNameValid;

    private final double[] varValues;

    private final boolean[] varSet;

    /* null when the tokens are structurally invalid; evaluate() then interprets the tokens */
    private final CompiledExpression program;

    /**
     * Creates a new expression that is a copy of the existing one.
     *
     * @param existing the expression to copy
     */
    public Expression(final Expression existing) {
        this.tokens = Arrays.copyOf(existing.tokens, existing.tokens.length);
        this.userFunctionNames = new HashSet<>(existing.userFunctionNames);
        this.varSlots = existing.varSlots;
        this.varNames = existing.varNames;
        this.varNameValid = existing.varNameValid;
        this.varValues = existing.varValues.clone();
        this.varSet = existing.varSet.clone();
        this.program = existing.program;
    }

    Expression(final Token[] tokens) {
        this(tokens, Collections.emptySet());
    }

    Expression(final Token[] tokens, Set<String> userFunctionNames) {
        this.tokens = tokens;
        this.userFunctionNames = userFunctionNames;
        final Map<String, Integer> slots = new HashMap<>();
        for (final Token t : tokens) {
            if (t.getType() == Token.TOKEN_VARIABLE) {
                slots.putIfAbsent(((VariableToken) t).getName(), slots.size());
            }
        }
        this.varSlots = slots;
        this.varNames = new String[slots.size()];
        this.varNameValid = new boolean[slots.size()];
        this.varValues = new double[slots.size()];
        this.varSet = new boolean[slots.size()];
        for (Map.Entry<String, Integer> e : slots.entrySet()) {
            final int slot = e.getValue();
            varNames[slot] = e.getKey();
            varNameValid[slot] = isValidVariableName(e.getKey());
        }
        setDefaultVariables();
        this.program = CompiledExpression.compile(tokens, slots);
    }

    private void setDefaultVariables() {
        setIfPresent("pi", Math.PI);
        setIfPresent("π", Math.PI);              // 3.141592653589793
        setIfPresent("φ", 1.618033988749895d);   // golden ratio (1 + √5) / 2
        setIfPresent("e", Math.E);               // 2.718281828459045
    }

    private void setIfPresent(final String name, final double value) {
        final Integer slot = varSlots.get(name);
        if (slot != null) {
            varValues[slot] = value;
            varSet[slot] = true;
        }
    }

    public Expression setVariable(final String name, final double value) {
        final Integer slot = this.varSlots.get(name);
        if (slot == null || !this.varNameValid[slot]) {
            this.checkVariableName(name);
        }
        if (slot != null) {
            this.varValues[slot] = value;
            this.varSet[slot] = true;
        }
        return this;
    }

    private boolean isValidVariableName(String name) {
        return !this.userFunctionNames.contains(name) && Functions.getBuiltinFunction(name) == null;
    }

    private void checkVariableName(String name) {
        if (!isValidVariableName(name)) {
            throw new IllegalArgumentException("The variable name '" + name + "' is invalid. Since there exists a function with the same name");
        }
    }

    public Expression setVariables(Map<String, Double> variables) {
        for (Map.Entry<String, Double> v : variables.entrySet()) {
            this.setVariable(v.getKey(), v.getValue());
        }
        return this;
    }

    public Expression clearVariables() {
        Arrays.fill(this.varSet, false);
        return this;
    }

    public Set<String> getVariableNames() {
        final Set<String> variables = new HashSet<>();
        for (final Token t : tokens) {
            if (t.getType() == Token.TOKEN_VARIABLE)
                variables.add(((VariableToken) t).getName());
        }
        return variables;
    }

    public ValidationResult validate(boolean checkVariablesSet) {
        final List<String> errors = new ArrayList<>(0);
        if (checkVariablesSet) {
            /* check that all vars have a value set */
            for (final Token t : this.tokens) {
                if (t.getType() == Token.TOKEN_VARIABLE) {
                    final String var = ((VariableToken) t).getName();
                    if (!this.varSet[this.varSlots.get(var)]) {
                        errors.add("The setVariable '" + var + "' has not been set");
                    }
                }
            }
        }

        /* Check if the number of operands, functions and operators match.
           The idea is to increment a counter for operands and decrease it for operators.
           When a function occurs the number of available arguments has to be greater
           than or equals to the function's expected number of arguments.
           The count has to be larger than 1 at all times and exactly 1 after all tokens
           have been processed */
        int count = 0;
        for (Token tok : this.tokens) {
            switch (tok.getType()) {
                case Token.TOKEN_NUMBER:
                case Token.TOKEN_VARIABLE:
                    count++;
                    break;
                case Token.TOKEN_FUNCTION:
                    final Function func = ((FunctionToken) tok).getFunction();
                    final int argsNum = func.getNumArguments();
                    if (argsNum > count) {
                        errors.add("Not enough arguments for '" + func.getName() + "'");
                    }
                    if (argsNum > 1) {
                        count -= argsNum - 1;
                    } else if (argsNum == 0) {
                        // see https://github.com/fasseg/exp4j/issues/59
                        count++;
                    }
                    break;
                case Token.TOKEN_OPERATOR:
                    Operator op = ((OperatorToken) tok).getOperator();
                    if (op.getNumOperands() == 2) {
                        count--;
                    }
                    break;
            }
            if (count < 1) {
                errors.add("Too many operators");
                return new ValidationResult(false, errors);
            }
        }
        if (count > 1) {
            errors.add("Too many operands");
        }
        return errors.size() == 0 ? ValidationResult.SUCCESS : new ValidationResult(false, errors);

    }

    public ValidationResult validate() {
        return validate(true);
    }

    public Future<Double> evaluateAsync(ExecutorService executor) {
        return executor.submit(this::evaluate);
    }

    public double evaluate() {
        if (program != null) {
            return program.evaluate(varValues, varSet, varNames);
        }
        return interpret();
    }

    /* Token interpreter, only used for structurally invalid expressions to keep their error reporting */
    private double interpret() {
        final ArrayStack output = new ArrayStack();
        for (Token t : tokens) {
            if (t.getType() == Token.TOKEN_NUMBER) {
                output.push(((NumberToken) t).getValue());
            } else if (t.getType() == Token.TOKEN_VARIABLE) {
                final String name = ((VariableToken) t).getName();
                final int slot = this.varSlots.get(name);
                if (!this.varSet[slot]) {
                    throw new IllegalArgumentException("No value has been set for the setVariable '" + name + "'.");
                }
                output.push(this.varValues[slot]);
            } else if (t.getType() == Token.TOKEN_OPERATOR) {
                OperatorToken op = (OperatorToken) t;
                if (output.size() < op.getOperator().getNumOperands()) {
                    throw new IllegalArgumentException("Invalid number of operands available for '" + op.getOperator().getSymbol() + "' operator");
                }
                if (op.getOperator().getNumOperands() == 2) {
                    /* pop the operands and push the result of the operation */
                    double rightArg = output.pop();
                    double leftArg = output.pop();
                    output.push(op.getOperator().apply(leftArg, rightArg));
                } else if (op.getOperator().getNumOperands() == 1) {
                    /* pop the operand and push the result of the operation */
                    double arg = output.pop();
                    output.push(op.getOperator().apply(arg));
                }
            } else if (t.getType() == Token.TOKEN_FUNCTION) {
                FunctionToken func = (FunctionToken) t;
                final int numArguments = func.getFunction().getNumArguments();
                if (output.size() < numArguments) {
                    throw new IllegalArgumentException("Invalid number of arguments available for '" + func.getFunction().getName() + "' function");
                }
                /* collect the arguments from the stack */
                double[] args = new double[numArguments];
                for (int j = numArguments - 1; j >= 0; j--) {
                    args[j] = output.pop();
                }
                output.push(func.getFunction().apply(args));
            }
        }
        if (output.size() > 1) {
            throw new IllegalArgumentException("Invalid number of items on the output queue. Might be caused by an invalid number of arguments for a function.");
        }
        return output.pop();
    }
}
