*exp4j is currently not in active development. I do not have time to maintain the project at the moment.*

exp4j
-----
exp4j is a mathematical expression evaluator for the Java programming language. It is a simple-to-use and small library (~50kb) without any external dependencies.

Check out http://www.objecthunter.net/exp4j/ for documentation and examples

```java
Expression e = new ExpressionBuilder("3 * sin(y) - 2 / (x - 2)")
        .variables("x", "y")
        .build()
        .setVariable("x", 2.3)
        .setVariable("y", 3.14);
double result = e.evaluate();
```

Performance
-----------
Expressions are compiled when they are built. Two evaluation strategies are available through
`ExpressionBuilder.strategy(...)`:

| Strategy | Evaluation speed (vs. the same formula in plain Java) | Use it for |
|---|---|---|
| `INTERPRETED` (default) | ~55–60 % | expressions evaluated a few times; cheap to build |
| `BYTECODE` | ~85–90 % | expressions evaluated many times; generates a hidden class with `java.lang.classfile` |

`BYTECODE` needs only `java.base` and works in jlink images. In a GraalVM native image it falls back to
`INTERPRETED`. See [Evaluation strategies and performance](docs/ARCHITECTURE.md#9-evaluation-strategies-and-performance).

Documentation
-------------
- [Architecture and developer guide](docs/ARCHITECTURE.md)
- [UML model (StarUML) and diagrams](docs/uml/README.md)
