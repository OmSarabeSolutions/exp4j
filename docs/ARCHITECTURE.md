# exp4j — Architecture and Developer Guide

exp4j evaluates mathematical expressions written as text, such as `3 sin(x) - 2 / (y - 2)`. It is a small library that needs nothing at runtime except the JDK.

This document explains how the library is built, how to use it, and how to extend it. The UML model is in [`uml/exp4j.mdj`](uml/exp4j.mdj) (StarUML). See [`uml/README.md`](uml/README.md) for the list of diagrams.

![Class overview](uml/images/01_Class_overview.png)

---

## 1. Quick start

```java
Expression e = new ExpressionBuilder("3 * sin(y) - 2 / (x - 2)")
        .variables("x", "y")
        .build()
        .setVariable("x", 2.3)
        .setVariable("y", 3.14);
double result = e.evaluate();
```

The library has three steps:

1. **Configure** an `ExpressionBuilder`: the expression text, the variable names, and optionally your own functions and operators.
2. **`build()`** parses the text once into an `Expression`.
3. **Evaluate** the `Expression` as many times as needed, with new variable values each time.

---

## 2. Packages

| Package (`net.objecthunter.exp4j…`) | Classes | Role |
|---|---|---|
| *(root)* | `ExpressionBuilder`, `Expression`, `ValidationResult`, `ArrayStack` | Public API: build, validate and evaluate expressions. `ArrayStack` is an internal stack of `double` values. |
| `.tokenizer` | `Tokenizer`, `Token`, `NumberToken`, `VariableToken`, `FunctionToken`, `OperatorToken`, `OpenParenthesesToken`, `CloseParenthesesToken`, `ArgumentSeparatorToken`, `UnknownFunctionOrVariableException` | Lexical analysis: splits the text into tokens. |
| `.shuntingyard` | `ShuntingYard` | Converts infix tokens to reverse Polish notation (RPN) with Dijkstra's shunting-yard algorithm. |
| `.function` | `Function` (abstract), `Functions` | Extension point for functions, plus the 31 built-in functions. |
| `.operator` | `Operator` (abstract), `Operators` | Extension point for operators, plus the 8 built-in operators. |

![Package dependencies](uml/images/10_Package_dependencies.png)

Dependencies point downward: the API depends on everything else. `function` and `operator` depend on nothing.

**Java module.** The jar is the named module `net.objecthunter.exp4j` (`src/main/java/module-info.java`). It requires only `java.base` and exports all five packages: the root package, `.function`, `.operator`, `.tokenizer` and `.shuntingyard`.

```java
module my.app {
    requires net.objecthunter.exp4j;
}
```

---

## 3. How an expression is processed

```
 "3 sin(x) - 2"                                                      double
       │                                                                ▲
       ▼                                                                │
  ┌──────────┐  infix tokens  ┌──────────────┐  Token[] (RPN)  ┌──────────────┐
  │Tokenizer │ ─────────────▶ │ ShuntingYard │ ──────────────▶ │  Expression  │
  └──────────┘                └──────────────┘                 │ + ArrayStack │
   names, numbers,             precedence,                     └──────────────┘
   unary vs binary,            associativity,                   push operands,
   implicit '*'                parentheses                      apply operators
```

![Parsing and evaluation](uml/images/05_Parsing_and_evaluation.png)

### 3.1 Tokenizing — `Tokenizer`

`Tokenizer.nextToken()` reads one token at a time. The type of the previous token (`lastToken`) decides how the next characters are read:

| Input | Token produced | Rules |
|---|---|---|
| Digits, `.`, scientific notation (`1.5e-3`) | `NumberToken` | A trailing `e`/`E` is not part of the number (so `2e` is `2 · e`). A number directly after a number is an error. |
| Letters, digits, `_`, `.` | `VariableToken` or `FunctionToken` | Greedy: the **longest** prefix that is a declared variable or a known function is used. Declared variables take priority over functions. If nothing matches, `UnknownFunctionOrVariableException` is thrown. |
| Operator characters | `OperatorToken` | The longest run of allowed characters that matches a user operator wins. For a built-in `+` or `-`, the operator is **unary** at the start, after `(`, after `,`, or after a binary or prefix operator. Otherwise it is **binary**. |
| `(` `[` `{` | `OpenParenthesesToken` | All three bracket styles are equivalent. |
| `)` `]` `}` | `CloseParenthesesToken` | |
| `,` | `ArgumentSeparatorToken` | Separates function arguments. |

**Implicit multiplication.** This is on by default; turn it off with `ExpressionBuilder.implicitMultiplication(false)`. A `*` is inserted when a number, a name, or `(` comes right after an operand: `2x`, `2(x+1)`, `(a)(b)`, `x y`. The character that triggers it is read again on the next call.

![Tokenizer states](uml/images/30_States_Tokenizer.png)

### 3.2 Reordering — `ShuntingYard.convertToRPN`

This is the classic algorithm with one output queue and one operator stack:

- Numbers and variables go straight to the output.
- Functions and `(` are pushed onto the stack.
- An operator `o1` first pops every operator `o2` on the stack that has higher precedence, or equal precedence when `o1` is left-associative. A unary operator never pops a binary one.
- `,` pops operators up to the enclosing `(`.
- `)` pops operators up to `(`, removes the `(`, then pops the function if one is on top.
- At the end, the remaining operators are popped. A leftover parenthesis means the parentheses are mismatched, and an `IllegalArgumentException` is thrown.

### 3.3 Evaluating — `Expression.evaluate`

`evaluate()` walks the RPN tokens once, using an `ArrayStack` of doubles:

| Token | Action |
|---|---|
| `NumberToken` | push its value |
| `VariableToken` | push the variable's value; `IllegalArgumentException` if it has none |
| `OperatorToken` | pop 1 or 2 operands, push `operator.apply(...)` |
| `FunctionToken` | pop `numArguments` values (the last popped is the first argument), push `function.apply(...)` |

At the end exactly one value must remain on the stack, and it is the result.

![Evaluate sequence](uml/images/21_Sequence_Evaluate_an_expression.png)

---

## 4. Public API reference

### `ExpressionBuilder`

| Method | Description |
|---|---|
| `ExpressionBuilder(String expression)` | Throws `IllegalArgumentException` if the expression is null or blank. |
| `variable(String)`, `variables(String...)`, `variables(Set<String>)` | Declares variable names. **A name must be declared to be recognized.** |
| `function(Function)`, `functions(Function...)`, `functions(List<Function>)` | Adds user functions. A user function overrides a built-in one with the same name. |
| `operator(Operator)`, `operator(Operator...)`, `operator(List<Operator>)` | Adds user operators. The symbol is checked against `Operator.ALLOWED_OPERATOR_CHARS`. |
| `implicitMultiplication(boolean)` | Turns implicit `*` on or off (default: `true`). |
| `build()` | Adds the constants, rejects variables named like a function, and parses the text into an `Expression`. |

![Build sequence](uml/images/20_Sequence_Build_an_expression.png)

### `Expression`

| Method | Description |
|---|---|
| `setVariable(String, double)`, `setVariables(Map<String, Double>)` | Sets values and returns `this` for chaining. Rejects names that clash with a function. |
| `clearVariables()` | Removes **all** values, including the constants `pi`, `π`, `e` and `φ`. |
| `getVariableNames()` | The variables used in the expression (constants included if they appear in it). |
| `validate()` / `validate(boolean checkVariablesSet)` | Checks the expression without evaluating it. See §6. |
| `evaluate()` | Returns the result as a `double`. |
| `evaluateAsync(ExecutorService)` | Submits `evaluate()` to the executor and returns a `Future<Double>`. |
| `Expression(Expression existing)` | Copy constructor: shares the parsed tokens and copies the variables. |

### `ValidationResult`

`isValid()` and `getErrors()`. `ValidationResult.SUCCESS` is a shared instance, and its `getErrors()` returns `null`.

---

## 5. Built-in elements

### Constants

These are predefined variables. Overriding them with `setVariable` is allowed.

| Name | Value |
|---|---|
| `pi`, `π` | `Math.PI` |
| `e` | `Math.E` |
| `φ` | 1.618033988749895, the golden ratio (1 + √5) / 2 |

### Operators (`Operators`)

| Symbol | Arity | Precedence | Associativity | Notes |
|---|---|---|---|---|
| `+` `-` | 2 | 500 | left | |
| `*` `/` `÷` `%` | 2 | 1000 | left | `/` and `%` throw `ArithmeticException` when dividing by zero |
| unary `-` `+` | 1 | 5000 | right | |
| `^` | 2 | 10000 | right | `2^3^2` = `2^9`. Because `^` has higher precedence than unary minus, `-2^2` = `-4`. |

The precedence values are public constants on `Operator` (`PRECEDENCE_ADDITION`, `PRECEDENCE_POWER`, …), so custom operators can be placed relative to them.

### Functions (`Functions`)

| Category | Functions |
|---|---|
| Trigonometric | `sin` `cos` `tan` `cot` `sec` `csc` `asin` `acos` `atan` |
| Hyperbolic | `sinh` `cosh` `tanh` `coth` `sech` `csch` |
| Powers and roots | `sqrt` `cbrt` `pow(x, y)` `exp` `expm1` |
| Logarithms | `log` (natural) `log10` `log2` `log1p` `logb(base, x)` |
| Rounding and sign | `abs` `ceil` `floor` `signum` |
| Angle conversion | `toradian` `todegree` |

`cot`, `sec` and `csc` throw `ArithmeticException` when their denominator is zero. `csch(0)` returns `0`.

---

## 6. Validation and errors

`validate(checkVariablesSet)` does two checks without evaluating anything:

1. If `checkVariablesSet` is true, it checks that every variable in the expression has a value.
2. It checks that operands and operators balance. A counter goes up for each operand and down for each binary operator. A function with *n* arguments lowers it by *n − 1*, and a zero-argument function raises it by 1. The counter must never drop below 1 and must end at exactly 1. Otherwise validation reports `Too many operators` or `Too many operands`.

![Validate sequence](uml/images/22_Sequence_Validate_an_expression.png)

| Situation | Exception | Thrown by |
|---|---|---|
| Empty expression | `IllegalArgumentException` | `ExpressionBuilder` constructor |
| Variable named like a function | `IllegalArgumentException` | `build()`, `setVariable()` |
| Invalid operator symbol | `IllegalArgumentException` | `ExpressionBuilder.operator()` |
| Invalid function name, or negative number of arguments | `IllegalArgumentException` | `Function` constructor |
| Unknown name | `UnknownFunctionOrVariableException` (an `IllegalArgumentException`) with `getToken()` and `getPosition()` | `Tokenizer`, during `build()` |
| Unparsable character, or a number right after a number | `IllegalArgumentException` | `Tokenizer` |
| Mismatched parentheses, misplaced `,` | `IllegalArgumentException` | `ShuntingYard` |
| Variable without a value, wrong number of operands or arguments | `IllegalArgumentException` | `evaluate()` |
| Division by zero (`/`, `%`, `cot`, `sec`, `csc`) | `ArithmeticException` | `evaluate()` |

---

## 7. Extending exp4j

### Custom function

```java
Function avg = new Function("avg", 3) {
    @Override
    public double apply(double... args) {
        return (args[0] + args[1] + args[2]) / 3;
    }
};
double r = new ExpressionBuilder("avg(1, 2, x)").function(avg).variables("x").build()
        .setVariable("x", 6).evaluate();   // 3.0
```

- A name starts with a letter or `_` and may then contain letters, digits and `_`.
- The number of arguments must be ≥ 0, and the default is 1. A function with 0 arguments is called as `f()`.

### Custom operator

```java
Operator factorial = new Operator("!", 1, true, Operator.PRECEDENCE_POWER + 1) {
    @Override
    public double apply(double... args) {
        // ...
    }
};
new ExpressionBuilder("3!").operator(factorial).build().evaluate();  // 6.0
```

- Symbols use only these characters: `+ - * / % ^ ! # § $ & ; : ~ < > | = ÷ √ ∛ ⌈ ⌊`.
- An operator with 1 operand that is **left-associative** acts as a postfix operator (`3!`). One that is **right-associative** acts as a prefix operator (`√4`).
- A symbol made of several characters (for example `>=`) is matched greedily.

![Functions and operators](uml/images/03_Functions_and_operators.png)

---

## 8. Thread safety

- `Function` and `Operator` instances, including the built-ins, have no state and can be shared between threads.
- An `Expression` stores its variable values in a mutable `HashMap`. Do not call `setVariable` from several threads on the same instance. Give each thread its own copy with `new Expression(existing)`; the copy is cheap because the parsed tokens are shared.
- `evaluate()` itself creates its own stack, so concurrent calls on an instance whose variables no longer change are safe.

![Asynchronous evaluation](uml/images/23_Sequence_Asynchronous_evaluation.png)

---

## 9. Building and testing

| | |
|---|---|
| Coordinates | `net.objecthunter:exp4j:0.4.20-SNAPSHOT` |
| Java | Compiled for release 26 (`maven.compiler.source/target` in `pom.xml`, branch `JDK26_Upgrade`) |
| Runtime dependencies | none |
| Test dependencies | JUnit 4.11, Logback 1.1.1 |

```bash
mvn test        # unit tests, including PerformanceTest (prints a benchmark table)
mvn package     # jar + sources jar
```

Notes on the build configuration:

- `maven-gpg-plugin` signs the artifacts in the `verify` phase. `mvn verify` and `mvn install` therefore need a GPG key, or `-Dgpg.skip`.
- The test classes are compiled into the module. `maven-compiler-plugin` adds `java.scripting` for test compilation only, because `PerformanceTest` uses `javax.script` and the library does not.
- The `attach-javadocs` execution in `<build>` is commented out. Javadoc is still available through `<reporting>` (`mvn site`).
- `PerformanceTest` skips its JavaScript benchmark because recent JDKs no longer ship a JSR-223 JavaScript engine.

| Test class | Covers |
|---|---|
| `ExpressionBuilderTest` | Most of the syntax, functions, operators and error cases |
| `ExpressionTest`, `ExpressionValidateTest` | Evaluation, variables, validation |
| `FunctionsTest` | Function names and argument counts |
| `TokenizerTest`, `TokenizerUnknownTokenOrVariableTest` | Tokens, implicit multiplication, error positions |
| `ShuntingYardTest` | RPN conversion |
| `ArrayStackTest` | The internal stack |
| `ConcurrencyTests` | Parallel evaluation |
| `PerformanceTest` | Benchmark against `Math` |
