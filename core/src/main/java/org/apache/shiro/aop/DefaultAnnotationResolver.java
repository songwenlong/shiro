/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.shiro.aop;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Default {@code AnnotationResolver} implementation that merely inspects the
 * {@link MethodInvocation MethodInvocation}'s {@link MethodInvocation#getMethod() target method},
 * and returns {@code targetMethod}.{@link Method#getAnnotation(Class) getAnnotation(class)}.
 * <p/>
 * 默认的AnnotationResolver实现，只检查MethodInvocation的目标方法，并返回targetMethod.getAnnotation(class)。
 * <p/>
 * Unfortunately Java's default reflection API for Annotations is not very robust, and this logic
 * may not be enough - if the incoming method invocation represents a method from an interface,
 * this default logic would not discover the annotation if it existed on the method implementation
 * directly (as opposed to being defined directly in the interface definition).
 * <p/>
 * 不幸的是，Java用于annotation的默认反射API不是很健壮，
 * 而且逻辑可能还不够——如果传入的方法调用表示 来自接口的方法，
 * 那么如果 注解直接存在于方法实现上(而不是直接在接口定义中定义)，这种默认逻辑就不会发现注释。
 * <p/>
 * More complex class hierarchy traversal logic is required to exhaust a method's target object's
 * classes, parent classes, interfaces and parent interfaces.  That logic will likely be added
 * to this implementation in due time, but for now, this implementation relies on the JDK's default
 * {@link Method#getAnnotation(Class) Method.getAnnotation(class)} logic.
 * <p/>
 * 需要更复杂的 类层次遍历逻辑 来遍历 方法的目标对象的类、父类、接口和父接口。
 * 该逻辑可能会在适当的时候添加到该实现中，但目前，该实现依赖于JDK的默认Method.getAnnotation(类)逻辑。
 * <p/>
 *
 * @since 1.1
 */
public class DefaultAnnotationResolver implements AnnotationResolver {

    /**
     * Returns {@code methodInvocation.}{@link org.apache.shiro.aop.MethodInvocation#getMethod() getMethod()}.{@link Method#getAnnotation(Class) getAnnotation(clazz)}.
     *
     * @param mi    the intercepted method to be invoked.
     * @param clazz the annotation class to use to find an annotation instance on the method.
     * @return the discovered annotation or {@code null} if an annotation instance could not be
     *         found.
     */
    public Annotation getAnnotation(MethodInvocation mi, Class<? extends Annotation> clazz) {
        if (mi == null) {
            throw new IllegalArgumentException("method argument cannot be null");
        }
        Method m = mi.getMethod();
        if (m == null) {
            String msg = MethodInvocation.class.getName() + " parameter incorrectly constructed.  getMethod() returned null";
            throw new IllegalArgumentException(msg);

        }
        //先从方法上获取注解
        Annotation annotation = m.getAnnotation(clazz);
        if (annotation == null ) {
            //方法上不存在指定注解，再从该方法所属的类上查找指定注解
            Object miThis = mi.getThis();
            //SHIRO-473 - miThis could be null for static methods, just return null
            //静态方法，miThis为null
            annotation = miThis != null ? miThis.getClass().getAnnotation(clazz) : null;
        }
        return annotation;
    }
}
