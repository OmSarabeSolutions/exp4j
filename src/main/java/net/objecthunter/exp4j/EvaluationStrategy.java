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

/**
 * How an {@link Expression} is evaluated. Selected with {@link ExpressionBuilder#strategy(EvaluationStrategy)}.
 */
public enum EvaluationStrategy {

    /**
     * Evaluate a compact instruction list with an interpreter loop. Building is cheap, so this
     * is the best choice for expressions evaluated only a few times. This is the default.
     */
    INTERPRETED,

    /**
     * Generate a hidden class with JVM bytecode for the expression at build time, using the
     * {@code java.lang.classfile} API. Building costs more (class generation and JIT warm-up),
     * but evaluation runs at close to hand-written Java speed; use it for expressions that are
     * evaluated many times. Only {@code java.base} is needed, so it works in jlink-ed images.
     * Where classes cannot be defined at runtime (e.g. a GraalVM native image) the expression
     * silently falls back to {@link #INTERPRETED}.
     */
    BYTECODE
}
