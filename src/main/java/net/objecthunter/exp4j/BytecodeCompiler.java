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
import net.objecthunter.exp4j.internal.Builtins;
import net.objecthunter.exp4j.operator.Operator;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Translates a {@link CompiledExpression} into a hidden class whose {@code evaluate} method
 * is straight-line JVM bytecode, so that the JIT compiles the expression like hand-written Java.
 * <p>
 * Built-ins are emitted as direct calls to their tiny implementing method in {@code Builtins},
 * which the JIT always inlines, independently of how polluted the shared profiles are. Each variable is checked and
 * loaded into a local once, at its first use. Custom operators and functions are called
 * through {@code apply(double...)} on instances held by the generated object.
 * <p>
 * Only {@code java.base} is required ({@code java.lang.classfile}, {@code java.lang.invoke}),
 * so the strategy works in jlink-ed runtime images. Hidden classes are defined in this
 * module's package and are unloaded when their expression becomes unreachable.
 */
final class BytecodeCompiler {

    private static final ClassDesc CD_PROGRAM = ClassDesc.of(Program.class.getName());
    private static final ClassDesc CD_COMPILED = ClassDesc.of(CompiledExpression.class.getName());
    private static final ClassDesc CD_BUILTINS = ClassDesc.of(Builtins.class.getName());
    private static final ClassDesc CD_OPERATOR = ClassDesc.of(Operator.class.getName());
    private static final ClassDesc CD_FUNCTION = ClassDesc.of(Function.class.getName());
    private static final ClassDesc CD_OBJECT_ARRAY = ConstantDescs.CD_Object.arrayType();
    private static final ClassDesc CD_DOUBLE_ARRAY = ConstantDescs.CD_double.arrayType();

    private static final MethodTypeDesc MTD_EVALUATE = MethodTypeDesc.of(ConstantDescs.CD_double,
            CD_DOUBLE_ARRAY, ConstantDescs.CD_boolean.arrayType());
    private static final MethodTypeDesc MTD_INIT = MethodTypeDesc.of(ConstantDescs.CD_void, CD_OBJECT_ARRAY);
    private static final MethodTypeDesc MTD_UNARY = MethodTypeDesc.of(ConstantDescs.CD_double,
            ConstantDescs.CD_double);
    private static final MethodTypeDesc MTD_BINARY = MethodTypeDesc.of(ConstantDescs.CD_double,
            ConstantDescs.CD_double, ConstantDescs.CD_double);
    private static final MethodTypeDesc MTD_APPLY = MethodTypeDesc.of(ConstantDescs.CD_double, CD_DOUBLE_ARRAY);
    private static final MethodTypeDesc MTD_NOT_SET = MethodTypeDesc.of(
            ClassDesc.of(IllegalArgumentException.class.getName()), ConstantDescs.CD_String);

    private static final String CUSTOMS = "customs";

    /* local variable slots of evaluate(double[] values, boolean[] valueSet) */
    private static final int LOCAL_THIS = 0;
    private static final int LOCAL_VALUES = 1;
    private static final int LOCAL_VALUE_SET = 2;
    private static final int LOCAL_FIRST_VAR = 3;

    private BytecodeCompiler() {
    }

    /**
     * @return the generated program, or {@code null} if the runtime cannot define classes
     * (e.g. a GraalVM native image), in which case the caller keeps using the interpreter
     */
    static Program compile(final CompiledExpression program) {
        try {
            final MethodHandles.Lookup lookup = MethodHandles.lookup();
            final ClassDesc thisClass = ClassDesc.of(BytecodeCompiler.class.getPackageName() + ".GeneratedExpression");
            final byte[] bytes = ClassFile.of().build(thisClass, cb -> cb
                    .withFlags(ClassFile.ACC_FINAL | ClassFile.ACC_SYNTHETIC)
                    .withInterfaceSymbols(CD_PROGRAM)
                    .withField(CUSTOMS, CD_OBJECT_ARRAY, ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL)
                    .withMethodBody(ConstantDescs.INIT_NAME, MTD_INIT, ClassFile.ACC_PUBLIC, cob -> cob
                            .aload(0)
                            .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                            .aload(0)
                            .aload(1)
                            .putfield(thisClass, CUSTOMS, CD_OBJECT_ARRAY)
                            .return_())
                    .withMethodBody("evaluate", MTD_EVALUATE, ClassFile.ACC_PUBLIC,
                            cob -> emitEvaluate(cob, thisClass, program)));
            final Class<?> hidden = lookup.defineHiddenClass(bytes, true).lookupClass();
            final Object[] customs = customs(program);
            return (Program) lookup.findConstructor(hidden, MethodType.methodType(void.class, Object[].class))
                    .invoke(customs);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            // runtime class definition unavailable (GraalVM native image reports it with an Error subclass):
            // fall back to the interpreter
            return null;
        }
    }

    /* custom operators followed by custom functions, indexed as in emitEvaluate() */
    private static Object[] customs(final CompiledExpression program) {
        final Object[] customs = new Object[program.operators.length + program.functions.length];
        System.arraycopy(program.operators, 0, customs, 0, program.operators.length);
        System.arraycopy(program.functions, 0, customs, program.operators.length, program.functions.length);
        return customs;
    }

    private static void emitEvaluate(final CodeBuilder cob, final ClassDesc thisClass, final CompiledExpression program) {
        final int numVars = program.varNames.length;
        final int firstTemp = LOCAL_FIRST_VAR + 2 * numVars;
        final boolean[] loaded = new boolean[numVars];
        final int[] ops = program.ops;
        final int[] args = program.args;
        for (int i = 0; i < ops.length; i++) {
            final int arg = args[i];
            switch (ops[i]) {
                case CompiledExpression.OP_CONST:
                    cob.loadConstant(program.constants[arg]);
                    break;
                case CompiledExpression.OP_VAR: {
                    final int local = LOCAL_FIRST_VAR + 2 * arg;
                    if (!loaded[arg]) {
                        // first use, in RPN order like the interpreter: check it is set, then cache it
                        final Label set = cob.newLabel();
                        cob.aload(LOCAL_VALUE_SET).loadConstant(arg).baload().ifne(set)
                                .loadConstant(program.varNames[arg])
                                .invokestatic(CD_COMPILED, "variableNotSet", MTD_NOT_SET)
                                .athrow()
                                .labelBinding(set);
                        cob.aload(LOCAL_VALUES).loadConstant(arg).daload().dstore(local);
                        loaded[arg] = true;
                    }
                    cob.dload(local);
                    break;
                }
                case CompiledExpression.OP_UNARY:
                    cob.invokestatic(CD_BUILTINS, Builtins.UNARY_METHODS[arg], MTD_UNARY);
                    break;
                case CompiledExpression.OP_BINARY:
                    cob.invokestatic(CD_BUILTINS, Builtins.BINARY_METHODS[arg], MTD_BINARY);
                    break;
                case CompiledExpression.OP_CUSTOM_OPERATOR: {
                    final Operator op = program.operators[arg];
                    emitApply(cob, thisClass, firstTemp, arg, CD_OPERATOR, op.getNumOperands());
                    break;
                }
                case CompiledExpression.OP_CUSTOM_FUNCTION: {
                    final Function f = program.functions[arg];
                    emitApply(cob, thisClass, firstTemp, program.operators.length + arg, CD_FUNCTION,
                            f.getNumArguments());
                    break;
                }
                default:
                    throw new IllegalStateException("Unknown opcode " + ops[i]);
            }
        }
        cob.dreturn();
    }

    /* customs[index].apply(new double[] {operands...}) with the operands taken from the operand stack */
    private static void emitApply(final CodeBuilder cob, final ClassDesc thisClass, final int firstTemp,
                                  final int index, final ClassDesc owner, final int numArgs) {
        for (int k = numArgs - 1; k >= 0; k--) {
            cob.dstore(firstTemp + 2 * k);
        }
        cob.aload(LOCAL_THIS)
                .getfield(thisClass, CUSTOMS, CD_OBJECT_ARRAY)
                .loadConstant(index)
                .aaload()
                .checkcast(owner)
                .loadConstant(numArgs)
                .newarray(TypeKind.DOUBLE);
        for (int k = 0; k < numArgs; k++) {
            cob.dup().loadConstant(k).dload(firstTemp + 2 * k).dastore();
        }
        cob.invokevirtual(owner, "apply", MTD_APPLY);
    }
}
