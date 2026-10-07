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

import net.objecthunter.exp4j.function.Function;

/**
 * A built-in function whose math is implemented by {@link Builtins}.
 */
public final class BuiltinFunction extends Function {

    private final int code;

    /**
     * @param name         the function name
     * @param numArguments 1 for a {@link Builtins#unary} code, 2 for a {@link Builtins#binary} code
     * @param code         the {@link Builtins} code
     */
    public BuiltinFunction(String name, int numArguments, int code) {
        super(name, numArguments);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    @Override
    public double apply(double... args) {
        return numArguments == 1 ? Builtins.unary(args[0], code) : Builtins.binary(args[0], args[1], code);
    }
}
