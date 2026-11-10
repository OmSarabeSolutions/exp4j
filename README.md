exp4j
-----

**current dev version : 0.4.21-SNAPSHOT**

exp4j is a mathematical expression evaluator for the Java programming language. It is a simple-to-use and small library (~50kb) without any external dependencies.

Check out http://www.objecthunter.net/exp4j/ for documentation and examples

Have a look at the docs directory for more technical information.

- [Architecture and developer guide](docs/ARCHITECTURE.md)
- [UML model (StarUML) and diagrams](docs/uml/README.md)

```java
Expression e = new ExpressionBuilder("3 * sin(y) - 2 / (x - 2)")
        .variables("x", "y")
        .build()
        .setVariable("x", 2.3)
        .setVariable("y", 3.14);
double result = e.evaluate();
```

I took over the original version because I wanted a library that benefits from Java 26.
I passed the code through Claude.ai, then generated extensive documentation.

I looked at the performace, which was already good, and moved it to new levels with internal refactoring and the addition of a EvaluationStrategy.BYTECODE. The default still is EvaluationStrategy.INTERPRETED.
At this time, EvaluationStrategy.BYTECODE works only for non native code.

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
