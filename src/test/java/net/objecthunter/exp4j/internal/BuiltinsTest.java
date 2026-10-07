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

import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

/**
 * The bytecode generator calls the methods named in {@link Builtins#UNARY_METHODS} and
 * {@link Builtins#BINARY_METHODS}, the interpreter goes through the dispatch switches:
 * both must resolve every code to the same implementation.
 */
public class BuiltinsTest {

    private static final double[] VALUES = {0d, -0d, 0.3, -0.7, 1d, 2.5, -4d, Math.PI, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

    @Test
    public void testUnaryMethodTableMatchesDispatch() throws Exception {
        for (int i = 0; i < Builtins.UNARY_METHODS.length; i++) {
            final int code = i;
            final Method m = Builtins.class.getMethod(Builtins.UNARY_METHODS[code], double.class);
            for (double a : VALUES) {
                assertEquals(Builtins.UNARY_METHODS[code] + "(" + a + ")",
                        outcome(() -> Builtins.unary(a, code)), outcome(() -> (double) m.invoke(null, a)));
            }
        }
    }

    @Test
    public void testBinaryMethodTableMatchesDispatch() throws Exception {
        for (int i = 0; i < Builtins.BINARY_METHODS.length; i++) {
            final int code = i;
            final Method m = Builtins.class.getMethod(Builtins.BINARY_METHODS[code], double.class, double.class);
            for (double a : VALUES) {
                for (double b : VALUES) {
                    assertEquals(Builtins.BINARY_METHODS[code] + "(" + a + ", " + b + ")",
                            outcome(() -> Builtins.binary(a, b, code)), outcome(() -> (double) m.invoke(null, a, b)));
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownUnaryCode() {
        Builtins.unary(1d, Builtins.UNARY_METHODS.length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownBinaryCode() {
        Builtins.binary(1d, 1d, Builtins.BINARY_METHODS.length);
    }

    private interface Call {
        double run() throws Exception;
    }

    /* the result, or the exception type and message, unwrapping reflection */
    private static Object outcome(Call call) {
        try {
            return call.run();
        } catch (InvocationTargetException e) {
            return e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
        } catch (Exception e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
