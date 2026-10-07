/*
 * Copyright 2014 Frank Asseg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * exp4j: a mathematical expression evaluator.
 * <p>
 * Clients use {@link net.objecthunter.exp4j.ExpressionBuilder} and
 * {@link net.objecthunter.exp4j.Expression}, and extend the library by
 * subclassing {@link net.objecthunter.exp4j.function.Function} and
 * {@link net.objecthunter.exp4j.operator.Operator}. The tokenizer and the
 * shunting-yard implementation are exported as well.
 */
module net.objecthunter.exp4j {
    exports net.objecthunter.exp4j;
    exports net.objecthunter.exp4j.function;
    exports net.objecthunter.exp4j.operator;
    exports net.objecthunter.exp4j.shuntingyard;
    exports net.objecthunter.exp4j.tokenizer;
}
